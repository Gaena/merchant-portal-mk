package az.millikart.ecom.service;

import az.millikart.ecom.repository.TxpgStatementRow;

// Вид операции шлюза — по phase, voidkind и trantype вместе: на стенде trantype у всех строк
// Purchase, и авторизацию от списания отличает только phase (выгрузка 11.09.2026, Р-75).
// Сверка точная и регистрозависимая, как у ProviderOrderStatus в pbl.
public enum EcomOperationKind {
    // phase Auth: холд, денег не списывает.
    AUTHORIZATION,
    // phase Clearing: списание холда, их может быть несколько — мультиклиринг. Этот же признак
    // стоит в SQL (TxpgTransactionRepository.finishedOrdersOnly): поменяешь здесь — поменяй и там.
    CAPTURE,
    // phase Single: оплата одним сообщением.
    PURCHASE,
    // voidkind Full или Partial (контракт §5.6). С phase Auth снимает холд или его часть, с Single
    // или Clearing — отменяет покупку или списание (стенд, 14.09.2026).
    REVERSAL,
    // trantype Refund (контракт §5.7).
    REFUND,
    // Не разобрано: в истории видна, в деньги заказа не входит.
    UNKNOWN;

    public static EcomOperationKind of(TxpgStatementRow row) {
        if (row.voidKind() != null && !row.voidKind().isBlank()) {
            return REVERSAL;
        }
        if ("Refund".equals(row.tranType())) {
            return REFUND;
        }
        if (!"Purchase".equals(row.tranType()) || row.phase() == null) {
            return UNKNOWN;
        }
        return switch (row.phase()) {
            case "Auth" -> AUTHORIZATION;
            case "Clearing" -> CAPTURE;
            case "Single" -> PURCHASE;
            default -> UNKNOWN;
        };
    }
}
