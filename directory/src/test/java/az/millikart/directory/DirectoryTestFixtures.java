package az.millikart.directory;

import az.millikart.directory.dto.CreateCompanyRequest;
import java.sql.Connection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

// Фикстуры, общие для тестов directory.
final class DirectoryTestFixtures {

    // Базы, где слепки ecom уже созданы: Liquibase на каждый вызов фикстуры стоил бы секунды.
    private static final Set<DataSource> WITH_ECOM_SCHEMA = Collections.newSetFromMap(new IdentityHashMap<>());

    private DirectoryTestFixtures() {
    }

    // provider_terminals и provider_logins держит ecom: создаёт их его настоящий changelog, а не копия DDL —
    // иначе переименованная там колонка ломала бы прод, а не тесты directory.
    static synchronized void ensureEcomSnapshots(JdbcTemplate jdbc) {
        DataSource dataSource = jdbc.getDataSource();
        if (WITH_ECOM_SCHEMA.contains(dataSource)) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            SharedDatabaseSchema.applyEcomChangelog(connection);
        } catch (Exception e) {
            throw new IllegalStateException("the ecom changelog did not apply", e);
        }
        WITH_ECOM_SCHEMA.add(dataSource);
    }

    // Компания с кредами к провайдеру: без них API её не заводит (Р-93). Логин уникален, поэтому — по id;
    // пройдёт проверку, только если providerLogins завёл его в слепок (Р-94).
    static CreateCompanyRequest company(String id, String name) {
        return new CreateCompanyRequest(id, name, "MultiMerchantSys/" + id, "secret-" + id);
    }

    // Слепок логинов мультимерчантов (Р-94). Логин каждой компании — активный, с одним активным мерчантом.
    static void providerLogins(JdbcTemplate jdbc, String... companyIds) {
        for (String companyId : companyIds) {
            providerLogin(jdbc, companyId, "Active", "Active", "M-" + companyId);
        }
    }

    static void providerLogin(JdbcTemplate jdbc, String login, String loginStatus, String linkStatus, String merchantRid) {
        ensureEcomSnapshots(jdbc);
        jdbc.update("DELETE FROM provider_logins WHERE login = ?", login);
        jdbc.update("INSERT INTO provider_logins (login, login_status, link_status, merchant_rid, synced_at) "
                + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)", login, loginStatus, linkStatus, merchantRid);
    }

    // Строка справочника провайдера: завести терминал можно только выбором из него (Р-93).
    static void providerTerminal(JdbcTemplate jdbc, String rid, String title, String login) {
        providerTerminal(jdbc, rid, title, login, true);
    }

    // Номер терминала у провайдера — "TID-" + код мерчанта (Р-96).
    static void providerTerminal(JdbcTemplate jdbc, String rid, String title, String login, boolean active) {
        ensureEcomSnapshots(jdbc);
        jdbc.update("DELETE FROM provider_terminals WHERE rid = ?", rid);
        jdbc.update("INSERT INTO provider_terminals (rid, title, login, active, terminal_rid) VALUES (?, ?, ?, ?, ?)",
                rid, title, login, active, "TID-" + rid);
    }

    // Терминал провайдера, который компания вправе завести: мерчант связан с её логином мультимерчанта (Р-96).
    static void companyTerminal(JdbcTemplate jdbc, String companyId, String rid, String title, String login) {
        providerTerminal(jdbc, rid, title, login);
        linkMerchant(jdbc, companyId, rid);
    }

    // Ещё одна активная связь логина компании с мерчантом — к тем, что уже в слепке.
    static void linkMerchant(JdbcTemplate jdbc, String companyId, String merchantRid) {
        ensureEcomSnapshots(jdbc);
        jdbc.update("INSERT INTO provider_logins (login, login_status, link_status, merchant_rid, synced_at) "
                + "VALUES (?, 'Active', 'Active', ?, CURRENT_TIMESTAMP)", companyId, merchantRid);
    }
}
