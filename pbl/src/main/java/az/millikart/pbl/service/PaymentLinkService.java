package az.millikart.pbl.service;

import az.millikart.pbl.domain.CustomerPhone;
import java.util.function.Supplier;
import az.millikart.common.exception.ConflictException;
import az.millikart.pbl.repository.MoneyOperationAttemptRepository;
import az.millikart.pbl.dto.TransactionActions;
import az.millikart.pbl.domain.PaymentType;
import az.millikart.pbl.domain.MoneyOperationAttempt;
import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.domain.PaymentLinkStatus;
import az.millikart.pbl.domain.Terminal;
import az.millikart.pbl.domain.Transaction;
import az.millikart.pbl.domain.TransactionRefund;
import az.millikart.pbl.domain.TransactionStatus;
import az.millikart.pbl.domain.UsageType;
import az.millikart.pbl.dto.CompleteDmsRequest;
import az.millikart.pbl.dto.CreatePaymentLinkRequest;
import az.millikart.pbl.dto.CustomerDto;
import az.millikart.common.dto.PagedResponse;
import az.millikart.pbl.dto.PaymentLinkResponse;
import az.millikart.pbl.dto.PaymentLinkSummaryResponse;
import az.millikart.pbl.dto.PaymentReceiptView;
import az.millikart.pbl.dto.RefundRequest;
import az.millikart.pbl.dto.RefundResponse;
import az.millikart.pbl.dto.TransactionResponse;
import az.millikart.pbl.dto.UpdatePaymentLinkRequest;
import az.millikart.common.audit.AuditAction;
import az.millikart.common.audit.AuditEntity;
import az.millikart.common.audit.AuditEvent;
import az.millikart.common.audit.AuditLogService;
import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.InvalidStateException;
import az.millikart.common.exception.PaymentOutcomeUnknownException;
import az.millikart.common.exception.ResourceNotFoundException;

import az.millikart.txpg.AcquiringClient;
import az.millikart.txpg.ProviderCredentials;
import az.millikart.pbl.provider.ProviderDeclineReason;
import az.millikart.pbl.provider.ProviderOrderDetails;
import az.millikart.pbl.provider.ProviderOrderDetails.TransactionFacts;
import az.millikart.pbl.provider.ProviderOrderStatus;
import az.millikart.pbl.provider.ProviderOrderStatus.ProviderOrderOutcome;
import az.millikart.txpg.ProviderPayloads;
import az.millikart.txpg.dto.MoneyOperationResult;
import az.millikart.pbl.repository.PaymentLinkRepository;
import az.millikart.pbl.repository.TerminalRepository;
import az.millikart.pbl.repository.TransactionRefundRepository;
import az.millikart.pbl.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import az.millikart.common.security.Role;
import az.millikart.common.security.UserPrincipal;

@Service
public class PaymentLinkService {

    private static final Logger log = LoggerFactory.getLogger(PaymentLinkService.class);

    // Незнакомый или внешний статус заказа — WARN один раз на пару «транзакция, статус»: сверка спрашивает
    // раз в 2 минуты до 7 дней (Р-98). Память процесса: после рестарта — ещё раз.
    private static final int NOTICED_STATUSES_CEILING = 10_000;
    private final Map<UUID, String> noticedProviderStatuses = new ConcurrentHashMap<>();

    private final PaymentLinkRepository paymentLinkRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionRefundRepository transactionRefundRepository;
    private final MoneyOperationAttemptRepository attemptRepository;
    private final TerminalRepository terminalRepository;
    private final AcquiringClient acquiringClient;
    private final ProviderCredentialsService providerCredentials;
    private final PaymentLinkMapper mapper;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate txTemplate;
    private final String baseUrl;
    private final Duration defaultLinkTtl;
    private final Duration maxLinkTtl;

    public PaymentLinkService(PaymentLinkRepository paymentLinkRepository,
                               TransactionRepository transactionRepository,
                               TransactionRefundRepository transactionRefundRepository,
                               MoneyOperationAttemptRepository attemptRepository,
                               TerminalRepository terminalRepository,
                               AcquiringClient acquiringClient,
                               ProviderCredentialsService providerCredentials,
                               PaymentLinkMapper mapper,
                               AuditLogService auditLogService,
                               ApplicationEventPublisher eventPublisher,
                               PlatformTransactionManager transactionManager,
                               @Value("${pbl.base-url}") String baseUrl,
                               @Value("${pbl.link.default-ttl}") Duration defaultLinkTtl,
                               @Value("${pbl.link.max-ttl}") Duration maxLinkTtl) {
        this.paymentLinkRepository = paymentLinkRepository;
        this.transactionRepository = transactionRepository;
        this.transactionRefundRepository = transactionRefundRepository;
        this.attemptRepository = attemptRepository;
        this.terminalRepository = terminalRepository;
        this.acquiringClient = acquiringClient;
        this.providerCredentials = providerCredentials;
        this.mapper = mapper;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.baseUrl = baseUrl;
        this.defaultLinkTtl = defaultLinkTtl;
        this.maxLinkTtl = maxLinkTtl;
    }

    public PaymentLinkResponse create(CreatePaymentLinkRequest request, UserPrincipal principal) {
        log.info("Request to create payment link: merchantOrderId={}, terminal={}, amount={}, currency={}",
                request.merchantOrderId(), request.terminal(), request.amount(), request.currency());

        String terminalCompanyId = validateAccess(request.terminal(), principal, LINK_WRITE_ROLES).getCompanyId();

        // На заблокированном терминале ссылка родилась бы нерабочей: открытие её отвергнет (Р-38).
        Terminal terminal = terminalRepository.findById(request.terminal())
                .orElseThrow(() -> new ResourceNotFoundException("Terminal not found: " + request.terminal()));
        if (terminal.isBlocked()) {
            log.warn("Refusing to create a payment link on blocked terminal {}", request.terminal());
            throw new BusinessException("terminal " + request.terminal()
                    + " is blocked and cannot take new payments; unblock it or use another terminal");
        }
        // Без кредов компании и номера терминала ссылка тоже родилась бы нерабочей (Р-93, Р-96).
        providerCredentials.forTerminal(terminal);
        providerCredentials.terminalRidOf(terminal);

        CustomerDto customer = request.customer();
        requireCustomerAllowed(request.usageType(), customer);
        String customerPhone = normalizedPhone(customer);
        String providerRef = "RID-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Срок есть у каждой ссылки (P1-9): без него планировщик не находит просроченных.
        Instant createdAt = Instant.now();
        Instant expiresAt = request.expiresAt() != null
                ? validateExpiresAt(request.expiresAt(), createdAt)
                : createdAt.plus(defaultLinkTtl);
        log.debug("Payment link will expire at {} ({})", expiresAt,
                request.expiresAt() != null ? "requested by the merchant" : "default TTL " + defaultLinkTtl);

        PaymentLink link = PaymentLink.builder()
                .providerReference(providerRef)
                .merchantOrderId(request.merchantOrderId())
                .terminalId(request.terminal())
                .amount(request.amount())
                .currency(request.currency())
                .description(request.description())
                .customerName(customer != null ? customer.fullName() : null)
                .customerEmail(customer != null ? customer.email() : null)
                .customerPhone(customerPhone)
                .paymentType(request.paymentType())
                .usageType(request.usageType())
                .maxPayments(request.usageType() == UsageType.MULTIPLE ? request.maxPayments() : null)
                // Дальше колонку переписывает счёт по PAID_STATUSES — то же число, что отдаёт API (P2-16).
                .currentPaymentsCount(0)
                .status(PaymentLinkStatus.ACTIVE)
                .metadata(request.metadata())
                .expiresAt(expiresAt)
                .build();

        PaymentLink saved = txTemplate.execute(status -> paymentLinkRepository.saveAndFlush(link));

        // txTemplate закоммитил запись выше, поэтому fallbackExecution пишет событие сразу (Р-35).
        eventPublisher.publishEvent(AuditEvent.of(AuditEntity.PAYMENT_LINK, saved.getId().toString(), AuditAction.CREATE,
                UserPrincipal.getUsername(principal), terminalCompanyId,
                "Created " + saved.getUsageType() + " " + saved.getPaymentType() + " link for "
                        + saved.getAmount() + " " + saved.getCurrency() + " on terminal "
                        + saved.getTerminalId() + ", expires " + saved.getExpiresAt()));

        log.info("Payment link created successfully with ID: {} and provider reference: {}", saved.getId(), providerRef);
        return mapper.toResponse(saved, 0, 0, null);
    }

    // Статусы попыток, замораживающие сумму ссылки (P2-9): плательщику уже показали цену. Перечислены
    // ЗАПИРАЮЩИЕ, а не «всё кроме FAILED»: новый статус TransactionStatus по умолчанию запрещает правку.
    private static final Set<TransactionStatus> AMOUNT_LOCKING_STATUSES = EnumSet.of(
            TransactionStatus.PENDING,
            TransactionStatus.AUTHORIZED,
            TransactionStatus.SUCCESS,
            TransactionStatus.PARTIALLY_REFUNDED,
            TransactionStatus.REFUNDED);

    // Возвращённая часть PAID_STATUSES — только для счётчика возвратов, использованием платёж быть не
    // перестаёт (P2-16, Р-50). В списочный ответ не добавлять: счётчик на строку — N+1 (P2-15).
    private static final Set<TransactionStatus> REFUNDED_STATUSES = EnumSet.of(
            TransactionStatus.REFUNDED,
            TransactionStatus.PARTIALLY_REFUNDED);

    // Использования — PAID_STATUSES, возвращённые тоже (Р-49): это currentPaymentsCount в API, колонка
    // и база для лимита.
    private long usedCount(UUID linkId) {
        return transactionRepository.countByLinkIdAndStatusIn(linkId, TransactionStatus.PAID_STATUSES);
    }

    private int refundedCount(UUID linkId) {
        return (int) transactionRepository.countByLinkIdAndStatusIn(linkId, REFUNDED_STATUSES);
    }

    // Только для одиночных эндпоинтов; в списке — lastPaidAtByLink на всю страницу (P2-15, Р-46).
    private Instant lastPaidAt(UUID linkId) {
        return transactionRepository
                .findFirstByLinkIdAndStatusInOrderByCreatedAtDesc(linkId, TransactionStatus.PAID_STATUSES)
                .map(Transaction::getCreatedAt)
                .orElse(null);
    }

    // Один группирующий запрос на страницу (P2-15): вызов на строку — N+1, сторож —
    // PaymentLinkListPaginationTest. Пустую страницу не запрашивать: IN () — невалидный SQL.
    private Map<UUID, Instant> lastPaidAtByLink(List<PaymentLink> links) {
        if (links.isEmpty()) {
            return Collections.emptyMap();
        }
        List<UUID> ids = links.stream().map(PaymentLink::getId).toList();
        Map<UUID, Instant> byLink = new HashMap<>();
        for (Object[] row : transactionRepository.findLastPaidAtByLinkIds(ids, TransactionStatus.PAID_STATUSES)) {
            byLink.put((UUID) row[0], (Instant) row[1]);
        }
        return byLink;
    }

    // Ручные переходы статуса (P2-9); остального нет, иначе просроченная ссылка воскресала бы одним
    // PATCH. CANCELED → ACTIVE — только со сроком впереди. SUSPENDED ставит и снимает только
    // блокировка терминала (P2-8, Р-39).
    private static final Map<PaymentLinkStatus, Set<PaymentLinkStatus>> ALLOWED_STATUS_TRANSITIONS = Map.of(
            PaymentLinkStatus.ACTIVE, EnumSet.of(PaymentLinkStatus.CANCELED),
            PaymentLinkStatus.EXPIRED, EnumSet.of(PaymentLinkStatus.CANCELED),
            PaymentLinkStatus.CANCELED, EnumSet.of(PaymentLinkStatus.ACTIVE),
            PaymentLinkStatus.COMPLETED, EnumSet.noneOf(PaymentLinkStatus.class),
            PaymentLinkStatus.SUSPENDED, EnumSet.noneOf(PaymentLinkStatus.class));

    // Под замком ссылки, как открытие, списание и возврат (LINK-PATCH-LOCK): без него незакоммиченная
    // попытка открытия не видна, и сумма менялась, пока открытие заводило заказ по старой (обход Р-31).
    @Transactional
    public PaymentLinkResponse update(UUID id, UpdatePaymentLinkRequest request, UserPrincipal principal) {
        log.debug("Request to update payment link {}", id);
        PaymentLink link = paymentLinkRepository.findWithLockById(id)
                .orElseThrow(() -> {
                    log.warn("Payment link not found: {}", id);
                    return new ResourceNotFoundException("Payment link not found: " + id);
                });

        String terminalCompanyId = validateAccess(link.getTerminalId(), principal, LINK_WRITE_ROLES).getCompanyId();
        PaymentLinkStatus statusBefore = link.getStatus();

        long usedCount = usedCount(id);

        // Сдвинутые поля — одной записью журнала на PATCH (P2-9).
        List<String> changes = new ArrayList<>();

        // Сумма, равная текущей, — не правка: PATCH с объектом целиком не должен падать на несдвинутом поле.
        if (request.amount() != null && request.amount().compareTo(link.getAmount()) != 0) {
            if (transactionRepository.existsByLinkIdAndStatusIn(id, AMOUNT_LOCKING_STATUSES)) {
                log.warn("Refusing to change the amount of link {}: it already has payment attempts", id);
                throw new BusinessException(
                        "payment link already has payments, its amount cannot be changed; create a new link instead");
            }
            changes.add("amount " + link.getAmount() + " -> " + request.amount());
            link.setAmount(request.amount());
        }
        if (request.description() != null && !request.description().equals(link.getDescription())) {
            changes.add("description");
            link.setDescription(request.description());
        }
        if (request.customer() != null) {
            CustomerDto customer = request.customer();
            requireCustomerAllowed(link.getUsageType(), customer);
            String phone = normalizedPhone(customer);
            // В аудит — только имена полей: значения здесь персональные данные.
            if (customer.fullName() != null && !customer.fullName().equals(link.getCustomerName())) {
                changes.add("customerName");
                link.setCustomerName(customer.fullName());
            }
            if (customer.email() != null && !customer.email().equals(link.getCustomerEmail())) {
                changes.add("customerEmail");
                link.setCustomerEmail(customer.email());
            }
            if (phone != null && !phone.equals(link.getCustomerPhone())) {
                changes.add("customerPhone");
                link.setCustomerPhone(phone);
            }
        }
        if (request.expiresAt() != null) {
            // Потолок — от created_at, а не от now (P1-9): иначе цепочка PATCH'ей продлевала бы срок
            // бесконечно.
            Instant expiresAt = validateExpiresAt(request.expiresAt(), link.getCreatedAt());
            if (!expiresAt.equals(link.getExpiresAt())) {
                changes.add("expiresAt " + link.getExpiresAt() + " -> " + expiresAt);
                link.setExpiresAt(expiresAt);
            }
        }
        if (request.maxPayments() != null) {
            if (link.getUsageType() != UsageType.MULTIPLE) {
                log.warn("Cannot set maxPayments on SINGLE use link {}", id);
                throw new BusinessException("maxPayments can only be set when usageType is MULTIPLE");
            }
            // Лимит ниже занятых слотов дал бы «3 из 2 использовано» (P2-9): возвращённый платёж — тоже
            // использование (P2-16), холд станет платежом при списании (MAXPAY-HOLDS) — счёт как у открытия.
            long occupiedSlots = transactionRepository.countByLinkIdAndStatusIn(id, TransactionStatus.SLOT_OCCUPYING_STATUSES);
            if (request.maxPayments() < occupiedSlots) {
                log.warn("Refusing to lower maxPayments of link {} to {}: {} slots are taken",
                        id, request.maxPayments(), occupiedSlots);
                throw new BusinessException("maxPayments cannot be lowered to " + request.maxPayments()
                        + ": " + occupiedSlots + " slots are taken by payments and holds awaiting capture"
                        + " (a refunded payment still counts as a use)");
            }
            if (!request.maxPayments().equals(link.getMaxPayments())) {
                changes.add("maxPayments " + link.getMaxPayments() + " -> " + request.maxPayments());
                link.setMaxPayments(request.maxPayments());
            }
        }
        if (request.metadata() != null && !request.metadata().equals(link.getMetadata())) {
            changes.add("metadata");
            link.setMetadata(request.metadata());
        }
        // Статус — последним: ACTIVE проверяется по сроку из того же PATCH.
        if (request.status() != null) {
            applyStatusChange(link, request.status(), changes);
        }

        PaymentLink saved = paymentLinkRepository.save(link);

        // CANCEL — только сама отмена: правка описания уже отменённой ссылки — UPDATE (AUDIT-CANCEL-KIND).
        boolean cancelled = statusBefore != PaymentLinkStatus.CANCELED && saved.getStatus() == PaymentLinkStatus.CANCELED;
        eventPublisher.publishEvent(AuditEvent.of(AuditEntity.PAYMENT_LINK, saved.getId().toString(),
                cancelled ? AuditAction.CANCEL : AuditAction.UPDATE,
                UserPrincipal.getUsername(principal), terminalCompanyId,
                changes.isEmpty() ? "No fields changed" : "Changed " + String.join(", ", changes)));

        log.info("Payment link {} updated: {}", id,
                changes.isEmpty() ? "no fields changed" : String.join(", ", changes));
        return mapper.toResponse(saved, (int) usedCount, refundedCount(id), lastPaidAt(id));
    }

    // Тот же статус — не правка и не ошибка: PATCH с объектом целиком не должен падать (P2-9).
    private void applyStatusChange(PaymentLink link, PaymentLinkStatus target, List<String> changes) {
        PaymentLinkStatus current = link.getStatus();
        if (target == current) {
            return;
        }
        // У SUSPENDED свой текст отказа: причина — в заблокированном терминале, а не в ссылке.
        if (current == PaymentLinkStatus.SUSPENDED) {
            log.warn("Refusing status change of suspended link {}: {} -> {}", link.getId(), current, target);
            throw new BusinessException("payment link is suspended because its terminal " + link.getTerminalId()
                    + " is blocked; unblock the terminal to bring its links back");
        }
        if (target == PaymentLinkStatus.SUSPENDED) {
            log.warn("Refusing to suspend link {} by hand", link.getId());
            throw new BusinessException("payment link status SUSPENDED is set by blocking terminal "
                    + link.getTerminalId() + ", not on the link itself");
        }
        if (!ALLOWED_STATUS_TRANSITIONS.getOrDefault(current, Collections.emptySet()).contains(target)) {
            log.warn("Refusing status change of link {}: {} -> {}", link.getId(), current, target);
            throw new BusinessException("payment link status cannot be changed from " + current
                    + " to " + target);
        }
        // Просроченную ссылку в ACTIVE не возвращать: она была бы неоплачиваемой до прохода планировщика.
        if (target == PaymentLinkStatus.ACTIVE
                && link.getExpiresAt() != null && !link.getExpiresAt().isAfter(Instant.now())) {
            log.warn("Refusing to reactivate link {}: it expired at {}", link.getId(), link.getExpiresAt());
            throw new BusinessException("payment link expired at " + link.getExpiresAt()
                    + " and cannot be reactivated; send a new expiresAt in the same request");
        }
        changes.add("status " + current + " -> " + target);
        link.setStatus(target);
    }

    // Клиент — только у одноразовой ссылки: у многоразовой платят разные люди (Р-96). Отказ, а не
    // молчаливый пропуск: отправитель должен знать, что данные не сохранены.
    private static void requireCustomerAllowed(UsageType usageType, CustomerDto customer) {
        if (usageType == UsageType.MULTIPLE && customer != null
                && (hasText(customer.fullName()) || hasText(customer.email()) || hasText(customer.phone()))) {
            throw new BusinessException("customer can only be set on a single-use link");
        }
    }

    // Хранится как +994XXXXXXXXX (Р-96): провайдер ждёт код страны и номер раздельно.
    private static String normalizedPhone(CustomerDto customer) {
        if (customer == null || !hasText(customer.phone())) {
            return null;
        }
        return CustomerPhone.normalize(customer.phone())
                .orElseThrow(() -> new BusinessException("customer.phone must be an Azerbaijani number: +994 and 9 digits"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    // Обе границы — отказ с 400, а не тихое подрезание: ссылка не должна умереть в момент, которого
    // мерчант не просил (P1-9). createdAt — точка отсчёта потолка, при правке — created_at ссылки.
    private Instant validateExpiresAt(Instant expiresAt, Instant createdAt) {
        if (!expiresAt.isAfter(Instant.now())) {
            log.warn("Refusing expiresAt {}: it is not in the future", expiresAt);
            throw new BusinessException("expiresAt must be in the future");
        }
        // created_at есть всегда (@CreationTimestamp); запасной now даёт сломанной строке 400, а не NPE.
        Instant ceiling = (createdAt != null ? createdAt : Instant.now()).plus(maxLinkTtl);
        if (expiresAt.isAfter(ceiling)) {
            log.warn("Refusing expiresAt {}: the ceiling for this link is {} ({} from its creation time)",
                    expiresAt, ceiling, maxLinkTtl);
            throw new BusinessException("expiresAt must not be later than " + ceiling
                    + ": a payment link may live at most " + formatTtl(maxLinkTtl) + " from the moment it was created");
        }
        return expiresAt;
    }

    // Срок словами: Duration.toString() показал бы мерчанту PT2160H.
    private static String formatTtl(Duration ttl) {
        if (ttl.toDays() > 0 && ttl.minusDays(ttl.toDays()).isZero()) {
            return ttl.toDays() + (ttl.toDays() == 1 ? " day" : " days");
        }
        if (ttl.toHours() > 0 && ttl.minusHours(ttl.toHours()).isZero()) {
            return ttl.toHours() + (ttl.toHours() == 1 ? " hour" : " hours");
        }
        return ttl.toString();
    }

    @Transactional(readOnly = true)
    public PaymentLinkResponse get(UUID id, UserPrincipal principal) {

        log.debug("Request to fetch payment link {}", id);
        PaymentLink link = findLinkOrThrow(id);

        validateAccess(link.getTerminalId(), principal, READ_ROLES);

        return mapper.toResponse(link, (int) usedCount(id), refundedCount(id), lastPaidAt(id));
    }

    @Transactional(readOnly = true)
    public PagedResponse<PaymentLinkSummaryResponse> list(Integer terminal, PaymentLinkStatus status, Pageable pageable, UserPrincipal principal) {
        String userId = UserPrincipal.getUserId(principal);
        Role userRole = UserPrincipal.getRole(principal);
        String rawRole = UserPrincipal.getRawRole(principal);
        String companyId = UserPrincipal.getCompanyId(principal);

        log.debug("Request to list payment links: terminal={}, status={}", terminal, status);
        if (userRole == null || !READ_ROLES.contains(userRole)) {
            log.warn("Access denied. Role {} is not authorized to list payment links.", rawRole);
            throw new InvalidStateException("Access denied: role " + rawRole + " is not authorized for this action");
        }

        boolean globalReader = isGlobalReader(userRole);
        List<Integer> allowedTerminals = Collections.emptyList();

        if (!globalReader) {
            if (companyId == null || companyId.isBlank()) {
                log.warn("Missing companyId claim for non-admin user: {}", userId);
                Page<PaymentLink> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
                return PagedResponse.of(emptyPage, Collections.emptyList());
            }
            allowedTerminals = terminalRepository.findAllByCompanyId(companyId).stream()
                    .map(Terminal::getId)
                    .toList();

            log.debug("Found allowed terminals for company {}: {}", companyId, allowedTerminals);
            if (allowedTerminals.isEmpty()) {
                Page<PaymentLink> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
                return PagedResponse.of(emptyPage, Collections.emptyList());
            }
        }

        if (terminal != null) {
            validateAccess(terminal, principal, READ_ROLES);
        }

        Page<PaymentLink> page = paymentLinkRepository.search(terminal, allowedTerminals, globalReader, status, pageable);
        Map<UUID, Instant> paidAt = lastPaidAtByLink(page.getContent());
        List<PaymentLinkSummaryResponse> content = page.getContent().stream()
                .map(link -> mapper.toSummary(link, paidAt.get(link.getId())))
                .toList();
        return PagedResponse.of(page, content);
    }

    // Блокировку терминала не проверять (Р-38): отказ оставил бы холд висеть на карте держателя — Void
    // у нас нет, и мерчант не смог бы ни списать его, ни отменить. Три шага — PreparedOperation (Р-123).
    public PaymentLinkResponse completeDms(UUID transactionId, CompleteDmsRequest request, UserPrincipal principal) {
        log.info("Request to complete DMS: transactionId={}, amount={}", transactionId, request.amount());
        PreparedOperation operation = txTemplate.execute(status -> prepareCapture(transactionId, request, principal));
        MoneyOperationResult capture = callAcquirer(operation,
                () -> acquiringClient.completeDms(operation.providerOrderId(), operation.credentials(), operation.amount()));
        return recordOutcome(operation, () -> recordConfirmedCapture(operation, capture));
    }

    private PreparedOperation prepareCapture(UUID transactionId, CompleteDmsRequest request, UserPrincipal principal) {
        Transaction transaction = lockLinkAndLoadTransaction(transactionId);
        PaymentLink link = transaction.getLink();

        String terminalCompanyId = validateAccess(link.getTerminalId(), principal, LINK_WRITE_ROLES).getCompanyId();
        requireNoOpenAttempt(transactionId);

        // Повторное списание SUCCESS дважды сняло бы деньги с держателя карты (P0-8).
        if (transaction.getStatus() == TransactionStatus.SUCCESS) {
            log.warn("Refusing repeat capture of transaction {}: it is already SUCCESS", transactionId);
            throw new BusinessException("Transaction has already been captured");
        }
        if (transaction.getStatus() != TransactionStatus.AUTHORIZED
                && transaction.getStatus() != TransactionStatus.PENDING) {
            log.warn("Cannot complete DMS. Transaction {} is in status: {}", transactionId, transaction.getStatus());
            throw new BusinessException("Transaction is in status " + transaction.getStatus() + ". Only PENDING or AUTHORIZED transactions can be completed.");
        }

        if (transaction.getStatus() == TransactionStatus.PENDING) {
            // Наша копия отстаёт: холд, поставленный после единственного опроса страницы возврата, здесь
            // ещё PENDING (P0-2). PaymentOutcomeUnknownException опроса не ловить: списывать по
            // непрочитанному статусу нельзя.
            log.info("Transaction {} is PENDING; polling the acquirer before deciding on the capture", transactionId);
            // Возвращаются те же управляемые экземпляры: link выше остаётся той же сущностью.
            transaction = refreshStatus(transaction).transaction();

            if (transaction.getStatus() == TransactionStatus.SUCCESS) {
                log.warn("Refusing capture of transaction {}: the acquirer reports it already settled", transactionId);
                throw new BusinessException("Transaction has already been captured");
            }
            if (transaction.getStatus() != TransactionStatus.AUTHORIZED) {
                // Отказ откатит обновлённый статус — безвредно: деньги не двигались, строку добьёт сверка.
                log.warn("Cannot complete DMS. Transaction {} is still {} at the acquirer", transactionId, transaction.getStatus());
                throw new BusinessException("Transaction is in status " + transaction.getStatus()
                        + " and has not been authorized by the acquirer yet. Capture is only possible for an authorized payment.");
            }
        }

        // Сумма уходит эквайеру — проверяется до отправки (P0-8); ноль и минус отсёк @Positive на DTO.
        assertCapturableScale(request.amount(), "Capture");
        if (request.amount().compareTo(transaction.getAmount()) > 0) {
            log.warn("Refusing capture of transaction {}: requested {} exceeds the authorized amount {}",
                    transactionId, request.amount(), transaction.getAmount());
            throw new BusinessException("Capture amount exceeds the authorized amount");
        }

        return prepare(transaction, MoneyOperationAttempt.Kind.CAPTURE, request.amount(), principal, terminalCompanyId);
    }

    private PaymentLinkResponse recordConfirmedCapture(PreparedOperation operation, MoneyOperationResult capture) {
        Transaction transaction = lockLinkAndLoadTransaction(operation.transactionId(), true);
        PaymentLink link = transaction.getLink();
        // Свидетельство списания — под своим ключом: в споре нужны идентификаторы эквайера.
        PaymentLink savedLink = applyCapture(transaction, link, operation.amount(), capture.raw(),
                moneyOperationRecord(capture, operation.amount(), Instant.now()));
        attemptRepository.deleteById(operation.transactionId());
        log.info("Transaction {} captured successfully for {} of the authorized {} and transitioned to SUCCESS.",
                operation.transactionId(), operation.amount(), transaction.getAmount());

        eventPublisher.publishEvent(AuditEvent.of(AuditEntity.TRANSACTION, operation.transactionId().toString(),
                AuditAction.CAPTURE, operation.actor(), operation.terminalCompanyId(),
                "Captured " + operation.amount() + " " + operation.currency() + " of the authorized "
                        + transaction.getAmount() + " (ridByPmo " + capture.ridByPmo()
                        + ", tranActionId " + capture.tranActionId()
                        + ", approvalCode " + capture.approvalCode() + ")"));

        return mapper.toResponse(savedLink, savedLink.getCurrentPaymentsCount(), refundedCount(savedLink.getId()),
                lastPaidAt(savedLink.getId()));
    }

    // amount остаётся авторизованной суммой; потолок возврата читает capturedAmount (P0-8). Частичное
    // списание — тоже SUCCESS. Сырое тело — только через ProviderPayloads.withoutSecrets (P0-9).
    private PaymentLink applyCapture(Transaction transaction, PaymentLink link, BigDecimal amount,
                                     Map<String, Object> raw, Map<String, Object> evidence) {
        Map<String, Object> mergedResponse = new HashMap<>();
        if (transaction.getProviderResponse() != null) {
            mergedResponse.putAll(transaction.getProviderResponse());
        }
        if (raw != null) {
            mergedResponse.putAll(ProviderPayloads.withoutSecrets(raw));
        }
        mergedResponse.put(CAPTURE_KEY, evidence);
        transaction.setProviderResponse(mergedResponse);
        transaction.setCapturedAmount(amount);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transactionRepository.save(transaction);

        // Использования, а не строки SUCCESS (P2-16): возвращённый платёж держит свой слот. В колонку —
        // то же число, что в API.
        long usedCount = usedCount(link.getId());
        link.setCurrentPaymentsCount((int) usedCount);
        if (link.getUsageType() == UsageType.SINGLE) {
            log.info("Single-use link {} successfully completed.", link.getId());
            link.setStatus(PaymentLinkStatus.COMPLETED);
        } else if (link.getUsageType() == UsageType.MULTIPLE && link.getMaxPayments() != null && usedCount >= link.getMaxPayments()) {
            log.info("Multi-use link {} reached max payments limit. Transitioned to COMPLETED.", link.getId());
            link.setStatus(PaymentLinkStatus.COMPLETED);
        }
        return paymentLinkRepository.save(link);
    }

    // Блокировку терминала не проверять (Р-38): покупатель не получил бы возврат, пока терминал
    // не разблокируют. Три шага — PreparedOperation (Р-123).
    public RefundResponse refund(UUID transactionId, RefundRequest request, UserPrincipal principal) {
        log.info("Request to refund transaction: transactionId={}, amount={}", transactionId, request.amount());
        PreparedOperation operation = txTemplate.execute(status -> prepareRefund(transactionId, request, principal));
        MoneyOperationResult result = callAcquirer(operation,
                () -> acquiringClient.refund(operation.providerOrderId(), operation.credentials(), operation.amount()));
        return recordOutcome(operation, () -> recordConfirmedRefund(operation, result));
    }

    private PreparedOperation prepareRefund(UUID transactionId, RefundRequest request, UserPrincipal principal) {
        Transaction transaction = lockLinkAndLoadTransaction(transactionId);
        PaymentLink link = transaction.getLink();

        String terminalCompanyId = validateAccess(link.getTerminalId(), principal, REFUND_ROLES).getCompanyId();
        requireNoOpenAttempt(transactionId);

        if (transaction.getStatus() != TransactionStatus.SUCCESS && transaction.getStatus() != TransactionStatus.PARTIALLY_REFUNDED) {
            log.warn("Cannot refund transaction. Current status: {}", transaction.getStatus());
            throw new BusinessException("Only successful or partially refunded transactions can be refunded");
        }

        assertCapturableScale(request.amount(), "Refund");

        BigDecimal refundableBase = refundableBase(transaction);
        if (transaction.getRefundedAmount().add(request.amount()).compareTo(refundableBase) > 0) {
            log.warn("Refund amount {} exceeds the remaining captured amount of transaction {} (captured {}, already refunded {}).",
                    request.amount(), transactionId, refundableBase, transaction.getRefundedAmount());
            throw new BusinessException("Refund amount exceeds the captured amount of the transaction");
        }

        return prepare(transaction, MoneyOperationAttempt.Kind.REFUND, request.amount(), principal, terminalCompanyId);
    }

    private RefundResponse recordConfirmedRefund(PreparedOperation operation, MoneyOperationResult result) {
        Transaction transaction = lockLinkAndLoadTransaction(operation.transactionId(), true);

        // Нет tranActionId — нет и refundId: выдуманный номер возврата в споре хуже никакого (§5.7).
        if (result.tranActionId() == null) {
            log.warn("Acquirer confirmed the refund of transaction {} (ridByPmo {}) without a tranActionId; "
                    + "the refund response will carry no refundId", operation.transactionId(), result.ridByPmo());
        }
        // Один момент на свидетельство и строку возврата: статистика и история операции не расходятся (Р-89).
        Instant refundedAt = Instant.now();
        applyRefund(transaction, operation.amount(), refundedAt, result.ridByPmo(), result.raw(),
                moneyOperationRecord(result, operation.amount(), refundedAt));
        attemptRepository.deleteById(operation.transactionId());

        eventPublisher.publishEvent(AuditEvent.of(AuditEntity.TRANSACTION, operation.transactionId().toString(),
                AuditAction.REFUND, operation.actor(), operation.terminalCompanyId(),
                "Refunded " + operation.amount() + " " + operation.currency() + " of " + refundableBase(transaction)
                        + "; refunded so far " + transaction.getRefundedAmount() + ", transaction now "
                        + transaction.getStatus() + " (ridByPmo " + result.ridByPmo()
                        + ", tranActionId " + result.tranActionId()
                        + ", approvalCode " + result.approvalCode() + ")"));

        return new RefundResponse(
                transaction.getId(),
                transaction.getStatus().name(),
                operation.amount(),
                result.tranActionId(),
                result.ridByPmo(),
                result.approvalCode()
        );
    }

    // Возвраты копятся списком под REFUNDS_KEY: частичных бывает несколько. По refunded_at статистика по
    // ссылкам вычитает возвраты периода (Р-89).
    private void applyRefund(Transaction transaction, BigDecimal amount, Instant refundedAt, String ridByPmo,
                             Map<String, Object> raw, Map<String, Object> evidence) {
        Map<String, Object> mergedResponse = new HashMap<>();
        if (transaction.getProviderResponse() != null) {
            mergedResponse.putAll(transaction.getProviderResponse());
        }
        if (raw != null) {
            mergedResponse.putAll(ProviderPayloads.withoutSecrets(raw));
        }
        List<Object> refunds = new ArrayList<>();
        if (mergedResponse.get(REFUNDS_KEY) instanceof List<?> previous) {
            refunds.addAll(previous);
        }
        refunds.add(evidence);
        mergedResponse.put(REFUNDS_KEY, refunds);
        transaction.setProviderResponse(mergedResponse);

        BigDecimal refundableBase = refundableBase(transaction);
        BigDecimal newRefundedAmount = transaction.getRefundedAmount().add(amount);
        transaction.setRefundedAmount(newRefundedAmount);
        if (newRefundedAmount.compareTo(refundableBase) == 0) {
            log.info("Transaction {} fully refunded.", transaction.getId());
            transaction.setStatus(TransactionStatus.REFUNDED);
        } else {
            log.info("Transaction {} partially refunded. Total refunded: {}", transaction.getId(), newRefundedAmount);
            transaction.setStatus(TransactionStatus.PARTIALLY_REFUNDED);
        }
        transactionRepository.save(transaction);
        transactionRefundRepository.save(TransactionRefund.builder()
                .transaction(transaction)
                .amount(amount)
                .refundedAt(refundedAt)
                .ridByPmo(ridByPmo)
                .build());
    }

    // Денежная операция — три шага (Р-123). 1: под замком ссылки (NOWAIT) проверки и строка попытки — она и
    // держит операцию на время вызова: второй возврат или списание — 409. 2: вызов эквайера без транзакции и
    // замка — соединение пула не ждёт эквайера. 3: под ждущим замком итог по перечитанной операции и удаление
    // строки. Строка без итога — исход неизвестен: её снимает только SYSTEM_ADMIN (resolveOutcome).
    private record PreparedOperation(UUID transactionId, MoneyOperationAttempt.Kind kind, BigDecimal amount,
                                     String providerOrderId, ProviderCredentials credentials, String currency,
                                     String actor, String terminalCompanyId) {
    }

    private PreparedOperation prepare(Transaction transaction, MoneyOperationAttempt.Kind kind, BigDecimal amount,
                                      UserPrincipal principal, String terminalCompanyId) {
        PaymentLink link = transaction.getLink();
        Terminal terminal = terminalRepository.findById(link.getTerminalId())
                .orElseThrow(() -> {
                    log.error("Terminal {} configuration missing for transaction {}", link.getTerminalId(), transaction.getId());
                    return new BusinessException("Terminal configuration not found");
                });
        // Креды — до строки попытки: их отсутствие — отказ (400), а не неизвестный исход (Р-93).
        ProviderCredentials credentials = providerCredentials.forTerminal(terminal);
        String actor = UserPrincipal.getUsername(principal);
        attemptRepository.save(MoneyOperationAttempt.builder()
                .transactionId(transaction.getId())
                .kind(kind)
                .amount(amount)
                .state(MoneyOperationAttempt.State.IN_PROGRESS)
                .startedBy(actor)
                .startedAt(Instant.now())
                .build());
        log.info("Sending {} of {} to the acquirer for providerOrderId: {}", kind, amount, transaction.getProviderOrderId());
        return new PreparedOperation(transaction.getId(), kind, amount, transaction.getProviderOrderId(), credentials,
                link.getCurrency(), actor, terminalCompanyId);
    }

    // Пока исход прошлой операции не записан, новая не уходит: та могла уже двинуть деньги (Р-123).
    private void requireNoOpenAttempt(UUID transactionId) {
        attemptRepository.findById(transactionId).ifPresent(attempt -> {
            boolean unknown = attempt.outcomeUnknown(Instant.now());
            log.warn("Refusing a money operation on transaction {}: an earlier {} of {} is {}", transactionId,
                    attempt.getKind(), attempt.getAmount(), unknown ? "of unknown outcome" : "still in progress");
            throw new ConflictException(unknown
                    ? "An earlier " + attempt.getKind().name().toLowerCase() + " of this transaction has an unknown "
                            + "outcome; a system administrator must resolve it before another money operation"
                    : "Another money operation on this transaction is in progress");
        });
    }

    // Возвращается только подтверждённый результат (tran.match.ridByPmo), иначе 502 (P1-8b).
    private MoneyOperationResult callAcquirer(PreparedOperation operation, Supplier<MoneyOperationResult> call) {
        try {
            return call.get();
        } catch (PaymentOutcomeUnknownException e) {
            markAttemptUnknown(operation.transactionId());
            auditLogService.logUnresolved(AuditEntity.TRANSACTION, operation.transactionId().toString(),
                    auditActionOf(operation.kind()), operation.actor(), operation.terminalCompanyId(),
                    capitalized(operation.kind()) + " of " + operation.amount() + " " + operation.currency()
                            + " left unconfirmed by the acquirer (providerOrderId " + operation.providerOrderId()
                            + "): " + e.getMessage() + ". Outcome unknown — a system administrator must reconcile "
                            + "it with the provider and resolve it before another money operation.");
            throw e;
        } catch (RuntimeException e) {
            // Отказ эквайера или разомкнутый breaker: деньги не двигались, запрет снимается.
            releaseAttempt(operation.transactionId());
            throw e;
        }
    }

    // Эквайер подтвердил, а записать не вышло: деньги ушли, итога у нас нет. Не 500 — исход для мерчанта
    // неизвестен, строка попытки остаётся и держит запрет до разрешения исхода.
    private <T> T recordOutcome(PreparedOperation operation, Supplier<T> record) {
        try {
            return txTemplate.execute(status -> record.get());
        } catch (RuntimeException e) {
            // Стектрейс напечатает GlobalExceptionHandler под маркером PAYMENT_OUTCOME_UNKNOWN — здесь без него (Р-98).
            log.error("The acquirer confirmed the {} of {} on transaction {}, but recording it failed: {}",
                    operation.kind(), operation.amount(), operation.transactionId(), e.getMessage());
            markAttemptUnknown(operation.transactionId());
            throw new PaymentOutcomeUnknownException("The acquirer confirmed the " + operation.kind().name().toLowerCase()
                    + ", but the portal failed to record it; a system administrator must resolve the outcome", e);
        }
    }

    private void markAttemptUnknown(UUID transactionId) {
        try {
            txTemplate.executeWithoutResult(status -> attemptRepository.findById(transactionId).ifPresent(attempt -> {
                attempt.setState(MoneyOperationAttempt.State.UNKNOWN);
                attemptRepository.save(attempt);
            }));
        } catch (RuntimeException e) {
            // Строка останется IN_PROGRESS и через STALE_AFTER всё равно прочтётся как неизвестная.
            log.error("Could not mark the money operation on transaction {} as of unknown outcome", transactionId, e);
        }
    }

    private void releaseAttempt(UUID transactionId) {
        try {
            txTemplate.executeWithoutResult(status -> attemptRepository.deleteById(transactionId));
        } catch (RuntimeException e) {
            log.error("Could not release the money operation on transaction {}; it will read as of unknown outcome "
                    + "after {} and need resolving", transactionId, MoneyOperationAttempt.STALE_AFTER, e);
        }
    }

    // Итог сверки SYSTEM_ADMIN с провайдером (Р-123): executed — операция записывается как подтверждённая, но
    // без идентификаторов эквайера; иначе запрет просто снимается. «Идёт» моложе STALE_AFTER не разрешается.
    @Transactional
    public TransactionResponse resolveOutcome(UUID transactionId, boolean executed, UserPrincipal principal) {
        Transaction transaction = lockLinkAndLoadTransaction(transactionId);
        PaymentLink link = transaction.getLink();
        String terminalCompanyId = validateAccess(link.getTerminalId(), principal, RESOLVE_ROLES).getCompanyId();
        MoneyOperationAttempt attempt = attemptRepository.findById(transactionId)
                .orElseThrow(() -> new ConflictException("This transaction has no money operation awaiting resolution"));
        Instant now = Instant.now();
        if (!attempt.outcomeUnknown(now)) {
            throw new ConflictException("The money operation on this transaction is still in progress");
        }
        String admin = UserPrincipal.getUsername(principal);
        if (executed) {
            Map<String, Object> evidence = new HashMap<>();
            evidence.put("amount", attempt.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString());
            evidence.put("at", attempt.getStartedAt().toString());
            evidence.put("resolvedBy", admin);
            evidence.put("resolvedAt", now.toString());
            if (attempt.getKind() == MoneyOperationAttempt.Kind.CAPTURE) {
                applyCapture(transaction, link, attempt.getAmount(), null, evidence);
            } else {
                if (transaction.getRefundedAmount().add(attempt.getAmount()).compareTo(refundableBase(transaction)) > 0) {
                    throw new ConflictException("Recording this refund would exceed the captured amount of the transaction");
                }
                applyRefund(transaction, attempt.getAmount(), attempt.getStartedAt(), null, null, evidence);
            }
        }
        attemptRepository.delete(attempt);
        log.info("Unknown {} of {} on transaction {} resolved by {} as {}", attempt.getKind(), attempt.getAmount(),
                transactionId, admin, executed ? "executed" : "not executed");
        eventPublisher.publishEvent(AuditEvent.of(AuditEntity.TRANSACTION, transactionId.toString(),
                AuditAction.RESOLVE, admin, terminalCompanyId,
                "Unknown " + attempt.getKind().name().toLowerCase() + " of " + attempt.getAmount() + " "
                        + link.getCurrency() + " started by " + attempt.getStartedBy() + " at " + attempt.getStartedAt()
                        + " resolved as " + (executed ? "executed: recorded without acquirer references"
                        : "not executed: nothing recorded")));
        return mapToTransactionResponse(transaction, actionsOf(transaction, principal));
    }

    private static String auditActionOf(MoneyOperationAttempt.Kind kind) {
        return kind == MoneyOperationAttempt.Kind.CAPTURE ? AuditAction.CAPTURE : AuditAction.REFUND;
    }

    private static String capitalized(MoneyOperationAttempt.Kind kind) {
        return kind == MoneyOperationAttempt.Kind.CAPTURE ? "Capture" : "Refund";
    }

    // Блокировка ссылки — ДО чтения транзакции и похода к эквайеру: иначе два возврата пройдут потолок
    // на одном снимке, два списания уйдут в шлюз, а конфликт @Version на коммите откатит подтверждённое
    // списание. Порядок «ссылка, потом транзакция» — как у открытия: взаимной блокировки нет.
    private Transaction lockLinkAndLoadTransaction(UUID transactionId) {
        return lockLinkAndLoadTransaction(transactionId, false);
    }

    private Transaction lockLinkAndLoadTransaction(UUID transactionId, boolean waitForLock) {
        UUID linkId = transactionRepository.findLinkIdById(transactionId)
                .orElseThrow(() -> {
                    log.warn("Transaction not found for a money operation: {}", transactionId);
                    return new ResourceNotFoundException("Transaction not found: " + transactionId);
                });
        (waitForLock ? paymentLinkRepository.findWithWaitingLockById(linkId) : paymentLinkRepository.findWithLockById(linkId))
                .orElseThrow(() -> new ResourceNotFoundException("Payment link not found: " + linkId));
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
    }

    // Идентификаторы эквайера (§5.5-5.7), сумма и время — строками, сумма в той форме, что ушла эквайеру.
    // UNNECESSARY не бросит: длиннее двух знаков отверг assertCapturableScale.
    private static Map<String, Object> moneyOperationRecord(MoneyOperationResult result, BigDecimal amount, Instant at) {
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("tranActionId", result.tranActionId());
        evidence.put("ridByPmo", result.ridByPmo());
        evidence.put("approvalCode", result.approvalCode());
        evidence.put("amount", amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString());
        evidence.put("at", at.toString());
        return evidence;
    }

    // Потолок возврата: у DMS — списанная сумма, у SMS — авторизованная. amount у DMS нельзя:
    // авторизовали 1500, списали 500 — вернуть можно только 500 (P0-8).
    private static BigDecimal refundableBase(Transaction tx) {
        return tx.getCapturedAmount() != null ? tx.getCapturedAmount() : tx.getAmount();
    }

    // TxpgAcquiringClient форматирует сумму с UNNECESSARY, чтобы ничего не округлилось за спиной
    // мерчанта: третий знак — 400 здесь, а не исключение в клиенте.
    private static void assertCapturableScale(BigDecimal amount, String operation) {
        if (amount.scale() > 2) {
            log.warn("Refusing {} of {}: more than two decimal places", operation.toLowerCase(), amount);
            throw new BusinessException(operation + " amount must not have more than two decimal places");
        }
    }

    // Опрос — под замком ссылки, как списание и возврат: без него он сохранял бы снимок, прочитанный до чужого
    // подтверждённого списания, и затирал бы его (Р-109). Занята — 409 до похода к эквайеру.
    @Transactional
    public TransactionResponse checkAndStatusUpdate(String identifier, UserPrincipal principal) {

        log.info("Request to check transaction status: identifier={}", identifier);

        UUID transactionId = resolveTransactionId(identifier);
        Integer terminalId = transactionRepository.findTerminalIdById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + identifier));
        requireStatusReadable(identifier, terminalId, principal);
        Transaction refreshed = refreshStatus(lockLinkAndLoadTransaction(transactionId)).transaction();
        return mapToTransactionResponse(refreshed, actionsOf(refreshed, principal));
    }

    // Номера заказов провайдера идут подряд: 403 на чужой при 404 на несуществующий выдавал перебором
    // портальные заказы и номера чужих терминалов. Чужой — тот же 404, а отказ — в журнал без компании:
    // под компанией актора его прочли бы руководитель и менеджер, и перебор шёл бы через журнал (Р-114).
    private void requireStatusReadable(String identifier, Integer terminalId, UserPrincipal principal) {
        Role role = UserPrincipal.getRole(principal);
        if (role == null || !READ_ROLES.contains(role) || isGlobalReader(role)) {
            validateAccess(terminalId, principal, READ_ROLES);
            return;
        }
        String companyId = UserPrincipal.getCompanyId(principal);
        boolean ownTerminal = companyId != null && terminalRepository.findById(terminalId)
                .map(terminal -> companyId.equals(terminal.getCompanyId()))
                .orElse(false);
        if (!ownTerminal) {
            log.warn("Status of transaction {} refused to company {}: terminal {} belongs to another company; "
                    + "answered as not found", identifier, companyId, terminalId);
            auditLogService.logDenied(AuditEntity.TERMINAL, String.valueOf(terminalId), AuditAction.READ,
                    UserPrincipal.getUsername(principal), null,
                    "Denied: role " + role + " of company " + companyId + " asked for the status of transaction "
                            + identifier + " on terminal " + terminalId + " of another company");
            throw new ResourceNotFoundException("Transaction not found: " + identifier);
        }
    }

    // Страница возврата плательщика: владение не проверяется — ключ случайный ridByMerchant, его не
    // перебрать. Ответ беден на персональные данные; пусто вместо ошибки — не выдать, есть ли операция.
    // Опрос — в своей транзакции под замком ссылки: занятый замок или недоступный эквайер не должны
    // портить страницу, тогда она рисуется последним известным состоянием.
    public Optional<PaymentReceiptView> refreshByRidByMerchant(UUID ridByMerchant) {
        Optional<UUID> found = transactionRepository.findIdByRidByMerchant(ridByMerchant);
        if (found.isEmpty()) {
            log.info("No transaction matches the ridByMerchant on the return page request");
            return Optional.empty();
        }

        UUID transactionId = found.get();
        try {
            return Optional.of(txTemplate.execute(status ->
                    toReceiptView(refreshStatus(lockLinkAndLoadTransaction(transactionId)).transaction())));
        } catch (OptimisticLockingFailureException e) {
            // Версию ссылки поднял запрос без её замка: повторяет контроллер — свежий опрос лучше старого состояния.
            throw e;
        } catch (PessimisticLockingFailureException e) {
            log.info("Transaction {} is being changed by another request; the return page shows the last known state",
                    transactionId);
        } catch (RuntimeException e) {
            log.warn("Status refresh failed for transaction {}: {}; rendering the last known state", transactionId, e.getMessage());
        }
        return txTemplate.execute(status -> transactionRepository.findById(transactionId).map(this::toReceiptView));
    }

    // Метка в providerResponse: платёж закончил этот сервис, а не эквайер.
    static final String RECONCILIATION_OUTCOME_KEY = "reconciliationOutcome";

    // Значение метки для платежа, к которому плательщик так и не вернулся.
    static final String OUTCOME_ABANDONED_TIMEOUT = "ABANDONED_TIMEOUT";

    // Метка на каждом опросе: как этот сервис классифицировал слово эквайера.
    static final String STATUS_OUTCOME_KEY = "mpStatusOutcome";

    // Метка для UNKNOWN и SETTLED_OTHER: сырой status эквайера дословно, для ручного разбора.
    static final String PROVIDER_STATUS_KEY = "mpProviderStatus";

    // Свидетельство DMS-списания после подтверждённого клиринга (P1-8b).
    static final String CAPTURE_KEY = "mpCapture";

    // Список подтверждённых возвратов той же формы, что CAPTURE_KEY (P1-8b).
    static final String REFUNDS_KEY = "mpRefunds";

    // Причина отказа эквайера — TransactionResponse.failureReason (P1-8b).
    static final String DECLINE_REASON_KEY = "mpDeclineReason";

    // По outcome reconcileOne решает, можно ли гасить платёж по таймауту.
    public record StatusRefresh(Transaction transaction, ProviderOrderOutcome outcome) {}

    // Своя транзакция: сбой на одной записи не откатывает пакет. FAILED — только если опрос удался,
    // вернул NON_FINAL и запись старше maxAge (Р-20). Под замком ссылки (Р-109): занята —
    // PessimisticLockingFailureException, и строку возьмёт следующий проход.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileOne(UUID transactionId, Duration maxAge) {
        if (transactionRepository.findLinkIdById(transactionId).isEmpty()) {
            log.debug("Reconciliation skipped: transaction {} no longer exists", transactionId);
            return;
        }
        Transaction tx = lockLinkAndLoadTransaction(transactionId);
        if (tx.getStatus() != TransactionStatus.PENDING) {
            log.debug("Reconciliation skipped: transaction {} is already {}", transactionId, tx.getStatus());
            return;
        }

        StatusRefresh refresh;
        try {
            refresh = refreshStatus(tx);
        } catch (RuntimeException e) {
            // Эквайер недоступен или отказал: строка остаётся PENDING до следующего прохода.
            log.warn("Reconciliation could not reach the acquirer for transaction {}: {}. Leaving it PENDING.",
                    transactionId, e.getMessage());
            return;
        }
        tx = refresh.transaction();

        if (tx.getStatus() != TransactionStatus.PENDING) {
            log.info("Reconciliation settled transaction {} as {}", transactionId, tx.getStatus());
            return;
        }

        // По таймауту гасится только NON_FINAL; остальное — человеку, каким бы старым ни было. WARN о
        // самом статусе уже дал refreshStatus.
        ProviderOrderOutcome outcome = refresh.outcome();
        if (outcome == ProviderOrderOutcome.UNKNOWN || outcome == ProviderOrderOutcome.SETTLED_OTHER) {
            log.debug("Reconciliation leaves transaction {} PENDING: outcome {} is never timed out", transactionId, outcome);
            return;
        }
        if (outcome != ProviderOrderOutcome.NON_FINAL) {
            // PAID, AUTHORIZED и FAILED_FINAL сюда не доходят; сторож от нового outcome, молча получившего
            // право на таймаут.
            log.warn("Reconciliation is leaving transaction {} PENDING: outcome {} is not eligible for the "
                    + "abandonment timeout", transactionId, outcome);
            return;
        }

        Instant createdAt = tx.getCreatedAt();
        if (createdAt == null || createdAt.isAfter(Instant.now().minus(maxAge))) {
            log.debug("Transaction {} is still PENDING but younger than {}; giving the payer more time",
                    transactionId, maxAge);
            return;
        }

        Map<String, Object> mergedResponse = new HashMap<>();
        if (tx.getProviderResponse() != null) {
            mergedResponse.putAll(tx.getProviderResponse());
        }
        // Метка отличает при разборе спора наш таймаут от отказа эквайера.
        mergedResponse.put(RECONCILIATION_OUTCOME_KEY, OUTCOME_ABANDONED_TIMEOUT);
        mergedResponse.put("reconciledAt", Instant.now().toString());
        tx.setProviderResponse(mergedResponse);
        tx.setStatus(TransactionStatus.FAILED);
        transactionRepository.save(tx);

        // Ссылку не трогать: одноразовая остаётся ACTIVE для новой попытки.
        log.info("Transaction {} abandoned by the payer (older than {}, acquirer still non-final); marked FAILED",
                transactionId, maxAge);
    }

    // Только номер: саму транзакцию читают после замка ссылки, иначе в сессии остался бы снимок до него.
    private UUID resolveTransactionId(String identifier) {
        try {
            UUID uuid = UUID.fromString(identifier);
            if (transactionRepository.existsById(uuid)) {
                return uuid;
            }
        } catch (IllegalArgumentException ignored) {}

        return transactionRepository.findIdByProviderOrderId(identifier)
                .orElseThrow(() -> {
                    log.warn("Transaction not found for identifier: {}", identifier);
                    return new ResourceNotFoundException("Transaction not found: " + identifier);
                });
    }

    // Один опрос эквайера; финальный статус возвращается без опроса. Блокировку терминала не проверять
    // (Р-38): платежи, шедшие в момент блокировки, застряли бы в PENDING навсегда. Пакетный доступ —
    // для OpenLinkService: прошлую попытку он спрашивает у эквайера, а не гасит.
    StatusRefresh refreshStatus(Transaction tx) {
        if (tx.getStatus() == TransactionStatus.SUCCESS || tx.getStatus() == TransactionStatus.FAILED
                || tx.getStatus() == TransactionStatus.REFUNDED || tx.getStatus() == TransactionStatus.PARTIALLY_REFUNDED) {
            log.debug("Transaction {} already in terminal state: {}", tx.getId(), tx.getStatus());
            return new StatusRefresh(tx, ProviderOrderOutcome.UNKNOWN);
        }

        UUID transactionId = tx.getId();
        TransactionStatus before = tx.getStatus();
        PaymentLink link = tx.getLink();
        Terminal terminal = terminalRepository.findById(link.getTerminalId())
                .orElseThrow(() -> {
                    log.error("Terminal {} configuration missing for transaction status check {}", link.getTerminalId(), transactionId);
                    return new BusinessException("Terminal configuration not found");
                });

        Map<String, Object> orderDetails = acquiringClient.getOrderStatus(tx.getProviderOrderId(), tx.getProviderPassword(),
                providerCredentials.forTerminal(terminal));
        if (orderDetails == null) {
            // Пустое тело так же неинформативно, как неизвестное слово, и так же заметно.
            log.warn("Acquirer returned no order payload for transaction {} (providerOrderId {}); "
                    + "treating the status as unknown", transactionId, tx.getProviderOrderId());
        }
        // Без приведения к String: число или объект под "status" уронили бы денежный поток
        // ClassCastException. Всё, кроме известной строки, — UNKNOWN.
        Object raw = orderDetails != null ? orderDetails.get("status") : null;
        ProviderOrderOutcome outcome = ProviderOrderStatus.classify(raw);
        log.debug("Provider order status check result: transactionId={}, providerStatus=\"{}\", outcome={}",
                transactionId, raw, outcome);

        boolean holdReleased = false;
        switch (outcome) {
            case PAID -> {
                tx.setStatus(TransactionStatus.SUCCESS);
                // Эта транзакция уже в счёте: auto-flush перед JPQL. «+ 1» здесь посчитает её дважды и
                // закроет двухплатёжную ссылку после первого платежа (P1-7). Счёт по PAID_STATUSES (P2-16).
                long usedCount = usedCount(link.getId());
                link.setCurrentPaymentsCount((int) usedCount);
                if (link.getUsageType() == UsageType.SINGLE) {
                    log.info("Single-use link {} completed due to status transition to SUCCESS.", link.getId());
                    link.setStatus(PaymentLinkStatus.COMPLETED);
                } else if (link.getUsageType() == UsageType.MULTIPLE && link.getMaxPayments() != null && usedCount >= link.getMaxPayments()) {
                    log.info("Multi-use link {} completed due to status transition to SUCCESS and limit reached.", link.getId());
                    link.setStatus(PaymentLinkStatus.COMPLETED);
                }
                paymentLinkRepository.save(link);
            }
            case AUTHORIZED -> tx.setStatus(TransactionStatus.AUTHORIZED);
            case FAILED_FINAL -> tx.setStatus(TransactionStatus.FAILED);
            case NON_FINAL -> {
                // Ещё не оплачено: менять нечего, по таймауту добьёт reconcileOne.
            }
            case SETTLED_OTHER -> {
                if ((tx.getStatus() == TransactionStatus.AUTHORIZED || tx.getStatus() == TransactionStatus.PENDING)
                        && ProviderOrderDetails.isReleasedAuthorization(orderDetails)) {
                    // Холд снял банк без списания (Closed ← Authorized, Р-75): денег нет, слот ссылки
                    // свободен. Иначе транзакция навсегда осталась бы AUTHORIZED.
                    log.info("Acquirer released the authorization of transaction {} (providerOrderId {}) "
                            + "without a capture; marking it FAILED", transactionId, tx.getProviderOrderId());
                    tx.setStatus(TransactionStatus.FAILED);
                    holdReleased = true;
                } else {
                    // Реверсал, возврат или закрытие мимо портала: статус не трогать — сумм мы не знаем,
                    // и REFUNDED записал бы выдуманный refunded_amount. Разбор — руками (AGENTS.md §10).
                    if (firstNotice(transactionId, raw)) {
                        log.warn("Transaction {} (provider order {}) stays {}: the acquirer reports \"{}\", set outside "
                                + "this service — never timed out, review the money by hand (AGENTS.md §10)",
                                transactionId, tx.getProviderOrderId(), tx.getStatus(), raw);
                    }
                }
            }
            case UNKNOWN -> {
                if (firstNotice(transactionId, raw)) {
                    log.warn("Transaction {} (provider order {}) stays {}: unknown acquirer status \"{}\" — never timed "
                            + "out; if legitimate, add it to ProviderOrderStatus (Р-20)",
                            transactionId, tx.getProviderOrderId(), tx.getStatus(), raw);
                }
            }
        }
        if (outcome != ProviderOrderOutcome.UNKNOWN && outcome != ProviderOrderOutcome.SETTLED_OTHER) {
            noticedProviderStatuses.remove(transactionId);
        }

        // Копия: payload бывает неизменяемым. При orderDetailLevel=2 он несёт пароль заказа —
        // withoutSecrets на каждом опросе, а не только при создании (P0-9).
        Map<String, Object> stored = new HashMap<>();
        if (orderDetails != null) {
            // Свежий payload заменяет прежний: устаревший mpProviderStatus не переживёт известный статус.
            stored.putAll(ProviderPayloads.withoutSecrets(orderDetails));
        } else if (tx.getProviderResponse() != null) {
            // Ответа нет — прежний payload сохраняется для ручного разбора. Через withoutSecrets всё
            // равно: строка, записанная до P0-9, может нести пароль.
            stored.putAll(ProviderPayloads.withoutSecrets(tx.getProviderResponse()));
        }
        stored.put(STATUS_OUTCOME_KEY, outcome.name());
        if ((outcome == ProviderOrderOutcome.UNKNOWN || outcome == ProviderOrderOutcome.SETTLED_OTHER) && raw != null) {
            stored.put(PROVIDER_STATUS_KEY, String.valueOf(raw));
        }
        if (outcome == ProviderOrderOutcome.FAILED_FINAL) {
            // Причина отказа из custAttrs (§5.8.7): мерчант видит «Invalid PAN», а не голый FAILED (P1-8b).
            ProviderDeclineReason.extract(orderDetails).ifPresent(reason -> {
                log.info("Acquirer decline reason for transaction {}: \"{}\"", transactionId, reason);
                stored.put(DECLINE_REASON_KEY, reason);
            });
        }
        if (holdReleased) {
            stored.put(DECLINE_REASON_KEY, "Authorization released by the acquirer without capture");
        }
        tx.setProviderResponse(stored);
        tx = transactionRepository.save(tx);
        // Опрос без перемены — не событие: сверка делает их сотнями в день.
        if (tx.getStatus() != before) {
            log.info("Transaction {} is now {} (was {}), acquirer status \"{}\"", tx.getId(), tx.getStatus(), before, raw);
        } else {
            log.debug("Transaction {} stays {}, acquirer status \"{}\"", tx.getId(), tx.getStatus(), raw);
        }

        return new StatusRefresh(tx, outcome);
    }

    // Потолок — чтобы память не росла без конца, если такие строки никто не разбирает.
    private boolean firstNotice(UUID transactionId, Object raw) {
        if (noticedProviderStatuses.size() >= NOTICED_STATUSES_CEILING) {
            noticedProviderStatuses.clear();
        }
        return !String.valueOf(raw).equals(noticedProviderStatuses.put(transactionId, String.valueOf(raw)));
    }

    @Transactional(readOnly = true)
    public PagedResponse<TransactionResponse> listTransactions(Pageable pageable, UserPrincipal principal) {
        String userId = UserPrincipal.getUserId(principal);
        Role userRole = UserPrincipal.getRole(principal);
        String rawRole = UserPrincipal.getRawRole(principal);
        String companyId = UserPrincipal.getCompanyId(principal);

        log.debug("Request to list transactions");
        if (userRole == null || !READ_ROLES.contains(userRole)) {
            log.warn("Access denied. Role {} is not authorized to list transactions.", rawRole);
            throw new InvalidStateException("Access denied: role " + rawRole + " is not authorized for this action");
        }

        Page<Transaction> page;
        if (isGlobalReader(userRole)) {
            page = transactionRepository.findAllBy(pageable);
        } else {
            if (companyId == null || companyId.isBlank()) {
                log.warn("Missing companyId claim for non-admin user: {}", userId);
                return PagedResponse.of(new PageImpl<>(Collections.emptyList(), pageable, 0), Collections.emptyList());
            }
            List<Integer> allowedTerminals = terminalRepository.findAllByCompanyId(companyId).stream()
                    .map(Terminal::getId)
                    .toList();

            log.debug("Found allowed terminals for company {}: {}", companyId, allowedTerminals);
            if (allowedTerminals.isEmpty()) {
                return PagedResponse.of(new PageImpl<>(Collections.emptyList(), pageable, 0), Collections.emptyList());
            }
            page = transactionRepository.findByLink_TerminalIdIn(allowedTerminals, pageable);
        }

        List<TransactionResponse> content = page.getContent().stream()
                .map(this::mapToTransactionResponse)
                .toList();
        return PagedResponse.of(page, content);
    }

    // Читать транзакции ссылки — то же, что читать саму ссылку (P0-4).
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByLinkId(UUID linkId, UserPrincipal principal) {
        PaymentLink link = findLinkOrThrow(linkId);
        validateAccess(link.getTerminalId(), principal, READ_ROLES);
        return transactionRepository.findByLinkIdOrderByCreatedAtDesc(linkId).stream()
                .map(this::mapToTransactionResponse)
                .toList();
    }

    // Без обращения к эквайеру (P3-7): за свежим исходом — /{identifier}/status.
    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(UUID id, UserPrincipal principal) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Transaction not found: {}", id);
                    return new ResourceNotFoundException("Transaction not found: " + id);
                });
        validateAccess(tx.getLink().getTerminalId(), principal, READ_ROLES);
        return mapToTransactionResponse(tx, actionsOf(tx, principal));
    }

    // Узкая проекция для страницы плательщика: только то, что плательщик и так знает.
    private PaymentReceiptView toReceiptView(Transaction tx) {
        PaymentLink link = tx.getLink();
        return new PaymentReceiptView(
                receiptState(tx.getStatus()),
                tx.getId(),
                tx.getAmount(),
                link != null ? link.getCurrency() : "AZN",
                link != null ? link.getMerchantOrderId() : null,
                link != null ? link.getDescription() : null,
                link != null ? link.getCustomerName() : null,
                link != null ? link.getCustomerEmail() : null,
                tx.getCreatedAt()
        );
    }

    private static String receiptState(TransactionStatus status) {
        return switch (status) {
            case SUCCESS, REFUNDED, PARTIALLY_REFUNDED -> "PAID";
            case AUTHORIZED -> "AUTHORIZED";
            case FAILED -> "FAILED";
            case PENDING -> "PENDING";
        };
    }

    // Маска карты, RRN и код авторизации — на лету из providerResponse через ProviderOrderDetails (P1-16).
    // Своих колонок нет намеренно: payload транзакции в финальном статусе опрос не переписывает.
    private TransactionResponse mapToTransactionResponse(Transaction tx) {
        return mapToTransactionResponse(tx, null);
    }

    // Кнопки карточки (Р-123): терминал, креды его компании и строка попытки — только для одной операции.
    private TransactionActions actionsOf(Transaction tx, UserPrincipal principal) {
        PaymentLink link = tx.getLink();
        Optional<Terminal> terminal = link != null ? terminalRepository.findById(link.getTerminalId()) : Optional.empty();
        return MoneyActions.decide(new MoneyActions.Facts(
                tx.getStatus(),
                link != null && link.getPaymentType() == PaymentType.DMS,
                tx.getAmount(),
                tx.getCapturedAmount(),
                tx.getRefundedAmount(),
                UserPrincipal.getRole(principal),
                terminal.isPresent(),
                terminal.map(providerCredentials::hasCredentials).orElse(false),
                attemptRepository.findById(tx.getId()).orElse(null),
                Instant.now()));
    }

    private TransactionResponse mapToTransactionResponse(Transaction tx, TransactionActions actions) {
        Map<String, Object> resp = tx.getProviderResponse();
        TransactionFacts facts = ProviderOrderDetails.read(resp);

        return new TransactionResponse(
                tx.getId(),
                tx.getLink() != null ? tx.getLink().getId() : null,
                tx.getStatus().name(),
                tx.getAmount(),
                tx.getCapturedAmount(),
                tx.getRefundedAmount(),
                tx.getLink() != null ? tx.getLink().getCurrency() : "AZN",
                tx.getLink() != null ? tx.getLink().getDescription() : null,
                tx.getLink() != null ? tx.getLink().getMerchantOrderId() : null,
                tx.getLink() != null && tx.getLink().getPaymentType() != null ? tx.getLink().getPaymentType().name() : "SMS",
                tx.getLink() != null ? tx.getLink().getTerminalId() : null,
                tx.getRidByMerchant() != null ? tx.getRidByMerchant().toString() : null,
                facts.maskedCard(),
                facts.rrn(),
                facts.approvalCode(),
                tx.getCreatedAt(),
                tx.getLink() != null ? tx.getLink().getCustomerName() : null,
                tx.getLink() != null ? tx.getLink().getCustomerEmail() : null,
                tx.getLink() != null ? tx.getLink().getCustomerPhone() : null,
                tx.getClientIp(),
                tx.getUserAgent(),
                tx.getProviderOrderId(),
                statusHistoryOf(tx, resp),
                failureReasonOf(resp),
                actions
        );
    }

    // Только записанное (Р-48, Р-63): createdAt, mpCapture.at, каждое mpRefunds[i].at. STATUS со
    // временем updatedAt — последним и лишь когда текущий статус выше не объяснён: после возврата
    // updatedAt принадлежит возврату, и STATUS приписал бы переходу чужое время.
    private List<TransactionResponse.TransactionEvent> statusHistoryOf(Transaction tx, Map<String, Object> resp) {
        List<TransactionResponse.TransactionEvent> events = new ArrayList<>();

        if (tx.getCreatedAt() != null) {
            events.add(new TransactionResponse.TransactionEvent(
                    tx.getCreatedAt(), "CREATED", TransactionStatus.PENDING.name(), null, null));
        }

        Map<String, Object> capture = resp != null && resp.get(CAPTURE_KEY) instanceof Map<?, ?> m
                ? castRecord(m)
                : null;
        if (capture != null) {
            events.add(new TransactionResponse.TransactionEvent(
                    instantOf(capture.get("at")), "CAPTURED", TransactionStatus.SUCCESS.name(),
                    amountOf(capture.get("amount")), ProviderPayloads.scalarText(capture.get("ridByPmo"))));
        }

        BigDecimal base = refundableBase(tx);
        BigDecimal running = BigDecimal.ZERO;
        if (resp != null && resp.get(REFUNDS_KEY) instanceof List<?> refunds) {
            for (Object entry : refunds) {
                if (!(entry instanceof Map<?, ?> m)) {
                    continue;
                }
                Map<String, Object> refund = castRecord(m);
                BigDecimal amount = amountOf(refund.get("amount"));
                if (amount != null) {
                    running = running.add(amount);
                }
                TransactionStatus after = base != null && running.compareTo(base) >= 0
                        ? TransactionStatus.REFUNDED
                        : TransactionStatus.PARTIALLY_REFUNDED;
                events.add(new TransactionResponse.TransactionEvent(
                        instantOf(refund.get("at")), "REFUNDED", after.name(),
                        amount, ProviderPayloads.scalarText(refund.get("ridByPmo"))));
            }
        }

        // События без времени сортировать не по чему, и показывать их как датированные нельзя.
        events.removeIf(event -> event.at() == null);
        events.sort(Comparator.comparing(TransactionResponse.TransactionEvent::at));

        String current = tx.getStatus().name();
        boolean explained = !events.isEmpty()
                && events.get(events.size() - 1).status().equals(current);
        if (!explained && tx.getUpdatedAt() != null) {
            events.add(new TransactionResponse.TransactionEvent(
                    tx.getUpdatedAt(), "STATUS", current, null, null));
        }
        return events;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castRecord(Map<?, ?> raw) {
        return (Map<String, Object>) raw;
    }

    // Нечитаемое время — «времени нет», а не падение карточки: такое событие отсеется.
    private static Instant instantOf(Object raw) {
        String text = ProviderPayloads.scalarText(raw);
        if (text == null) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            log.warn("Transaction event carries an unparseable timestamp {}; the event is dropped", text);
            return null;
        }
    }

    private static BigDecimal amountOf(Object raw) {
        String text = ProviderPayloads.scalarText(raw);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            log.warn("Transaction event carries an unparseable amount {}; the event keeps no amount", text);
            return null;
        }
    }

    private static String failureReasonOf(Map<String, Object> providerResponse) {
        Object reason = providerResponse != null ? providerResponse.get(DECLINE_REASON_KEY) : null;
        return reason != null ? String.valueOf(reason) : null;
    }

    // public: этими же воротами ходит DashboardService — своей копии правил доступа у статистики быть
    // не должно (AGENTS §12, п. 8).
    public static final Set<Role> READ_ROLES = EnumSet.allOf(Role.class);

    // Создание и правка ссылок, списание DMS-холда.
    static final Set<Role> LINK_WRITE_ROLES =
            EnumSet.of(Role.SYSTEM_ADMIN, Role.COMPANY_HEAD, Role.COMPANY_MANAGER, Role.COMPANY_EMPLOYEE);

    // Без COMPANY_EMPLOYEE: возврат двигает деньги обратно.
    static final Set<Role> REFUND_ROLES =
            EnumSet.of(Role.SYSTEM_ADMIN, Role.COMPANY_HEAD, Role.COMPANY_MANAGER);

    // Разрешить неизвестный исход — только администратор, после сверки с провайдером (Р-123).
    private static final Set<Role> RESOLVE_ROLES = EnumSet.of(Role.SYSTEM_ADMIN);

    // Читают через все компании (Р-1).
    public static boolean isGlobalReader(Role role) {
        return role == Role.SYSTEM_ADMIN || role == Role.AUDITOR;
    }

    // Единственные ворота доступа по терминалу. principal, а не строки: null-principal и нераспознанная
    // роль кончаются отказом, а не NPE. Компания возвращённого терминала — компания записей журнала (Р-104).
    private Terminal validateAccess(Integer terminalId, UserPrincipal principal, Set<Role> allowedRoles) {
        Role userRole = UserPrincipal.getRole(principal);
        String rawRole = UserPrincipal.getRawRole(principal);
        String companyId = UserPrincipal.getCompanyId(principal);

        log.debug("Validating terminal access: terminalId={}, role={}, companyId={}, allowedRoles={}", terminalId, rawRole, companyId, allowedRoles);
        if (userRole == null || !allowedRoles.contains(userRole)) {
            log.warn("Access denied. Role {} is not in allowed roles: {}", rawRole, allowedRoles);
            // Отказ — под терминал: всё здесь достигается через компанию терминала. Действие READ, как
            // в directory, — иначе поиск по журналу не находит одно по другому (P3-2).
            auditLogService.logDenied(AuditEntity.TERMINAL, String.valueOf(terminalId), AuditAction.READ,
                    UserPrincipal.getUsername(principal), companyId,
                    "Denied: role " + rawRole + " is not allowed to act on terminal " + terminalId);
            throw new InvalidStateException("Access denied: role " + rawRole + " is not authorized for this action");
        }
        Terminal terminal = terminalRepository.findById(terminalId)
                .orElseThrow(() -> {
                    log.warn("Terminal not found: {}", terminalId);
                    return new ResourceNotFoundException("Terminal not found: " + terminalId);
                });
        if (isGlobalReader(userRole)) {
            log.debug("Access granted. User is a global reader ({}).", userRole);
            return terminal;
        }
        if (companyId == null || !companyId.equals(terminal.getCompanyId())) {
            log.warn("Access denied. User companyId {} does not match terminal companyId {}", companyId, terminal.getCompanyId());
            auditLogService.logDenied(AuditEntity.TERMINAL, String.valueOf(terminalId), AuditAction.READ,
                    UserPrincipal.getUsername(principal), companyId,
                    "Denied: role " + rawRole + " of company " + companyId
                            + " attempted to act on terminal " + terminalId + " of another company");
            throw new InvalidStateException("Access denied to terminal: " + terminalId);
        }
        log.debug("Access granted for company: {}", companyId);
        return terminal;
    }

    private PaymentLink findLinkOrThrow(UUID id) {
        return paymentLinkRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Payment link not found: {}", id);
                    return new ResourceNotFoundException("Payment link not found: " + id);
                });
    }
}
