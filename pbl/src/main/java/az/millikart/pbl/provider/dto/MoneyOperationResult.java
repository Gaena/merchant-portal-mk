package az.millikart.pbl.provider.dto;

import java.util.Map;

// Ответ эквайера на exec-tran — списание холда или возврат (контракт §5.5-5.7, форма одна на все).
// ridByPmo — доказательство, что операция прошла: его же читает проверка успеха у самого эквайера
// (§5.8.8). Пустым он здесь не бывает — TxpgAcquiringClient отказывается собрать объект без него
// (P1-8b). approvalCode и tranActionId полезны в споре, но могут отсутствовать.
public record MoneyOperationResult(
        String approvalCode,
        String tranActionId,
        String ridByPmo,
        Map<String, Object> raw
) {
}
