package az.millikart.common.audit;

import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Цепочка записей журнала (Р-138, PCI DSS 10.3.4): у каждой записи — звено в audit_chain с номером и HMAC от звена
// перед ней. Правка, удаление или вставка мимо приложения рвут цепочку; пересчитать её без AUDIT_CHAIN_KEY нельзя.
// Звенья пишутся по одному — под блокировкой строки audit_chain_head, в транзакции самой записи: номера без дыр.
// Сама запись (AuditLog) не меняется — её «только дозапись» (Р-42) остаётся как была.
@Component
public class AuditChain {

    public static final String GENESIS = "0".repeat(64);
    private static final int MIN_KEY_BYTES = 32;
    private static final String ALGORITHM = "HmacSHA256";

    private final EntityManager entityManager;
    private final SecretKeySpec key;

    public AuditChain(EntityManager entityManager, @Value("${mp.audit.chain-key}") String chainKey) {
        if (chainKey == null || chainKey.getBytes(StandardCharsets.UTF_8).length < MIN_KEY_BYTES) {
            throw new IllegalStateException("The audit chain key (AUDIT_CHAIN_KEY) is not set or shorter than "
                    + MIN_KEY_BYTES + " bytes. Generate one with `openssl rand -base64 48` and give the SAME value to "
                    + "auth, directory, pbl and ecom: every service seals its journal records with it.");
        }
        this.entityManager = entityManager;
        this.key = new SecretKeySpec(chainKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    // Только внутри транзакции записи: блокировка головы держится до её коммита, откат снимает и звено, и номер.
    public void seal(AuditLog saved) {
        Object[] head = (Object[]) entityManager.createNativeQuery(
                        "SELECT last_seq, last_hash FROM audit_chain_head WHERE id = 1 FOR UPDATE")
                .getSingleResult();
        long seq = ((Number) head[0]).longValue() + 1;
        long sealedAtMicros = toMicros(Instant.now());
        String hash = hash((String) head[1], canonical(saved, seq, sealedAtMicros));
        entityManager.createNativeQuery("INSERT INTO audit_chain (seq, audit_id, sealed_at_micros, hash) "
                        + "VALUES (:seq, :auditId, :sealedAt, :hash)")
                .setParameter("seq", seq)
                .setParameter("auditId", saved.getId())
                .setParameter("sealedAt", sealedAtMicros)
                .setParameter("hash", hash)
                .executeUpdate();
        entityManager.createNativeQuery("UPDATE audit_chain_head SET last_seq = :seq, last_hash = :hash WHERE id = 1")
                .setParameter("seq", seq)
                .setParameter("hash", hash)
                .executeUpdate();
    }

    public String hash(String previousHash, String canonical) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            mac.update(previousHash.getBytes(StandardCharsets.UTF_8));
            mac.update(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available in this JVM", e);
        }
    }

    // Поля — с длиной впереди: «ab|c» и «a|bc» не дают одну строку. Время записи — не created_at (его ставит
    // база с округлением), а свой момент в микросекундах: он хранится в звене целым числом и читается точно.
    public static String canonical(AuditLog log, long seq, long sealedAtMicros) {
        StringBuilder out = new StringBuilder();
        field(out, Long.toString(seq));
        field(out, String.valueOf(log.getId()));
        field(out, Long.toString(sealedAtMicros));
        field(out, log.getEntityType());
        field(out, log.getEntityId());
        field(out, log.getAction());
        field(out, log.getPerformedBy());
        field(out, log.getCompanyId());
        field(out, log.getDetails());
        field(out, log.getClientIp());
        field(out, log.getOutcome() != null ? log.getOutcome().name() : null);
        field(out, log.getTraceId());
        return out.toString();
    }

    public static long toMicros(Instant instant) {
        return ChronoUnit.MICROS.between(Instant.EPOCH, instant);
    }

    private static void field(StringBuilder out, String value) {
        if (value == null) {
            out.append("-|");
        } else {
            out.append(value.length()).append(':').append(value).append('|');
        }
    }
}
