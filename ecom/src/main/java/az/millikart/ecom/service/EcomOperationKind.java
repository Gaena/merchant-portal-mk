package az.millikart.ecom.service;

import az.millikart.ecom.repository.TxpgStatementRow;
import java.util.List;

// Вид операции шлюза — по trantype, phase и voidkind вместе. На стенде trantype у всех покупок
// Purchase, и авторизацию от списания отличает только phase (выгрузка 11.09.2026, Р-75); на контуре
// из test.env DMS пишется своими типами — Authorization/Auth и Capture/Charge (заказ 1003, Р-86).
// Сверка точная и регистрозависимая, как у ProviderOrderStatus в pbl; пустые trantype и phase
// сравниваются так же и дают UNKNOWN, а не исключение, которое уронило бы всю выписку.
public enum EcomOperationKind {
    // Purchase/Auth или Authorization/Auth: холд, денег не списывает.
    AUTHORIZATION,
    // Пара из CAPTURE_SIGNS: списание холда, их может быть несколько — мультиклиринг.
    CAPTURE,
    // Purchase/Single: оплата одним сообщением.
    PURCHASE,
    // voidkind Full или Partial (контракт §5.6). С phase Auth снимает холд или его часть, с Single
    // или Clearing — отменяет покупку или списание (стенд, 14.09.2026).
    REVERSAL,
    // trantype Refund (контракт §5.7).
    REFUND,
    // Не разобрано: в истории видна, в деньги заказа не входит.
    UNKNOWN;

    // Словарь видов по парам trantype/phase. Из этих же списков SQL строит признак списания для
    // заказов Authorized (Р-76) и фильтр по типу оплаты (EcomPaymentType, Р-87): второго словаря в SQL
    // не заводить — новая пара добавляется только сюда.
    public static final List<TypePhase> AUTHORIZATION_SIGNS = List.of(
            new TypePhase("Purchase", "Auth"),
            new TypePhase("Authorization", "Auth"));

    public static final List<TypePhase> PURCHASE_SIGNS = List.of(
            new TypePhase("Purchase", "Single"));

    public static final List<TypePhase> CAPTURE_SIGNS = List.of(
            new TypePhase("Purchase", "Clearing"),
            // Контур из test.env, устроен как прод: заказ 1003, 16.09.2026 (Р-86). clearamt положительный.
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
