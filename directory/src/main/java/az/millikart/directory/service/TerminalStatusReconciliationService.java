package az.millikart.directory.service;

import az.millikart.common.audit.AuditAction;
import az.millikart.common.audit.AuditEntity;
import az.millikart.common.audit.AuditEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

// Таблица переходов — project_docs/modules/directory.md §3.2 (Р-66). Ручную блокировку (BLOCKED +
// MANUAL) не снимать никогда: это решение клиента. Нет строки в слепке — «не знаем», не трогать.
@Service
public class TerminalStatusReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(TerminalStatusReconciliationService.class);

    private static final String SYSTEM_ACTOR = "system";

    // Ширина terminals.name; у провайдера название до 256 (ecom/001). Длиннее — режем, и сравниваем
    // уже обрезанное: иначе каждый проход видел бы «новое» название и падал на записи (RECON-AUDIT-ROLLBACK).
    static final int NAME_MAX_LENGTH = 255;

    private final TerminalRepository terminalRepository;
    private final ProviderTerminalStatusRepository snapshot;
    private final PaymentLinkStatusRepository paymentLinkStatusRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate perTerminal;

    public TerminalStatusReconciliationService(TerminalRepository terminalRepository,
                                               ProviderTerminalStatusRepository snapshot,
                                               PaymentLinkStatusRepository paymentLinkStatusRepository,
                                               ApplicationEventPublisher eventPublisher,
                                               PlatformTransactionManager transactionManager) {
        this.terminalRepository = terminalRepository;
        this.snapshot = snapshot;
        this.paymentLinkStatusRepository = paymentLinkStatusRepository;
        this.eventPublisher = eventPublisher;
        // REQUIRES_NEW: позванная внутри чужой транзакции, сверка иначе снова стала бы одной на все терминалы.
        this.perTerminal = new TransactionTemplate(transactionManager);
        this.perTerminal.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record ReconcileOutcome(int blocked, int unblocked, int untouched) {
    }

    private enum Change { BLOCKED, UNBLOCKED, NONE }

    // Транзакция на терминал, а не на проход: сбой или конфликт одного не откатывает остальные, а записи
    // журнала ложатся после коммита своей — откат не оставляет в журнале несостоявшихся блокировок
    // (RECON-AUDIT-ROLLBACK). Терминал, который правят руками во время прохода, — до следующего (@Version).
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
            Change change;
            try {
                change = perTerminal.execute(status -> reconcileOne(terminal, activity, identity));
            } catch (OptimisticLockingFailureException e) {
                // Терминал правят руками прямо сейчас: его решение свежее нашего снимка.
                log.info("Terminal {} was changed while the reconciliation ran; leaving it to the next pass",
                        terminal.getId());
                change = Change.NONE;
            } catch (RuntimeException e) {
                log.warn("Reconciliation of terminal {} failed; continuing with the rest", terminal.getId(), e);
                change = Change.NONE;
            }
            switch (change == null ? Change.NONE : change) {
                case BLOCKED -> blocked++;
                case UNBLOCKED -> unblocked++;
                case NONE -> untouched++;
            }
        }

        if (blocked > 0 || unblocked > 0) {
            log.info("Terminal reconciliation applied: blocked {}, unblocked {}, untouched {}",
                    blocked, unblocked, untouched);
        }
        return new ReconcileOutcome(blocked, unblocked, untouched);
    }

    private Change reconcileOne(Terminal terminal, Map<String, Boolean> activity,
                                Map<String, ProviderTerminalStatusRepository.ProviderTerminalRow> identity) {
        String rid = terminal.getMerchantRid();
        if (rid == null || rid.isBlank()) {
            return Change.NONE;
        }
        ProviderTerminalStatusRepository.ProviderTerminalRow row = identity.get(rid);
        if (row != null) {
            alignIdentity(terminal, row);
        }
        Boolean activeAtProvider = activity.get(rid);
        if (activeAtProvider == null) {
            log.debug("Terminal {} points at provider terminal {}, which the snapshot does not "
                    + "mention; leaving it alone", terminal.getId(), rid);
            return Change.NONE;
        }

        boolean ourActive = terminal.getStatus() != TerminalStatus.BLOCKED;
        if (ourActive && !activeAtProvider) {
            apply(terminal, TerminalStatus.BLOCKED, rid);
            return Change.BLOCKED;
        }
        if (!ourActive && activeAtProvider && terminal.getStatusSource() == TerminalStatusSource.PROVIDER) {
            apply(terminal, TerminalStatus.ACTIVE, rid);
            return Change.UNBLOCKED;
        }
        return Change.NONE;
    }

    // Название, логин и номер — провайдера (Р-67): старый номер отправил бы заказ pbl не на тот
    // терминал (Р-96).
    private void alignIdentity(Terminal terminal, ProviderTerminalStatusRepository.ProviderTerminalRow row) {
        String login = row.gatewayLogin();
        String title = row.title() != null && !row.title().isBlank() ? fitName(row.title()) : null;
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

        eventPublisher.publishEvent(AuditEvent.of(AuditEntity.TERMINAL, String.valueOf(terminal.getId()),
                AuditAction.UPDATE, SYSTEM_ACTOR, terminal.getCompanyId(), details));
    }

    private static String fitName(String title) {
        return title.length() <= NAME_MAX_LENGTH ? title : title.substring(0, NAME_MAX_LENGTH);
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

        eventPublisher.publishEvent(AuditEvent.of(
                AuditEntity.TERMINAL,
                String.valueOf(terminalId),
                target == TerminalStatus.BLOCKED ? AuditAction.BLOCK : AuditAction.UNBLOCK,
                SYSTEM_ACTOR,
                terminal.getCompanyId(),
                details
        ));
    }
}
