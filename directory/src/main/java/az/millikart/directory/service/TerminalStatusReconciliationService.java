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

/**
 * Сверка статусов наших терминалов с тем, что видит провайдер.
 *
 * Правила целиком, и каждое — ответ на конкретный вопрос «а что если».
 *
 *   наш ACTIVE, у провайдера выключен
 *       → выключаем, источник PROVIDER, активные ссылки приостанавливаются.
 *         Через терминал, снятый с обслуживания, платёж всё равно не пройдёт, и оставлять
 *         ссылки оплачиваемыми значит отправлять плательщиков в отказ.
 *
 *   наш BLOCKED с источником PROVIDER, у провайдера включён
 *       → включаем обратно, ссылки восстанавливаются существующей логикой.
 *         Без этой ветки одно временное отключение на их стороне гасило бы терминал навсегда:
 *         человек включить его не может, а система бы не стала.
 *
 *   наш BLOCKED с источником MANUAL
 *       → не трогаем никогда, что бы ни говорил провайдер. Это решение клиента, и оно не
 *         отменяется тем, что у провайдера всё в порядке.
 *
 *   у нашего терминала нет привязки к провайдеру, либо его rid не встречается в слепке
 *       → не трогаем. Отсутствие строки — это «не знаем», а не «выключен»; выключение
 *         фиксируется явным флагом в слепке, и только после нескольких пропаданий подряд
 *         (см. ProviderTerminalSyncService в ecom).
 *
 * Актором в журнале стоит «system»: массовая правка чужих платёжных ссылок без человека обязана
 * быть отличима от той, что сделал администратор.
 */
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
            // Пустой слепок — это «синхронизация ещё не проходила» или «ecom здесь не развёрнут».
            // Считать его сообщением о том, что у провайдера не осталось терминалов, нельзя:
            // так выключится всё сразу.
            log.info("Terminal reconciliation skipped: the provider snapshot is empty");
            return new ReconcileOutcome(0, 0, 0);
        }

        int blocked = 0;
        int unblocked = 0;
        int untouched = 0;

        for (Terminal terminal : terminalRepository.findAll()) {
            String rid = terminal.getMerchantRid();
            if (rid == null || rid.isBlank()) {
                untouched++;
                continue;
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

    /**
     * Смена статуса вместе с её последствиями для ссылок — тем же порядком, что и у ручной
     * блокировки: заблокированного терминала с оплачиваемыми ссылками не должно быть ни мгновения.
     */
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

        // Синхронно, а не событием после коммита: этот вызов идёт из планировщика, и запись
        // о массовой правке чужих ссылок должна лечь даже если транзакция дальше упадёт.
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
