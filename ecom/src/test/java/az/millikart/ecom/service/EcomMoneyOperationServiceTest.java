package az.millikart.ecom.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import az.millikart.common.audit.AuditEvent;
import az.millikart.common.audit.AuditLogService;
import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.ConflictException;
import az.millikart.common.exception.InvalidStateException;
import az.millikart.common.exception.PaymentOutcomeUnknownException;
import az.millikart.common.money.MoneyActionReason;
import az.millikart.common.money.OperationActions;
import az.millikart.common.money.OperationActions.Action;
import az.millikart.common.security.CredentialCipher;
import az.millikart.common.security.UserPrincipal;
import az.millikart.ecom.domain.ProviderOrderAttempt;
import az.millikart.ecom.dto.EcomMoneyOperationResponse;
import az.millikart.ecom.dto.EcomTransactionResponse;
import az.millikart.ecom.repository.PortalPaymentsRepository;
import az.millikart.ecom.repository.PortalPaymentsRepository.PortalTerminal;
import az.millikart.ecom.repository.PortalPaymentsRepository.StoredCredentials;
import az.millikart.txpg.AcquiringClient;
import az.millikart.txpg.ProviderCredentials;
import az.millikart.txpg.dto.MoneyOperationResult;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// Возврат и списание заказа выписки (Р-125). Главное: выключенная кнопка — тот же отказ, и к провайдеру он не
// идёт; без ответа провайдера заказ остаётся запертым, а отказ провайдера его освобождает.
class EcomMoneyOperationServiceTest {

    private static final String ORDER = "175900";
    private static final String TEST_KEY = "dGVzdC1vbmx5LWNyZWRlbnRpYWxzLWtleS0wMTIzNDU=";

    private final CredentialCipher cipher = new CredentialCipher(TEST_KEY);
    private final UserPrincipal head = new UserPrincipal("1", "head@comp1.com", "COMPANY_HEAD", "comp-01");
    private final UserPrincipal admin = new UserPrincipal("9", "admin@test.com", "SYSTEM_ADMIN", null);

    private EcomTransactionService orders;
    private PortalPaymentsRepository portal;
    private ProviderOrderAttemptService attempts;
    private AcquiringClient acquiringClient;
    private AuditLogService auditLogService;
    private EcomMoneyOperationService service;

    @BeforeEach
    void setUp() {
        orders = mock(EcomTransactionService.class);
        portal = mock(PortalPaymentsRepository.class);
        attempts = mock(ProviderOrderAttemptService.class);
        acquiringClient = mock(AcquiringClient.class);
        auditLogService = mock(AuditLogService.class);
        service = new EcomMoneyOperationService(orders, portal, attempts, acquiringClient, cipher, auditLogService);
        when(portal.terminalOfMerchant("M-1")).thenReturn(Optional.of(new PortalTerminal(7, "comp-01")));
        when(portal.credentialsOf("comp-01"))
                .thenReturn(Optional.of(new StoredCredentials("MultiMerchantSys/shop", cipher.encrypt("company-password"))));
    }

    @Test
    void aRefund_goesToTheProviderWithTheCompanyCredentials_andReleasesTheOrder() {
        when(orders.order(ORDER, head)).thenReturn(order(refundable("40"), null));
        when(acquiringClient.refund(anyString(), any(), any())).thenReturn(confirmed());

        EcomMoneyOperationResponse response = service.refund(ORDER, new BigDecimal("25.00"), null, head);

        verify(attempts).begin(ORDER, ProviderOrderAttempt.Kind.REFUND, new BigDecimal("25.00"), "head@comp1.com");
        verify(acquiringClient).refund(ORDER, new ProviderCredentials("MultiMerchantSys/shop", "company-password"),
                new BigDecimal("25.00"));
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(attempts).finish(eq(ORDER), event.capture());
        assertEquals("REFUND", event.getValue().action());
        assertEquals("comp-01", event.getValue().companyId());
        assertEquals("RID-1", response.acquirerReference());
    }

    // Р-126: причина возврата — в записи журнала, без пробелов по краям; эквайеру она не уходит.
    @Test
    void theReasonOfARefund_goesToTheJournal() {
        when(orders.order(ORDER, head)).thenReturn(order(refundable("40"), null));
        when(acquiringClient.refund(anyString(), any(), any())).thenReturn(confirmed());

        service.refund(ORDER, new BigDecimal("25.00"), "  Goods returned  ", head);

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(attempts).finish(eq(ORDER), event.capture());
        assertTrue(event.getValue().details().endsWith("; reason: Goods returned"), event.getValue().details());
    }

    // Требование заказчика: выключенная кнопка — не только на экране. Тот же отказ с той же причиной, и к провайдеру
    // запрос не идёт; нет прав — 403 и запись в журнал.
    @Test
    void aDisabledButton_isTheSameRefusal_andNeverReachesTheProvider() {
        when(orders.order(ORDER, head)).thenReturn(order(disabled(MoneyActionReason.TERMINAL_NOT_IN_PORTAL), null));
        BusinessException notInPortal = assertThrows(BusinessException.class,
                () -> service.refund(ORDER, new BigDecimal("10.00"), null, head));
        assertTrue(notInPortal.getMessage().contains("not registered in the portal"));

        when(orders.order(ORDER, head)).thenReturn(order(disabled(MoneyActionReason.OUTCOME_UNKNOWN), null));
        assertThrows(ConflictException.class, () -> service.refund(ORDER, new BigDecimal("10.00"), null, head));

        when(orders.order(ORDER, head)).thenReturn(order(disabled(MoneyActionReason.NO_RIGHTS), null));
        assertThrows(InvalidStateException.class, () -> service.refund(ORDER, new BigDecimal("10.00"), null, head));
        verify(auditLogService).logDenied(eq("PROVIDER_ORDER"), eq(ORDER), eq("REFUND"), anyString(), any(), anyString());

        verifyNoInteractions(acquiringClient);
        verify(attempts, never()).begin(any(), any(), any(), any());
    }

    @Test
    void anAmountAboveWhatIsLeft_orWithThreeDecimals_isRefused() {
        when(orders.order(ORDER, head)).thenReturn(order(refundable("40"), null));

        assertThrows(BusinessException.class, () -> service.refund(ORDER, new BigDecimal("40.01"), null, head));
        assertThrows(BusinessException.class, () -> service.refund(ORDER, new BigDecimal("10.005"), null, head));
        verifyNoInteractions(acquiringClient);
    }

    // Заказ завёл портал — его деньги учитывает pbl: здесь его не проводят.
    @Test
    void aPortalOrder_isSentToItsPortalTransaction() {
        when(orders.order(ORDER, head)).thenReturn(order(null, "0c7d3c1e-9a7a-4f9e-9a0e-6b5f2b1d1a11"));

        ConflictException refused = assertThrows(ConflictException.class,
                () -> service.refund(ORDER, new BigDecimal("10.00"), null, head));
        assertTrue(refused.getMessage().contains("0c7d3c1e-9a7a-4f9e-9a0e-6b5f2b1d1a11"));
        verifyNoInteractions(acquiringClient);
    }

    // Провайдер не ответил: деньги могли уйти, поэтому заказ остаётся запертым, а в журнале — UNRESOLVED.
    @Test
    void anUnknownOutcome_keepsTheOrderLocked() {
        when(orders.order(ORDER, head)).thenReturn(order(refundable("40"), null));
        when(acquiringClient.refund(anyString(), any(), any())).thenThrow(new PaymentOutcomeUnknownException("Read timed out"));

        assertThrows(PaymentOutcomeUnknownException.class, () -> service.refund(ORDER, new BigDecimal("10.00"), null, head));

        verify(attempts).markUnknown(ORDER);
        verify(attempts, never()).release(any());
        verify(auditLogService).logUnresolved(eq("PROVIDER_ORDER"), eq(ORDER), eq("REFUND"), anyString(), eq("comp-01"), anyString());
    }

    // Отказ провайдера — деньги не двигались: заказ свободен для следующей попытки, а попытка остаётся в журнале
    // записью DECLINED с текстом отказа (Р-134).
    @Test
    void aDecline_releasesTheOrder() {
        when(orders.order(ORDER, head)).thenReturn(order(refundable("40"), null));
        when(acquiringClient.refund(anyString(), any(), any())).thenThrow(new BusinessException("Acquirer error: declined"));

        assertThrows(BusinessException.class, () -> service.refund(ORDER, new BigDecimal("10.00"), null, head));

        verify(attempts).release(ORDER);
        verify(attempts, never()).markUnknown(any());
        verify(auditLogService).logDeclined(eq("PROVIDER_ORDER"), eq(ORDER), eq("REFUND"), anyString(), eq("comp-01"),
                contains("declined by the acquirer: Acquirer error: declined"));
    }

    @Test
    void onlyTheSystemAdminResolves_andOnlyAnUnknownOutcome() {
        assertThrows(InvalidStateException.class, () -> service.resolve(ORDER, true, head));
        verify(auditLogService).logDenied(eq("PROVIDER_ORDER"), eq(ORDER), eq("RESOLVE"), anyString(), any(), anyString());

        when(orders.order(ORDER, admin)).thenReturn(order(refundable("40"), null));
        when(attempts.find(ORDER)).thenReturn(Optional.of(attempt(Duration.ofMinutes(1), ProviderOrderAttempt.State.IN_PROGRESS)));
        assertThrows(ConflictException.class, () -> service.resolve(ORDER, true, admin));

        ProviderOrderAttempt unknown = attempt(Duration.ofMinutes(1), ProviderOrderAttempt.State.UNKNOWN);
        when(attempts.find(ORDER)).thenReturn(Optional.of(unknown));
        service.resolve(ORDER, false, admin);
        verify(attempts).resolve(unknown, false, "admin@test.com", "comp-01");
    }

    private static OperationActions refundable(String max) {
        return new OperationActions(Action.enabled(new BigDecimal(max)), null, null);
    }

    private static OperationActions disabled(MoneyActionReason reason) {
        return new OperationActions(Action.disabled(reason), null, null);
    }

    private static EcomTransactionResponse order(OperationActions actions, String portalTransactionId) {
        return new EcomTransactionResponse(ORDER, "M-1", "Shop", "R-1", "SUCCESS", "FullyPaid", null,
                new BigDecimal("40"), new BigDecimal("40"), BigDecimal.ZERO, "AZN", "test", Instant.now(), Instant.now(),
                null, null, null, List.of(), actions, portalTransactionId);
    }

    private static ProviderOrderAttempt attempt(Duration age, ProviderOrderAttempt.State state) {
        return ProviderOrderAttempt.builder().orderId(ORDER).kind(ProviderOrderAttempt.Kind.REFUND)
                .amount(new BigDecimal("10.00")).state(state).startedBy("head@comp1.com")
                .startedAt(Instant.now().minus(age)).build();
    }

    private static MoneyOperationResult confirmed() {
        return new MoneyOperationResult("AC-1", "TA-1", "RID-1",
                Map.of("tran", Map.of("approvalCode", "AC-1", "match", Map.of("tranActionId", "TA-1", "ridByPmo", "RID-1"))));
    }
}
