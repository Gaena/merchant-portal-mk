package az.millikart.ecom.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import az.millikart.common.money.OperationActions;
import az.millikart.common.security.Role;
import az.millikart.ecom.domain.ProviderOrderAttempt;
import az.millikart.ecom.service.EcomStatusResolver.EcomStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

// Правила кнопок заказа выписки (Р-124) — те же, что у операции портала, на восьми статусах выписки. Ошибка
// здесь — кнопка, которая обещает возврат, а сервис его отвергнет, или потолок, выше списанного.
class EcomMoneyActionsTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");

    @Test
    void aPaidOrder_isRefundableUpToWhatIsLeft() {
        OperationActions actions = decide(new Fixture(EcomStatus.PARTIALLY_REFUNDED, false).money("100", "100", "30"));

        assertTrue(actions.refund().enabled());
        assertEquals(0, new BigDecimal("70").compareTo(actions.refund().maxAmount()));
        assertNull(actions.capture());
    }

    // Частичная оплата — тоже деньги на счету: вернуть можно списанное, не сумму заказа.
    @Test
    void aPartlyPaidOrder_isRefundableUpToTheCapturedAmount() {
        OperationActions actions = decide(new Fixture(EcomStatus.PARTIALLY_PAID, true).money("100", "60", "0"));

        assertEquals(0, new BigDecimal("60").compareTo(actions.refund().maxAmount()));
        assertEquals("ALREADY_CAPTURED", actions.capture().reason());
    }

    @Test
    void aHold_isCapturableForItsAmount_andItsRefundWaitsForTheCapture() {
        OperationActions actions = decide(new Fixture(EcomStatus.AUTHORIZED, true).money("100", "0", "0"));

        assertTrue(actions.capture().enabled());
        assertEquals(0, new BigDecimal("100").compareTo(actions.capture().maxAmount()));
        assertEquals("CAPTURE_FIRST", actions.refund().reason());
    }

    @Test
    void unpaidFailedOrCanceledOrders_haveNoButtons() {
        for (EcomStatus status : new EcomStatus[] {EcomStatus.PENDING, EcomStatus.FAILED, EcomStatus.CANCELED}) {
            OperationActions actions = decide(new Fixture(status, true));
            assertNull(actions.refund(), status.name());
            assertNull(actions.capture(), status.name());
        }
    }

    @Test
    void aFullyRefundedOrder_saysSo() {
        assertEquals("FULLY_REFUNDED",
                decide(new Fixture(EcomStatus.REFUNDED, false).money("100", "100", "100")).refund().reason());
    }

    @Test
    void rolesWithoutTheRight_seeTheButtonsDisabled() {
        assertEquals("NO_RIGHTS", decide(new Fixture(EcomStatus.SUCCESS, false).role(Role.COMPANY_EMPLOYEE)).refund().reason());
        assertEquals("NO_RIGHTS", decide(new Fixture(EcomStatus.AUTHORIZED, true).role(Role.AUDITOR)).capture().reason());
        assertTrue(decide(new Fixture(EcomStatus.AUTHORIZED, true).role(Role.COMPANY_EMPLOYEE)).capture().enabled());
    }

    // Требование заказчика: заказ мерчанта, которого нет среди терминалов портала, провести нельзя — и это видно.
    @Test
    void anOrderTheProviderCannotBeAskedAbout_isExplained() {
        assertEquals("TERMINAL_NOT_IN_PORTAL", decide(new Fixture(EcomStatus.SUCCESS, false).terminal(false)).refund().reason());
        assertEquals("NO_PROVIDER_CREDENTIALS",
                decide(new Fixture(EcomStatus.AUTHORIZED, true).credentials(false)).capture().reason());
    }

    @Test
    void anOpenAttempt_disablesBothButtons_andOnlyAStaleOneIsResolvable() {
        OperationActions fresh = decide(new Fixture(EcomStatus.SUCCESS, true).role(Role.SYSTEM_ADMIN)
                .attempt(Duration.ofMinutes(4)));
        assertEquals("IN_PROGRESS", fresh.refund().reason());
        assertEquals("IN_PROGRESS", fresh.capture().reason());
        assertFalse(fresh.unresolved().resolvable());

        OperationActions stale = decide(new Fixture(EcomStatus.SUCCESS, true).role(Role.SYSTEM_ADMIN)
                .attempt(Duration.ofMinutes(6)));
        assertEquals("OUTCOME_UNKNOWN", stale.refund().reason());
        assertEquals("UNKNOWN", stale.unresolved().state());
        assertTrue(stale.unresolved().resolvable());
    }

    private static OperationActions decide(Fixture fixture) {
        return EcomMoneyActions.decide(fixture.build());
    }

    private static final class Fixture {
        private final EcomStatus status;
        private final boolean dms;
        private BigDecimal amount = new BigDecimal("100");
        private BigDecimal captured = new BigDecimal("100");
        private BigDecimal refunded = BigDecimal.ZERO;
        private Role role = Role.COMPANY_HEAD;
        private boolean terminal = true;
        private boolean credentials = true;
        private ProviderOrderAttempt attempt;

        private Fixture(EcomStatus status, boolean dms) {
            this.status = status;
            this.dms = dms;
        }

        Fixture money(String amount, String captured, String refunded) {
            this.amount = new BigDecimal(amount);
            this.captured = new BigDecimal(captured);
            this.refunded = new BigDecimal(refunded);
            return this;
        }

        Fixture role(Role value) {
            role = value;
            return this;
        }

        Fixture terminal(boolean value) {
            terminal = value;
            return this;
        }

        Fixture credentials(boolean value) {
            credentials = value;
            return this;
        }

        Fixture attempt(Duration age) {
            attempt = ProviderOrderAttempt.builder()
                    .orderId("175900")
                    .kind(ProviderOrderAttempt.Kind.REFUND)
                    .amount(new BigDecimal("10"))
                    .state(ProviderOrderAttempt.State.IN_PROGRESS)
                    .startedBy("head@test.com")
                    .startedAt(NOW.minus(age))
                    .build();
            return this;
        }

        EcomMoneyActions.Facts build() {
            return new EcomMoneyActions.Facts(status, dms, amount, captured, refunded, role, terminal, credentials,
                    attempt, NOW);
        }
    }
}
