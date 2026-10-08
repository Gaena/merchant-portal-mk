package az.millikart.directory.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Итог проверки цепочки журнала (Р-138). notCovered — записи до начала цепочки (сделаны до Р-138, звеньев у
// них нет); unsealedRecords — записи после её начала без звена: вставлены мимо приложения.
public record AuditIntegrityReport(
        boolean intact,
        long checkedRecords,
        long headSeq,
        Instant chainStartedAt,
        long notCovered,
        long unsealedRecords,
        List<Problem> problems,
        boolean problemsTruncated,
        Instant verifiedAt
) {

    // kind: RECORD_CHANGED, RECORD_DELETED, LINKS_MISSING, TIME_CHANGED, HEAD_MISMATCH, RECORDS_OUTSIDE_CHAIN.
    public record Problem(String kind, Long seq, UUID auditId, String detail) {
    }
}
