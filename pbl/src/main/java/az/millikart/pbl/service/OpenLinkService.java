package az.millikart.pbl.service;

import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.domain.PaymentLinkStatus;
import az.millikart.pbl.domain.Terminal;
import az.millikart.pbl.domain.Transaction;
import az.millikart.pbl.domain.TransactionStatus;
import az.millikart.pbl.domain.UsageType;
import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.ConflictException;
import az.millikart.common.exception.InvalidStateException;
import az.millikart.common.exception.ResourceNotFoundException;

import az.millikart.pbl.provider.AcquiringClient;
import az.millikart.pbl.provider.dto.EcomCreateOrderResponse;
import az.millikart.pbl.repository.PaymentLinkRepository;
import az.millikart.pbl.repository.TerminalRepository;
import az.millikart.pbl.repository.TransactionRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class OpenLinkService {

    private static final Logger log = LoggerFactory.getLogger(OpenLinkService.class);

    // Слот использования ссылки занимают все состоявшиеся платежи (возврат слот НЕ освобождает,
    // Р-49) плюс AUTHORIZED по своему отдельному основанию (P1-6): холд станет платежом, как только
    // мерчант его спишет. Выводится из PAID_STATUSES, а не перечисляется заново, — иначе наборы
    // разъедутся, и одноразовую ссылку с живым холдом снова можно будет открыть.
    private static final Set<TransactionStatus> SLOT_OCCUPYING_STATUSES;

    static {
        EnumSet<TransactionStatus> statuses = EnumSet.copyOf(TransactionStatus.PAID_STATUSES);
        statuses.add(TransactionStatus.AUTHORIZED);
        SLOT_OCCUPYING_STATUSES = Collections.unmodifiableSet(statuses);
    }

    // Гасить при переоткрытии можно только PENDING: за любым другим незавершённым состоянием стоят
    // деньги на карте плательщика.
    private static final List<TransactionStatus> RETIRABLE_STATUSES = List.of(TransactionStatus.PENDING);

    // Отдельный отказ для ссылки, занятой холдом: «использована» и «ждёт списания» — разные
    // ситуации для мерчанта, и один текст на оба их скрывал.
    private static final String HOLD_BLOCKED_MESSAGE = "Payment link has an authorized payment awaiting capture";

    private final AcquiringClient acquiringClient;
    private final PaymentLinkRepository paymentLinkRepository;
    private final TransactionRepository transactionRepository;
    private final TerminalRepository terminalRepository;
    private final String baseUrl;

    public OpenLinkService(AcquiringClient acquiringClient,
                           PaymentLinkRepository paymentLinkRepository,
                           TransactionRepository transactionRepository,
                           TerminalRepository terminalRepository,
                           @Value("${pbl.base-url}") String baseUrl) {
        this.acquiringClient = acquiringClient;
        this.paymentLinkRepository = paymentLinkRepository;
        this.transactionRepository = transactionRepository;
        this.terminalRepository = terminalRepository;
        this.baseUrl = baseUrl;
    }

    // Одна транзакция, и она начинается с блокировки строки ссылки (P1-5). Раньше проверка и вставка
    // жили в разных транзакциях с HTTP-вызовом между ними: два одновременных открытия одноразовой
    // ссылки оба проходили проверку «ещё не оплачена», и ссылку можно было оплатить дважды. Размены
    // (блокировка держится на время похода к эквайеру; заказ у эквайера при сбое коммита) — §10.
    @Transactional
    public String openAndBuildRedirect(UUID id, String clientIp, String userAgent) {
        log.info("Opening payment link session for ID: {}, clientIp: {}, userAgent: {}", id, clientIp, userAgent);

        // Прочитано до запроса блокировки: всё, что создано позже этого момента, появилось, пока
        // запрос стоял в очереди за другим открытием той же ссылки (проверка дубля ниже).
        Instant openedAt = Instant.now();

        PaymentLink link = lockLinkOrThrow(id);

        // Терминал читается здесь — под блокировкой ссылки и до любой записи, а не там, где нужны
        // его учётные данные (P2-8). Прочитаешь до блокировки — блокировка терминала, случившаяся в
        // этот момент, останется невидимой, и платёж начнётся на снятом с обслуживания терминале.
        // Статус SUSPENDED самой ссылки проверяется тут же: блокировка ставит оба.
        Terminal terminal = loadTerminalOrThrow(link.getTerminalId(), id);
        if (terminal.isBlocked() || link.getStatus() == PaymentLinkStatus.SUSPENDED) {
            // Тот же отказ, что для любой недоступной ссылки, без упоминания терминала: какой
            // терминал мерчант снял с обслуживания — не дело плательщика.
            log.warn("Refusing to open link {}: terminal {} is {}, link is {}",
                    id, terminal.getId(), terminal.getStatus(), link.getStatus());
            throw new InvalidStateException("Payment link is not available for payment");
        }

        if (link.getStatus() == PaymentLinkStatus.CANCELED
                || link.getStatus() == PaymentLinkStatus.COMPLETED) {
            log.warn("Payment link {} is not available in status: {}", id, link.getStatus());
            throw new InvalidStateException("Payment link is not available for payment (status: " + link.getStatus() + ")");
        }

        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(Instant.now())) {
            log.info("Payment link {} has expired, changing status to EXPIRED", id);
            // Эта запись и COMPLETED ниже откатываются следующим за ними отказом — транзакция одна.
            // Долговременные переходы ACTIVE → EXPIRED / COMPLETED делают PaymentLinkScheduler и
            // платёжный путь, а не эта ветка.
            link.setStatus(PaymentLinkStatus.EXPIRED);
            paymentLinkRepository.save(link);
            throw new InvalidStateException("Payment link has expired");
        }
        if (link.getStatus() == PaymentLinkStatus.EXPIRED) {
            log.warn("Payment link {} has expired", id);
            throw new InvalidStateException("Payment link has expired");
        }

        // Живая авторизация идёт в лимит наравне с прошедшими платежами (P1-6).
        long occupiedSlots = transactionRepository.countByLinkIdAndStatusIn(id, SLOT_OCCUPYING_STATUSES);

        // Состоявшиеся платежи (возвращённые в том числе, Р-49) отличаются от живых холдов только
        // ради выбора текста отказа.
        if (link.getUsageType() == UsageType.SINGLE && occupiedSlots > 0) {
            if (transactionRepository.countByLinkIdAndStatusIn(id, TransactionStatus.PAID_STATUSES) > 0) {
                log.warn("Single-use payment link {} was already paid (a refund does not reopen it)", id);
                throw new InvalidStateException("Single-use payment link has already been used");
            }
            log.warn("Single-use payment link {} is held by an authorized payment awaiting capture", id);
            throw new InvalidStateException(HOLD_BLOCKED_MESSAGE);
        }
        if (link.getUsageType() == UsageType.MULTIPLE && link.getMaxPayments() != null && occupiedSlots >= link.getMaxPayments()) {
            long paidPayments = transactionRepository.countByLinkIdAndStatusIn(id, TransactionStatus.PAID_STATUSES);
            if (paidPayments >= link.getMaxPayments()) {
                log.info("Payment link {} has reached max payments limit: {}, setting status to COMPLETED", id, link.getMaxPayments());
                link.setStatus(PaymentLinkStatus.COMPLETED);
                paymentLinkRepository.save(link);
                throw new InvalidStateException("Payment link has reached its usage limit");
            }
            log.warn("Payment link {} has {} of {} slots taken, {} of them by payments awaiting capture",
                    id, occupiedSlots, link.getMaxPayments(), occupiedSlots - paidPayments);
            throw new InvalidStateException(HOLD_BLOCKED_MESSAGE);
        }

        // Гасим прошлую незавершённую попытку: эквайер выдаёт одну сессию на заказ. Только PENDING
        // (P1-6) — пометка AUTHORIZED как FAILED у эквайера не меняла ничего: холд оставался, деньги
        // держателя были заморожены, а портал считал платёж неуспешным, и мерчант не мог ни списать
        // его, ни отменить. Снять холд мы тоже не умеем (Void в AcquiringClient нет) — AGENTS.md §10.
        Optional<Transaction> previousAttempt = transactionRepository
                .findFirstByLinkIdAndStatusInOrderByCreatedAtDesc(id, RETIRABLE_STATUSES);

        if (previousAttempt.isPresent()) {
            Transaction attempt = previousAttempt.get();
            // Создана после начала этого запроса, то есть пока он ждал блокировку: это второй клик
            // по той же ссылке, а не брошенная сессия. Погасить её и зарегистрировать ещё один заказ
            // значило бы оставить плательщику два живых заказа на одной ссылке.
            if (attempt.getCreatedAt() != null && attempt.getCreatedAt().isAfter(openedAt)) {
                log.warn("Refusing a duplicate open of link {}: attempt {} was registered while this request waited for the link lock",
                        id, attempt.getId());
                throw new ConflictException("A payment session for this link is already being opened");
            }
            log.info("Found uncompleted transaction {} for link {}. Marking as FAILED to issue a fresh Millikart session.", attempt.getId(), id);
            attempt.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(attempt);
        }

        UUID ridByMerchant = UUID.randomUUID();

        log.info("Registering fresh order at provider for link: {}, terminal: {}, ridByMerchant: {}, clientIp: {}", id, terminal.getId(), ridByMerchant, clientIp);

        String hppRedirectUrl = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/api/v1/payment-links/redirect/{tx}")
                .buildAndExpand(ridByMerchant.toString())
                .toUriString();

        log.debug("Using redirect URL for provider: {}", hppRedirectUrl);

        EcomCreateOrderResponse response = acquiringClient.createEcomOrder(link, terminal.getLogin(), terminal.getPassword(), ridByMerchant, hppRedirectUrl);
        if (response == null || response.order() == null) {
            log.error("Failed to register order at provider for ridByMerchant: {}", ridByMerchant);
            throw new BusinessException("Failed to register order with provider");
        }

        log.info("Fresh order registered at provider. ProviderOrderId: {}, redirecting user to HPP.", response.order().id());

        Transaction transaction = Transaction.builder()
                .link(link)
                .ridByMerchant(ridByMerchant)
                .providerOrderId(String.valueOf(response.order().id()))
                .providerPassword(response.order().password())
                .amount(link.getAmount())
                .status(TransactionStatus.PENDING)
                .clientIp(clientIp)
                .userAgent(userAgent)
                // P0-9: пароля заказа здесь нет. Он живёт в своей колонке (providerPassword выше),
                // откуда его и читают последующие вызовы; JSON-колонка — след для разбора споров.
                .providerResponse(Map.of(
                        "hppUrl", response.order().hppUrl(),
                        "id", response.order().id(),
                        "status", response.order().status()
                ))
                .build();
        transactionRepository.save(transaction);
        log.debug("Persisted new PENDING transaction: {} for ridByMerchant: {}, clientIp: {}", transaction.getId(), ridByMerchant, clientIp);

        // Пароль остаётся в редиректе плательщика — он и открывает платёжную страницу (§5.3). Отсюда
        // и дальше этот адрес не логировать: контроллер пишет его через ProviderPayloads.urlForLog.
        return response.order().hppUrl() + "?id=" + response.order().id() + "&password=" + response.order().password();
    }

    // Блокировка строки здесь и сериализует одновременные открытия одной ссылки: всё, что делает
    // вызывающий дальше, идёт без второго открытия той же ссылки.
    private PaymentLink lockLinkOrThrow(UUID id) {
        return paymentLinkRepository.findWithLockById(id)
                .orElseThrow(() -> {
                    log.warn("Payment link not found for ID: {}", id);
                    return new ResourceNotFoundException("Payment link not found: " + id);
                });
    }

    // Читать только после блокировки строки ссылки — почему, написано в месте вызова.
    private Terminal loadTerminalOrThrow(Integer terminalId, UUID linkId) {
        return terminalRepository.findById(terminalId)
                .orElseThrow(() -> {
                    log.error("Terminal {} configuration is missing for link {}", terminalId, linkId);
                    return new BusinessException("Terminal configuration is not configured");
                });
    }
}
