package az.millikart.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import az.millikart.auth.domain.RefreshToken;
import az.millikart.auth.domain.User;
import az.millikart.auth.dto.LoginRequest;
import az.millikart.auth.dto.LoginResponse;
import az.millikart.auth.dto.LogoutRequest;
import az.millikart.auth.dto.RefreshRequest;
import az.millikart.auth.repository.CompanyRepository;
import az.millikart.auth.repository.RefreshTokenRepository;
import az.millikart.auth.repository.UserRepository;
import az.millikart.auth.service.AuthService;
import az.millikart.auth.service.RefreshTokenService;
import az.millikart.common.exception.UnauthorizedException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.crypto.password.PasswordEncoder;

// P1-12: refresh и отзыв одной семьи могут идти в один момент — logout против refresh, админ
// блокирует пользователя посреди refresh. Кто коммитит вторым, обязан увидеть работу первого:
// иначе logout или block отчитаются об успехе, а живой токен уцелеет. Оба сценария идут через
// сервисы мимо MockMvc: SpyBean держит refresh в find или issue. Без Transactional — нужны коммиты.
@SpringBootTest
class RefreshTokenConcurrencyTest {

    private static final String EMAIL = "race@comp01.com";
    private static final String PASSWORD = "RacePassword123!";
    // Лимитер считает по адресу, а эти тесты логинятся многократно — даём им отдельный.
    private static final String CLIENT_IP = "198.51.100.60";

    // Достаточно долго, чтобы второй поток заведомо стоял в блокировке, пока первый удержан.
    private static final long HOLD_MILLIS = 300;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @SpyBean
    private RefreshTokenService refreshTokenService;

    private ExecutorService pool;
    private UUID userId;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
        userId = userRepository.save(User.builder()
                .username(EMAIL)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Race Condition")
                .role("COMPANY_EMPLOYEE")
                .status("ACTIVE")
                .build()).getId();
        pool = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    @Test
    @DisplayName("logout committed between the refresh's read and its rotation: the refresh is refused, nothing new is issued")
    void refresh_whenFamilyRevokedAfterItWasRead_isRefusedAndIssuesNothing() throws Exception {
        String token = authService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT_IP).refreshToken();

        // Держим refresh сразу после шага 1 (токен прочитан, revoked_at = NULL в памяти) и до
        // коммита logout. Держится только поток refresh; find() потока logout проходит свободно.
        CountDownLatch tokenRead = new CountDownLatch(1);
        CountDownLatch logoutDone = new CountDownLatch(1);
        AtomicReference<Thread> refreshThread = new AtomicReference<>();
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (Thread.currentThread() == refreshThread.get()) {
                tokenRead.countDown();
                assertTrue(logoutDone.await(5, TimeUnit.SECONDS), "logout did not finish in time");
            }
            return result;
        }).when(refreshTokenService).find(anyString());

        Future<Throwable> refreshOutcome = pool.submit(() -> {
            refreshThread.set(Thread.currentThread());
            try {
                authService.refresh(new RefreshRequest(token));
                return null;
            } catch (Throwable t) {
                return t;
            }
        });

        assertTrue(tokenRead.await(5, TimeUnit.SECONDS), "refresh did not reach its read in time");
        authService.logout(new LogoutRequest(token));   // commits: family revoked
        logoutDone.countDown();

        Throwable outcome = refreshOutcome.get(10, TimeUnit.SECONDS);
        assertNotNull(outcome, "the refresh must be refused, not served");
        assertEquals(UnauthorizedException.class, outcome.getClass(), String.valueOf(outcome));

        List<RefreshToken> rows = refreshTokenRepository.findAllByUserId(userId);
        assertEquals(1, rows.size(), "no successor may be minted for a family revoked mid-refresh");
        assertTrue(rows.get(0).isRevoked(), "the logout's revocation must not be overwritten by the refresh");
        assertFalse(rows.get(0).isRotated(), "a refused refresh must not retire the token either");
    }

    @Test
    @DisplayName("logout arriving while the refresh already holds the row: it waits, then revokes the successor too")
    void logout_whileRefreshIsCommitting_revokesTheSuccessorAsWell() throws Exception {
        String token = authService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT_IP).refreshToken();

        // Держим refresh внутри шага 6: markRotatedIfLive уже взял блокировку строки, преемник ещё
        // не вставлен. Начатый сейчас logout обязан встать на этой блокировке (revokeFamily берёт
        // SELECT ... FOR UPDATE по семье до массового UPDATE) и, освободившись, отозвать преемника,
        // которого до того видеть не мог.
        CountDownLatch rowLocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            rowLocked.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS), "refresh was not released in time");
            return invocation.callRealMethod();
        }).when(refreshTokenService).issue(any(), any(), any());

        Future<LoginResponse> refreshOutcome = pool.submit(() -> authService.refresh(new RefreshRequest(token)));
        assertTrue(rowLocked.await(5, TimeUnit.SECONDS), "refresh did not reach issue() in time");

        Future<?> logoutOutcome = pool.submit(() -> authService.logout(new LogoutRequest(token)));
        // Logout сейчас стоит на блокировке строки; держим его там столько, чтобы "он дождался"
        // было единственной причиной того, что он увидел преемника. (Таймаут блокировки H2 — 1 с.)
        Thread.sleep(HOLD_MILLIS);
        assertFalse(logoutOutcome.isDone(), "logout must wait for the in-flight refresh, not overtake it");
        release.countDown();

        LoginResponse refreshed = refreshOutcome.get(10, TimeUnit.SECONDS);
        logoutOutcome.get(10, TimeUnit.SECONDS);
        assertNotNull(refreshed.refreshToken());

        List<RefreshToken> rows = refreshTokenRepository.findAllByUserId(userId);
        assertEquals(2, rows.size(), "the refresh committed first: predecessor + successor");
        assertTrue(rows.stream().allMatch(RefreshToken::isRevoked),
                "the logout that waited must revoke the successor as well, not only the token it was given");
        assertThrows(UnauthorizedException.class,
                () -> authService.refresh(new RefreshRequest(refreshed.refreshToken())),
                "the successor minted during the race must be dead after the logout");
    }

    @Test
    @DisplayName("markRotated refuses a token that has been revoked — the conditional UPDATE the race protection stands on")
    void markRotated_onRevokedToken_updatesNothing() {
        String token = authService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT_IP).refreshToken();
        RefreshToken stored = refreshTokenRepository.findAllByUserId(userId).get(0);

        assertTrue(refreshTokenService.markRotated(stored.getId(), Instant.now()), "a live token can be rotated");
        Instant firstRotation = refreshTokenRepository.findById(stored.getId()).orElseThrow().getRotatedAt();
        assertTrue(refreshTokenService.markRotated(stored.getId(), Instant.now().plusSeconds(60)),
                "a rotated but live token can be rotated again (grace-window replay)");
        assertEquals(firstRotation, refreshTokenRepository.findById(stored.getId()).orElseThrow().getRotatedAt(),
                "a second rotation must not move rotated_at forward");

        authService.logout(new LogoutRequest(token));
        assertFalse(refreshTokenService.markRotated(stored.getId(), Instant.now()), "a revoked token cannot be rotated");
        assertTrue(refreshTokenRepository.findById(stored.getId()).orElseThrow().isRevoked(),
                "the failed rotation must leave the revocation in place");
    }
}
