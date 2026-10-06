package az.millikart.pbl.dto;

import java.math.BigDecimal;

// Модель публичной страницы возврата (redirect.html) — чек плательщика по закону о платёжных услугах
// (ст. 17.1) и правилам ЦБ АР № 12/3 (п. 14.1, 15.5), Р-130. Страница открывается по ссылке без входа:
// IP и user agent плательщика, пароль заказа и первые цифры карты сюда не попадают. Любое поле, кроме
// state и реквизитов провайдера, бывает null — тогда строки на чеке нет, а не выдуманное значение.
public record PaymentReceiptView(
        String state,
        String providerName,
        String providerTaxId,
        String merchantName,
        String merchantTaxId,
        String terminalName,
        String terminalCode,
        // Номер заказа у провайдера — номер чека; RRN — номер, по которому операцию находит банк.
        String receiptNumber,
        String referenceNumber,
        String approvalCode,
        String operationTime,
        String cardBrand,
        String cardLastFour,
        BigDecimal amount,
        String currency,
        String linkNumber,
        String merchantOrderId,
        String description,
        String customerName,
        String customerEmail,
        String customerPhone
) {
}
