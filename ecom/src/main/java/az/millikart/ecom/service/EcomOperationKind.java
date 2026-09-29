package az.millikart.ecom.service;

import az.millikart.ecom.repository.TxpgStatementRow;
import java.util.List;

// Вид операции — по trantype, phase и voidkind вместе: у всех покупок trantype = Purchase, списание
// отличает phase (Р-75); DMS пишется и парами Authorization/Auth, Capture/Charge (Р-86). Сверка точная;
// незнакомое и пустое — UNKNOWN, а не исключение, которое уронило бы всю выписку.
public enum EcomOperationKind {
    // Холд, денег не списывает.
    AUTHORIZATION,
    // Списание холда; их может быть несколько — мультиклиринг.
    CAPTURE,
    // Оплата одним сообщением (SMS).
    PURCHASE,
    // voidkind Full или Partial (контракт §5.6): с phase Auth снимает холд или его часть, с Single или
    // Clearing — отменяет покупку или списание.
    REVERSAL,
    REFUND,
    // Не разобрано: в истории видна, в деньги заказа не входит.
    UNKNOWN;

    // Из этих же списков SQL строит признак списания (Р-76) и фильтр типа оплаты (Р-87): второго
    // словаря в SQL не заводить, новая пара добавляется только сюда.
    public static final List<TypePhase> AUTHORIZATION_SIGNS = List.of(
            new TypePhase("Purchase", "Auth"),
            new TypePhase("Authorization", "Auth"));

    public static final List<TypePhase> PURCHASE_SIGNS = List.of(
            new TypePhase("Purchase", "Single"));

    public static final List<TypePhase> CAPTURE_SIGNS = List.of(
            new TypePhase("Purchase", "Clearing"),
            // Словарь контура, устроенного как прод (Р-86); clearamt положительный.
            new TypePhase("Capture", "Charge"));

    // record: equals сравнивает и пустые поля, поэтому contains не падает на null.
    public record TypePhase(String type, String phase) {
    }

    public static EcomOperationKind of(TxpgStatementRow row) {
        if (row.voidKind() != null && !row.voidKind().isBlank()) {
            return REVERSAL;
        }
        String type = row.tranType();
        String phase = row.phase();
        if ("Refund".equals(type)) {
            return REFUND;
        }
        TypePhase pair = new TypePhase(type, phase);
        if (CAPTURE_SIGNS.contains(pair)) {
            return CAPTURE;
        }
        if (AUTHORIZATION_SIGNS.contains(pair)) {
            return AUTHORIZATION;
        }
        return PURCHASE_SIGNS.contains(pair) ? PURCHASE : UNKNOWN;
    }
}
