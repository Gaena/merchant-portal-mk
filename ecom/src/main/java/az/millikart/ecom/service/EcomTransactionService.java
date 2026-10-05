package az.millikart.ecom.service;

import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.ResourceNotFoundException;
import az.millikart.common.money.OperationActions;
import az.millikart.common.security.UserPrincipal;
import az.millikart.ecom.config.TxpgProperties;
import az.millikart.ecom.domain.ProviderLogin;
import az.millikart.ecom.domain.ProviderTerminal;
import az.millikart.ecom.dto.CursorPage;
import az.millikart.ecom.dto.EcomDashboardResponse;
import az.millikart.ecom.dto.EcomStatsResponse;
import az.millikart.ecom.dto.EcomTerminalResponse;
import az.millikart.ecom.dto.EcomTransactionFilter;
import az.millikart.ecom.dto.EcomTransactionResponse;
import az.millikart.ecom.repository.PortalPaymentsRepository;
import az.millikart.ecom.repository.ProviderLoginRepository;
import az.millikart.ecom.repository.ProviderTerminalRepository;
import az.millikart.ecom.repository.TxpgTransactionRepository;
import az.millikart.ecom.service.EcomStatusResolver.EcomStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Пустой скоуп — пустая выписка без похода в шлюз, а не чужие платежи (Р-97, AGENTS.md §10).
@Service
public class EcomTransactionService {

    private static final Logger log = LoggerFactory.getLogger(EcomTransactionService.class);

    private static final int DEFAULT_PAGE_SIZE = 25;

    private final TxpgTransactionRepository repository;
    private final EcomScopeService scope;
    private final ProviderTerminalRepository providerTerminals;
    private final ProviderLoginRepository providerLogins;
    private final TxpgProperties properties;
    private final PortalPaymentsRepository portal;
    private final ProviderOrderAttemptService attempts;

    public EcomTransactionService(TxpgTransactionRepository repository,
                                  EcomScopeService scope,
                                  ProviderTerminalRepository providerTerminals,
                                  ProviderLoginRepository providerLogins,
                                  TxpgProperties properties,
                                  PortalPaymentsRepository portal,
                                  ProviderOrderAttemptService attempts) {
        this.repository = repository;
        this.scope = scope;
        this.providerTerminals = providerTerminals;
        this.providerLogins = providerLogins;
        this.properties = properties;
        this.portal = portal;
        this.attempts = attempts;
    }

    public CursorPage<EcomTransactionResponse> list(Instant dateFrom, Instant dateTo, List<String> merchantRids,
                                                    BigDecimal minAmount, BigDecimal maxAmount, String query,
                                                    String status, String paymentType,
                                                    String cursor, Integer size, UserPrincipal principal) {
        // Незнакомое значение фильтра — 400 до всякого похода в шлюз, а не молча пустой фильтр.
        EcomStatus wantedStatus = parseStatus(status);
        EcomPaymentType type = parsePaymentType(paymentType);
        EcomScope scoped = scope.scopeFor(principal);
        List<String> rids = narrow(scoped.merchantRids(), merchantRids);
        if (rids.isEmpty()) {
            log.info("No merchants in scope for this request; returning an empty statement");
            return new CursorPage<>(List.of(), null);
        }
        requireWindow(dateFrom, dateTo);
        int pageSize = pageSize(size);
        EcomTransactionFilter filter = new EcomTransactionFilter(
                rids, dateFrom, dateTo, minAmount, maxAmount, blankToNull(query), type);
        Long before = decodeCursor(cursor);

        long started = System.nanoTime();
        CursorPage<EcomTransactionResponse> page = wantedStatus != null
                ? pageOfStatus(filter, before, pageSize, wantedStatus)
                : plainPage(filter, before, pageSize);
        log.info("Statement page: merchants={}, period=[{}, {}), status={}, type={}, orders={}, more={}, took {} ms",
                rids.size(), dateFrom, dateTo, wantedStatus, type, page.content().size(), page.nextCursor() != null,
                elapsedMillis(started));
        return page;
    }

    private CursorPage<EcomTransactionResponse> plainPage(EcomTransactionFilter filter, Long before, int pageSize) {
        List<Long> orderIds = repository.findOrderIds(filter, before, pageSize + 1);
        boolean hasMore = orderIds.size() > pageSize;
        List<Long> pageIds = hasMore ? orderIds.subList(0, pageSize) : orderIds;

        List<EcomTransactionResponse> orders =
                EcomOrderAssembler.assemble(repository.findRows(pageIds, filter.merchantRids(), filter.dateFrom()));
        // Курсор — последний номер страницы, а не последней собранной строки: заказ, пропавший
        // между двумя запросами, не должен сдвинуть следующую страницу.
        String nextCursor = hasMore ? encodeCursor(pageIds.get(pageIds.size() - 1)) : null;
        return new CursorPage<>(orders, nextCursor);
    }

    // Статус считается в Java по операциям заказа, второго набора правил в SQL не заводить (Р-87).
    // Просмотр ограничен status-scan-limit: страница бывает короче размера или пустой, но с курсором
    // последнего просмотренного заказа. Курсор null — просмотрен весь период.
    private CursorPage<EcomTransactionResponse> pageOfStatus(EcomTransactionFilter filter, Long before,
                                                             int pageSize, EcomStatus wanted) {
        int limit = Math.max(properties.getStatusScanLimit(), pageSize);
        List<EcomTransactionResponse> matched = new ArrayList<>();
        Long position = before;
        int scanned = 0;
        while (true) {
            int batch = Math.min(properties.getMaxPageSize(), limit - scanned);
            List<Long> ids = repository.findOrderIds(filter, position, batch + 1);
            boolean more = ids.size() > batch;
            List<Long> batchIds = more ? ids.subList(0, batch) : ids;
            if (batchIds.isEmpty()) {
                return new CursorPage<>(matched, null);
            }
            Map<String, EcomTransactionResponse> assembled = EcomOrderAssembler
                    .assemble(repository.findRows(batchIds, filter.merchantRids(), filter.dateFrom())).stream()
                    .collect(Collectors.toMap(EcomTransactionResponse::orderId, Function.identity()));
            for (int i = 0; i < batchIds.size(); i++) {
                long orderId = batchIds.get(i);
                position = orderId;
                scanned++;
                EcomTransactionResponse order = assembled.get(Long.toString(orderId));
                if (order == null || !wanted.name().equals(order.status())) {
                    continue;
                }
                matched.add(order);
                if (matched.size() == pageSize) {
                    boolean anythingLeft = i < batchIds.size() - 1 || more;
                    return new CursorPage<>(matched, anythingLeft ? encodeCursor(orderId) : null);
                }
            }
            if (!more) {
                return new CursorPage<>(matched, null);
            }
            if (scanned >= limit) {
                log.info("Status filter {} scanned {} orders and found {}; handing back a cursor to continue",
                        wanted, scanned, matched.size());
                return new CursorPage<>(matched, encodeCursor(position));
            }
        }
    }

    // Фильтра статуса нет: итоги и так разложены по статусам.
    public EcomStatsResponse stats(Instant dateFrom, Instant dateTo, List<String> merchantRids,
                                   String paymentType, UserPrincipal principal) {
        EcomPaymentType type = parsePaymentType(paymentType);
        EcomScope scoped = scope.scopeFor(principal);
        List<String> rids = narrow(scoped.merchantRids(), merchantRids);
        EcomStatsAccumulator accumulator = new EcomStatsAccumulator();
        if (rids.isEmpty()) {
            return accumulator.result();
        }
        requireWindow(dateFrom, dateTo);
        long started = System.nanoTime();
        repository.streamPeriodRows(
                new EcomTransactionFilter(rids, dateFrom, dateTo, null, null, null, type), accumulator);
        EcomStatsResponse result = accumulator.result();
        log.info("Statement totals: merchants={}, period=[{}, {}), type={}, orders={}, took {} ms",
                rids.size(), dateFrom, dateTo, type, result.orderCount(), elapsedMillis(started));
        return result;
    }

    // Те же заказы и правила денег, что в итогах выписки, одним проходом: главная открывается чаще
    // выписки, и второго запроса к боевой базе шлюза на неё быть не должно (Р-91).
    public EcomDashboardResponse dashboard(Instant dateFrom, Instant dateTo, UserPrincipal principal) {
        EcomScope scoped = scope.scopeFor(principal);
        requireWindow(dateFrom, dateTo);
        EcomDashboardAccumulator accumulator = new EcomDashboardAccumulator(properties.getZone());
        if (!scoped.merchantRids().isEmpty()) {
            long started = System.nanoTime();
            repository.streamPeriodRows(
                    new EcomTransactionFilter(scoped.merchantRids(), dateFrom, dateTo, null, null, null, null),
                    accumulator);
            log.info("Dashboard summary: merchants={}, period=[{}, {}), orders={}, took {} ms",
                    scoped.merchantRids().size(), dateFrom, dateTo,
                    accumulator.totals().stream().mapToLong(EcomDashboardResponse.CurrencyTotals::orderCount).sum(),
                    elapsedMillis(started));
        } else {
            log.info("No merchants in scope for the dashboard; returning an empty summary");
        }

        List<EcomDashboardAccumulator.RankedTerminal> ranked = accumulator.topTerminals();
        List<String> rids = ranked.stream().map(EcomDashboardAccumulator.RankedTerminal::merchantRid)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<String, ProviderTerminal> known = rids.isEmpty() ? Map.of() : providerTerminals.findAllById(rids).stream()
                .collect(Collectors.toMap(ProviderTerminal::getRid, Function.identity()));
        List<EcomDashboardResponse.TerminalTotal> topTerminals = ranked.stream()
                .map(terminal -> {
                    ProviderTerminal snapshot = terminal.merchantRid() == null ? null : known.get(terminal.merchantRid());
                    return new EcomDashboardResponse.TerminalTotal(terminal.currency(), terminal.merchantRid(),
                            snapshot != null ? snapshot.getLogin() : null,
                            snapshot != null ? snapshot.getTerminalRid() : null,
                            snapshot != null && snapshot.getTitle() != null ? snapshot.getTitle() : terminal.merchantTitle(),
                            terminal.netAmount(), terminal.orderCount());
                })
                .toList();

        return new EcomDashboardResponse(
                new EcomDashboardResponse.Window(dateFrom, dateTo, properties.getZone().getId()),
                accumulator.totals(),
                accumulator.statusCounts(),
                accumulator.dailyTotals(dateFrom, dateTo),
                topTerminals);
    }

    // Без похода в шлюз. Мерчант без строки в слепке терминалов остаётся с названием из слепка логинов:
    // иначе свои платежи по нему не отфильтровать.
    public List<EcomTerminalResponse> terminals(UserPrincipal principal) {
        List<String> rids = scope.scopeFor(principal).merchantRids();
        if (rids.isEmpty()) {
            return List.of();
        }
        Map<String, ProviderTerminal> known = providerTerminals.findAllById(rids).stream()
                .collect(Collectors.toMap(ProviderTerminal::getRid, Function.identity()));
        List<String> unknown = rids.stream().filter(rid -> !known.containsKey(rid)).toList();
        Map<String, String> merchantTitles = unknown.isEmpty() ? Map.of() : providerLogins.findByMerchantRidIn(unknown).stream()
                .filter(link -> link.getMerchantTitle() != null)
                .collect(Collectors.toMap(ProviderLogin::getMerchantRid, ProviderLogin::getMerchantTitle, (a, b) -> a));
        return rids.stream()
                .map(rid -> {
                    ProviderTerminal terminal = known.get(rid);
                    return terminal != null
                            ? new EcomTerminalResponse(rid, terminal.getTitle(), terminal.getLogin(), terminal.getTerminalRid())
                            : new EcomTerminalResponse(rid, merchantTitles.get(rid), null, null);
                })
                .sorted(Comparator.comparing(EcomTerminalResponse::title, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(EcomTerminalResponse::merchantRid))
                .toList();
    }

    public EcomTransactionResponse order(String orderId, UserPrincipal principal) {
        List<String> rids = scope.scopeFor(principal).merchantRids();
        Long id = parseOrderId(orderId);
        // Чужой, несуществующий и незавершённый заказ неотличимы: подобранный номер не должен
        // подтверждать, что такой заказ у провайдера есть.
        List<EcomTransactionResponse> orders = List.of();
        if (!rids.isEmpty() && id != null) {
            long started = System.nanoTime();
            orders = EcomOrderAssembler.assemble(repository.findRows(List.of(id), rids, null));
            log.info("Order card {}: found={}, merchants={}, took {} ms", id, !orders.isEmpty(), rids.size(), elapsedMillis(started));
        }
        if (orders.isEmpty()) {
            // Номер из адреса отражается в ответ, только когда это число.
            throw new ResourceNotFoundException(id != null ? "Transaction not found: " + id : "Transaction not found");
        }
        return withActions(orders.get(0), principal);
    }

    // Кнопки возврата и списания (Р-124). Заказ, заведённый порталом, проводит pbl (решение 5 к MONEY-ACTIONS-ALL):
    // вместо своих кнопок — номер его операции, и деньги учитываются в одном месте.
    private EcomTransactionResponse withActions(EcomTransactionResponse order, UserPrincipal principal) {
        Optional<UUID> portalTransaction = portal.portalTransactionOf(order.orderId());
        if (portalTransaction.isPresent()) {
            return order.withActions(null, portalTransaction.get().toString());
        }
        EcomStatus status = parseKnownStatus(order.status());
        if (status == null) {
            return order;
        }
        Optional<PortalPaymentsRepository.PortalTerminal> terminal = portal.terminalOfMerchant(order.merchantRid());
        boolean credentials = terminal.map(PortalPaymentsRepository.PortalTerminal::companyId)
                .map(portal::hasProviderCredentials)
                .orElse(false);
        // DMS — по операциям заказа, как фильтр типа оплаты (Р-87): авторизация или списание.
        boolean dms = order.operations().stream()
                .anyMatch(op -> EcomOperationKind.AUTHORIZATION.name().equals(op.kind())
                        || EcomOperationKind.CAPTURE.name().equals(op.kind()));
        OperationActions actions = EcomMoneyActions.decide(new EcomMoneyActions.Facts(status, dms, order.amount(),
                order.capturedAmount(), order.refundedAmount(), UserPrincipal.getRole(principal), terminal.isPresent(),
                credentials, attempts.open(order).orElse(null), Instant.now()));
        return order.withActions(actions, null);
    }

    private static EcomStatus parseKnownStatus(String status) {
        if (status == null) {
            return null;
        }
        try {
            return EcomStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // Каждый запрос выписки — проход по боевой базе шлюза (Р-91): одна строка INFO с его ценой.
    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    // Фильтр только сужает скоуп: мерчант вне скоупа из запроса выпадает (Р-97).
    private static List<String> narrow(List<String> scopeRids, List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return scopeRids;
        }
        return scopeRids.stream().filter(requested::contains).distinct().toList();
    }

    // Период обязателен и ограничен сверху: выписка «за всё время» — полный скан операционной
    // базы шлюза на инстансе, который в этот момент проводит авторизации.
    private void requireWindow(Instant dateFrom, Instant dateTo) {
        if (dateFrom == null || dateTo == null) {
            throw new BusinessException("dateFrom and dateTo are required");
        }
        if (!dateTo.isAfter(dateFrom)) {
            throw new BusinessException("dateTo must be after dateFrom");
        }
        Duration max = properties.getMaxWindow();
        if (Duration.between(dateFrom, dateTo).compareTo(max) > 0) {
            throw new BusinessException("The requested period exceeds the maximum of " + max.toDays() + " days");
        }
    }

    private int pageSize(Integer requested) {
        int value = requested != null ? requested : DEFAULT_PAGE_SIZE;
        return Math.clamp(value, 1, properties.getMaxPageSize());
    }

    // Значения — ровно имена enum, регистрозависимо, как у parseRole: «success» — не SUCCESS.
    private static EcomStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return EcomStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Unknown status: expected one of PENDING, AUTHORIZED, SUCCESS, "
                    + "PARTIALLY_PAID, FAILED, PARTIALLY_REFUNDED, REFUNDED, CANCELED");
        }
    }

    private static EcomPaymentType parsePaymentType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return EcomPaymentType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Unknown paymentType: expected SMS or DMS");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Long parseOrderId(String value) {
        if (value == null || value.isBlank() || value.length() > 18 || !value.chars().allMatch(Character::isDigit)) {
            return null;
        }
        long id = Long.parseLong(value);
        return id > 0 ? id : null;
    }

    // Курсор — позиция, а не секрет: номер последнего заказа страницы. Base64 — чтобы клиент не
    // собирал его сам; подделка не даёт ничего, скоуп подставляется в запрос отдельно.
    private static String encodeCursor(long orderId) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Long.toString(orderId).getBytes(StandardCharsets.UTF_8));
    }

    private static Long decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        Long orderId;
        try {
            orderId = parseOrderId(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            orderId = null;
        }
        if (orderId == null) {
            // 400, а не 500: курсор правит клиент. Само значение в ответ не отражается.
            log.warn("Rejecting an unreadable statement cursor");
            throw new BusinessException("Invalid page cursor");
        }
        return orderId;
    }
}
