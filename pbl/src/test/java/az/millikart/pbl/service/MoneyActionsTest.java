package az.millikart.pbl.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import az.millikart.common.security.Role;
import az.millikart.pbl.domain.MoneyOperationAttempt;
import az.millikart.pbl.domain.TransactionStatus;
import az.millikart.common.money.OperationActions;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

// Правила кнопок карточки (Р-123): видна по смыслу, активна по правилам сервиса, иначе — причина. Экран их не
// повторяет, поэтому ошибка здесь — это кнопка, которая обещает действие, а сервис его отвергнет, или наоборот.
class MoneyActionsTest {

    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");
    private static final BigDecimal HUNDRED = new BigDecimal("100.00");

    @Test
    void aPaidOperation_isRefundableUpToWhatIsLeft() {
        OperationActions actions = decide(facts(TransactionStatus.PARTIALLY_REFUNDED, false).refunded("30.00"));

        assertTrue(actions.refund().enabled());
        assertEquals(0, new BigDecimal("70.00").compareTo(actions.refund().maxAmount()));
        assertNull(actions.capture(), "an SMS payment has no capture at all");
        assertNull(actions.unresolved());
    }

    // P0-8: у DMS потолок — списанное, не авторизованное.
    @Test
    void aPartlyCapturedHold_isRefundableUpToTheCapturedAmount() {
        OperationActions actions = decide(facts(TransactionStatus.SUCCESS, true)
                .amounts("1500.00", "500.00", "0.00"));

        assertEquals(0, new BigDecimal("500.00").compareTo(actions.refund().maxAmount()));
        assertEquals("ALREADY_CAPTURED", actions.capture().reason());
    }

    @Test
    void aHold_isCapturable_andItsRefundWaitsForTheCapture() {
        OperationActions actions = decide(facts(TransactionStatus.AUTHORIZED, true));

        assertTrue(actions.capture().enabled());
        assertEquals(0, HUNDRED.compareTo(actions.capture().maxAmount()));
        assertEquals("CAPTURE_FIRST", actions.refund().reason());
    }

    // Копия PENDING могла отстать от холда: списание сначала спросит эквайера (P0-2). Возврата у неё нет.
    @Test
    void aPendingDmsPayment_offersOnlyTheCapture() {
        OperationActions actions = decide(facts(TransactionStatus.PENDING, true));

        assertTrue(actions.capture().enabled());
        assertNull(actions.refund());
    }

    @Test
    void aFailedOrPendingSmsPayment_hasNoButtonsAtAll() {
        for (TransactionStatus status : new TransactionStatus[] {TransactionStatus.FAILED, TransactionStatus.PENDING}) {
            OperationActions actions = decide(facts(status, false));
            assertNull(actions.refund(), status.name());
            assertNull(actions.capture(), status.name());
        }
        assertNull(decide(facts(TransactionStatus.FAILED, true)).capture());
    }

    @Test
    void aFullyRefundedOperation_saysSo() {
        assertEquals("FULLY_REFUNDED", decide(facts(TransactionStatus.REFUNDED, false).refunded("100.00")).refund().reason());
    }

    // Р-123, ответ 4: роли без права кнопку видят, но выключенной — и это первая причина, раньше всех прочих.
    @Test
    void rolesWithoutTheRight_seeTheButtonsDisabled() {
        OperationActions employee = decide(facts(TransactionStatus.SUCCESS, false).role(Role.COMPANY_EMPLOYEE));
        assertEquals("NO_RIGHTS", employee.refund().reason());

        OperationActions auditor = decide(facts(TransactionStatus.AUTHORIZED, true).role(Role.AUDITOR)
                .attempt(MoneyOperationAttempt.State.UNKNOWN, Duration.ofMinutes(1)));
        assertEquals("NO_RIGHTS", auditor.capture().reason());
        assertEquals("NO_RIGHTS", auditor.refund().reason());
        assertFalse(auditor.unresolved().resolvable());

        assertEquals("NO_RIGHTS", decide(facts(TransactionStatus.SUCCESS, false).role(null)).refund().reason());
        assertTrue(decide(facts(TransactionStatus.AUTHORIZED, true).role(Role.COMPANY_EMPLOYEE)).capture().enabled(),
                "an employee captures holds");
    }

    @Test
    void anOperationTheProviderCannotBeAskedAbout_isExplained() {
        assertEquals("TERMINAL_NOT_IN_PORTAL", decide(facts(TransactionStatus.SUCCESS, false).terminal(false)).refund().reason());
        assertEquals("NO_PROVIDER_CREDENTIALS", decide(facts(TransactionStatus.AUTHORIZED, true).credentials(false)).capture().reason());
    }

    // Строка попытки старше пяти минут — неизвестный исход; разрешает его только SYSTEM_ADMIN.
    @Test
    void anOpenAttempt_disablesBothButtons_andOnlyAStaleOneIsResolvable() {
        OperationActions fresh = decide(facts(TransactionStatus.SUCCESS, true).role(Role.SYSTEM_ADMIN)
                .attempt(MoneyOperationAttempt.State.IN_PROGRESS, Duration.ofMinutes(4)));
        assertEquals("IN_PROGRESS", fresh.refund().reason());
        assertEquals("IN_PROGRESS", fresh.capture().reason());
        assertEquals("IN_PROGRESS", fresh.unresolved().state());
        assertFalse(fresh.unresolved().resolvable());

        OperationActions stale = decide(facts(TransactionStatus.SUCCESS, true).role(Role.SYSTEM_ADMIN)
                .attempt(MoneyOperationAttempt.State.IN_PROGRESS, Duration.ofMinutes(6)));
        assertEquals("OUTCOME_UNKNOWN", stale.refund().reason());
        assertEquals("UNKNOWN", stale.unresolved().state());
        assertTrue(stale.unresolved().resolvable());

        OperationActions head = decide(facts(TransactionStatus.SUCCESS, true)
                .attempt(MoneyOperationAttempt.State.UNKNOWN, Duration.ofMinutes(1)));
        assertEquals("OUTCOME_UNKNOWN", head.refund().reason());
        assertFalse(head.unresolved().resolvable());
    }

    private static OperationActions decide(Fixture fixture) {
        return MoneyActions.decide(fixture.build());
    }

    private static Fixture facts(TransactionStatus status, boolean dms) {
        return new Fixture(status, dms);
    }

    private static final class Fixture {
        private final TransactionStatus status;
        private final boolean dms;
        private BigDecimal amount = HUNDRED;
        private BigDecimal captured;
        private BigDecimal refunded = BigDecimal.ZERO;
        private Role role = Role.COMPANY_HEAD;
        private boolean terminal = true;
        private boolean credentials = true;
        private MoneyOperationAttempt attempt;

        private Fixture(TransactionStatus status, boolean dms) {
            this.status = status;
            this.dms = dms;
        }

        Fixture refunded(String value) {
            refunded = new BigDecimal(value);
            return this;
        }

        Fixture amounts(String amount, String captured, String refunded) {
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

        Fixture attempt(MoneyOperationAttempt.State state, Duration age) {
            attempt = MoneyOperationAttempt.builder()
                    .kind(MoneyOperationAttempt.Kind.REFUND)
                    .amount(new BigDecimal("10.00"))
                    .state(state)
                    .startedBy("head@test.com")
                    .startedAt(NOW.minus(age))
                    .build();
            return this;
        }

        MoneyActions.Facts build() {
            return new MoneyActions.Facts(status, dms, amount, captured, refunded, role, terminal, credentials, attempt, NOW);
        }
    }
}
