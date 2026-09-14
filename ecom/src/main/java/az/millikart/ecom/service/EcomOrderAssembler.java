package az.millikart.ecom.service;

import az.millikart.ecom.dto.EcomOperationResponse;
import az.millikart.ecom.dto.EcomTransactionResponse;
import az.millikart.ecom.repository.TxpgStatementRow;
import az.millikart.ecom.service.EcomStatusResolver.EcomStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Строки шлюза → заказы с историей (Р-74) и деньги заказа (Р-75). В Java, а не в SQL: правила
// проверяются тестом на выгрузке стенда, а запрос к Oracle провайдера нам проверить нечем.
public final class EcomOrderAssembler {

    private static final Logger log = LoggerFactory.getLogger(EcomOrderAssembler.class);

    private static final String APPROVED = "Approved";

    private static final Comparator<TxpgStatementRow> BY_TIME =
            Comparator.comparing(TxpgStatementRow::tranAt, Comparator.nullsLast(Comparator.naturalOrder()));

    private EcomOrderAssembler() {
    }

    public record OrderMoney(BigDecimal captured, BigDecimal refunded, EcomStatus status) {
    }

    // Порядок заказов — порядок первых строк; строкам одного заказа идти подряд не обязательно.
    public static List<EcomTransactionResponse> assemble(List<TxpgStatementRow> rows) {
        Map<String, List<TxpgStatementRow>> byOrder = new LinkedHashMap<>();
        for (TxpgStatementRow row : rows) {
            byOrder.computeIfAbsent(row.orderId(), id -> new ArrayList<>()).add(row);
        }
        return byOrder.values().stream().map(EcomOrderAssembler::toOrder).toList();
    }

    public static OrderMoney money(List<TxpgStatementRow> orderRows) {
        BigDecimal captured = BigDecimal.ZERO;
        BigDecimal refunded = BigDecimal.ZERO;
        boolean approvedPayment = false;
        boolean anyApproved = false;
        for (TxpgStatementRow row : orderRows) {
            if (!APPROVED.equals(row.resultCode())) {
                continue;
            }
            anyApproved = true;
            BigDecimal cleared = row.clearAmount() != null ? row.clearAmount() : BigDecimal.ZERO;
            switch (EcomOperationKind.of(row)) {
                case AUTHORIZATION -> approvedPayment = true;
                // Реверсал холда (phase Auth) денег не двигает. Реверсал покупки или списания отменяет
                // её до расчётов: это не возврат, списанное уменьшается — у провайдера такой заказ
                // Cancelled (Р-77). Модуль — чтобы итог не зависел от знака.
                case REVERSAL -> {
                    if (!"Auth".equals(row.phase())) {
                        captured = captured.subtract(cleared.abs());
                    }
                }
                // У возврата сумма с минусом (стенд, 175195); модуль — по той же причине.
                case REFUND -> refunded = refunded.add(cleared.abs());
                // Отрицательное списание без voidkind по контракту (§5.8.8) — возврат или реверсал.
                case CAPTURE, PURCHASE -> {
                    approvedPayment = true;
                    if (cleared.signum() >= 0) {
                        captured = captured.add(cleared);
                    } else {
                        refunded = refunded.add(cleared.negate());
                    }
                }
                case UNKNOWN -> log.warn("Order {} has an approved operation the statement cannot classify "
                                + "(type={}, phase={}, voidKind={}); its amount is not counted",
                        row.orderId(), row.tranType(), row.phase(), row.voidKind());
            }
        }

        TxpgStatementRow head = orderRows.get(0);
        if (captured.signum() < 0) {
            log.warn("Order {} has more reversed than captured ({}); reading it as nothing captured, "
                    + "the operation dictionary needs checking", head.orderId(), captured);
            captured = BigDecimal.ZERO;
        }
        EcomStatus status = EcomStatusResolver.resolve(
                captured, refunded, head.orderAmount(), approvedPayment, !anyApproved, head.orderStatus());
        if (status == EcomStatus.REFUNDED && captured.signum() <= 0) {
            log.warn("Order {} carries refunds of {} with nothing captured or authorized; "
                    + "the operation dictionary needs checking", head.orderId(), refunded);
        }
        return new OrderMoney(captured, refunded, status);
    }

    private static EcomTransactionResponse toOrder(List<TxpgStatementRow> rows) {
        List<TxpgStatementRow> history = rows.stream().sorted(BY_TIME).toList();
        TxpgStatementRow head = history.get(0);
        TxpgStatementRow last = history.get(history.size() - 1);
        OrderMoney money = money(history);
        boolean anyApproved = history.stream().anyMatch(row -> APPROVED.equals(row.resultCode()));

        return new EcomTransactionResponse(
                head.orderId(),
                head.merchantRid(),
                head.merchantTitle(),
                head.ridByMerchant(),
                money.status().name(),
                head.orderStatus(),
                head.orderPrevStatus(),
                head.orderAmount(),
                money.captured(),
                money.refunded(),
                head.orderCurrency(),
                head.description(),
                head.orderCreatedAt(),
                history.stream().map(TxpgStatementRow::tranAt).filter(Objects::nonNull)
                        .max(Comparator.naturalOrder()).orElse(null),
                history.stream().map(TxpgStatementRow::cardMask).filter(Objects::nonNull)
                        .findFirst().orElse(null),
                history.stream().map(TxpgStatementRow::rrn).filter(rrn -> rrn != null && !rrn.isBlank())
                        .findFirst().orElse(null),
                anyApproved ? null : last.resultCode(),
                history.stream().map(EcomOrderAssembler::toOperation).toList());
    }

    private static EcomOperationResponse toOperation(TxpgStatementRow row) {
        return new EcomOperationResponse(
                row.tranId(),
                row.tranAt(),
                EcomOperationKind.of(row).name(),
                row.tranType(),
                row.phase(),
                row.voidKind(),
                row.authKind(),
                row.resultCode(),
                row.tranAmount(),
                row.clearAmount(),
                row.tranCurrency(),
                row.rrn());
    }
}
