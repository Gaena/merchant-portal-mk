package az.millikart.ecom.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Одна строка вкладки — **один заказ**, а не одна операция.
 *
 * В схеме шлюза `tran` даёт строку на операцию, и у одного DMS-платежа их как минимум две.
 * Отдавать их как есть значило бы показать мерчанту каждый платёж дважды, задвоить любую сумму
 * в карточках статистики и сломать пагинацию. Операции по заказу живут в отдельном ответе.
 */
public record EcomTransactionResponse(
        // Идентификаторы, по которым мерчант узнаёт платёж на своей стороне — те же, что и на
        // вкладке платёжных ссылок: номер заказа у провайдера идёт первым.
        String orderId,
        /** `merchant.rid` — reference id **мерчанта**, его задаёт провайдер. */
        String merchantRid,
        String merchantTitle,
        /**
         * `order_.ridbymerchant` — reference id **платежа**, его задаёт мерчант.
         *
         * **Бывает пустым, и это нормально.** По ссылкам, заведённым порталом, его генерирует
         * `OpenLinkService` и он есть всегда; по заказам, которые мерчант завёл у провайдера
         * сам, его может не быть вовсе. Подставлять вместо него номер заказа провайдера или
         * внутренний идентификатор нельзя — на карточке операции такая подмена уже была и
         * выдавала мерчанту чужие номера за его собственные.
         */
        String ridByMerchant,
        /** Разобранный статус: те же шесть значений, что у платёжных ссылок. */
        String status,
        /** Сырые коды заказа у провайдера. Показываются рядом, чтобы расхождение было видно. */
        String providerStatus,
        String providerPrevStatus,
        /** Сумма заказа. Не путать с `capturedAmount`: при частичном списании они разные. */
        BigDecimal amount,
        BigDecimal capturedAmount,
        BigDecimal refundedAmount,
        String currency,
        String description,
        Instant createdAt,
        /** Время первой и последней операции по заказу — выписка читается по последней. */
        Instant firstOperationAt,
        Instant lastOperationAt,
        int operationCount,
        String terminalId,
        String cardMask,
        String rrn,
        /** Код отказа провайдера. Заполнен, когда одобренных операций не было. */
        String declineCode,
        /** Почта и телефон плательщика: в шлюзе они есть (`order_.srcemail`, `.srcmobile`). */
        String customerEmail,
        String customerPhone
) {
}
