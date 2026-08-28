package az.millikart.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;

// Единственное место, которое решает, каков адрес клиента. X-Forwarded-For и X-Real-IP напрямую
// не читать нигде: это обычные заголовки, их пишет кто угодно, а значение становится ключом
// лимитера входа и строкой в журнале аудита — так и сломалось в P2-10 (брали X-Forwarded-For[0]).
// Пустой список доверенных прокси означает «заголовкам не верить» — см. TrustedProxies.
public final class ClientIp {

    private static final String X_REAL_IP = "X-Real-IP";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    // Самый длинный текстовый IPv6 — 45 символов; длиннее адресом быть не может. Значение идёт в
    // ключ кэша LoginRateLimiter, и 4 КБ заголовка не должны его раздувать.
    private static final int MAX_TEXT_LENGTH = 45;

    private ClientIp() {
    }

    // Пир не в списке доверенных — заголовки игнорируются целиком, ответ — адрес пира. Иначе
    // X-Real-IP (nginx перезаписывает его сам, клиент на него не влияет), иначе ПОСЛЕДНИЙ элемент
    // X-Forwarded-For: его дописывает наш прокси, всё перед ним прислал клиент. Не первый.
    public static String resolve(HttpServletRequest request, Set<String> trustedProxies) {
        String peer = request.getRemoteAddr();
        if (!isTrusted(peer, trustedProxies)) {
            return peer;
        }

        String realIp = literal(request.getHeader(X_REAL_IP));
        if (realIp != null) {
            return realIp;
        }

        String forwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (forwardedFor != null) {
            int lastSeparator = forwardedFor.lastIndexOf(',');
            String appendedByOurProxy = literal(
                    lastSeparator < 0 ? forwardedFor : forwardedFor.substring(lastSeparator + 1));
            if (appendedByOurProxy != null) {
                return appendedByOurProxy;
            }
        }

        return peer;
    }

    // Сравнение в канонической форме: ::1 в конфиге и 0:0:0:0:0:0:0:1 у пира — один и тот же хост.
    private static boolean isTrusted(String peer, Set<String> trustedProxies) {
        if (trustedProxies == null || trustedProxies.isEmpty()) {
            return false;
        }
        String canonicalPeer = canonical(peer);
        if (canonicalPeer == null) {
            return false;
        }
        for (String trusted : trustedProxies) {
            if (canonicalPeer.equals(canonical(trusted))) {
                return true;
            }
        }
        return false;
    }

    private static String literal(String raw) {
        return parse(raw) == null ? null : raw.trim();
    }

    private static String canonical(String raw) {
        InetAddress address = parse(raw);
        return address == null ? null : address.getHostAddress();
    }

    private static InetAddress parse(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty() || value.length() > MAX_TEXT_LENGTH || !looksLikeLiteral(value)) {
            return null;
        }
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    // Сторож, не пускающий getByName к резолверу: строка из цифр и точек идёт только путём
    // IPv4-литерала, с двоеточием — только IPv6, оба падают на мусоре без обращения к DNS.
    // Пропустишь что-то ещё (хоть "a.b") — подделанный заголовок превратит каждую попытку входа
    // в DNS-запрос.
    private static boolean looksLikeLiteral(String value) {
        boolean ipv6 = value.indexOf(':') >= 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= '0' && c <= '9') || c == '.'
                    || (ipv6 && (c == ':' || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')));
            if (!allowed) {
                return false;
            }
        }
        return true;
    }
}
