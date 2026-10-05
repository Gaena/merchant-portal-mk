package az.millikart.ecom.service;

import az.millikart.common.audit.AuditAction;
import az.millikart.common.audit.AuditEntity;
import az.millikart.common.audit.AuditEvent;
import az.millikart.common.exception.ConflictException;
import az.millikart.ecom.domain.ProviderOrderAttempt;
import az.millikart.ecom.dto.EcomOperationResponse;
import az.millikart.ecom.dto.EcomTransactionResponse;
import az.millikart.ecom.repository.ProviderOrderAttemptRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Строка попытки заказа выписки (Р-124, Р-125): замок на время вызова провайдера и след неизвестного исхода.
// Каждая запись — своя короткая транзакция: вызов провайдера идёт вне транзакции, соединение пула его не ждёт.
@Service
public class ProviderOrderAttemptService {

    private static final Logger log = LoggerFactory.getLogger(ProviderOrderAttemptService.class);

    static final String IN_PROGRESS_MESSAGE = "Another money operation on this order is in progress";

    // Как в EcomOrderAssembler: одобренная операция шлюза.
    private static final String APPROVED = "Approved";

    // Часы шлюза и наши расходятся: операция, записанная шлюзом чуть «раньше» отправки, — всё равно эта.
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(1);

    private final ProviderOrderAttemptRepository repository;
    private final ApplicationEventPublisher events;

    public ProviderOrderAttemptService(ProviderOrderAttemptRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    // Неизвестный исход снимается сам, когда выписка показывает одобренную операцию того же вида и суммы после
    // отправки (решение 6 к MONEY-ACTIONS-ALL): деньги сдвинулись, и шлюз это записал. Иначе строка остаётся.
    @Transactional
    public Optional<ProviderOrderAttempt> open(EcomTransactionResponse order) {
        Optional<ProviderOrderAttempt> attempt = repository.findById(order.orderId());
        if (attempt.isEmpty() || !attempt.get().outcomeUnknown(Instant.now())
                || !seenInStatement(attempt.get(), order.operations())) {
            return attempt;
        }
        ProviderOrderAttempt seen = attempt.get();
        repository.delete(seen);
        log.info("Unknown {} of {} on order {} found approved in the statement; the order is released",
                seen.getKind(), seen.getAmount(), order.orderId());
        events.publishEvent(AuditEvent.of(AuditEntity.PROVIDER_ORDER, order.orderId(), AuditAction.RESOLVE, "system",
                null, "Unknown " + seen.getKind().name().toLowerCase() + " of " + seen.getAmount()
                        + " started by " + seen.getStartedBy() + " at " + seen.getStartedAt()
                        + " resolved as executed: the statement shows it approved"));
        return Optional.empty();
    }

    static boolean seenInStatement(ProviderOrderAttempt attempt, List<EcomOperationResponse> operations) {
        String kind = attempt.getKind() == ProviderOrderAttempt.Kind.REFUND
                ? EcomOperationKind.REFUND.name()
                : EcomOperationKind.CAPTURE.name();
        Instant since = attempt.getStartedAt().minus(CLOCK_SKEW);
        return operations.stream().anyMatch(operation -> kind.equals(operation.kind())
                && APPROVED.equals(operation.resultCode())
                && operation.at() != null && !operation.at().isBefore(since)
                && operation.amount() != null && operation.amount().compareTo(attempt.getAmount()) == 0);
    }

    public Optional<ProviderOrderAttempt> find(String orderId) {
        return repository.findById(orderId);
    }

    // Строка — вставкой: вторая одновременная попытка падает на ключе и получает 409, а к провайдеру не идёт.
    @Transactional
    public void begin(String orderId, ProviderOrderAttempt.Kind kind, BigDecimal amount, String actor) {
        try {
            repository.saveAndFlush(ProviderOrderAttempt.builder()
                    .orderId(orderId)
                    .kind(kind)
                    .amount(amount)
                    .state(ProviderOrderAttempt.State.IN_PROGRESS)
                    .startedBy(actor)
                    .startedAt(Instant.now())
                    .build());
        } catch (DataIntegrityViolationException e) {
            log.warn("Refusing a {} on order {}: another money operation took it first", kind, orderId);
            throw new ConflictException(IN_PROGRESS_MESSAGE);
        }
    }

    @Transactional
    public void markUnknown(String orderId) {
        repository.findById(orderId).ifPresent(attempt -> attempt.setState(ProviderOrderAttempt.State.UNKNOWN));
    }

    // Отказ провайдера или разомкнутый breaker: деньги не двигались, заказ свободен.
    @Transactional
    public void release(String orderId) {
        repository.deleteById(orderId);
    }

    // Подтверждённая операция: строка снимается, запись журнала ляжет после коммита.
    @Transactional
    public void finish(String orderId, AuditEvent confirmed) {
        repository.deleteById(orderId);
        events.publishEvent(confirmed);
    }

    // Итог сверки администратора с провайдером (Р-125). Денег у нас не пишется: они видны в выписке.
    @Transactional
    public void resolve(ProviderOrderAttempt attempt, boolean executed, String admin, String companyId) {
        repository.delete(attempt);
        events.publishEvent(AuditEvent.of(AuditEntity.PROVIDER_ORDER, attempt.getOrderId(), AuditAction.RESOLVE, admin,
                companyId, "Unknown " + attempt.getKind().name().toLowerCase() + " of " + attempt.getAmount()
                        + " started by " + attempt.getStartedBy() + " at " + attempt.getStartedAt()
                        + " resolved as " + (executed ? "executed" : "not executed")));
    }
}
