package az.millikart.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

// P3-Auth / P2-10: правила ClientIp — та самая разница между лимитом запросов и лимитом, который
// обходится одним заголовком. Без Spring: MockHttpServletRequest для этого кода — полноценный
// запрос, и весь смысл класса в том, что он больше ни от чего не зависит.
class ClientIpTest {

    private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "::1");

    // X-Real-IP пишет сам nginx, из $remote_addr — он побеждает всё остальное.
    @Test
    @DisplayName("1. trusted peer, X-Real-IP present → X-Real-IP")
    void trustedPeer_xRealIpWins() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "203.0.113.7");
        request.addHeader("X-Forwarded-For", "9.9.9.9, 203.0.113.7");

        assertEquals("203.0.113.7", ClientIp.resolve(request, LOOPBACK));
    }

    // Сердце P2-10. $proxy_add_x_forwarded_for дописывает настоящего пира в КОНЕЦ списка и
    // оставляет впереди то, что прислал клиент, поэтому первый элемент (здесь 9.9.9.9) — ровно
    // тот, который выбрал атакующий.
    @Test
    @DisplayName("2. trusted peer, no X-Real-IP → the LAST element of X-Forwarded-For")
    void trustedPeer_forwardedForTakesTheLastElement() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "9.9.9.9, 203.0.113.7");

        assertEquals("203.0.113.7", ClientIp.resolve(request, LOOPBACK));
    }

    @Test
    @DisplayName("2b. a single-element X-Forwarded-For is that element")
    void trustedPeer_forwardedForWithOneElement() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", " 203.0.113.7 ");

        assertEquals("203.0.113.7", ClientIp.resolve(request, LOOPBACK));
    }

    // Запрос, пришедший не через наш прокси, не несёт ничего, чему есть причина верить.
    @Test
    @DisplayName("3. untrusted peer with forged headers → the peer address")
    void untrustedPeer_headersIgnored() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.50");
        request.addHeader("X-Real-IP", "9.9.9.9");
        request.addHeader("X-Forwarded-For", "9.9.9.9, 8.8.8.8");

        assertEquals("203.0.113.50", ClientIp.resolve(request, LOOPBACK));
    }

    @Test
    @DisplayName("4. no headers at all → the peer address")
    void noHeaders_peerAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        assertEquals("127.0.0.1", ClientIp.resolve(request, LOOPBACK));
    }

    // Значение становится ключом кэша в лимитере логина, поэтому заголовок, который не является
    // адресом, туда попадать не должен. Откат к адресу пира — безопасный ответ, а не ошибка.
    @Test
    @DisplayName("5. implausible X-Real-IP (empty, blanks, 500 chars, not an address) → the peer address")
    void implausibleHeader_peerAddress() {
        assertEquals("127.0.0.1", resolveWithRealIp(""));
        assertEquals("127.0.0.1", resolveWithRealIp("   "));
        assertEquals("127.0.0.1", resolveWithRealIp("1".repeat(500)));
        assertEquals("127.0.0.1", resolveWithRealIp("not-an-address"));
        assertEquals("127.0.0.1", resolveWithRealIp("1.2.3.4.5"));
        assertEquals("127.0.0.1", resolveWithRealIp("203.0.113.7 evil"));
    }

    // Мусор в X-Real-IP не отключает запасной путь — X-Forwarded-For всё равно читается.
    @Test
    @DisplayName("5b. implausible X-Real-IP but a usable X-Forwarded-For → the last forwarded element")
    void implausibleRealIp_fallsThroughToForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "   ");
        request.addHeader("X-Forwarded-For", "9.9.9.9, 203.0.113.7");

        assertEquals("203.0.113.7", ClientIp.resolve(request, LOOPBACK));
    }

    // Контейнер может сообщить loopback в любом написании, а конфигурация написана в одном.
    // Обе стороны сравниваются канонически, поэтому ::1 в списке совпадает с 0:0:0:0:0:0:0:1
    // на проводе — иначе сервис, доступный по IPv6, молча перестал бы верить своему прокси.
    @Test
    @DisplayName("6. ::1 in the trusted list works like 127.0.0.1, in either spelling")
    void ipv6Loopback_isTrustedTheSameWay() {
        MockHttpServletRequest shortForm = new MockHttpServletRequest();
        shortForm.setRemoteAddr("::1");
        shortForm.addHeader("X-Real-IP", "203.0.113.7");
        assertEquals("203.0.113.7", ClientIp.resolve(shortForm, LOOPBACK));

        MockHttpServletRequest longForm = new MockHttpServletRequest();
        longForm.setRemoteAddr("0:0:0:0:0:0:0:1");
        longForm.addHeader("X-Real-IP", "203.0.113.7");
        assertEquals("203.0.113.7", ClientIp.resolve(longForm, LOOPBACK));
    }

    // Верная настройка для сервиса, перед которым ничего нет: не верить ни одному заголовку.
    @Test
    @DisplayName("7. empty trusted list → headers are never believed")
    void emptyTrustedList_headersNeverBelieved() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "9.9.9.9");
        request.addHeader("X-Forwarded-For", "9.9.9.9, 8.8.8.8");

        assertEquals("127.0.0.1", ClientIp.resolve(request, Set.of()));
        assertEquals("127.0.0.1", ClientIp.resolve(request, null));
    }

    private static String resolveWithRealIp(String headerValue) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", headerValue);
        return ClientIp.resolve(request, LOOPBACK);
    }
}
