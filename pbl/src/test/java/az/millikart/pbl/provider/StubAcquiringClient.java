package az.millikart.pbl.provider;

import az.millikart.pbl.domain.PaymentLink;
import az.millikart.pbl.provider.dto.EcomCreateOrderResponse;
import az.millikart.pbl.provider.dto.MoneyOperationResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Тестовый дублёр эквайера: любой вызов успешен, любой заказ читается как полностью оплаченный.
// Только тестовые исходники: до 20.08.2026 это был продовый бин по pbl.provider.stub, и работающий
// сервис можно было уговорить отвечать «оплачено», ни разу не спросив эквайера. Существует ради
// одного: прогон не должен зависеть от того, поднят ли тестовый стенд и в каком он состоянии.
public class StubAcquiringClient implements AcquiringClient {

    private static final Logger log = LoggerFactory.getLogger(StubAcquiringClient.class);

    @Override
    public EcomCreateOrderResponse createEcomOrder(PaymentLink link, String login, String password, UUID merchantRid, String hppRedirectUrl) {
        long orderId = (long) (Math.random() * 1000000000L);
        String hppUrl = "https://gateway.txpg.example.com/pay?rid=" + orderId;
        log.info("[STUB PROVIDER] createEcomOrder for merchantRid: {}, amount: {}, generated orderId: {}", merchantRid, link.getAmount(), orderId);
        return new EcomCreateOrderResponse(
                new EcomCreateOrderResponse.Order(hppUrl, orderId, "Preparing", "password123")
        );
    }

    @Override
    public MoneyOperationResult completeDms(String providerOrderId, String password, String login, String terminalPassword, BigDecimal amount) {
        log.info("[STUB PROVIDER] completeDms for providerOrderId: {}, amount: {}", providerOrderId, amount);
        return confirmedOperation();
    }

    @Override
    public MoneyOperationResult refund(String providerOrderId, String password, String login, String terminalPassword, BigDecimal amount) {
        log.info("[STUB PROVIDER] refund for providerOrderId: {}, amount: {}", providerOrderId, amount);
        return confirmedOperation();
    }

    @Override
    public Map<String, Object> getOrderStatus(String providerOrderId, String password, String login, String terminalPassword) {
        log.info("[STUB PROVIDER] getOrderStatus for providerOrderId: {}", providerOrderId);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "FullyPaid");
        response.put("id", providerOrderId);
        return response;
    }

    // P1-8b: та же форма, которой настоящий шлюз отвечает на exec-tran (контракт §5.5–5.7), и
    // ничего сверх контракта — выдуманного refundId больше нет. Все идентификаторы на месте, чтобы
    // стаб проходил ту самую проверку TxpgAcquiringClient: непустой tran.match.ridByPmo. Значения
    // различаются от вызова к вызову, чтобы два частичных возврата не получили одну ссылку.
    private static MoneyOperationResult confirmedOperation() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String approvalCode = String.format("%06d", random.nextInt(1_000_000));
        String tranActionId = stamp + "-" + String.format("%08d", random.nextInt(100_000_000)) + "-"
                + String.format("%06x", random.nextInt(0x1000000)) + "=";
        String ridByPmo = stamp + String.format("%012d", random.nextLong(1_000_000_000_000L));

        Map<String, Object> match = new HashMap<>();
        match.put("tranActionId", tranActionId);
        match.put("ridByPmo", ridByPmo);
        Map<String, Object> tran = new HashMap<>();
        tran.put("approvalCode", approvalCode);
        tran.put("match", match);
        Map<String, Object> raw = new HashMap<>();
        raw.put("tran", tran);
        return new MoneyOperationResult(approvalCode, tranActionId, ridByPmo, raw);
    }
}
