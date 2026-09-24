package az.millikart.directory;

import az.millikart.directory.dto.CreateCompanyRequest;
import org.springframework.jdbc.core.JdbcTemplate;

// Фикстуры, общие для тестов directory.
final class DirectoryTestFixtures {

    private DirectoryTestFixtures() {
    }

    // Компания с кредами к провайдеру: без них API её не заводит (Р-93). Логин уникален, поэтому — по id.
    static CreateCompanyRequest company(String id, String name) {
        return new CreateCompanyRequest(id, name, "TerminalSys/" + id, "secret-" + id);
    }

    // Строка справочника провайдера. Таблицу держит ecom, в базе тестов directory её нет, а завести
    // терминал теперь можно только выбором из справочника (Р-93). Колонки — те, что читает directory.
    static void providerTerminal(JdbcTemplate jdbc, String rid, String title, String login) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS provider_terminals (rid varchar(64) PRIMARY KEY, "
                + "title varchar(256), login varchar(128), active boolean NOT NULL DEFAULT true)");
        jdbc.update("DELETE FROM provider_terminals WHERE rid = ?", rid);
        jdbc.update("INSERT INTO provider_terminals (rid, title, login, active) VALUES (?, ?, ?, true)",
                rid, title, login);
    }
}
