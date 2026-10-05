package az.millikart.ecom.service;

import az.millikart.common.audit.AuditAction;
import az.millikart.common.audit.AuditEntity;
import az.millikart.common.audit.AuditEvent;
import az.millikart.common.audit.AuditLogService;
import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.ConflictException;
import az.millikart.common.exception.InvalidStateException;
import az.millikart.common.exception.PaymentOutcomeUnknownException;
import az.millikart.common.money.MoneyActionReason;
import az.millikart.common.money.MoneyActionRoles;
import az.millikart.common.money.OperationActions;
import az.millikart.common.security.CredentialCipher;
import az.millikart.common.security.Role;
import az.millikart.common.security.UserPrincipal;
import az.millikart.ecom.domain.ProviderOrderAttempt;
import az.millikart.ecom.dto.EcomMoneyOperationResponse;
import az.millikart.ecom.dto.EcomTransactionResponse;
import az.millikart.ecom.repository.PortalPaymentsRepository;
import az.millikart.txpg.AcquiringClient;
import az.millikart.txpg.ProviderCredentials;
import az.millikart.txpg.dto.MoneyOperationResult;
import java.math.BigDecimal;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Возврат и списание заказа выписки (Р-125). Правила — те же, что у кнопок (EcomMoneyActions): выключенная
// кнопка — тот же отказ с той же причиной. Три шага, как у pbl (Р-123), только замок — строка попытки, потому
// что своей строки у заказа нет: вставка строки; вызов провайдера без транзакции; итог — удаление строки.
// Деньги у нас не пишутся: шлюз их записал, и выписка их покажет.
@Service
public class EcomMoneyOperationService {

    private static final Logger log = LoggerFactory.getLogger(EcomMoneyOperationService.class);

    private final EcomTransactionService orders;
    private final PortalPaymentsRepository portal;
    private final ProviderOrderAttemptService attempts;
    private final AcquiringClient acquiringClient;
    private final CredentialCipher cipher;
    private final AuditLogService auditLogService;

    public EcomMoneyOperationService(EcomTransactionService orders, PortalPaymentsRepository portal,
                                     ProviderOrderAttemptService attempts, AcquiringClient acquiringClient,
                                     CredentialCipher cipher, AuditLogService auditLogService) {
        this.orders = orders;
        this.portal = portal;
        this.attempts = attempts;
        this.acquiringClient = acquiringClient;
        this.cipher = cipher;
        this.auditLogService = auditLogService;
    }

    public EcomMoneyOperationResponse refund(String orderId, BigDecimal amount, UserPrincipal principal) {
        return operate(orderId, ProviderOrderAttempt.Kind.REFUND, amount, principal);
    }

    public EcomMoneyOperationResponse capture(String orderId, BigDecimal amount, UserPrincipal principal) {
        return operate(orderId, ProviderOrderAttempt.Kind.CAPTURE, amount, principal);
    }

    private EcomMoneyOperationResponse operate(String orderId, ProviderOrderAttempt.Kind kind, BigDecimal amount,
                                               UserPrincipal principal) {
        log.info("Request to {} order {}: amount={}", verb(kind), orderId, amount);
        // Карточка — скоуп (чужой заказ — 404) и кнопки по тем же правилам, что видит экран.
        EcomTransactionResponse order = orders.order(orderId, principal);
        if (order.portalTransactionId() != null) {
            throw new ConflictException("This order was created by the portal; " + verb(kind)
                    + " it as portal transaction " + order.portalTransactionId());
        }
        OperationActions.Action action = order.actions() == null ? null
                : kind == ProviderOrderAttempt.Kind.REFUND ? order.actions().refund() : order.actions().capture();
        if (action == null) {
            throw new BusinessException("A " + verb(kind) + " is not possible for an order in status " + order.status());
        }
        if (!action.enabled()) {
            refuse(order, kind, action.reason(), principal);
        }
        if (amount.scale() > 2) {
            throw new BusinessException(capitalized(kind) + " amount must not have more than two decimal places");
        }
        if (amount.compareTo(action.maxAmount()) > 0) {
            throw new BusinessException(capitalized(kind) + " amount exceeds " + action.maxAmount() + " available for this order");
        }

        // Кнопка активна — значит, терминал и креды есть (EcomMoneyActions); проверка здесь — от гонки с правкой.
        String companyId = portal.terminalOfMerchant(order.merchantRid())
                .map(PortalPaymentsRepository.PortalTerminal::companyId)
                .orElseThrow(() -> new BusinessException("The terminal of this order is not registered in the portal"));
        ProviderCredentials credentials = portal.credentialsOf(companyId)
                .map(stored -> new ProviderCredentials(stored.login(), cipher.decrypt(stored.encryptedPassword())))
                .orElseThrow(() -> new BusinessException("Company " + companyId
                        + " has no acquirer credentials; a system administrator must set them on the company"));
        String actor = UserPrincipal.getUsername(principal);

        attempts.begin(orderId, kind, amount, actor);
        MoneyOperationResult result;
        try {
            result = kind == ProviderOrderAttempt.Kind.REFUND
                    ? acquiringClient.refund(orderId, credentials, amount)
                    : acquiringClient.completeDms(orderId, credentials, amount);
        } catch (PaymentOutcomeUnknownException e) {
            markUnknown(orderId);
            auditLogService.logUnresolved(AuditEntity.PROVIDER_ORDER, orderId, auditAction(kind), actor, companyId,
                    capitalized(kind) + " of " + amount + " " + order.currency() + " left unconfirmed by the acquirer: "
                            + e.getMessage() + ". Outcome unknown — a system administrator must reconcile it with the "
                            + "provider and resolve it before another money operation.");
            throw e;
        } catch (RuntimeException e) {
            release(orderId);
            throw e;
        }

        try {
            attempts.finish(orderId, AuditEvent.of(AuditEntity.PROVIDER_ORDER, orderId, auditAction(kind), actor, companyId,
                    pastTense(kind) + " " + amount + " " + order.currency() + " (ridByPmo " + result.ridByPmo()
                            + ", tranActionId " + result.tranActionId() + ", approvalCode " + result.approvalCode() + ")"));
        } catch (RuntimeException e) {
            // Деньги ушли и подтверждены; строка останется и снимется сама, когда выписка покажет операцию.
            log.error("The acquirer confirmed the {} of {} on order {}, but releasing the order failed: {}",
                    kind, amount, orderId, e.getMessage());
        }
        log.info("Order {}: {} of {} confirmed by the acquirer (ridByPmo {})", orderId, kind, amount, result.ridByPmo());
        return new EcomMoneyOperationResponse(orderId, kind.name(), amount, result.tranActionId(), result.ridByPmo(),
                result.approvalCode());
    }

    // Итог сверки с провайдером — только SYSTEM_ADMIN (Р-125); у заказа выписки он идёт только в журнал.
    public EcomTransactionResponse resolve(String orderId, boolean executed, UserPrincipal principal) {
        Role role = UserPrincipal.getRole(principal);
        if (role == null || !MoneyActionRoles.RESOLVE.contains(role)) {
            auditLogService.logDenied(AuditEntity.PROVIDER_ORDER, orderId, AuditAction.RESOLVE,
                    UserPrincipal.getUsername(principal), UserPrincipal.getCompanyId(principal),
                    "Denied: role " + UserPrincipal.getRawRole(principal) + " attempted to resolve an unknown outcome");
            throw new InvalidStateException("Access denied: role " + UserPrincipal.getRawRole(principal)
                    + " is not authorized for this action");
        }
        EcomTransactionResponse order = orders.order(orderId, principal);
        ProviderOrderAttempt attempt = attempts.find(orderId)
                .orElseThrow(() -> new ConflictException("This order has no money operation awaiting resolution"));
        if (!attempt.outcomeUnknown(Instant.now())) {
            throw new ConflictException("The money operation on this order is still in progress");
        }
        String companyId = portal.terminalOfMerchant(order.merchantRid())
                .map(PortalPaymentsRepository.PortalTerminal::companyId)
                .orElse(null);
        attempts.resolve(attempt, executed, UserPrincipal.getUsername(principal), companyId);
        log.info("Unknown {} of {} on order {} resolved by {} as {}", attempt.getKind(), attempt.getAmount(), orderId,
                UserPrincipal.getUsername(principal), executed ? "executed" : "not executed");
        return orders.order(orderId, principal);
    }

    // Отказ — той же причиной, что у выключенной кнопки. Нет прав — 403 и запись в журнал, как у pbl.
    private void refuse(EcomTransactionResponse order, ProviderOrderAttempt.Kind kind, String reason,
                        UserPrincipal principal) {
        MoneyActionReason code = MoneyActionReason.valueOf(reason);
        switch (code) {
            case NO_RIGHTS -> {
                auditLogService.logDenied(AuditEntity.PROVIDER_ORDER, order.orderId(), auditAction(kind),
                        UserPrincipal.getUsername(principal), UserPrincipal.getCompanyId(principal),
                        "Denied: role " + UserPrincipal.getRawRole(principal) + " attempted to " + verb(kind)
                                + " order " + order.orderId());
                throw new InvalidStateException("Access denied: role " + UserPrincipal.getRawRole(principal)
                        + " is not authorized for this action");
            }
            case IN_PROGRESS -> throw new ConflictException(ProviderOrderAttemptService.IN_PROGRESS_MESSAGE);
            case OUTCOME_UNKNOWN -> throw new ConflictException("An earlier money operation on this order has an unknown "
                    + "outcome; a system administrator must resolve it before another one");
            case TERMINAL_NOT_IN_PORTAL -> throw new BusinessException("The terminal of this order is not registered in the portal");
            case NO_PROVIDER_CREDENTIALS -> throw new BusinessException("The company of this order's terminal has no "
                    + "acquirer credentials; a system administrator must set them on the company");
            case FULLY_REFUNDED -> throw new BusinessException("The order is fully refunded");
            case CAPTURE_FIRST -> throw new BusinessException("The hold of this order must be captured before a refund");
            case ALREADY_CAPTURED -> throw new BusinessException("The hold of this order is already captured");
        }
    }

    private void markUnknown(String orderId) {
        try {
            attempts.markUnknown(orderId);
        } catch (RuntimeException e) {
            // Строка останется IN_PROGRESS и через STALE_AFTER всё равно прочтётся как неизвестная.
            log.error("Could not mark the money operation on order {} as of unknown outcome: {}", orderId, e.getMessage());
        }
    }

    private void release(String orderId) {
        try {
            attempts.release(orderId);
        } catch (RuntimeException e) {
            log.error("Could not release order {} after a refused money operation; it will read as of unknown outcome "
                    + "after {}: {}", orderId, ProviderOrderAttempt.STALE_AFTER, e.getMessage());
        }
    }

    private static String auditAction(ProviderOrderAttempt.Kind kind) {
        return kind == ProviderOrderAttempt.Kind.REFUND ? AuditAction.REFUND : AuditAction.CAPTURE;
    }

    private static String verb(ProviderOrderAttempt.Kind kind) {
        return kind == ProviderOrderAttempt.Kind.REFUND ? "refund" : "capture";
    }

    private static String capitalized(ProviderOrderAttempt.Kind kind) {
        return kind == ProviderOrderAttempt.Kind.REFUND ? "Refund" : "Capture";
    }

    private static String pastTense(ProviderOrderAttempt.Kind kind) {
        return kind == ProviderOrderAttempt.Kind.REFUND ? "Refunded" : "Captured";
    }
}
