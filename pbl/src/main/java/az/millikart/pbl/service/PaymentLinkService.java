package az.millikart.pbl.service;

import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.domain.PaymentLinkStatus;
import az.millikart.pbl.domain.Terminal;
import az.millikart.pbl.domain.Transaction;
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

import az.millikart.pbl.provider.AcquiringClient;
import az.millikart.pbl.provider.ProviderDeclineReason;
import az.millikart.pbl.provider.ProviderOrderDetails;
import az.millikart.pbl.provider.ProviderOrderDetails.TransactionFacts;
import az.millikart.pbl.provider.ProviderOrderStatus;
import az.millikart.pbl.provider.ProviderOrderStatus.ProviderOrderOutcome;
import az.millikart.pbl.provider.ProviderPayloads;
import az.millikart.pbl.provider.dto.MoneyOperationResult;
import az.millikart.pbl.repository.PaymentLinkRepository;
import az.millikart.pbl.repository.TerminalRepository;
import az.millikart.pbl.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final PaymentLinkRepository paymentLinkRepository;
    private final TransactionRepository transactionRepository;
    private final TerminalRepository terminalRepository;
    private final AcquiringClient acquiringClient;
    private final PaymentLinkMapper mapper;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate txTemplate;
    private final String baseUrl;
    private final Duration defaultLinkTtl;
    private final Duration maxLinkTtl;

    public PaymentLinkService(PaymentLinkRepository paymentLinkRepository,
                               TransactionRepository transactionRepository,
                               TerminalRepository terminalRepository,
                               AcquiringClient acquiringClient,
                               PaymentLinkMapper mapper,
                               AuditLogService auditLogService,
                               ApplicationEventPublisher eventPublisher,
                               PlatformTransactionManager transactionManager,
                               @Value("${pbl.base-url}") String baseUrl,
                               @Value("${pbl.link.default-ttl}") Duration defaultLinkTtl,
                               @Value("${pbl.link.max-ttl}") Duration maxLinkTtl) {
        this.paymentLinkRepository = paymentLinkRepository;
        this.transactionRepository = transactionRepository;
        this.terminalRepository = terminalRepository;
        this.acquiringClient = acquiringClient;
        this.mapper = mapper;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.baseUrl = baseUrl;
        this.defaultLinkTtl = defaultLinkTtl;
        this.maxLinkTtl = maxLinkTtl;
    }

    public PaymentLinkResponse create(CreatePaymentLinkRequest request, UserPrincipal principal) {
        String userId = UserPrincipal.getUserId(principal);
        String rawRole = UserPrincipal.getRawRole(principal);
        String companyId = UserPrincipal.getCompanyId(principal);

        log.info("Request to create payment link: merchantOrderId={}, terminal={}, amount={}, currency={}, userId={}, role={}, companyId={}",
                request.merchantOrderId(), request.terminal(), request.amount(), request.currency(), userId, rawRole, companyId);

        validateAccess(request.terminal(), principal, LINK_WRITE_ROLES);

        // На заблокированном терминале новых платежей нет (Р-38), а ссылка на нём родилась бы
        // нерабочей: путь открытия её всё равно отвергнет.
        Terminal terminal = terminalRepository.findById(request.terminal())
                .orElseThrow(() -> new ResourceNotFoundException("Terminal not found: " + request.terminal()));
        if (terminal.isBlocked()) {
            log.warn("Refusing to create a payment link on blocked terminal {}", request.terminal());
            throw new BusinessException("terminal " + request.terminal()
                    + " is blocked and cannot take new payments; unblock it or use another terminal");
        }

        CustomerDto customer = request.customer();
        String providerRef = "RID-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // P1-9: срок есть у каждой ссылки. Раньше expires_at оставался NULL — планировщик не находил
        // просроченных, и ссылки не кончались никогда, пока портал рисовал над ними обратный отсчёт.
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
                .customerPhone(customer != null ? customer.phone() : null)
                .paymentType(request.paymentType())
                .usageType(request.usageType())
                .maxPayments(request.usageType() == UsageType.MULTIPLE ? request.maxPayments() : null)
                // Ноль при рождении; дальше его переписывает счёт по PAID_STATUSES — то же число,
                // что отдаёт API (P2-16). Возврат из набора не выводит, писать нечего.
                .currentPaymentsCount(0)
                .status(PaymentLinkStatus.ACTIVE)
                .metadata(request.metadata())
                .expiresAt(expiresAt)
                .build();

        PaymentLink saved = txTemplate.execute(status -> paymentLinkRepository.saveAndFlush(link));

        // txTemplate закоммитил запись выше, поэтому fallbackExecution пишет событие сразу (Р-35).
        eventPublisher.publishEvent(AuditEvent.of(AuditEntity.PAYMENT_LINK, saved.getId().toString(), AuditAction.CREATE,
                UserPrincipal.getUsername(principal), companyId,
                "Created " + saved.getUsageType() + " " + saved.getPaymentType() + " link for "
                        + saved.getAmount() + " " + saved.getCurrency() + " on terminal "
                        + saved.getTerminalId() + ", expires " + saved.getExpiresAt()));

        log.info("Payment link created successfully with ID: {} and provider reference: {}", saved.getId(), providerRef);
        // У только что созданной ссылки платежей нет — искать нечего.
        return mapper.toResponse(saved, 0, 0, null);
    }

    // Статусы попыток, замораживающие сумму ссылки (P2-9): плательщику уже показали цену, и она
    // стала частью записи. Перечислены статусы, которые ЗАПИРАЮТ, а не «всё кроме FAILED», — чтобы
    // новый статус в TransactionStatus по умолчанию запрещал правку, а не тихо разрешал её.
    // PENDING запирает тоже: это платёж, идущий прямо сейчас.
    private static final Set<TransactionStatus> AMOUNT_LOCKING_STATUSES = EnumSet.of(
            TransactionStatus.PENDING,
            TransactionStatus.AUTHORIZED,
            TransactionStatus.SUCCESS,
            TransactionStatus.PARTIALLY_REFUNDED,
            TransactionStatus.REFUNDED);

    // Возвращённая часть PAID_STATUSES (P2-16, Р-50). Использованием ссылки такие платежи быть не
    // перестают — набор нужен только чтобы показать, сколько из них кончились возвратом.
    // В списочный ответ не добавлять: список строится без походов в транзакции, счётчик на строку
    // вернёт N+1, снятый в P2-15.
    private static final Set<TransactionStatus> REFUNDED_STATUSES = EnumSet.of(
            TransactionStatus.REFUNDED,
            TransactionStatus.PARTIALLY_REFUNDED);

    // Сколько раз ссылкой воспользовались: состоявшиеся платежи, возвращённые в том числе
    // (PAID_STATUSES, Р-49). Это и currentPaymentsCount в API, и колонка, и база для лимита.
    private long usedCount(UUID linkId) {
        return transactionRepository.countByLinkIdAndStatusIn(linkId, TransactionStatus.PAID_STATUSES);
    }

    // Сколько платежей ссылки вернули — полностью или частично (P2-16, Р-50).
    private int refundedCount(UUID linkId) {
        return (int) transactionRepository.countByLinkIdAndStatusIn(linkId, REFUNDED_STATUSES);
    }

    // Лишний запрос на вызов, который могут позволить себе только одиночные эндпоинты.
    // В списке не использовать — там lastPaidAtByLink на всю страницу разом (P2-15, Р-46).
    private Instant lastPaidAt(UUID linkId) {
        return transactionRepository
                .findFirstByLinkIdAndStatusInOrderByCreatedAtDesc(linkId, TransactionStatus.PAID_STATUSES)
                .map(Transaction::getCreatedAt)
                .orElse(null);
    }

    // Время последней оплаты на целую страницу одним группирующим запросом (P2-15): вызов на
    // строку — это двадцать запросов на листинг, тот же N+1, что убрали в P2-4. Сторож —
    // PaymentLinkListPaginationTest со счётчиком запросов Hibernate. Пустая страница запрос
    // пропускает: IN () — невалидный SQL.
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

    // Переходы статуса, доступные мерчанту руками (P2-9): всего, чего в таблице нет, отвергается —
    // иначе срок и лимит ничего не значат (раньше любой статус ставился в ACTIVE, и просроченная
    // ссылка воскресала одним PATCH). CANCELED → ACTIVE ещё и требует, чтобы срок был впереди.
    // SUSPENDED недостижим и неотменяем отсюда (P2-8, Р-39): его ставит блокировка терминала.
    private static final Map<PaymentLinkStatus, Set<PaymentLinkStatus>> ALLOWED_STATUS_TRANSITIONS = Map.of(
            PaymentLinkStatus.ACTIVE, EnumSet.of(PaymentLinkStatus.CANCELED),
            PaymentLinkStatus.EXPIRED, EnumSet.of(PaymentLinkStatus.CANCELED),
            PaymentLinkStatus.CANCELED, EnumSet.of(PaymentLinkStatus.ACTIVE),
            PaymentLinkStatus.COMPLETED, EnumSet.noneOf(PaymentLinkStatus.class),
            PaymentLinkStatus.SUSPENDED, EnumSet.noneOf(PaymentLinkStatus.class));

    @Transactional
    public PaymentLinkResponse update(UUID id, UpdatePaymentLinkRequest request, UserPrincipal principal) {
        String userId = UserPrincipal.getUserId(principal);
        String rawRole = UserPrincipal.getRawRole(principal);
        String companyId = UserPrincipal.getCompanyId(principal);

        log.info("Request to update payment link: id={}, userId={}, role={}, companyId={}", id, userId, rawRole, companyId);
        PaymentLink link = findLinkOrThrow(id);

        validateAccess(link.getTerminalId(), principal, LINK_WRITE_ROLES);

        long usedCount = usedCount(id);

        // Один PATCH — одна правка: сдвинутые поля собираются здесь и пишутся одной строкой (P2-9).
        List<String> changes = new ArrayList<>();

        // PATCH, вернувший объект целиком, не должен падать на поле, которое не двигали: сумма,
        // равная текущей, — не правка и до сторожа не доходит.
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
            // В аудит — только имена полей: значения здесь персональные данные.
            if (customer.fullName() != null && !customer.fullName().equals(link.getCustomerName())) {
                changes.add("customerName");
                link.setCustomerName(customer.fullName());
            }
            if (customer.email() != null && !customer.email().equals(link.getCustomerEmail())) {
                changes.add("customerEmail");
                link.setCustomerEmail(customer.email());
            }
            if (customer.phone() != null && !customer.phone().equals(link.getCustomerPhone())) {
                changes.add("customerPhone");
                link.setCustomerPhone(customer.phone());
            }
        }
        if (request.expiresAt() != null) {
            // Потолок отсчитывается от created_at ссылки, а не от now (P1-9): иначе мерчант шагал
            // бы сроком вперёд по одному PATCH — 90 дней, потом ещё 90 — и потолок не ограничивал бы.
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
            // Лимит ниже уже прошедших платежей дал бы «3 из 2 использовано» (P2-9). Считается по
            // использованиям, а не по строкам SUCCESS (P2-16): возвращённый платёж — тоже использование.
            if (request.maxPayments() < usedCount) {
                log.warn("Refusing to lower maxPayments of link {} to {}: it was already used {} times",
                        id, request.maxPayments(), usedCount);
                throw new BusinessException("maxPayments cannot be lowered to " + request.maxPayments()
                        + ": the link was already used " + usedCount
                        + " times (a refunded payment still counts as a use)");
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
        // Последним, чтобы PATCH и с новым сроком, и с ACTIVE судился по только что заданному сроку.
        if (request.status() != null) {
            applyStatusChange(link, request.status(), changes);
        }

        PaymentLink saved = paymentLinkRepository.save(link);

        eventPublisher.publishEvent(AuditEvent.of(AuditEntity.PAYMENT_LINK, saved.getId().toString(),
                saved.getStatus() == PaymentLinkStatus.CANCELED ? AuditAction.CANCEL : AuditAction.UPDATE,
                UserPrincipal.getUsername(principal), companyId,
                changes.isEmpty() ? "No fields changed" : "Changed " + String.join(", ", changes)));

        log.info("Payment link {} updated by userId={}: {}", id, userId,
                changes.isEmpty() ? "no fields changed" : String.join(", ", changes));
        return mapper.toResponse(saved, (int) usedCount, refundedCount(id), lastPaidAt(id));
    }

    // Установка статуса, который у ссылки уже стоит, — не правка и не ошибка: PATCH, вернувший
    // объект целиком, не должен на ней падать (P2-9).
    private void applyStatusChange(PaymentLink link, PaymentLinkStatus target, List<String> changes) {
        PaymentLinkStatus current = link.getStatus();
        if (target == current) {
            return;
        }
        // У обоих направлений SUSPENDED свой текст: «нельзя сменить с SUSPENDED на ACTIVE» отправило
        // бы мерчанта искать причину в ссылке, а причина — снятый с обслуживания терминал.
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
        // Снятие отмены не должно возвращать ссылку с уже прошедшим сроком: она была бы ACTIVE и
        // неоплачиваемой до следующего прохода планировщика. Пустой срок — ссылка без срока.
        if (target == PaymentLinkStatus.ACTIVE
                && link.getExpiresAt() != null && !link.getExpiresAt().isAfter(Instant.now())) {
            log.warn("Refusing to reactivate link {}: it expired at {}", link.getId(), link.getExpiresAt());
            throw new BusinessException("payment link expired at " + link.getExpiresAt()
                    + " and cannot be reactivated; send a new expiresAt in the same request");
        }
        changes.add("status " + current + " -> " + target);
        link.setStatus(target);
    }

    // Проверка присланного мерчантом срока по двум границам (P1-9). Обе — отказ с 400, а не тихое
    // подрезание: срок, о котором мерчант не просил, хуже отклонённого запроса — ссылка умрёт в
    // момент, которого никто не планировал. createdAt — точка отсчёта потолка: при создании это
    // «сейчас», при правке — created_at самой ссылки (почему — в месте вызова).
    private Instant validateExpiresAt(Instant expiresAt, Instant createdAt) {
        if (!expiresAt.isAfter(Instant.now())) {
            log.warn("Refusing expiresAt {}: it is not in the future", expiresAt);
            throw new BusinessException("expiresAt must be in the future");
        }
        // Ссылки без created_at быть не может (@CreationTimestamp); запасной now оставляет сломанной
        // строке 400 вместо NullPointerException.
        Instant ceiling = (createdAt != null ? createdAt : Instant.now()).plus(maxLinkTtl);
        if (expiresAt.isAfter(ceiling)) {
            log.warn("Refusing expiresAt {}: the ceiling for this link is {} ({} from its creation time)",
                    expiresAt, ceiling, maxLinkTtl);
            throw new BusinessException("expiresAt must not be later than " + ceiling
                    + ": a payment link may live at most " + formatTtl(maxLinkTtl) + " from the moment it was created");
        }
        return expiresAt;
    }

    // Срок словами, как его читает мерчант: Duration.toString() написал бы PT2160H — то же число
    // и никакого объяснения.
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
        String userId = UserPrincipal.getUserId(principal);
        String rawRole = UserPrincipal.getRawRole(principal);
        String companyId = UserPrincipal.getCompanyId(principal);

        log.info("Request to fetch payment link details: id={}, userId={}, role={}, companyId={}", id, userId, rawRole, companyId);
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

        log.info("Request to list payment links: terminal={}, status={}, userId={}, role={}, companyId={}", terminal, status, userId, rawRole, companyId);
        // Нераспознанная роль в READ_ROLES не попадает, поэтому отвергается здесь, а не ниже.
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
        // Один запрос на страницу, никогда не на строку (P2-15).
        Map<UUID, Instant> paidAt = lastPaidAtByLink(page.getContent());
        List<PaymentLinkSummaryResponse> content = page.getContent().stream()
                .map(link -> mapper.toSummary(link, paidAt.get(link.getId())))
                .toList();
        return PagedResponse.of(page, content);
    }

    // Блокировку терминала не проверяет и не должен (Р-38): списываются деньги, уже удержанные на
    // карте. «Терминал заблокирован» значит «новых платежей нет», а не «бросить прежние холды» —
    // отказ оставил бы холд висеть на карте держателя (Void у нас нет), и мерчант не смог бы ни
    // списать его, ни отменить. Блокировка проверяется там, где платёж НАЧИНАЕТСЯ.
    @Transactional
    public PaymentLinkResponse completeDms(UUID transactionId, CompleteDmsRequest request, UserPrincipal principal) {
        String userId = UserPrincipal.getUserId(principal);
        String rawRole = UserPrincipal.getRawRole(principal);
        String companyId = UserPrincipal.getCompanyId(principal);

        log.info("Request to complete DMS: transactionId={}, amount={}, userId={}, role={}, companyId={}", transactionId, request.amount(), userId, rawRole, companyId);
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> {
                    log.warn("Transaction not found for DMS complete: {}", transactionId);
                    return new ResourceNotFoundException("Transaction not found: " + transactionId);
                });
        PaymentLink link = transaction.getLink();

        validateAccess(link.getTerminalId(), principal, LINK_WRITE_ROLES);

        // P0-8: раньше SUCCESS принимался и здесь — одну авторизацию можно было склирить дважды,
        // то есть дважды снять деньги с держателя карты.
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
            // PENDING остаётся допустимым: наша копия статуса отстаёт — страница плательщика
            // опрашивает эквайера ровно один раз (P0-2), и холд, поставленный после опроса, здесь
            // всё ещё PENDING. Спрашиваем эквайера, а не гадаем. PaymentOutcomeUnknownException из
            // опроса намеренно не ловится: списывать по непрочитанному статусу нельзя.
            log.info("Transaction {} is PENDING; polling the acquirer before deciding on the capture", transactionId);
            // Возвращаются те же управляемые экземпляры, поэтому link выше остаётся той же сущностью.
            transaction = refreshStatus(transaction).transaction();

            if (transaction.getStatus() == TransactionStatus.SUCCESS) {
                log.warn("Refusing capture of transaction {}: the acquirer reports it already settled", transactionId);
                throw new BusinessException("Transaction has already been captured");
            }
            if (transaction.getStatus() != TransactionStatus.AUTHORIZED) {
                // Отказ откатит обновлённый статус вместе с транзакцией — безвредно: деньги не
                // двигались, строку добьёт фоновая сверка.
                log.warn("Cannot complete DMS. Transaction {} is still {} at the acquirer", transactionId, transaction.getStatus());
                throw new BusinessException("Transaction is in status " + transaction.getStatus()
                        + " and has not been authorized by the acquirer yet. Capture is only possible for an authorized payment.");
            }
        }

        // P0-8: сумма теперь действительно уходит эквайеру, поэтому проверяется до отправки.
        // @Positive на DTO уже отсёк ноль и минус; здесь то, чего он не видит.
        assertCapturableScale(request.amount(), "Capture");
        if (request.amount().compareTo(transaction.getAmount()) > 0) {
            log.warn("Refusing capture of transaction {}: requested {} exceeds the authorized amount {}",
                    transactionId, request.amount(), transaction.getAmount());
            throw new BusinessException("Capture amount exceeds the authorized amount");
        }

        Terminal terminal = terminalRepository.findById(link.getTerminalId())
                .orElseThrow(() -> {
                    log.error("Terminal {} configuration missing for transaction {}", link.getTerminalId(), transactionId);
                    return new BusinessException("Terminal configuration not found");
                });

        log.info("Sending DMS Clearing capture request to provider for providerOrderId: {}, amount: {}", transaction.getProviderOrderId(), request.amount());

        // P1-8b: возвращается только при подтверждённом клиринге (tran.match.ridByPmo);
        // неподтверждённый ответ — 502, и до смены статуса ниже дело не доходит.
        MoneyOperationResult capture;
        try {
            capture = acquiringClient.completeDms(
                    transaction.getProviderOrderId(),
                    transaction.getProviderPassword(),
                    terminal.getLogin(),
                    terminal.getPassword(),
                    request.amount()
            );
        } catch (PaymentOutcomeUnknownException e) {
            // Единственное денежное событие без локального следа: ниже ничего не выполнится,
            // транзакция откатится, 502 велит мерчанту проверить перед повтором. AFTER_COMMIT тут
            // не сработает — потому запись здесь и сейчас (P2-14), и она единственное свидетельство,
            // что операцию вообще пытались провести.
            auditLogService.logUnresolved(AuditEntity.TRANSACTION, transactionId.toString(), AuditAction.CAPTURE,
                    UserPrincipal.getUsername(principal), companyId,
                    "Capture of " + request.amount() + " " + link.getCurrency()
                            + " left unconfirmed by the acquirer (providerOrderId "
                            + transaction.getProviderOrderId() + "): " + e.getMessage()
                            + ". Outcome unknown — reconcile before retrying.");
            throw e;
        }

        // Сырой ответ плюс свидетельство этого списания под своим ключом, чтобы в споре были
        // идентификаторы эквайера. Сырое тело идёт через ProviderPayloads.withoutSecrets (P0-9).
        Map<String, Object> mergedResponse = new HashMap<>();
        if (transaction.getProviderResponse() != null) {
            mergedResponse.putAll(transaction.getProviderResponse());
        }
        if (capture.raw() != null) {
            mergedResponse.putAll(ProviderPayloads.withoutSecrets(capture.raw()));
        }
        mergedResponse.put(CAPTURE_KEY, moneyOperationRecord(capture, request.amount()));
        transaction.setProviderResponse(mergedResponse);

        // P0-8: сколько эквайер реально склирил — отдельно от amount, который остаётся
        // авторизованной суммой. Потолок возврата читает это поле: вернуть неснятое нельзя.
        transaction.setCapturedAmount(request.amount());
        // По-прежнему SUCCESS: транзакция рассчитана, просто на списанную сумму. Частичное списание
        // — не отдельное состояние жизненного цикла.
        transaction.setStatus(TransactionStatus.SUCCESS);
        transactionRepository.save(transaction);
        log.info("Transaction {} captured successfully for {} of the authorized {} and transitioned to SUCCESS.",
                transactionId, request.amount(), transaction.getAmount());

        // Считаем использования, а не строки SUCCESS (P2-16): возвращённый платёж держит свой слот,
        // и это списание может оказаться исчерпывающим лимит. В колонку идёт то же число, что в API.
        long usedCount = usedCount(link.getId());
        link.setCurrentPaymentsCount((int) usedCount);
        if (link.getUsageType() == UsageType.SINGLE) {
            log.info("Single-use link {} successfully completed.", link.getId());
            link.setStatus(PaymentLinkStatus.COMPLETED);
        } else if (link.getUsageType() == UsageType.MULTIPLE && link.getMaxPayments() != null && usedCount >= link.getMaxPayments()) {
            log.info("Multi-use link {} reached max payments limit. Transitioned to COMPLETED.", link.getId());
            link.setStatus(PaymentLinkStatus.COMPLETED);
        }
        PaymentLink savedLink = paymentLinkRepository.save(link);

        eventPublisher.publishEvent(AuditEvent.of(AuditEntity.TRANSACTION, transactionId.toString(),
                AuditAction.CAPTURE, UserPrincipal.getUsername(principal), companyId,
                "Captured " + request.amount() + " " + link.getCurrency() + " of the authorized "
                        + transaction.getAmount() + " (ridByPmo " + capture.ridByPmo()
                        + ", tranActionId " + capture.tranActionId()
                        + ", approvalCode " + capture.approvalCode() + ")"));

        return mapper.toResponse(savedLink, (int) usedCount, refundedCount(savedLink.getId()),
                lastPaidAt(savedLink.getId()));
    }

    // Блокировку терминала не проверяет и не должен (Р-38): возврат отдаёт деньги за уже
    // состоявшийся платёж. Блокировка — решение о будущих платежах; заморозить ею возвраты значило
    // бы наказать покупателя, и деньги застряли бы до того, как терминал вспомнят разблокировать.
    @Transactional
    public RefundResponse refund(UUID transactionId, RefundRequest request, UserPrincipal principal) {
        String userId = UserPrincipal.getUserId(principal);
        String rawRole = UserPrincipal.getRawRole(principal);
        String companyId = UserPrincipal.getCompanyId(principal);

        log.info("Request to refund transaction: transactionId={}, amount={}, userId={}, role={}, companyId={}", transactionId, request.amount(), userId, rawRole, companyId);
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> {
                    log.warn("Transaction not found for refund: {}", transactionId);
                    return new ResourceNotFoundException("Transaction not found: " + transactionId);
                });
        PaymentLink link = transaction.getLink();

        validateAccess(link.getTerminalId(), principal, REFUND_ROLES);

        if (transaction.getStatus() != TransactionStatus.SUCCESS && transaction.getStatus() != TransactionStatus.PARTIALLY_REFUNDED) {
            log.warn("Cannot refund transaction. Current status: {}", transaction.getStatus());
            throw new BusinessException("Only successful or partially refunded transactions can be refunded");
        }

        // Как в completeDms: сумма уходит эквайеру строкой с двумя знаками, третий знак надо
        // отвергнуть здесь, а не взрывать на выходе.
        assertCapturableScale(request.amount(), "Refund");

        BigDecimal refundableBase = refundableBase(transaction);
        BigDecimal newRefundedAmount = transaction.getRefundedAmount().add(request.amount());
        if (newRefundedAmount.compareTo(refundableBase) > 0) {
            log.warn("Refund amount {} exceeds the remaining captured amount of transaction {} (captured {}, already refunded {}).",
                    request.amount(), transactionId, refundableBase, transaction.getRefundedAmount());
            throw new BusinessException("Refund amount exceeds the captured amount of the transaction");
        }

        Terminal terminal = terminalRepository.findById(link.getTerminalId())
                .orElseThrow(() -> {
                    log.error("Terminal {} configuration missing for transaction {}", link.getTerminalId(), transactionId);
                    return new BusinessException("Terminal configuration not found");
                });

        log.info("Sending refund request to provider for providerOrderId: {}, amount: {}", transaction.getProviderOrderId(), request.amount());

        // P1-8b: возвращается только при подтверждённом эквайером возврате (tran.match.ridByPmo).
        MoneyOperationResult result;
        try {
            result = acquiringClient.refund(transaction.getProviderOrderId(), transaction.getProviderPassword(),
                    terminal.getLogin(), terminal.getPassword(), request.amount());
        } catch (PaymentOutcomeUnknownException e) {
            // То же, что в completeDms, и здесь важнее: деньги могли уйти со счёта мерчанта, а у нас
            // не осталось ничего. Синхронно — транзакция сейчас откатится и унесла бы событие (P2-14).
            auditLogService.logUnresolved(AuditEntity.TRANSACTION, transactionId.toString(), AuditAction.REFUND,
                    UserPrincipal.getUsername(principal), companyId,
                    "Refund of " + request.amount() + " " + link.getCurrency()
                            + " left unconfirmed by the acquirer (providerOrderId "
                            + transaction.getProviderOrderId() + "): " + e.getMessage()
                            + ". Outcome unknown — reconcile before retrying.");
            throw e;
        }

        // Идентификаторы — собственные у эквайера (§5.7), ничего не выдумывается: раньше читался
        // несуществующий ключ, а до того подставлялся «REF-XXXXXXXX» — в споре выдуманный номер
        // возврата хуже, чем никакого.
        if (result.tranActionId() == null) {
            log.warn("Acquirer confirmed the refund of transaction {} (ridByPmo {}) without a tranActionId; "
                    + "the refund response will carry no refundId", transactionId, result.ridByPmo());
        }

        // След каждого возврата: их бывает несколько (частичные), поэтому они складываются в список
        // под своим ключом, а не перезаписывают друг друга. Сырое тело мержится сверху, как в
        // completeDms, и идёт через withoutSecrets (P0-9).
        Map<String, Object> mergedResponse = new HashMap<>();
        if (transaction.getProviderResponse() != null) {
            mergedResponse.putAll(transaction.getProviderResponse());
        }
        if (result.raw() != null) {
            mergedResponse.putAll(ProviderPayloads.withoutSecrets(result.raw()));
        }
        List<Object> refunds = new ArrayList<>();
        if (mergedResponse.get(REFUNDS_KEY) instanceof List<?> previous) {
            refunds.addAll(previous);
        }
        refunds.add(moneyOperationRecord(result, request.amount()));
        mergedResponse.put(REFUNDS_KEY, refunds);
        transaction.setProviderResponse(mergedResponse);

        transaction.setRefundedAmount(newRefundedAmount);
        if (newRefundedAmount.compareTo(refundableBase) == 0) {
            log.info("Transaction {} fully refunded.", transactionId);
            transaction.setStatus(TransactionStatus.REFUNDED);
        } else {
            log.info("Transaction {} partially refunded. Total refunded: {}", transactionId, newRefundedAmount);
            transaction.setStatus(TransactionStatus.PARTIALLY_REFUNDED);
        }
        transactionRepository.save(transaction);

        eventPublisher.publishEvent(AuditEvent.of(AuditEntity.TRANSACTION, transactionId.toString(),
                AuditAction.REFUND, UserPrincipal.getUsername(principal), companyId,
                "Refunded " + request.amount() + " " + link.getCurrency() + " of " + refundableBase
                        + "; refunded so far " + newRefundedAmount + ", transaction now "
                        + transaction.getStatus() + " (ridByPmo " + result.ridByPmo()
                        + ", tranActionId " + result.tranActionId()
                        + ", approvalCode " + result.approvalCode() + ")"));

        return new RefundResponse(
                transaction.getId(),
                transaction.getStatus().name(),
                request.amount(),
                result.tranActionId(),
                result.ridByPmo(),
                result.approvalCode()
        );
    }

    // Свидетельство одного подтверждённого движения денег: три идентификатора эквайера (§5.5-5.7),
    // сумма и время записи. Значения строками, чтобы в колонке лежало ровно то, что можно
    // процитировать: сумма в той же форме с двумя знаками, в какой ушла эквайеру. UNNECESSARY здесь
    // не бросит — assertCapturableScale уже отверг всё, что длиннее двух знаков.
    private static Map<String, Object> moneyOperationRecord(MoneyOperationResult result, BigDecimal amount) {
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("tranActionId", result.tranActionId());
        evidence.put("ridByPmo", result.ridByPmo());
        evidence.put("approvalCode", result.approvalCode());
        evidence.put("amount", amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString());
        evidence.put("at", Instant.now().toString());
        return evidence;
    }

    // Сколько реально ушло у держателя карты и, значит, может быть возвращено: для DMS —
    // склиренная при списании сумма, для SMS стадии списания нет и базой служит авторизованная.
    // P0-8: чтение amount вместо этого и было дырой, которую открывало частичное списание —
    // авторизовали 1500, списали 500, а вернуть можно было 1500.
    private static BigDecimal refundableBase(Transaction tx) {
        return tx.getCapturedAmount() != null ? tx.getCapturedAmount() : tx.getAmount();
    }

    // Деньги уходят строкой с двумя знаками, и TxpgAcquiringClient форматирует их с UNNECESSARY,
    // чтобы за спиной мерчанта ничего не округлилось. Третий знак отвергается здесь, на краю, где
    // это обычные 400, а не сломанный инвариант глубже.
    private static void assertCapturableScale(BigDecimal amount, String operation) {
        if (amount.scale() > 2) {
            log.warn("Refusing {} of {}: more than two decimal places", operation.toLowerCase(), amount);
            throw new BusinessException(operation + " amount must not have more than two decimal places");
        }
    }

    // Проверка статуса для мерчанта: нужна аутентификация и роль на чтение в компании терминала.
    @Transactional
    public TransactionResponse checkAndStatusUpdate(String identifier, UserPrincipal principal) {
        String rawRole = UserPrincipal.getRawRole(principal);
        String companyId = UserPrincipal.getCompanyId(principal);

        log.info("Request to check transaction status: identifier={}, userId={}, role={}, companyId={}",
                identifier, UserPrincipal.getUserId(principal), rawRole, companyId);

        Transaction tx = resolveTransaction(identifier);
        validateAccess(tx.getLink().getTerminalId(), principal, READ_ROLES);
        return mapToTransactionResponse(refreshStatus(tx).transaction());
    }

    // Проверка статуса для плательщика (страница возврата от провайдера). Ключ — случайный
    // merchantRid из пути, перебрать его нельзя, поэтому проверки владения здесь нет, а ответ
    // намеренно беден на персональные данные. Пусто вместо ошибки — чтобы страница не выдала,
    // существовала ссылка или нет.
    @Transactional
    public Optional<PaymentReceiptView> refreshByMerchantRid(UUID merchantRid) {
        Optional<Transaction> found = transactionRepository.findByMerchantRid(merchantRid);
        if (found.isEmpty()) {
            log.info("No transaction matches the merchantRid on the return page request");
            return Optional.empty();
        }

        Transaction tx = found.get();
        try {
            tx = refreshStatus(tx).transaction();
        } catch (RuntimeException e) {
            // Страница плательщика обязана отрисоваться и при недоступном эквайере: показываем
            // последнее известное состояние, а не роняем запрос.
            log.error("Status refresh failed for transaction {}; rendering last known state", tx.getId(), e);
        }
        return Optional.of(toReceiptView(tx));
    }

    // Метка в providerResponse: платёж закончил этот сервис, а не эквайер.
    static final String RECONCILIATION_OUTCOME_KEY = "reconciliationOutcome";

    // Значение метки для платежа, к которому плательщик так и не вернулся.
    static final String OUTCOME_ABANDONED_TIMEOUT = "ABANDONED_TIMEOUT";

    // Метка на каждом опросе: как этот сервис классифицировал слово эквайера.
    static final String STATUS_OUTCOME_KEY = "mpStatusOutcome";

    // Метка для UNKNOWN и SETTLED_OTHER: сырое значение status дословно, чтобы при разборе было
    // видно, что именно сказал эквайер.
    static final String PROVIDER_STATUS_KEY = "mpProviderStatus";

    // P1-8b: свидетельство DMS-списания, записанное после подтверждения клиринга.
    static final String CAPTURE_KEY = "mpCapture";

    // P1-8b: список подтверждённых возвратов той же формы, что CAPTURE_KEY, — их бывает несколько.
    static final String REFUNDS_KEY = "mpRefunds";

    // P1-8b: причина отказа эквайера; отдаётся как TransactionResponse.failureReason.
    static final String DECLINE_REASON_KEY = "mpDeclineReason";

    // Результат одного опроса: строка транзакции и то, что означало слово эквайера. reconcileOne
    // смотрит на outcome, решая, можно ли гасить платёж по таймауту.
    public record StatusRefresh(Transaction transaction, ProviderOrderOutcome outcome) {}

    // Сверка одной зависшей транзакции с эквайером: асинхронного колбэка от провайдера нет, и этот
    // опрос — единственный путь PENDING-строки к финалу. Своя транзакция, чтобы сбой на одной записи
    // не откатил весь пакет. FAILED ставится только при трёх условиях сразу: после опроса всё ещё
    // PENDING, опрос удался и вернул понятный нефинальный статус, запись старше maxAge — см. Р-20.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileOne(UUID transactionId, Duration maxAge) {
        Transaction tx = transactionRepository.findById(transactionId).orElse(null);
        if (tx == null) {
            log.debug("Reconciliation skipped: transaction {} no longer exists", transactionId);
            return;
        }
        if (tx.getStatus() != TransactionStatus.PENDING) {
            log.debug("Reconciliation skipped: transaction {} is already {}", transactionId, tx.getStatus());
            return;
        }

        StatusRefresh refresh;
        try {
            // Тот же опрос и то же отображение статусов, что у путей мерчанта и плательщика.
            refresh = refreshStatus(tx);
        } catch (RuntimeException e) {
            // Эквайер недоступен или отказал. До опроса ничего не менялось, строка коммитится
            // нетронутой — снова PENDING, до следующего прохода.
            log.warn("Reconciliation could not reach the acquirer for transaction {}: {}. Leaving it PENDING.",
                    transactionId, e.getMessage());
            return;
        }
        tx = refresh.transaction();

        if (tx.getStatus() != TransactionStatus.PENDING) {
            log.info("Reconciliation settled transaction {} as {}", transactionId, tx.getStatus());
            return;
        }

        // После опроса всё ещё PENDING. По таймауту гасится только статус, понятый как «ещё не
        // оплачено»; всё остальное остаётся человеку, каким бы старым ни было.
        ProviderOrderOutcome outcome = refresh.outcome();
        if (outcome == ProviderOrderOutcome.UNKNOWN) {
            log.warn("Reconciliation is leaving transaction {} PENDING: the acquirer's status is not in "
                            + "ProviderOrderStatus, so there is no evidence the payment did not happen. "
                            + "It will not be marked FAILED by age; a human must look at it.",
                    transactionId);
            return;
        }
        if (outcome == ProviderOrderOutcome.SETTLED_OTHER) {
            log.warn("Reconciliation is leaving transaction {} PENDING: the acquirer reports a final state "
                            + "reached outside this service (reversal, refund or closed order). That is not "
                            + "an abandoned payment, so it will not be marked FAILED by age; the money side "
                            + "must be reviewed by hand (order.trans[] — problems.md §6).",
                    transactionId);
            return;
        }
        if (outcome != ProviderOrderOutcome.NON_FINAL) {
            // PAID / AUTHORIZED / FAILED_FINAL меняют статус внутри refreshStatus и сюда не доходят;
            // сторож на случай, если новый outcome молча получит право на таймаут.
            log.warn("Reconciliation is leaving transaction {} PENDING: outcome {} is not eligible for the "
                    + "abandonment timeout", transactionId, outcome);
            return;
        }

        // Дальше эквайер уже ответил — и ответил, что заказ всё ещё не оплачен.
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
        // Хранит последний payload и то, кто закончил платёж: при разборе спора видно, отказ это
        // эквайера или наш таймаут.
        mergedResponse.put(RECONCILIATION_OUTCOME_KEY, OUTCOME_ABANDONED_TIMEOUT);
        mergedResponse.put("reconciledAt", Instant.now().toString());
        tx.setProviderResponse(mergedResponse);
        tx.setStatus(TransactionStatus.FAILED);
        transactionRepository.save(tx);

        // Ссылку намеренно не трогаем: одноразовая должна остаться ACTIVE, чтобы клиент мог начать
        // попытку заново.
        log.info("Transaction {} abandoned by the payer (older than {}, acquirer still non-final); marked FAILED",
                transactionId, maxAge);
    }

    // Ищет транзакцию сначала по её UUID, потом по id заказа у провайдера.
    private Transaction resolveTransaction(String identifier) {
        Transaction tx = null;

        try {
            UUID uuid = UUID.fromString(identifier);
            tx = transactionRepository.findById(uuid).orElse(null);
        } catch (IllegalArgumentException ignored) {}

        if (tx == null) {
            tx = transactionRepository.findByProviderOrderId(identifier)
                    .orElseThrow(() -> {
                        log.warn("Transaction not found for identifier: {}", identifier);
                        return new ResourceNotFoundException("Transaction not found: " + identifier);
                    });
        }
        return tx;
    }

    // Опрашивает эквайера один раз и применяет переход к транзакции, а при расчёте — к её ссылке.
    // Транзакция в терминальном статусе возвращается как есть: ничего не спрашивали — ничего и не
    // известно. Блокировку терминала не проверяет и не должен (Р-38): иначе платежи, шедшие в момент
    // блокировки, застряли бы в PENDING навсегда — ответ у эквайера есть, а спросить некому.
    private StatusRefresh refreshStatus(Transaction tx) {
        if (tx.getStatus() == TransactionStatus.SUCCESS || tx.getStatus() == TransactionStatus.FAILED
                || tx.getStatus() == TransactionStatus.REFUNDED || tx.getStatus() == TransactionStatus.PARTIALLY_REFUNDED) {
            log.debug("Transaction {} already in terminal state: {}", tx.getId(), tx.getStatus());
            return new StatusRefresh(tx, ProviderOrderOutcome.UNKNOWN);
        }

        UUID transactionId = tx.getId();
        PaymentLink link = tx.getLink();
        Terminal terminal = terminalRepository.findById(link.getTerminalId())
                .orElseThrow(() -> {
                    log.error("Terminal {} configuration missing for transaction status check {}", link.getTerminalId(), transactionId);
                    return new BusinessException("Terminal configuration not found");
                });

        Map<String, Object> orderDetails = acquiringClient.getOrderStatus(tx.getProviderOrderId(), tx.getProviderPassword(), terminal.getLogin(), terminal.getPassword());
        if (orderDetails == null) {
            // Раньше было тихим no-op: отсутствующее тело так же неинформативно, как неизвестное
            // слово, и должно быть так же заметно.
            log.warn("Acquirer returned no order payload for transaction {} (providerOrderId {}); "
                    + "treating the status as unknown", transactionId, tx.getProviderOrderId());
        }
        // Без жёсткого приведения: число или объект под "status" роняли ClassCastException посреди
        // денежного потока. Всё, что не известная строка, — UNKNOWN.
        Object raw = orderDetails != null ? orderDetails.get("status") : null;
        ProviderOrderOutcome outcome = ProviderOrderStatus.classify(raw);
        log.info("Provider order status check result: transactionId={}, providerStatus=\"{}\", outcome={}",
                transactionId, raw, outcome);

        switch (outcome) {
            case PAID -> {
                tx.setStatus(TransactionStatus.SUCCESS);
                // Транзакция, только что ставшая SUCCESS, уже внутри этого счёта: Hibernate делает
                // auto-flush перед JPQL-запросом. Стоявшая здесь «+ 1» считала её второй раз и
                // закрывала двухплатёжную ссылку после первого платежа (P1-7). Счёт по
                // PAID_STATUSES (P2-16): возвращённый платёж — тоже использование.
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
                // Заказ есть, никто ещё не заплатил. Менять нечего; reconcileOne может добить позже.
            }
            case SETTLED_OTHER ->
                // Реверсал, возврат или закрытие сделаны на стороне эквайера, мимо портала. Статус
                // намеренно не трогаем: сумм мы не знаем, и REFUNDED положил бы в refunded_amount
                // число, которого никто не видел. Разбор order.trans[] — отдельная задача
                // (problems.md §6); до неё строка разбирается руками.
                log.warn("Acquirer reports order status \"{}\" for transaction {} (providerOrderId {}): the "
                                + "order was changed outside this service (reversal, refund or closed). Local "
                                + "status stays {} and reconciliation will NOT mark it FAILED; the money side "
                                + "needs a manual review — see pbl/TXPG-client-side-integration.md §5.8.8.",
                        raw, transactionId, tx.getProviderOrderId(), tx.getStatus());
            case UNKNOWN ->
                log.warn("Acquirer returned an order status this service does not know: \"{}\" "
                                + "(transaction {}, providerOrderId {}). The transaction stays {} and will NOT be "
                                + "marked FAILED by reconciliation. If this status is legitimate, add it to "
                                + "ProviderOrderStatus — see pbl/TXPG-client-side-integration.md §5.8.8.",
                        raw, transactionId, tx.getProviderOrderId(), tx.getStatus());
        }

        // Payload эквайера плюс то, что сервис из него понял; payload бывает неизменяемым — отсюда
        // копия. P0-9: при orderDetailLevel=2 он несёт пароль заказа, а колонка — след для разбора
        // споров, поэтому идёт через ProviderPayloads.withoutSecrets на каждом опросе, а не только
        // при создании.
        Map<String, Object> stored = new HashMap<>();
        if (orderDetails != null) {
            // Свежий payload заменяет прежний: устаревший mpProviderStatus не должен пережить
            // статус, ставший известным.
            stored.putAll(ProviderPayloads.withoutSecrets(orderDetails));
        } else if (tx.getProviderResponse() != null) {
            // Ответа нет — заменять нечем, и сохранённое знание ценно именно здесь: строка остаётся
            // PENDING на ручной разбор, а стёртый payload оставил бы разбирающему пустоту. Чистим
            // всё равно: строка, записанная до P0-9, может нести пароль тех времён.
            stored.putAll(ProviderPayloads.withoutSecrets(tx.getProviderResponse()));
        }
        stored.put(STATUS_OUTCOME_KEY, outcome.name());
        if ((outcome == ProviderOrderOutcome.UNKNOWN || outcome == ProviderOrderOutcome.SETTLED_OTHER) && raw != null) {
            // Строка — дословно, иначе toString(): в любом случае то, что прислал эквайер.
            stored.put(PROVIDER_STATUS_KEY, String.valueOf(raw));
        }
        if (outcome == ProviderOrderOutcome.FAILED_FINAL) {
            // P1-8b: эквайер говорит, почему отказал (custAttrs, §5.8.7). Мерчант должен видеть
            // «Invalid PAN», а не голый FAILED.
            ProviderDeclineReason.extract(orderDetails).ifPresent(reason -> {
                log.info("Acquirer decline reason for transaction {}: \"{}\"", transactionId, reason);
                stored.put(DECLINE_REASON_KEY, reason);
            });
        }
        tx.setProviderResponse(stored);
        tx = transactionRepository.save(tx);
        log.info("Transaction {} updated status to: {}", tx.getId(), tx.getStatus());

        return new StatusRefresh(tx, outcome);
    }

    @Transactional(readOnly = true)
    public PagedResponse<TransactionResponse> listTransactions(Pageable pageable, UserPrincipal principal) {
        String userId = UserPrincipal.getUserId(principal);
        Role userRole = UserPrincipal.getRole(principal);
        String rawRole = UserPrincipal.getRawRole(principal);
        String companyId = UserPrincipal.getCompanyId(principal);

        log.info("Request to list transactions: userId={}, role={}, companyId={}", userId, rawRole, companyId);
        // Нераспознанный claim роли приходит сюда как null и отвергается, а не идёт дальше.
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

    // P0-4: здесь стояли MERCHANT_ADMIN / MERCHANT_USER — роли, которых никогда не существовало, и
    // все, кроме SYSTEM_ADMIN, получали 403. Читать транзакции ссылки — то же, что читать саму ссылку.
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByLinkId(UUID linkId, UserPrincipal principal) {
        PaymentLink link = findLinkOrThrow(linkId);
        validateAccess(link.getTerminalId(), principal, READ_ROLES);
        return transactionRepository.findByLinkIdOrderByCreatedAtDesc(linkId).stream()
                .map(this::mapToTransactionResponse)
                .toList();
    }

    // P3-7: чтения транзакции по id не было вовсе. Карточка операции открывалась только тем, что
    // список успел положить в состояние роутера, а фронтенд этот адрес уже звал и молча получал
    // отказ. Без обращения к эквайеру: за свежим исходом ходит /{identifier}/status, открытие
    // карточки внешнего вызова не стоит.
    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(UUID id, UserPrincipal principal) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Transaction not found: {}", id);
                    return new ResourceNotFoundException("Transaction not found: " + id);
                });
        validateAccess(tx.getLink().getTerminalId(), principal, READ_ROLES);
        return mapToTransactionResponse(tx);
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

    // Схлопывает жизненный цикл транзакции в четыре состояния, которые рисует страница плательщика.
    private static String receiptState(TransactionStatus status) {
        return switch (status) {
            case SUCCESS, REFUNDED, PARTIALLY_REFUNDED -> "PAID";
            case AUTHORIZED -> "AUTHORIZED";
            case FAILED -> "FAILED";
            case PENDING -> "PENDING";
        };
    }

    // P1-16: маска карты, RRN и код авторизации читаются на лету из providerResponse
    // (order.srcToken.displayName и запись покупки order.trans[], §5.8.3-5.8.6); раньше читались с
    // верхнего уровня по ключам, которых там нет, и всегда были пусты. Своих колонок нет намеренно:
    // payload терминальной транзакции больше не переписывается, поэтому чтение на лету стабильно.
    private TransactionResponse mapToTransactionResponse(Transaction tx) {
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
                tx.getMerchantRid() != null ? tx.getMerchantRid().toString() : null,
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
                failureReasonOf(resp)
        );
    }

    // P1-8b: причина отказа, сохранённая refreshStatus, или null, если её нет.
    private static String failureReasonOf(Map<String, Object> providerResponse) {
        Object reason = providerResponse != null ? providerResponse.get(DECLINE_REASON_KEY) : null;
        return reason != null ? String.valueOf(reason) : null;
    }

    // Роли, которым можно читать ссылки и транзакции, — все роли системы.
    // public, потому что этими же воротами ходит DashboardService: сводка показывает те же
    // строки, что и список, и своей копии правил доступа заводить не должна (AGENTS §12, п. 8).
    public static final Set<Role> READ_ROLES = EnumSet.allOf(Role.class);

    // Роли, которым можно создавать и менять ссылки и списывать DMS-холд.
    private static final Set<Role> LINK_WRITE_ROLES =
            EnumSet.of(Role.SYSTEM_ADMIN, Role.COMPANY_HEAD, Role.COMPANY_MANAGER, Role.COMPANY_EMPLOYEE);

    // Возврат двигает деньги обратно, поэтому останавливается на уровень выше COMPANY_EMPLOYEE.
    private static final Set<Role> REFUND_ROLES =
            EnumSet.of(Role.SYSTEM_ADMIN, Role.COMPANY_HEAD, Role.COMPANY_MANAGER);

    // Роли, читающие через все компании, а не только свои терминалы.
    public static boolean isGlobalReader(Role role) {
        return role == Role.SYSTEM_ADMIN || role == Role.AUDITOR;
    }

    // Единственные ворота доступа по терминалу. Принимает principal, а не строки роли и компании,
    // чтобы null-principal и нераспознанная роль кончались отказом, а не NullPointerException.
    private void validateAccess(Integer terminalId, UserPrincipal principal, Set<Role> allowedRoles) {
        Role userRole = UserPrincipal.getRole(principal);
        String rawRole = UserPrincipal.getRawRole(principal);
        String companyId = UserPrincipal.getCompanyId(principal);

        log.debug("Validating terminal access: terminalId={}, role={}, companyId={}, allowedRoles={}", terminalId, rawRole, companyId, allowedRoles);
        if (userRole == null || !allowedRoles.contains(userRole)) {
            log.warn("Access denied. Role {} is not in allowed roles: {}", rawRole, allowedRoles);
            // Подшивается под терминал: всё в этом сервисе — ссылки, транзакции, возвраты —
            // достигается через компанию терминала. Действие READ, то же, что пишет directory
            // (P3-2): раньше здесь было приватное имя, и поиск по одному не находил другого.
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
            return;
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
    }

    private PaymentLink findLinkOrThrow(UUID id) {
        return paymentLinkRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Payment link not found: {}", id);
                    return new ResourceNotFoundException("Payment link not found: " + id);
                });
    }
}
