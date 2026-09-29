package az.millikart.pbl;

import az.millikart.common.security.CredentialCipher;
import org.springframework.jdbc.core.JdbcTemplate;

// Компания с кредами к провайдеру. К провайдеру pbl ходит от имени компании терминала, и без кредов
// отказывает до обращения к нему (Р-93) — поэтому каждый тест, доходящий до провайдера, заводит их.
public final class CompanyCredentialsFixture {

    private CompanyCredentialsFixture() {
    }

    public static String loginOf(String companyId) {
        return "TerminalSys/" + companyId;
    }

    public static String passwordOf(String companyId) {
        return "secret-" + companyId;
    }

    public static void seed(JdbcTemplate jdbc, CredentialCipher cipher, String... companyIds) {
        for (String companyId : companyIds) {
            jdbc.update("DELETE FROM companies WHERE id = ?", companyId);
            jdbc.update("INSERT INTO companies (id, name, status, provider_login, provider_password) "
                            + "VALUES (?, ?, 'ACTIVE', ?, ?)",
                    companyId, "Company " + companyId, loginOf(companyId), cipher.encrypt(passwordOf(companyId)));
        }
    }
}
