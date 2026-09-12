package az.millikart.pbl.service;

import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.InvalidStateException;
import az.millikart.common.security.Role;
import az.millikart.common.security.UserPrincipal;
import az.millikart.pbl.domain.PaymentLinkStatus;
import az.millikart.pbl.domain.PaymentType;
import az.millikart.pbl.domain.Terminal;
import az.millikart.pbl.domain.TransactionStatus;
import az.millikart.pbl.domain.UsageType;
import az.millikart.pbl.dto.DashboardSummaryResponse;
import az.millikart.pbl.dto.DashboardSummaryResponse.CurrencyTotals;
import az.millikart.pbl.dto.DashboardSummaryResponse.DailyTotal;
import az.millikart.pbl.dto.DashboardSummaryResponse.HourlyCount;
import az.millikart.pbl.dto.DashboardSummaryResponse.LinkStatusCount;
import az.millikart.pbl.dto.DashboardSummaryResponse.PaymentLinkTotals;
import az.millikart.pbl.dto.DashboardSummaryResponse.PaymentTypeCount;
import az.millikart.pbl.dto.DashboardSummaryResponse.StatusCount;
import az.millikart.pbl.dto.DashboardSummaryResponse.TerminalTotal;
import az.millikart.pbl.dto.DashboardSummaryResponse.UsageTypeCount;
import az.millikart.pbl.dto.DashboardSummaryResponse.Window;
import az.millikart.pbl.repository.DashboardRepository;
import az.millikart.pbl.repository.TerminalRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Сводка главной страницы (P3-7). Всё, что здесь происходит после запросов, — сложение уже
// сгруппированных базой чисел и добивка пустых корзин. Сырых строк транзакций сервис не видит.
@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private static final int DEFAULT_WINDOW_DAYS = 7;

    // Потолок окна. Превышение — отказ, а не зажим: молча отдать окно, которого не просили,
    // значит снова показать цифру не за тот период и назвать её настоящей.
    private static final int MAX_WINDOW_DAYS = 92;

    private static final int TOP_TERMINALS = 5;

    // Копейки в ответе всегда двузначны. Колонки денег — numeric(19,2), больше двух знаков в них
    // не бывает, поэтому округления здесь нет — есть выравнивание: H2 возвращает SUM со scale 1,
    // PostgreSQL — с 2, и без этого клиент видел бы то «100.0», то «100.00» в зависимости от СУБД.
    private static final int MONEY_SCALE = 2;

    // Терминала с таким id не бывает. Связывается вместо списка, когда отбора по терминалам нет
    // (глобальный читатель): условие :unscoped = TRUE до него не доходит, но параметр обязан быть
    // связан, а пустой список — невалидный SQL IN ().
    private static final List<Integer> NO_TERMINAL_FILTER = List.of(Integer.MIN_VALUE);

    private final DashboardRepository dashboardRepository;
    private final TerminalRepository terminalRepository;
    private final ZoneId zone;

    public DashboardService(DashboardRepository dashboardRepository,
                            TerminalRepository terminalRepository,
                            @Value("${pbl.dashboard.zone}") String zoneId) {
        this.dashboardRepository = dashboardRepository;
        this.terminalRepository = terminalRepository;
        this.zone = ZoneId.of(zoneId);
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(Instant from, Instant to, UserPrincipal principal) {
        Role role = UserPrincipal.getRole(principal);
        String rawRole = UserPrincipal.getRawRole(principal);
        String companyId = UserPrincipal.getCompanyId(principal);

        // Правила доступа — те же, что у списка транзакций, и берутся из его же наборов:
        // сводка показывает те же строки, иначе она стала бы обходом.
        if (role == null || !PaymentLinkService.READ_ROLES.contains(role)) {
            log.warn("Access denied. Role {} is not authorized to read the dashboard.", rawRole);
            throw new InvalidStateException("Access denied: role " + rawRole + " is not authorized for this action");
        }

        Instant resolvedTo = to != null ? to : Instant.now();
        Instant resolvedFrom = from != null ? from : defaultFrom(resolvedTo);
        validateWindow(resolvedFrom, resolvedTo);

        boolean unscoped = PaymentLinkService.isGlobalReader(role);
        List<Integer> terminalIds = NO_TERMINAL_FILTER;
        if (!unscoped) {
            if (companyId == null || companyId.isBlank()) {
                // Ровно как listTransactions: пустой результат, а не отказ. У пользователя без
                // компании нет своих денег, но и запрещать ему смотреть не на что.
                log.warn("Missing companyId claim for non-admin user; empty dashboard");
                return emptySummary(resolvedFrom, resolvedTo);
            }
            terminalIds = terminalRepository.findAllByCompanyId(companyId).stream()
                    .map(Terminal::getId)
                    .toList();
            if (terminalIds.isEmpty()) {
                return emptySummary(resolvedFrom, resolvedTo);
            }
        }

        List<Object[]> buckets = dashboardRepository.aggregateByHourBucket(
                resolvedFrom, resolvedTo, unscoped, terminalIds);
        List<Object[]> byTerminal = dashboardRepository.aggregateByTerminal(
                resolvedFrom, resolvedTo, unscoped, terminalIds);
        List<Object[]> links = dashboardRepository.aggregateLinks(
                resolvedFrom, resolvedTo, unscoped, terminalIds);

        Map<String, Accumulator> perCurrency = new TreeMap<>();
        Map<TransactionStatus, Long> perStatus = new EnumMap<>(TransactionStatus.class);
        Map<DayKey, Accumulator> perDay = new LinkedHashMap<>();
        Map<Integer, Long> perHour = new TreeMap<>();

        foldBuckets(buckets, perCurrency, perStatus, perDay, perHour);

        return new DashboardSummaryResponse(
                new Window(resolvedFrom, resolvedTo, zone.getId()),
                totals(perCurrency),
                statusBreakdown(perStatus),
                dailyTotals(resolvedFrom, resolvedTo, perCurrency.keySet(), perDay),
                hourlyTotals(perHour),
                topTerminals(byTerminal),
                linkTotals(links));
    }

    // ─── окно ────────────────────────────────────────────────────────────────

    // Семь календарных суток, включая сегодняшние, а не «сейчас минус 168 часов»: на графике
    // должно быть семь целых столбиков, а не шесть с половиной и обрезок.
    private Instant defaultFrom(Instant to) {
        return to.atZone(zone).toLocalDate()
                .minusDays(DEFAULT_WINDOW_DAYS - 1L)
                .atStartOfDay(zone)
                .toInstant();
    }

    // Раскладывает часовые корзины базы по суткам и часам **пояса отчёта**.
    //
    // Сутки и час берутся из MIN(createdAt) корзины, а не из SQL: соглашение о хранении времени
    // у PostgreSQL и у H2 тестов разное (см. заголовок DashboardRepository), и любое приведение
    // средствами SQL верно на одном движке и неверно на другом. Момент же читается одинаково.
    //
    // Условие корректности: корзина не должна пересекать полночь пояса отчёта. База режет строки
    // по часам своего пояса, а границы часов совпадают у любых двух поясов со смещением, кратным
    // часу, — это верно для всех поясов, где работает портал. У пояса с получасовым смещением
    // (Индия, Иран) корзина на стыке суток разъехалась бы; такой пояс здесь не настраивают.
    private void foldBuckets(List<Object[]> buckets,
                             Map<String, Accumulator> perCurrency,
                             Map<TransactionStatus, Long> perStatus,
                             Map<DayKey, Accumulator> perDay,
                             Map<Integer, Long> perHour) {
        for (Object[] row : buckets) {
            ZonedDateTime moment = asInstant(row[0]).atZone(zone);
            String currency = (String) row[1];
            TransactionStatus status = (TransactionStatus) row[2];
            long count = asLong(row[3]);
            BigDecimal captured = asAmount(row[4]);
            BigDecimal refunded = asAmount(row[5]);

            perCurrency.computeIfAbsent(currency, key -> new Accumulator()).add(status, count, captured, refunded);
            perStatus.merge(status, count, Long::sum);
            perDay.computeIfAbsent(new DayKey(moment.toLocalDate(), currency), key -> new Accumulator())
                    .add(status, count, captured, refunded);
            perHour.merge(moment.getHour(), count, Long::sum);
        }
    }

    private void validateWindow(Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new BusinessException("'from' must not be after 'to'");
        }
        if (Duration.between(from, to).toDays() > MAX_WINDOW_DAYS) {
            throw new BusinessException("Window must not exceed " + MAX_WINDOW_DAYS + " days");
        }
    }

    // ─── свёртка ─────────────────────────────────────────────────────────────

    private List<CurrencyTotals> totals(Map<String, Accumulator> perCurrency) {
        return perCurrency.entrySet().stream()
                .map(entry -> {
                    Accumulator acc = entry.getValue();
                    BigDecimal average = acc.paidCount > 0
                            ? acc.paidAmount.divide(BigDecimal.valueOf(acc.paidCount), MONEY_SCALE, RoundingMode.HALF_UP)
                            : money(BigDecimal.ZERO);
                    return new CurrencyTotals(entry.getKey(), acc.transactionCount, acc.paidCount,
                            acc.failedCount, acc.pendingCount, acc.refundedCount,
                            money(acc.paidAmount), money(acc.refundedAmount), money(acc.net()), average);
                })
                .toList();
    }

    // Все шесть статусов, включая нулевые: отсутствующая доля читается как «такого не бывает».
    private List<StatusCount> statusBreakdown(Map<TransactionStatus, Long> perStatus) {
        List<StatusCount> result = new ArrayList<>();
        for (TransactionStatus status : TransactionStatus.values()) {
            result.add(new StatusCount(status.name(), perStatus.getOrDefault(status, 0L)));
        }
        return result;
    }

    // Сутки без операций присутствуют с нулями: дыра в графике читается как «не работали»,
    // а пропуск дня — как «данных нет», и это разные утверждения.
    private List<DailyTotal> dailyTotals(Instant from, Instant to,
                                         Iterable<String> currencies, Map<DayKey, Accumulator> perDay) {
        List<DailyTotal> result = new ArrayList<>();
        LocalDate last = to.atZone(zone).toLocalDate();
        for (String currency : currencies) {
            for (LocalDate date = from.atZone(zone).toLocalDate();
                 !date.isAfter(last);
                 date = date.plusDays(1)) {
                Accumulator acc = perDay.get(new DayKey(date, currency));
                result.add(new DailyTotal(
                        date,
                        currency,
                        acc == null ? money(BigDecimal.ZERO) : money(acc.net()),
                        acc == null ? 0L : acc.transactionCount));
            }
        }
        return result;
    }

    private List<HourlyCount> hourlyTotals(Map<Integer, Long> counts) {
        List<HourlyCount> result = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            result.add(new HourlyCount(hour, counts.getOrDefault(hour, 0L)));
        }
        return result;
    }

    // Топ пять внутри каждой валюты: «первые пять по сумме» поверх разных валют было бы
    // сравнением манатов с евро.
    private List<TerminalTotal> topTerminals(List<Object[]> rows) {
        Map<TerminalKey, Accumulator> perTerminal = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Integer terminalId = (Integer) row[0];
            String currency = (String) row[1];
            perTerminal.computeIfAbsent(new TerminalKey(terminalId, currency), key -> new Accumulator())
                    .add((TransactionStatus) row[2], asLong(row[3]), asAmount(row[4]), asAmount(row[5]));
        }

        Map<String, List<Map.Entry<TerminalKey, Accumulator>>> byCurrency = perTerminal.entrySet().stream()
                .collect(Collectors.groupingBy(entry -> entry.getKey().currency(), TreeMap::new, Collectors.toList()));

        List<TerminalTotal> result = new ArrayList<>();
        List<Integer> needed = new ArrayList<>();
        List<Map.Entry<TerminalKey, Accumulator>> selected = new ArrayList<>();
        for (List<Map.Entry<TerminalKey, Accumulator>> entries : byCurrency.values()) {
            entries.stream()
                    .sorted(Comparator.<Map.Entry<TerminalKey, Accumulator>, BigDecimal>comparing(
                            entry -> entry.getValue().net()).reversed())
                    .limit(TOP_TERMINALS)
                    .forEach(entry -> {
                        selected.add(entry);
                        needed.add(entry.getKey().terminalId());
                    });
        }

        // Логин и имя добираются одним запросом по готовому топу, не построчно. Терминала может
        // уже не быть — тогда подписи нет, и выдумывать её («TRM-…», «Default Terminal») нельзя.
        Map<Integer, Terminal> terminals = terminalRepository.findAllById(needed).stream()
                .filter(terminal -> terminal.getId() != null)
                .collect(Collectors.toMap(Terminal::getId, terminal -> terminal, (first, second) -> first));

        for (Map.Entry<TerminalKey, Accumulator> entry : selected) {
            TerminalKey key = entry.getKey();
            Terminal terminal = terminals.get(key.terminalId());
            result.add(new TerminalTotal(key.currency(), key.terminalId(),
                    terminal != null ? terminal.getLogin() : null,
                    terminal != null ? terminal.getName() : null,
                    money(entry.getValue().net()), entry.getValue().transactionCount));
        }
        return result;
    }

    private PaymentLinkTotals linkTotals(List<Object[]> rows) {
        Map<PaymentLinkStatus, Long> byStatus = new EnumMap<>(PaymentLinkStatus.class);
        Map<PaymentType, Long> byType = new EnumMap<>(PaymentType.class);
        Map<UsageType, Long> byUsage = new EnumMap<>(UsageType.class);
        long total = 0;

        for (Object[] row : rows) {
            long count = asLong(row[3]);
            total += count;
            byStatus.merge((PaymentLinkStatus) row[0], count, Long::sum);
            byType.merge((PaymentType) row[1], count, Long::sum);
            byUsage.merge((UsageType) row[2], count, Long::sum);
        }

        List<PaymentTypeCount> types = new ArrayList<>();
        for (PaymentType value : PaymentType.values()) {
            types.add(new PaymentTypeCount(value.name(), byType.getOrDefault(value, 0L)));
        }
        List<UsageTypeCount> usages = new ArrayList<>();
        for (UsageType value : UsageType.values()) {
            usages.add(new UsageTypeCount(value.name(), byUsage.getOrDefault(value, 0L)));
        }
        List<LinkStatusCount> statuses = new ArrayList<>();
        for (PaymentLinkStatus value : PaymentLinkStatus.values()) {
            statuses.add(new LinkStatusCount(value.name(), byStatus.getOrDefault(value, 0L)));
        }
        return new PaymentLinkTotals(total, types, usages, statuses);
    }

    // Нули, а не 403 и не пустое тело: экран должен нарисоваться и честно показать, что операций
    // нет. Форма ответа та же, что у непустой сводки.
    private DashboardSummaryResponse emptySummary(Instant from, Instant to) {
        return new DashboardSummaryResponse(
                new Window(from, to, zone.getId()),
                List.of(),
                statusBreakdown(Map.of()),
                List.of(),
                hourlyTotals(Map.of()),
                List.of(),
                linkTotals(List.of()));
    }

    // ─── чтение строк группировки ────────────────────────────────────────────

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static long asLong(Object value) {
        return ((Number) value).longValue();
    }

    // sum() по группе без строк не бывает — группа существует только со строками; null здесь
    // означал бы, что запрос изменили, и ноль честнее падения.
    private static BigDecimal asAmount(Object value) {
        return value == null ? BigDecimal.ZERO : (BigDecimal) value;
    }

    // MIN(createdAt) читается тем же преобразованием, что и любое чтение колонки, поэтому
    // момент возвращается верным независимо от того, в каком поясе Hibernate его положил.
    private static Instant asInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalStateException("Unexpected timestamp type from the grouping query: "
                + Objects.requireNonNull(value).getClass());
    }

    private record DayKey(LocalDate date, String currency) {
    }

    private record TerminalKey(Integer terminalId, String currency) {
    }

    // Накопитель одной корзины. Деньги складываются только по PAID_STATUSES: суммы у FAILED
    // база тоже посчитала, но платежом они не были.
    private static final class Accumulator {
        private long transactionCount;
        private long paidCount;
        private long failedCount;
        private long pendingCount;
        private long refundedCount;
        private BigDecimal paidAmount = BigDecimal.ZERO;
        private BigDecimal refundedAmount = BigDecimal.ZERO;

        private void add(TransactionStatus status, long count, BigDecimal captured, BigDecimal refunded) {
            transactionCount += count;
            if (TransactionStatus.PAID_STATUSES.contains(status)) {
                paidCount += count;
                paidAmount = paidAmount.add(captured);
                refundedAmount = refundedAmount.add(refunded);
            }
            if (status == TransactionStatus.FAILED) {
                failedCount += count;
            }
            if (status == TransactionStatus.PENDING || status == TransactionStatus.AUTHORIZED) {
                pendingCount += count;
            }
            if (status == TransactionStatus.REFUNDED || status == TransactionStatus.PARTIALLY_REFUNDED) {
                refundedCount += count;
            }
        }

        private BigDecimal net() {
            return paidAmount.subtract(refundedAmount);
        }
    }
}
