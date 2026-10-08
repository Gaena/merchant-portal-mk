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
import az.millikart.pbl.dto.DashboardSummaryResponse.LinkFunnel;
import az.millikart.pbl.dto.DashboardSummaryResponse.LinkStatusCount;
import az.millikart.pbl.dto.DashboardSummaryResponse.PaymentLinkTotals;
import az.millikart.pbl.dto.DashboardSummaryResponse.PaymentTypeCount;
import az.millikart.pbl.dto.DashboardSummaryResponse.StatusCount;
import az.millikart.pbl.dto.DashboardSummaryResponse.TerminalTotal;
import az.millikart.pbl.dto.DashboardSummaryResponse.TimeToPay;
import az.millikart.pbl.dto.DashboardSummaryResponse.TimeToPayBucket;
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
import java.util.Collections;
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

// Статистика оплат по ссылкам — вкладка «Статистика» страницы Pay by Link (P3-7, Р-91).
// После запросов здесь только сложение сгруппированных базой чисел и добивка пустых корзин.
@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private static final int DEFAULT_WINDOW_DAYS = 7;

    // Превышение — отказ, а не зажим: иначе цифра не за тот период выдаётся за настоящую.
    private static final int MAX_WINDOW_DAYS = 92;

    private static final int TOP_TERMINALS = 5;

    // Выравнивание, а не округление: колонки numeric(19,2), но SUM у H2 приходит со scale 1, у
    // PostgreSQL — 2, и клиент видел бы то «100.0», то «100.00».
    private static final int MONEY_SCALE = 2;

    // Несуществующий терминал для глобального читателя: до него :unscoped = TRUE не доходит, но
    // параметр обязан быть связан, а IN () — невалидный SQL.
    private static final List<Integer> NO_TERMINAL_FILTER = List.of(Integer.MIN_VALUE);

    private final DashboardRepository dashboardRepository;
    private final TerminalRepository terminalRepository;
    private final TerminalScope terminalScope;
    private final ZoneId zone;

    public DashboardService(DashboardRepository dashboardRepository,
                            TerminalRepository terminalRepository,
                            TerminalScope terminalScope,
                            @Value("${pbl.dashboard.zone}") String zoneId) {
        this.dashboardRepository = dashboardRepository;
        this.terminalRepository = terminalRepository;
        this.terminalScope = terminalScope;
        this.zone = ZoneId.of(zoneId);
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(Instant from, Instant to, UserPrincipal principal) {
        Role role = UserPrincipal.getRole(principal);
        String rawRole = UserPrincipal.getRawRole(principal);
        String companyId = UserPrincipal.getCompanyId(principal);

        // Наборы ролей — из PaymentLinkService: свои правила сделали бы сводку обходом.
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
                // Как listTransactions: без компании — пустой результат, а не отказ.
                log.warn("Missing companyId claim for non-admin user; empty dashboard");
                return emptySummary(resolvedFrom, resolvedTo);
            }
            // Сотруднику — только назначенные терминалы (Р-131).
            terminalIds = terminalScope.companyTerminalIds(principal);
            if (terminalIds.isEmpty()) {
                return emptySummary(resolvedFrom, resolvedTo);
            }
        }

        List<Object[]> buckets = dashboardRepository.aggregateByHourBucket(
                resolvedFrom, resolvedTo, unscoped, terminalIds);
        List<Object[]> refundBuckets = dashboardRepository.refundsByHourBucket(
                resolvedFrom, resolvedTo, unscoped, terminalIds);
        List<Object[]> byTerminal = dashboardRepository.aggregateByTerminal(
                resolvedFrom, resolvedTo, unscoped, terminalIds);
        List<Object[]> refundsByTerminal = dashboardRepository.refundsByTerminal(
                resolvedFrom, resolvedTo, unscoped, terminalIds);
        List<Object[]> links = dashboardRepository.aggregateLinks(
                resolvedFrom, resolvedTo, unscoped, terminalIds);
        List<Object[]> funnel = dashboardRepository.linkFunnel(
                resolvedFrom, resolvedTo, unscoped, terminalIds, TransactionStatus.SLOT_OCCUPYING_STATUSES);
        List<Object[]> paidTimes = dashboardRepository.paidLinkTimes(
                resolvedFrom, resolvedTo, unscoped, terminalIds, UsageType.SINGLE,
                TransactionStatus.SLOT_OCCUPYING_STATUSES);

        Map<String, Accumulator> perCurrency = new TreeMap<>();
        Map<TransactionStatus, Long> perStatus = new EnumMap<>(TransactionStatus.class);
        Map<DayKey, Accumulator> perDay = new LinkedHashMap<>();
        Map<Integer, Long> perHour = new TreeMap<>();

        foldBuckets(buckets, perCurrency, perStatus, perDay, perHour);
        foldRefunds(refundBuckets, perCurrency, perDay);

        return new DashboardSummaryResponse(
                new Window(resolvedFrom, resolvedTo, zone.getId()),
                totals(perCurrency),
                statusBreakdown(perStatus),
                dailyTotals(resolvedFrom, resolvedTo, perCurrency.keySet(), perDay),
                hourlyTotals(perHour),
                topTerminals(byTerminal, refundsByTerminal),
                linkTotals(links),
                linkFunnel(funnel),
                timeToPay(paidTimes));
    }

    // Семь календарных суток, включая сегодня, а не «сейчас минус 168 часов»: на графике — семь
    // целых столбиков, без обрезка.
    private Instant defaultFrom(Instant to) {
        return to.atZone(zone).toLocalDate()
                .minusDays(DEFAULT_WINDOW_DAYS - 1L)
                .atStartOfDay(zone)
                .toInstant();
    }

    // Сутки и час корзины — в поясе отчёта из MIN(createdAt), а не в SQL (см. DashboardRepository).
    // Верно, пока часовая корзина не пересекает полночь пояса отчёта: так у всех поясов со смещением,
    // кратным часу. Пояс с получасовым смещением (Индия, Иран) здесь не настраивать.
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

            perCurrency.computeIfAbsent(currency, key -> new Accumulator()).add(status, count, captured);
            perStatus.merge(status, count, Long::sum);
            perDay.computeIfAbsent(new DayKey(moment.toLocalDate(), currency), key -> new Accumulator())
                    .add(status, count, captured);
            perHour.merge(moment.getHour(), count, Long::sum);
        }
    }

    // Возвраты — в сутки и валюту возврата (Р-89), в счётчики операций и статусов не входят: возврат —
    // движение денег по старой операции. Валюта с одними возвратами — строка с отрицательной выручкой.
    private void foldRefunds(List<Object[]> refunds,
                             Map<String, Accumulator> perCurrency,
                             Map<DayKey, Accumulator> perDay) {
        for (Object[] row : refunds) {
            ZonedDateTime moment = asInstant(row[0]).atZone(zone);
            String currency = (String) row[1];
            BigDecimal amount = asAmount(row[2]);
            perCurrency.computeIfAbsent(currency, key -> new Accumulator()).addRefund(amount);
            perDay.computeIfAbsent(new DayKey(moment.toLocalDate(), currency), key -> new Accumulator())
                    .addRefund(amount);
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

    // Все статусы, включая нулевые: отсутствующая доля читается как «такого не бывает».
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

    // Топ — внутри каждой валюты, иначе сравнивались бы манаты с евро. Выручка терминала — оплаты
    // окна минус возвраты окна (Р-89).
    private List<TerminalTotal> topTerminals(List<Object[]> rows, List<Object[]> refunds) {
        Map<TerminalKey, Accumulator> perTerminal = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Integer terminalId = (Integer) row[0];
            String currency = (String) row[1];
            perTerminal.computeIfAbsent(new TerminalKey(terminalId, currency), key -> new Accumulator())
                    .add((TransactionStatus) row[2], asLong(row[3]), asAmount(row[4]));
        }
        for (Object[] row : refunds) {
            perTerminal.computeIfAbsent(new TerminalKey((Integer) row[0], (String) row[1]), key -> new Accumulator())
                    .addRefund(asAmount(row[2]));
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

        // Одним запросом по готовому топу, не построчно. Нет терминала — нет подписи, не выдумывать (Р-48).
        Map<Integer, Terminal> terminals = terminalRepository.findAllById(needed).stream()
                .filter(terminal -> terminal.getId() != null)
                .collect(Collectors.toMap(Terminal::getId, terminal -> terminal, (first, second) -> first));

        for (Map.Entry<TerminalKey, Accumulator> entry : selected) {
            TerminalKey key = entry.getKey();
            Terminal terminal = terminals.get(key.terminalId());
            result.add(new TerminalTotal(key.currency(), key.terminalId(),
                    terminal != null ? terminal.getTerminalRid() : null,
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

    private LinkFunnel linkFunnel(List<Object[]> rows) {
        if (rows.isEmpty()) {
            return new LinkFunnel(0, 0, 0, 0);
        }
        Object[] row = rows.get(0);
        return new LinkFunnel(countOrZero(row[0]), countOrZero(row[1]), countOrZero(row[2]), countOrZero(row[3]));
    }

    // Медиана, а не среднее: одна ссылка, оплаченная через месяц, сдвинула бы среднее на дни.
    private TimeToPay timeToPay(List<Object[]> rows) {
        List<Long> seconds = new ArrayList<>(rows.size());
        Map<TimeToPayRange, Long> perRange = new EnumMap<>(TimeToPayRange.class);
        for (Object[] row : rows) {
            Duration elapsed = Duration.between(asInstant(row[0]), asInstant(row[1]));
            // Попытка раньше ссылки невозможна; отрицательное — расхождение часов, а не оплата «до создания».
            long value = Math.max(0L, elapsed.toSeconds());
            seconds.add(value);
            perRange.merge(TimeToPayRange.of(value), 1L, Long::sum);
        }
        Collections.sort(seconds);
        Long median = null;
        int size = seconds.size();
        if (size > 0) {
            median = size % 2 == 1
                    ? seconds.get(size / 2)
                    : (seconds.get(size / 2 - 1) + seconds.get(size / 2)) / 2;
        }
        List<TimeToPayBucket> buckets = new ArrayList<>();
        for (TimeToPayRange range : TimeToPayRange.values()) {
            buckets.add(new TimeToPayBucket(range.name(), perRange.getOrDefault(range, 0L)));
        }
        return new TimeToPay(size, median, buckets);
    }

    // Нули в форме непустой сводки, а не 403 и не пустое тело: экран рисуется и показывает, что операций нет.
    private DashboardSummaryResponse emptySummary(Instant from, Instant to) {
        return new DashboardSummaryResponse(
                new Window(from, to, zone.getId()),
                List.of(),
                statusBreakdown(Map.of()),
                List.of(),
                hourlyTotals(Map.of()),
                List.of(),
                linkTotals(List.of()),
                linkFunnel(List.of()),
                timeToPay(List.of()));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static long asLong(Object value) {
        return ((Number) value).longValue();
    }

    // SUM без строк — null, а не ноль: в окне нет ни одной ссылки.
    private static long countOrZero(Object value) {
        return value == null ? 0L : asLong(value);
    }

    // null от SUM по непустой группе значит, что запрос изменили; ноль честнее падения.
    private static BigDecimal asAmount(Object value) {
        return value == null ? BigDecimal.ZERO : (BigDecimal) value;
    }

    // MIN(createdAt) читается тем же преобразованием, что и колонка: момент верен при любом поясе,
    // в котором Hibernate его положил.
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

    // Интервалы времени до оплаты (Р-128): верхняя граница не входит, последний — без границы.
    private enum TimeToPayRange {
        UP_TO_1_HOUR(Duration.ofHours(1)),
        UP_TO_1_DAY(Duration.ofDays(1)),
        UP_TO_7_DAYS(Duration.ofDays(7)),
        OVER_7_DAYS(null);

        private final Duration upperBound;

        TimeToPayRange(Duration upperBound) {
            this.upperBound = upperBound;
        }

        private static TimeToPayRange of(long seconds) {
            for (TimeToPayRange range : values()) {
                if (range.upperBound == null || seconds < range.upperBound.toSeconds()) {
                    return range;
                }
            }
            return OVER_7_DAYS;
        }
    }

    private record TerminalKey(Integer terminalId, String currency) {
    }

    // Оплаты — только по PAID_STATUSES: суммы FAILED база тоже посчитала. Возвраты — отдельно, по своему
    // времени (Р-89); refundedCount — платежи окна, возвращённые сейчас, а не деньги.
    private static final class Accumulator {
        private long transactionCount;
        private long paidCount;
        private long failedCount;
        private long pendingCount;
        private long refundedCount;
        private BigDecimal paidAmount = BigDecimal.ZERO;
        private BigDecimal refundedAmount = BigDecimal.ZERO;

        private void add(TransactionStatus status, long count, BigDecimal captured) {
            transactionCount += count;
            if (TransactionStatus.PAID_STATUSES.contains(status)) {
                paidCount += count;
                paidAmount = paidAmount.add(captured);
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

        private void addRefund(BigDecimal amount) {
            refundedAmount = refundedAmount.add(amount);
        }

        private BigDecimal net() {
            return paidAmount.subtract(refundedAmount);
        }
    }
}
