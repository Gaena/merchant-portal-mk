package az.millikart.directory.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// Р-137: ячейка CSV выгрузки журнала. Ловит значение, которое Excel запустил бы формулой (логин неудачного входа —
// чужой ввод), и разделитель или перевод строки в деталях, ломающие колонки.
class AuditCsvCellTest {

    @Test
    void aFormulaStart_isNeutralised() {
        assertThat(AuditLogQueryService.cell("=1+1")).isEqualTo("'=1+1");
        assertThat(AuditLogQueryService.cell("+994")).isEqualTo("'+994");
        assertThat(AuditLogQueryService.cell("-5")).isEqualTo("'-5");
        assertThat(AuditLogQueryService.cell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
    }

    @Test
    void separatorsQuotesAndLineBreaks_areQuoted() {
        assertThat(AuditLogQueryService.cell("a;b")).isEqualTo("\"a;b\"");
        assertThat(AuditLogQueryService.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(AuditLogQueryService.cell("line\nbreak")).isEqualTo("\"line\nbreak\"");
        assertThat(AuditLogQueryService.cell("plain")).isEqualTo("plain");
        assertThat(AuditLogQueryService.cell(null)).isEmpty();
    }
}
