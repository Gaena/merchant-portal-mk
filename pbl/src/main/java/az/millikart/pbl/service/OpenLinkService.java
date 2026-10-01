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
import az.millikart.pbl.provider.ProviderOrderStatus.ProviderOrderOutcome;
import az.millikart.pbl.provider.dto.EcomCreateOrderResponse;
import az.millikart.pbl.repository.PaymentLinkRepository;
import az.millikart.pbl.repository.TerminalRepository;
import az.millikart.pbl.repository.TransactionRepository;
import az.millikart.pbl.service.PaymentLinkService.StatusRefresh;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    // Прошлую попытку сверять с эквайером, не гасить вслепую: заказ у него живёт ещё ~10 минут (Р-71),
    // а FAILED никто не опрашивает — оплата потеряется.
    private static final List<TransactionStatus> UNSETTLED_ATTEMPT_STATUSES = List.of(TransactionStatus.PENDING);

    // У ссылки, занятой холдом, свой текст отказа: «использована» и «ждёт списания» — разные ситуации.
    private static final String HOLD_BLOCKED_MESSAGE = "Payment link has an authorized payment awaiting capture";

    // Неоплаченный заказ провайдер закрывает через 10 минут (Р-71, Р-75): попытка старше окна живой быть не
    // может, а оплаченную старую добирает сверка. Окно — с тройным запасом (Р-112).
    private static final Duration LIVE_ORDER_WINDOW = Duration.ofMinutes(30);

    private static final String SESSION_OPEN_MESSAGE =
            "A payment session for this link is already open; complete it or try again in about 10 minutes";
    private static final String SESSION_UNCHECKED_MESSAGE =
            "The previous payment session for this link could not be checked; try again in a minute";

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
    // похода к эквайеру (AGENTS §10) и заказ у эквайера без нашей строки при сбое коммита. Отказ её
    // коммитит: найденное опросом эквайера — факт, и откат вернул бы оплаченную попытку в PENDING (Р-113).
    @Transactional(noRollbackFor = {InvalidStateException.class, ConflictException.class, BusinessException.class})
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
            // Остаётся и после отказа, как COMPLETED ниже (Р-113).
            link.setStatus(PaymentLinkStatus.EXPIRED);
            paymentLinkRepository.save(link);
            throw new InvalidStateException("Payment link has expired");
        }
        if (link.getStatus() == PaymentLinkStatus.EXPIRED) {
            log.warn("Payment link {} has expired", id);
            throw new InvalidStateException("Payment link has expired");
        }

        // Прошлые PENDING — к эквайеру до подсчёта слотов: оплаченная по старой странице занимает слот.
        // Неоплаченные остаются PENDING до сверки.
        List<Transaction> unsettled = unsettledAttempts(link, openedAt);
        // Создана после начала запроса — второй одновременный клик, а не брошенная сессия: ещё один
        // заказ дал бы плательщику два живых заказа на одной ссылке.
        if (!unsettled.isEmpty() && unsettled.getFirst().getCreatedAt() != null
                && unsettled.getFirst().getCreatedAt().isAfter(openedAt)) {
            log.warn("Refusing a duplicate open of link {}: attempt {} was registered while this request waited for the link lock",
                    id, unsettled.getFirst().getId());
            throw new ConflictException("A payment session for this link is already being opened");
        }
        boolean sessionOpen = false;
        boolean sessionUnchecked = false;
        for (Transaction attempt : unsettled) {
            Optional<ProviderOrderOutcome> outcome = refreshAtAcquirer(attempt, id).map(StatusRefresh::outcome);
            if (outcome.isPresent() && outcome.get() == ProviderOrderOutcome.NON_FINAL) {
                sessionOpen = true;
            } else if ((outcome.isEmpty() || outcome.get() == ProviderOrderOutcome.UNKNOWN)
                    && mayStillBeLive(attempt, openedAt)) {
                sessionUnchecked = true;
            }
        }

        // Живая авторизация идёт в лимит наравне с прошедшими платежами (P1-6).
        long occupiedSlots = transactionRepository.countByLinkIdAndStatusIn(id, TransactionStatus.SLOT_OCCUPYING_STATUSES);
        if (slotsTaken(link, occupiedSlots) && holdReleasedAtAcquirer(id)) {
            occupiedSlots = transactionRepository.countByLinkIdAndStatusIn(id, TransactionStatus.SLOT_OCCUPYING_STATUSES);
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
        // Второй заказ одноразовой ссылки, пока жив первый, — два оплачиваемых заказа (OPEN-DOUBLE-PAY, Р-112).
        // Вернуть плательщика на тот же заказ нельзя: повторно его страницу провайдер не открывает.
        if (link.getUsageType() == UsageType.SINGLE && sessionOpen) {
            log.warn("Refusing to open single-use link {}: an earlier payment session is still open at the acquirer", id);
            throw new ConflictException(SESSION_OPEN_MESSAGE);
        }
        if (link.getUsageType() == UsageType.SINGLE && sessionUnchecked) {
            log.warn("Refusing to open single-use link {}: an earlier payment session younger than {} could not be "
                    + "checked at the acquirer", id, LIVE_ORDER_WINDOW);
            throw new ConflictException(SESSION_UNCHECKED_MESSAGE);
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

    // Многоразовой — только последняя: у каждого плательщика свой заказ, и опрос всех чужих сессий под
    // замком ссылки задержал бы всех. Одноразовой — все за окно (Р-112): оплаченная ранняя иначе не заняла
    // бы слот до сверки. Последняя — в любом случае, как бы стара ни была.
    private List<Transaction> unsettledAttempts(PaymentLink link, Instant openedAt) {
        Optional<Transaction> latest = transactionRepository
                .findFirstByLinkIdAndStatusInOrderByCreatedAtDesc(link.getId(), UNSETTLED_ATTEMPT_STATUSES);
        if (latest.isEmpty() || link.getUsageType() != UsageType.SINGLE) {
            return latest.map(List::of).orElse(List.of());
        }
        List<Transaction> recent = transactionRepository.findByLinkIdAndStatusInAndCreatedAtAfterOrderByCreatedAtDesc(
                link.getId(), UNSETTLED_ATTEMPT_STATUSES, openedAt.minus(LIVE_ORDER_WINDOW));
        return recent.isEmpty() ? List.of(latest.get()) : recent;
    }

    private static boolean mayStillBeLive(Transaction attempt, Instant openedAt) {
        return attempt.getCreatedAt() == null || attempt.getCreatedAt().isAfter(openedAt.minus(LIVE_ORDER_WINDOW));
    }

    // Слоты заняты, а последний холд мог давно снять банк (Closed ← Authorized, Р-75): спросить
    // эквайера дешевле, чем навсегда отказывать ссылке. true — холд больше не AUTHORIZED.
    private boolean holdReleasedAtAcquirer(UUID linkId) {
        return transactionRepository
                .findFirstByLinkIdAndStatusInOrderByCreatedAtDesc(linkId, List.of(TransactionStatus.AUTHORIZED))
                .map(hold -> refreshAtAcquirer(hold, linkId)
                        .map(refresh -> refresh.transaction().getStatus() != TransactionStatus.AUTHORIZED)
                        .orElse(false))
                .orElse(false);
    }

    // Пусто — эквайер не ответил: попытка остаётся в прежнем статусе, её дожмут сверка и /status.
    private Optional<StatusRefresh> refreshAtAcquirer(Transaction attempt, UUID linkId) {
        try {
            StatusRefresh refresh = paymentLinkService.refreshStatus(attempt);
            log.info("Attempt {} of link {} is {} at the acquirer", attempt.getId(), linkId,
                    refresh.transaction().getStatus());
            return Optional.of(refresh);
        } catch (RuntimeException e) {
            log.warn("Could not refresh attempt {} of link {} at the acquirer: {}; leaving it {}",
                    attempt.getId(), linkId, e.getMessage(), attempt.getStatus());
            return Optional.empty();
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
