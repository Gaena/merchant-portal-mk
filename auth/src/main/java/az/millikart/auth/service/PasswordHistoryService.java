package az.millikart.auth.service;

import az.millikart.auth.domain.PasswordHistory;
import az.millikart.auth.domain.User;
import az.millikart.auth.repository.PasswordHistoryRepository;
import az.millikart.common.exception.BusinessException;
import java.time.Instant;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

// PCI DSS 8.3.7 (Р-102): новый пароль владельца не повторяет ни один из четырёх последних — текущий и три
// из password_history. Проверка — только когда пароль задаёт сам владелец: на сбросе чужого пароля она
// сказала бы администратору, какие пароли пользователь недавно использовал.
@Service
public class PasswordHistoryService {

    // Сколько последних паролей нельзя повторить, считая текущий. Не подкручивать: так требует 8.3.7.
    static final int REMEMBERED = 4;
    static final String RECENT_PASSWORD = "The new password must differ from the last " + REMEMBERED + " passwords";

    private final PasswordHistoryRepository repository;
    private final PasswordEncoder passwordEncoder;

    public PasswordHistoryService(PasswordHistoryRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public void requireNotRecent(User user, String newPassword) {
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException(RECENT_PASSWORD);
        }
        List<PasswordHistory> previous = repository.findByUserIdOrderByReplacedAtDesc(user.getId());
        for (PasswordHistory entry : previous.subList(0, Math.min(previous.size(), REMEMBERED - 1))) {
            if (passwordEncoder.matches(newPassword, entry.getPasswordHash())) {
                throw new BusinessException(RECENT_PASSWORD);
            }
        }
    }

    // Текущий пароль уходит в историю перед заменой; старше трёх прежних не хранится ничего.
    public void rememberCurrent(User user, Instant now) {
        repository.save(PasswordHistory.builder()
                .userId(user.getId())
                .passwordHash(user.getPasswordHash())
                .replacedAt(now)
                .build());
        List<PasswordHistory> previous = repository.findByUserIdOrderByReplacedAtDesc(user.getId());
        if (previous.size() > REMEMBERED - 1) {
            repository.deleteAll(previous.subList(REMEMBERED - 1, previous.size()));
        }
    }
}
