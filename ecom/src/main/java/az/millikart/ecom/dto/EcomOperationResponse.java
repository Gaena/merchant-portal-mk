package az.millikart.ecom.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Одна операция по заказу — авторизация, списание, возврат, отмена.
 *
 * Отдаётся только на карточке заказа и служит её историей: в отличие от платёжных ссылок, где
 * историю приходится собирать из своих же меток, здесь она есть у провайдера в готовом виде.
 *
 * Коды операции уходят сырыми. Словарь `trantype` / `phase` / `pmoresultcode` провайдер не
 * утверждал, и переводить незнакомое значение в свой словарь значило бы выдумывать.
 */
public record EcomOperationResponse(
        String operationId,
        Instant at,
        String type,
        String phase,
        String resultCode,
        BigDecimal amount,
        String currency,
        String rrn,
        String terminalId
) {
}
