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

    // Слот занимают PAID_STATUSES (возврат слот НЕ освобождает, Р-49) и AUTHORIZED — будущий платёж
    // (P1-6). Выводится из PAID_STATUSES, а не перечисляется заново: разъедутся наборы — одноразовую
    // ссылку с живым холдом откроют повторно.
    private static final Set<TransactionStatus> SLOT_OCCUPYING_STATUSES;

    static {
        EnumSet<TransactionStatus> statuses = EnumSet.copyOf(TransactionStatus.PAID_STATUSES);
        statuses.add(TransactionStatus.AUTHORIZED);
        SLOT_OCCUPYING_STATUSES = Collections.unmodifiableSet(statuses);
    }

    // Прошлую попытку сверять с эквайером, не гасить вслепую: заказ у него живёт ещё ~10 минут (Р-71),
    // а FAILED никто не опрашивает — оплата потеряется.
    private static final List<TransactionStatus> UNSETTLED_ATTEMPT_STATUSES = List.of(TransactionStatus.PENDING);

    // У ссылки, занятой холдом, свой текст отказа: «использована» и «ждёт списания» — разные ситуации.
    private static final String HOLD_BLOCKED_MESSAGE = "Payment link has an authorized payment awaiting capture";

    // Ширина transactions.user_agent. Длиннее — обрезаем: строка пишется уже после заказа у провайдера, и
    // отказ базы оставил бы плательщика без платёжной страницы (DB-CONSTRAINT-500).
    private static final int USER_AGENT_MAX_LENGTH = 512;

    private final AcquiringClient acquiringClient;
    private final PaymentLinkRepository paymentLinkRepository;
    private final TransactionRepository transactionRepository;
    private final TerminalRepository terminalRepository;
    private final PaymentLinkService paymentLinkService;
    private final ProviderCredentialsService providerCredentials;
    private final String baseUrl;

    public OpenLinkService(AcquiringClient acquiringClient,
                           PaymentLinkRepository paymentLinkRepository,
                           TransactionRepository transactionRepository,
                           TerminalRepository terminalRepository,
                           PaymentLinkService paymentLinkService,
                           ProviderCredentialsService providerCredentials,
                           @Value("${pbl.base-url}") String baseUrl) {
        this.acquiringClient = acquiringClient;
        this.paymentLinkRepository = paymentLinkRepository;
        this.transactionRepository = transactionRepository;
        this.terminalRepository = terminalRepository;
        this.paymentLinkService = paymentLinkService;
        this.providerCredentials = providerCredentials;
        this.baseUrl = baseUrl;
    }

    // Одна транзакция от блокировки строки ссылки до записи попытки (P1-5): иначе два одновременных
    // открытия одноразовой ссылки оба пройдут проверку, и её оплатят дважды. Цена — блокировка на время
    // похода к эквайеру (AGENTS §10) и заказ у эквайера без нашей строки при сбое коммита.
    @Transactional
    public String openAndBuildRedirect(UUID id, String clientIp, String userAgent) {
        // До блокировки: попытка, созданная позже, — от одновременного открытия той же ссылки (ниже).
        Instant openedAt = Instant.now();

        PaymentLink link = lockLinkOrThrow(id);

        // Терминал — только после блокировки ссылки (P2-8): прочитанный раньше не увидит блокировку
        // терминала, закоммиченную в этот момент, и платёж начнётся на заблокированном. SUSPENDED
        // ссылки — здесь же: блокировка терминала ставит оба.
        Terminal terminal = loadTerminalOrThrow(link.getTerminalId(), id);
        if (terminal.isBlocked() || link.getStatus() == PaymentLinkStatus.SUSPENDED) {
            // Отказ как у любой недоступной ссылки: про терминал плательщику знать незачем.
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
            // Эта запись и COMPLETED ниже откатываются следующим отказом. Надолго EXPIRED и COMPLETED
            // ставят PaymentLinkScheduler и платёжный путь.
            link.setStatus(PaymentLinkStatus.EXPIRED);
            paymentLinkRepository.save(link);
            throw new InvalidStateException("Payment link has expired");
        }
        if (link.getStatus() == PaymentLinkStatus.EXPIRED) {
            log.warn("Payment link {} has expired", id);
            throw new InvalidStateException("Payment link has expired");
        }

        // Прошлая PENDING — к эквайеру до подсчёта слотов: оплаченная по старой странице занимает слот.
        // Неоплаченная остаётся PENDING до сверки.
        Optional<Transaction> previousAttempt = transactionRepository
                .findFirstByLinkIdAndStatusInOrderByCreatedAtDesc(id, UNSETTLED_ATTEMPT_STATUSES);
        if (previousAttempt.isPresent()) {
            Transaction attempt = previousAttempt.get();
            // Создана после начала запроса — второй одновременный клик, а не брошенная сессия: ещё один
            // заказ дал бы плательщику два живых заказа на одной ссылке.
            if (attempt.getCreatedAt() != null && attempt.getCreatedAt().isAfter(openedAt)) {
                log.warn("Refusing a duplicate open of link {}: attempt {} was registered while this request waited for the link lock",
                        id, attempt.getId());
                throw new ConflictException("A payment session for this link is already being opened");
            }
            refreshAtAcquirer(attempt, id);
        }

        // Живая авторизация идёт в лимит наравне с прошедшими платежами (P1-6).
        long occupiedSlots = transactionRepository.countByLinkIdAndStatusIn(id, SLOT_OCCUPYING_STATUSES);
        if (slotsTaken(link, occupiedSlots) && holdReleasedAtAcquirer(id)) {
            occupiedSlots = transactionRepository.countByLinkIdAndStatusIn(id, SLOT_OCCUPYING_STATUSES);
        }

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

        UUID ridByMerchant = UUID.randomUUID();

        log.debug("Registering an order at the provider for link {}, terminal {}, ridByMerchant {}", id, terminal.getId(), ridByMerchant);

        String hppRedirectUrl = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/api/v1/payment-links/redirect/{tx}")
                .buildAndExpand(ridByMerchant.toString())
                .toUriString();

        log.debug("Using redirect URL for provider: {}", hppRedirectUrl);

        EcomCreateOrderResponse response = acquiringClient.createEcomOrder(link, providerCredentials.forTerminal(terminal),
                providerCredentials.terminalRidOf(terminal), ridByMerchant, hppRedirectUrl);
        if (response == null || response.order() == null) {
            log.error("Failed to register order at provider for ridByMerchant: {}", ridByMerchant);
            throw new BusinessException("Failed to register order with provider");
        }

        Transaction transaction = Transaction.builder()
                .link(link)
                .ridByMerchant(ridByMerchant)
                .providerOrderId(String.valueOf(response.order().id()))
                .providerPassword(response.order().password())
                .amount(link.getAmount())
                .status(TransactionStatus.PENDING)
                .clientIp(clientIp)
                .userAgent(fitUserAgent(userAgent))
                // Пароля заказа здесь нет и не класть: он только в provider_password (P0-9).
                .providerResponse(Map.of(
                        "hppUrl", response.order().hppUrl(),
                        "id", response.order().id(),
                        "status", response.order().status()
                ))
                .build();
        transactionRepository.save(transaction);
        // Одна строка на открытие: адрес плательщика — в MDC, запрос и ответ провайдера — на DEBUG.
        log.info("Link {} opened: attempt {}, provider order {}, terminal {}, user agent: {}",
                id, transaction.getId(), response.order().id(), terminal.getId(), transaction.getUserAgent());

        // Пароль нужен плательщику для платёжной страницы (§5.3). Адрес не логировать — только через
        // ProviderPayloads.urlForLog, как в контроллере.
        return response.order().hppUrl() + "?id=" + response.order().id() + "&password=" + response.order().password();
    }

    // Суррогатную пару не разрезаем: половинка символа ушла бы в базу мусором.
    private static String fitUserAgent(String userAgent) {
        if (userAgent == null || userAgent.length() <= USER_AGENT_MAX_LENGTH) {
            return userAgent;
        }
        int end = Character.isHighSurrogate(userAgent.charAt(USER_AGENT_MAX_LENGTH - 1))
                ? USER_AGENT_MAX_LENGTH - 1
                : USER_AGENT_MAX_LENGTH;
        return userAgent.substring(0, end);
    }

    private static boolean slotsTaken(PaymentLink link, long occupiedSlots) {
        return (link.getUsageType() == UsageType.SINGLE && occupiedSlots > 0)
                || (link.getUsageType() == UsageType.MULTIPLE && link.getMaxPayments() != null
                        && occupiedSlots >= link.getMaxPayments());
    }

    // Слоты заняты, а последний холд мог давно снять банк (Closed ← Authorized, Р-75): спросить
    // эквайера дешевле, чем навсегда отказывать ссылке. true — холд больше не AUTHORIZED.
    private boolean holdReleasedAtAcquirer(UUID linkId) {
        return transactionRepository
                .findFirstByLinkIdAndStatusInOrderByCreatedAtDesc(linkId, List.of(TransactionStatus.AUTHORIZED))
                .map(hold -> refreshAtAcquirer(hold, linkId) != TransactionStatus.AUTHORIZED)
                .orElse(false);
    }

    // Сбой опроса не мешает открытию: попытка остаётся в прежнем статусе, её дожмут сверка и /status.
    private TransactionStatus refreshAtAcquirer(Transaction attempt, UUID linkId) {
        try {
            TransactionStatus status = paymentLinkService.refreshStatus(attempt).transaction().getStatus();
            log.info("Attempt {} of link {} is {} at the acquirer", attempt.getId(), linkId, status);
            return status;
        } catch (RuntimeException e) {
            log.warn("Could not refresh attempt {} of link {} at the acquirer: {}; leaving it {}",
                    attempt.getId(), linkId, e.getMessage(), attempt.getStatus());
            return attempt.getStatus();
        }
    }

    // Сериализует открытия ссылки: второе одновременное получает 409 сразу (NOWAIT, Р-85).
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
