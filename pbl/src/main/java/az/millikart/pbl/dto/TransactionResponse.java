package az.millikart.pbl.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID paymentLinkId,
        String status,
        BigDecimal amount,
        // Сколько эквайер реально склирил при списании DMS; null для SMS-платежей и для холдов,
        // которые не списывали. После частичного списания меньше amount и служит потолком, по
        // которому меряется каждый возврат этой транзакции.
        BigDecimal capturedAmount,
        BigDecimal refundedAmount,
        String currency,
        String description,
        String merchantOrderId,
        String paymentType,
        Integer terminalId,
        // Reference id платежа, заданный мерчантом (словарь провайдера). Не путать с merchantRid —
        // reference id самого мерчанта, который задаёт провайдер. По ссылкам, заведённым порталом,
        // заполнен всегда: его генерирует OpenLinkService.
        String ridByMerchant,
        String cardNumberMasked,
        String rrn,
        String approvalCode,
        Instant createdAt,
        String customerName,
        String customerEmail,
        String customerPhone,
        String clientIp,
        String userAgent,
        String providerOrderId,
        /**
         * Что и когда с операцией происходило — только записанное, по возрастанию времени.
         * Собирается в PaymentLinkService.statusHistoryOf из createdAt, метки списания
         * (mpCapture) и списка возвратов (mpRefunds): у каждого из них есть своё время,
         * записанное в момент события.
         *
         * Переходов, времени которых никто не записывал, здесь нет и не будет. У SMS-платежа
         * нет отдельного события «стал SUCCESS» — есть лишь updatedAt, и он попадает сюда
         * единственным финальным событием, когда текущий статус ничем выше не объяснён.
         * Дорисовывать недостающее (две записи с одним временем, «Payment successfully
         * completed» под выдуманной датой) нельзя: карточка операции с деньгами — не то место,
         * где догадка сходит за факт.
         */
        List<TransactionEvent> statusHistory,
        // Причина отказа словами эквайера: custAttrs DeclineDescription, иначе
        // PmoDeclineDescription, иначе PmoResultCode (контракт §5.8.7). Заполнен только у FAILED,
        // чей финальный опрос статуса принёс причину; иначе null.
        String failureReason
) {

    /**
     * Одно записанное событие жизни операции.
     *
     * `status` — состояние операции ПОСЛЕ события, из того же словаря TransactionStatus, что
     * и `TransactionResponse.status`: фронтенд разбирает их одним разбором.
     *
     * `amount` и `acquirerReference` заполнены только у денежных событий — списания и возврата.
     * Ссылка — это tran.match.ridByPmo, та самая, что весома в споре (§5.7).
     */
    public record TransactionEvent(
            Instant at,
            // CREATED, CAPTURED, REFUNDED или STATUS — что именно произошло. STATUS означает
            // «операция пришла в это состояние», без записи о том, каким действием.
            String type,
            String status,
            BigDecimal amount,
            String acquirerReference
    ) {
    }
}
