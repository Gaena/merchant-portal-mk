package az.millikart.txpg.dto;

import java.util.Map;

// Подтверждённый ответ exec-tran — списание холда или возврат (§5.5-5.7). ridByPmo не пуст никогда:
// без него объект не собирается (P1-8b). approvalCode и tranActionId могут отсутствовать.
public record MoneyOperationResult(
        String approvalCode,
        String tranActionId,
        String ridByPmo,
        Map<String, Object> raw
) {
}
