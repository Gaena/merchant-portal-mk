package az.millikart.directory.service;

import az.millikart.common.audit.AuditAction;
import az.millikart.common.audit.AuditEntity;
import az.millikart.common.audit.AuditEvent;
import az.millikart.common.audit.AuditLogService;
import az.millikart.directory.domain.Terminal;
import az.millikart.directory.domain.TerminalStatus;
import az.millikart.directory.domain.TerminalStatusSource;
import az.millikart.directory.repository.PaymentLinkStatusRepository;
import az.millikart.directory.repository.ProviderTerminalStatusRepository;
import az.millikart.directory.repository.TerminalRepository;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Таблица переходов — project_docs/modules/directory.md §3.2 (Р-66). Ручную блокировку (BLOCKED +
// MANUAL) не снимать никогда: это решение клиента. Нет строки в слепке — «не знаем», не трогать.
@Service
public class TerminalStatusReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(TerminalStatusReconciliationService.class);

    private static final String SYSTEM_ACTOR = "system";

    private final TerminalRepository terminalRepository;
    private final ProviderTerminalStatusRepository snapshot;
    private final PaymentLinkStatusRepository paymentLinkStatusRepository;
    private final AuditLogService auditLogService;

    public TerminalStatusReconciliationService(TerminalRepository terminalRepository,
                                               ProviderTerminalStatusRepository snapshot,
                                               PaymentLinkStatusRepository paymentLinkStatusRepository,
                                               AuditLogService auditLogService) {
        this.terminalRepository = terminalRepository;
        this.snapshot = snapshot;
        this.paymentLinkStatusRepository = paymentLinkStatusRepository;
        this.auditLogService = auditLogService;
    }

    public record ReconcileOutcome(int blocked, int unblocked, int untouched) {
    }

    @Transactional
    public ReconcileOutcome reconcile() {
        Map<String, Boolean> activity = snapshot.activityByRid();
        if (activity.isEmpty()) {
            // Пустой слепок — «ещё не синхронизировались» или «ecom не развёрнут», а не «у провайдера
            // нет терминалов»: иначе выключится всё сразу.
            log.info("Terminal reconciliation skipped: the provider snapshot is empty");
            return new ReconcileOutcome(0, 0, 0);
        }

        Map<String, ProviderTerminalStatusRepository.ProviderTerminalRow> identity = snapshot.rowsByRid();

        int blocked = 0;
        int unblocked = 0;
        int untouched = 0;

        for (Terminal terminal : terminalRepository.findAll()) {
            String rid = terminal.getMerchantRid();
            if (rid == null || rid.isBlank()) {
                untouched++;
                continue;
            }
            ProviderTerminalStatusRepository.ProviderTerminalRow row = identity.get(rid);
            if (row != null) {
                alignIdentity(terminal, row);
            }
            Boolean activeAtProvider = activity.get(rid);
            if (activeAtProvider == null) {
                log.debug("Terminal {} points at provider terminal {}, which the snapshot does not "
                        + "mention; leaving it alone", terminal.getId(), rid);
                untouched++;
                continue;
            }

            boolean ourActive = terminal.getStatus() != TerminalStatus.BLOCKED;

            if (ourActive && !activeAtProvider) {
                apply(terminal, TerminalStatus.BLOCKED, rid);
                blocked++;
            } else if (!ourActive && activeAtProvider
                    && terminal.getStatusSource() == TerminalStatusSource.PROVIDER) {
                apply(terminal, TerminalStatus.ACTIVE, rid);
                unblocked++;
            } else {
                untouched++;
            }
        }

        if (blocked > 0 || unblocked > 0) {
            log.info("Terminal reconciliation applied: blocked {}, unblocked {}, untouched {}",
                    blocked, unblocked, untouched);
        }
        return new ReconcileOutcome(blocked, unblocked, untouched);
    }

    // Название, логин и номер — провайдера (Р-67): старый номер отправил бы заказ pbl не на тот
    // терминал (Р-96).
    private void alignIdentity(Terminal terminal, ProviderTerminalStatusRepository.ProviderTerminalRow row) {
        String login = row.gatewayLogin();
        String title = row.title() != null && !row.title().isBlank() ? row.title() : null;
        String terminalRid = row.terminalRid() != null && !row.terminalRid().isBlank() ? row.terminalRid() : null;
        boolean loginChanged = login != null && !login.equals(terminal.getLogin());
        boolean titleChanged = title != null && !title.equals(terminal.getName());
        boolean terminalRidChanged = terminalRid != null && !terminalRid.equals(terminal.getTerminalRid());
        if (!loginChanged && !titleChanged && !terminalRidChanged) {
            return;
        }
        String details = "Provider terminal " + row.rid() + " changed for terminal " + terminal.getId() + ":"
                + (loginChanged ? " login " + terminal.getLogin() + " -> " + login : "")
                + (titleChanged ? " name " + terminal.getName() + " -> " + title : "")
                + (terminalRidChanged ? " terminal " + terminal.getTerminalRid() + " -> " + terminalRid : "");
        if (loginChanged) {
            terminal.setLogin(login);
        }
        if (titleChanged) {
            terminal.setName(title);
        }
        if (terminalRidChanged) {
            terminal.setTerminalRid(terminalRid);
        }
        terminal.setUpdatedBy(SYSTEM_ACTOR);
        terminalRepository.save(terminal);
        log.info(details);

        auditLogService.recordSuccess(AuditEvent.of(AuditEntity.TERMINAL, String.valueOf(terminal.getId()),
                AuditAction.UPDATE, SYSTEM_ACTOR, terminal.getCompanyId(), details));
    }

    // Ссылки — в той же транзакции, как у ручной блокировки (Р-39, Р-40).
    private void apply(Terminal terminal, TerminalStatus target, String rid) {
        Integer terminalId = terminal.getId();
        terminal.setStatus(target);
        terminal.setStatusSource(TerminalStatusSource.PROVIDER);
        terminal.setUpdatedBy(SYSTEM_ACTOR);
        terminalRepository.save(terminal);

        String details;
        if (target == TerminalStatus.BLOCKED) {
            int suspended = paymentLinkStatusRepository.suspendActiveLinks(terminalId);
            details = "Blocked terminal " + terminalId + " because provider terminal " + rid
                    + " is out of service; suspended " + suspended + " links";
        } else {
            Instant now = Instant.now();
            int resumed = paymentLinkStatusRepository.resumeSuspendedLinks(terminalId, now);
            int expired = paymentLinkStatusRepository.expireSuspendedLinks(terminalId, now);
            details = "Unblocked terminal " + terminalId + " because provider terminal " + rid
                    + " is back in service; resumed " + resumed + " links, expired " + expired + " links";
        }
        log.info(details);

        // Синхронно, а не событием после коммита: запись о массовой правке ссылок должна лечь, даже
        // если транзакция планировщика дальше упадёт.
        auditLogService.recordSuccess(AuditEvent.of(
                AuditEntity.TERMINAL,
                String.valueOf(terminalId),
                target == TerminalStatus.BLOCKED ? AuditAction.BLOCK : AuditAction.UNBLOCK,
                SYSTEM_ACTOR,
                terminal.getCompanyId(),
                details
        ));
    }
}
