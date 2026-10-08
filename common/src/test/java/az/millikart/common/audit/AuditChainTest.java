package az.millikart.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

// Р-138: печать записи журнала. Ловит пустой или короткий ключ (цепочку пересчитал бы любой), склейку полей без
// границ («ab|c» и «a|bc» — одна строка, и правку не заметили бы) и поле, не входящее в печать.
class AuditChainTest {

    private static final String KEY = "test-only-audit-chain-key-not-used-anywhere-else-0123456789";
    private final AuditChain chain = new AuditChain(mock(EntityManager.class), KEY);

    @Test
    void aMissingOrShortKey_refusesToStart() {
        assertThrows(IllegalStateException.class, () -> new AuditChain(mock(EntityManager.class), null));
        assertThrows(IllegalStateException.class, () -> new AuditChain(mock(EntityManager.class), "too-short"));
    }

    @Test
    void fieldBoundaries_arePartOfTheSeal() {
        String first = AuditChain.canonical(record("ab", "c", null), 1, 10);
        String second = AuditChain.canonical(record("a", "bc", null), 1, 10);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void anyChangedField_orAnotherPreviousLink_changesTheHash() {
        String sealed = chain.hash(AuditChain.GENESIS, AuditChain.canonical(record("USER", "u-1", "details"), 1, 10));

        assertThat(chain.hash(AuditChain.GENESIS, AuditChain.canonical(record("USER", "u-1", "forged"), 1, 10)))
                .isNotEqualTo(sealed);
        assertThat(chain.hash(AuditChain.GENESIS, AuditChain.canonical(record("USER", "u-1", "details"), 2, 10)))
                .isNotEqualTo(sealed);
        assertThat(chain.hash(AuditChain.GENESIS, AuditChain.canonical(record("USER", "u-1", "details"), 1, 11)))
                .isNotEqualTo(sealed);
        assertThat(chain.hash("f".repeat(64), AuditChain.canonical(record("USER", "u-1", "details"), 1, 10)))
                .isNotEqualTo(sealed);
        assertThat(new AuditChain(mock(EntityManager.class), KEY + "x")
                .hash(AuditChain.GENESIS, AuditChain.canonical(record("USER", "u-1", "details"), 1, 10)))
                .as("another key, another seal").isNotEqualTo(sealed);
        assertThat(chain.hash(AuditChain.GENESIS, AuditChain.canonical(record("USER", "u-1", "details"), 1, 10)))
                .isEqualTo(sealed);
    }

    private static AuditLog record(String entityType, String entityId, String details) {
        return AuditLog.builder().entityType(entityType).entityId(entityId).action(AuditAction.UPDATE)
                .performedBy("head@test.com").companyId("comp-01").details(details).build();
    }
}
