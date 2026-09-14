package az.millikart.ecom.service;

import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.ResourceNotFoundException;
import az.millikart.common.security.UserPrincipal;
import az.millikart.ecom.config.TxpgProperties;
import az.millikart.ecom.domain.ProviderTerminal;
import az.millikart.ecom.dto.CursorPage;
import az.millikart.ecom.dto.EcomStatsResponse;
import az.millikart.ecom.dto.EcomTerminalResponse;
import az.millikart.ecom.dto.EcomTransactionFilter;
import az.millikart.ecom.dto.EcomTransactionResponse;
import az.millikart.ecom.repository.ProviderTerminalRepository;
import az.millikart.ecom.repository.TxpgTransactionRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Выписка по эквайринговым платежам мерчанта. Скоуп собирается здесь и только здесь: репозиторий
// получает готовый список логинов терминалов и подставляет его в каждый запрос. Пустой список —
// пустая выписка без похода в шлюз, а не чужие платежи (AGENTS.md §10).
@Service
public class EcomTransactionService {

    private static final Logger log = LoggerFactory.getLogger(EcomTransactionService.class);

    private static final int DEFAULT_PAGE_SIZE = 25;

    private final TxpgTransactionRepository repository;
    private final EcomScopeService scope;
    private final ProviderTerminalRepository providerTerminals;
    private final TxpgProperties properties;

    public EcomTransactionService(TxpgTransactionRepository repository,
                                  EcomScopeService scope,
                                  ProviderTerminalRepository providerTerminals,
                                  TxpgProperties properties) {
        this.repository = repository;
        this.scope = scope;
        this.providerTerminals = providerTerminals;
        this.properties = properties;
    }

    public CursorPage<EcomTransactionResponse> list(Instant dateFrom, Instant dateTo, List<String> merchantRids,
                                                    BigDecimal minAmount, BigDecimal maxAmount, String query,
                                                    String cursor, Integer size, UserPrincipal principal) {
        EcomScope scoped = scope.scopeFor(principal);
        List<String> rids = narrow(scoped.merchantRids(), merchantRids);
        if (isEmpty(scoped, rids)) {
            log.info("No terminals in scope for this request; returning an empty statement");
            return new CursorPage<>(List.of(), null);
        }
        requireWindow(dateFrom, dateTo);
        int pageSize = pageSize(size);
        EcomTransactionFilter filter = new EcomTransactionFilter(
                scoped.logins(), rids, dateFrom, dateTo, minAmount, maxAmount, blankToNull(query));

        // Лишний номер запрошен ради одного вопроса: есть ли что-то дальше.
        List<Long> orderIds = repository.findOrderIds(filter, decodeCursor(cursor), pageSize + 1);
        boolean hasMore = orderIds.size() > pageSize;
        List<Long> pageIds = hasMore ? orderIds.subList(0, pageSize) : orderIds;

        List<EcomTransactionResponse> orders =
                EcomOrderAssembler.assemble(repository.findRows(pageIds, scoped.logins(), dateFrom));
        // Курсор — последний номер страницы, а не последней собранной строки: заказ, пропавший
        // между двумя запросами, не должен сдвинуть следующую страницу.
        String nextCursor = hasMore ? encodeCursor(pageIds.get(pageIds.size() - 1)) : null;
        return new CursorPage<>(orders, nextCursor);
    }

    public EcomStatsResponse stats(Instant dateFrom, Instant dateTo, List<String> merchantRids,
                                   UserPrincipal principal) {
        EcomScope scoped = scope.scopeFor(principal);
        List<String> rids = narrow(scoped.merchantRids(), merchantRids);
        EcomStatsAccumulator accumulator = new EcomStatsAccumulator();
        if (isEmpty(scoped, rids)) {
            return accumulator.result();
        }
        requireWindow(dateFrom, dateTo);
        repository.streamPeriodRows(
                new EcomTransactionFilter(scoped.logins(), rids, dateFrom, dateTo, null, null, null), accumulator);
        return accumulator.result();
    }

    // Источник фильтра — терминалы скоупа из нашей базы, без похода в шлюз: у провайдера терминал
    // и мерчант одно, и список «кто мог платить» и есть список терминалов компании. Терминал без
    // merchantRid в выписке виден, но в фильтре его нет: фильтр идёт по терминалу провайдера.
    public List<EcomTerminalResponse> terminals(UserPrincipal principal) {
        List<String> rids = scope.scopeFor(principal).merchantRids();
        if (rids.isEmpty()) {
            return List.of();
        }
        Map<String, ProviderTerminal> known = providerTerminals.findAllById(rids).stream()
                .collect(Collectors.toMap(ProviderTerminal::getRid, Function.identity()));
        return rids.stream()
                .map(rid -> {
                    ProviderTerminal terminal = known.get(rid);
                    return terminal != null
                            ? new EcomTerminalResponse(rid, terminal.getTitle(), terminal.getLogin())
                            : new EcomTerminalResponse(rid, null, null);
                })
                .sorted(Comparator.comparing(EcomTerminalResponse::title, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(EcomTerminalResponse::merchantRid))
                .toList();
    }

    public EcomTransactionResponse order(String orderId, UserPrincipal principal) {
        List<String> logins = scope.scopeFor(principal).logins();
        Long id = parseOrderId(orderId);
        // Чужой, несуществующий и незавершённый заказ неотличимы: подобранный номер не должен
        // подтверждать, что такой заказ у провайдера есть.
        List<EcomTransactionResponse> orders = logins.isEmpty() || id == null
                ? List.of()
                : EcomOrderAssembler.assemble(repository.findRows(List.of(id), logins, null));
        if (orders.isEmpty()) {
            // Номер из адреса отражается в ответ, только когда это число.
            throw new ResourceNotFoundException(id != null ? "Transaction not found: " + id : "Transaction not found");
        }
        return orders.get(0);
    }

    // Фильтр сужает скоуп и никогда его не расширяет: мерчант вне скоупа из запроса просто выпадает.
    // null — фильтра нет; пустой список — в фильтре ни одного своего мерчанта.
    private static List<String> narrow(List<String> scopeRids, List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return null;
        }
        return scopeRids.stream().filter(requested::contains).distinct().toList();
    }

    // Без логинов выписка пустая; фильтр из одних чужих мерчантов — тоже, и в шлюз за ней не ходим.
    private static boolean isEmpty(EcomScope scoped, List<String> rids) {
        return scoped.logins().isEmpty() || (rids != null && rids.isEmpty());
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
