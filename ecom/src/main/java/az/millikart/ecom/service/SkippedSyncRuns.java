package az.millikart.ecom.service;

import org.slf4j.Logger;

// Пропуск прохода синхронизации — в лог при начале и при смене вида, повтор каждые 15 минут — DEBUG, первый
// применённый проход — INFO (ECOM-SYNC-LOG, Р-98). Сравнивается вид, а не текст: у Oracle в тексте ошибки
// свой CONNECTION_ID на каждую попытку. Память процесса; зовётся под замком sync().
final class SkippedSyncRuns {

    private final Logger log;
    private final String sync;
    private String reportedKind;

    SkippedSyncRuns(Logger log, String sync) {
        this.log = log;
        this.sync = sync;
    }

    // true — пропуск нового вида, и вызывающий пишет его сам, со стектрейсом.
    boolean isNew(String kind, String reason) {
        if (kind.equals(reportedKind)) {
            log.debug("{} skipped again: {}", sync, reason);
            return false;
        }
        reportedKind = kind;
        return true;
    }

    void applied() {
        if (reportedKind != null) {
            log.info("{} is applied again after skipped runs ({})", sync, reportedKind);
            reportedKind = null;
        }
    }
}
