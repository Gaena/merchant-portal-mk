package az.millikart.auth.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

// Терминалы сотрудника (Р-131). Запросы, а не сущность: terminals — таблица directory и pbl, auth её только
// читает, чтобы проверить компанию терминала. Соединение — транзакции вызывающего (§10, Р-85).
@Repository
public class UserTerminalRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public UserTerminalRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Integer> terminalIdsOf(UUID userId) {
        return jdbc.queryForList(
                "SELECT terminal_id FROM user_terminals WHERE user_id = :userId ORDER BY terminal_id",
                new MapSqlParameterSource("userId", userId), Integer.class);
    }

    // Одним запросом на страницу списка, а не на строку. С пустой коллекцией не вызывать — IN () невалиден.
    public Map<UUID, List<Integer>> terminalIdsOf(Collection<UUID> userIds) {
        Map<UUID, List<Integer>> result = new HashMap<>();
        jdbc.query("SELECT user_id, terminal_id FROM user_terminals WHERE user_id IN (:userIds) "
                        + "ORDER BY user_id, terminal_id",
                new MapSqlParameterSource("userIds", userIds),
                rs -> {
                    UUID userId = rs.getObject(1, UUID.class);
                    result.computeIfAbsent(userId, key -> new java.util.ArrayList<>()).add(rs.getInt(2));
                });
        return result;
    }

    public void replace(UUID userId, Collection<Integer> terminalIds, String assignedBy, Instant at) {
        jdbc.update("DELETE FROM user_terminals WHERE user_id = :userId", new MapSqlParameterSource("userId", userId));
        for (Integer terminalId : terminalIds) {
            jdbc.update("INSERT INTO user_terminals (user_id, terminal_id, assigned_by, assigned_at) "
                            + "VALUES (:userId, :terminalId, :assignedBy, :assignedAt)",
                    new MapSqlParameterSource()
                            .addValue("userId", userId)
                            .addValue("terminalId", terminalId)
                            .addValue("assignedBy", assignedBy)
                            .addValue("assignedAt", Timestamp.from(at)));
        }
    }

    // Те из terminalIds, что принадлежат компании. Заблокированные тоже: по ним остаётся история платежей.
    public Set<Integer> ownedByCompany(String companyId, Collection<Integer> terminalIds) {
        if (companyId == null || terminalIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList(
                "SELECT id FROM terminals WHERE company_id = :companyId AND id IN (:ids)",
                new MapSqlParameterSource().addValue("companyId", companyId).addValue("ids", terminalIds),
                Integer.class));
    }
}
