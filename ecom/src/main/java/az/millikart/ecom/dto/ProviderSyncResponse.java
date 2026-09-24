package az.millikart.ecom.dto;

import az.millikart.ecom.service.ProviderLoginSyncService;
import az.millikart.ecom.service.ProviderTerminalSyncService;

// Итог ручного обновления справочников провайдера. Верхние поля — слепок терминалов, как было до Р-94;
// logins — слепок логинов мультимерчантов: одна кнопка обновляет оба.
public record ProviderSyncResponse(boolean applied, int seen, int ambiguous, int disabled, String skippedBecause,
                                   ProviderLoginSyncService.SyncOutcome logins) {

    public static ProviderSyncResponse of(ProviderTerminalSyncService.SyncOutcome terminals,
                                          ProviderLoginSyncService.SyncOutcome logins) {
        return new ProviderSyncResponse(terminals.applied(), terminals.seen(), terminals.ambiguous(),
                terminals.disabled(), terminals.skippedBecause(), logins);
    }
}
