package az.millikart.ecom.service;

import az.millikart.common.exception.BusinessException;
import az.millikart.common.exception.ResourceNotFoundException;
import az.millikart.common.security.UserPrincipal;
import az.millikart.ecom.config.TxpgProperties;
import az.millikart.ecom.dto.CursorPage;
import az.millikart.ecom.dto.EcomOperationResponse;
import az.millikart.ecom.dto.EcomStatsResponse;
import az.millikart.ecom.dto.EcomTerminalResponse;
import az.millikart.ecom.dto.EcomTransactionFilter;
import az.millikart.ecom.dto.EcomTransactionResponse;
import az.millikart.ecom.repository.TxpgTransactionRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Выписка по эквайринговым платежам мерчанта.
 *
 * Весь скоуп собирается здесь и только здесь: репозиторий получает уже готовый список RID и
 * подставляет его в каждый запрос. Пустой список означает пустую выписку — мерчант, которому
 * не завели ни одной привязки, видит пустой экран, а не чужие платежи.
 */
@Service
public class EcomTransactionService {

    private static final Logger log = LoggerFactory.getLogger(EcomTransactionService.class);

    private final TxpgTransactionRepository repository;
    private final EcomScopeService scope;
    private final TxpgProperties properties;

    public EcomTransactionService(TxpgTransactionRepository repository,
                                  EcomScopeService scope,
                                  TxpgProperties properties) {
        this.repository = repository;
        this.scope = scope;
        this.properties = properties;
    }

    public CursorPage<EcomTransactionResponse> list(Instant dateFrom, Instant dateTo,
                                                    List<String> terminalIds,
                                                    BigDecimal minAmount, BigDecimal maxAmount,
                                                    String query, String cursor, Integer size,
                                                    UserPrincipal principal) {
        List<String> rids = scope.merchantRidsFor(principal);
        if (rids.isEmpty()) {
            log.info("No provider terminals are linked for this caller; returning an empty statement");
            return new CursorPage<>(List.of(), null);
        }

        int pageSize = pageSize(size);
        EcomTransactionFilter filter = new EcomTransactionFilter(
                rids, dateFrom, window(dateFrom, dateTo), terminalIds,
                minAmount, maxAmount, query, cursor, pageSize + 1);

        List<EcomTransactionResponse> rows = repository.findPage(filter, decodeCursor(cursor));

        // Лишняя строка запрошена ради одного вопроса: есть ли что-то дальше. Иначе последняя
        // страница отличалась бы от полной только на глаз, и «дальше» предлагалось бы в пустоту.
        boolean hasMore = rows.size() > pageSize;
        List<EcomTransactionResponse> content = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore ? encodeCursor(content.get(content.size() - 1)) : null;

        return new CursorPage<>(List.copyOf(content), nextCursor);
    }

    public EcomStatsResponse stats(Instant dateFrom, Instant dateTo, UserPrincipal principal) {
        List<String> rids = scope.merchantRidsFor(principal);
        if (rids.isEmpty()) {
            return new EcomStatsResponse(0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, null);
        }
        return repository.findStats(new EcomTransactionFilter(
                rids, dateFrom, window(dateFrom, dateTo), null, null, null, null, null, 0));
    }

    public List<EcomTerminalResponse> terminals(Instant dateFrom, Instant dateTo, UserPrincipal principal) {
        List<String> rids = scope.merchantRidsFor(principal);
        if (rids.isEmpty()) {
            return List.of();
        }
        return repository.findTerminals(new EcomTransactionFilter(
                rids, dateFrom, window(dateFrom, dateTo), null, null, null, null, null, 0));
    }

    public List<EcomOperationResponse> operations(String orderId, UserPrincipal principal) {
        List<String> rids = scope.merchantRidsFor(principal);
        if (rids.isEmpty()) {
            // Не «пустая история», а «такого заказа для вас нет»: подобранный номер не должен
            // отличаться на экране от заказа, который существует, но принадлежит другому.
            throw new ResourceNotFoundException("Transaction not found: " + orderId);
        }
        List<EcomOperationResponse> operations = repository.findOperations(rids, orderId);
        if (operations.isEmpty()) {
            throw new ResourceNotFoundException("Transaction not found: " + orderId);
        }
        return operations;
    }

    /**
     * Период обязателен и ограничен сверху. Выписка «за всё время» по операционной базе шлюза —
     * это полный скан на инстансе, который в этот момент проводит авторизации.
     */
    private Instant window(Instant dateFrom, Instant dateTo) {
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
        return dateTo;
    }

    private int pageSize(Integer requested) {
        int fallback = 25;
        int value = requested != null ? requested : fallback;
        return Math.clamp(value, 1, properties.getMaxPageSize());
    }

    /**
     * Курсор — это позиция, а не секрет: время последней операции и номер заказа последней
     * отданной строки. Base64 здесь лишь затем, чтобы значение пережило строку запроса, и
     * подделка курсора не даёт ничего — скоуп по мерчанту в запрос подставляется отдельно.
     */
    private String encodeCursor(EcomTransactionResponse last) {
        String raw = last.lastOperationAt() + "|" + last.orderId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private TxpgTransactionRepository.Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf('|');
            if (separator < 0) {
                throw new IllegalArgumentException("no separator");
            }
            return new TxpgTransactionRepository.Cursor(
                    Instant.parse(raw.substring(0, separator)),
                    raw.substring(separator + 1));
        } catch (RuntimeException e) {
            // Битый курсор — это 400, а не 500: его правит клиент, а не мы. Значение в сообщение
            // не попадает, чтобы не отражать в ответ произвольную строку из запроса.
            log.warn("Rejecting an unreadable statement cursor: {}", e.getMessage());
            throw new BusinessException("Invalid page cursor");
        }
    }
}
