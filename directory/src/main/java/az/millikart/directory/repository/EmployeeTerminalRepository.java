package az.millikart.directory.repository;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

// Терминалы сотрудника (Р-131). Строки пишет auth; запросы, а не сущность — таблица общая. Соединение —
// транзакции вызывающего (§10, Р-85).
@Repository
public class EmployeeTerminalRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public EmployeeTerminalRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Только терминалы, ещё принадлежащие компании сотрудника: перенесённый в другую компанию из скоупа
    // выпадает сам, даже если назначение не сняли. userId не UUID — назначений нет: скоуп пуст, а не «всё».
    public Set<Integer> assignedTerminalIds(String userId, String companyId) {
        UUID id = parse(userId);
        if (id == null || companyId == null) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList(
                "SELECT ut.terminal_id FROM user_terminals ut JOIN terminals t ON t.id = ut.terminal_id "
                        + "WHERE ut.user_id = :userId AND t.company_id = :companyId",
                new MapSqlParameterSource().addValue("userId", id).addValue("companyId", companyId),
                Integer.class));
    }

    // Терминал ушёл в другую компанию — сотрудникам прежней он больше не принадлежит.
    public int dropAssignmentsOf(Integer terminalId) {
        return jdbc.update("DELETE FROM user_terminals WHERE terminal_id = :terminalId",
                new MapSqlParameterSource("terminalId", terminalId));
    }

    private static UUID parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
