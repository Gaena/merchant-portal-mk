package az.millikart.ecom.repository;

import az.millikart.ecom.config.TxpgDataSourceConfig;
import az.millikart.ecom.config.TxpgProperties;
import az.millikart.ecom.dto.EcomTransactionFilter;
import az.millikart.ecom.service.EcomOperationKind;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

// Колонки — только из SQL провайдера: одна отсутствующая в схеме роняет всю выписку. o.password не
// выбирается никогда (AGENTS.md §10).
@Repository
public class TxpgTransactionRepository {

    // Перечислено незавершённое, а не завершённое: заказ с незнакомым статусом виден с кодом
    // провайдера — пропавший из выписки платёж хуже лишней строки (Р-71). Authorized со списанием —
    // исключение, см. finishedOrdersOnly (Р-76).
    public static final List<String> UNFINISHED_ORDER_STATUSES = List.of("Preparing", "Authorized", "Expired");

    // Первая операция проходит, пока заказ жив (неоплаченный провайдер закрывает через 10 минут),
    // поэтому заказы периода ищутся по операциям не дальше суток после его конца.
    static final Duration FIRST_OPERATION_LAG = Duration.ofDays(1);

    private static final String COLUMNS = """
            select o.id             order_id,
                   o.ridbymerchant  rid_by_merchant,
                   o.status         order_status,
                   o.prevstatus     order_prev_status,
                   o.description    description,
                   o.amt            order_amount,
                   o.ccy            order_ccy,
                   o.createtime     order_created,
                   m.rid            merchant_rid,
                   m.title          merchant_title,
                   tr.ridbyacq      tran_id,
                   tr.rrn           rrn,
                   tr.origtime      tran_time,
                   tr.pmoresultcode result_code,
                   tr.tranamt       tran_amount,
                   tr.clearamt      clear_amount,
                   tr.tranccy       tran_ccy,
                   tr.trantype      tran_type,
                   tr.phase         phase,
                   tr.voidkind      void_kind,
                   tr.authkind      auth_kind,
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final TxpgProperties properties;
    private final Clock clock;
    private final RowMapper<TxpgStatementRow> rowMapper = this::mapRow;

    @Autowired
    public TxpgTransactionRepository(@Qualifier(TxpgDataSourceConfig.TXPG_JDBC) NamedParameterJdbcTemplate jdbc,
                                     TxpgProperties properties) {
        this(jdbc, properties, Clock.systemUTC());
    }

    TxpgTransactionRepository(NamedParameterJdbcTemplate jdbc, TxpgProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    // Только номера заказов: одним запросом с операциями на странице было бы N операций, а не
    // N заказов (Р-74).
    public List<Long> findOrderIds(EcomTransactionFilter filter, Long beforeOrderId, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder(periodOrders(filter, params));
        if (filter.minAmount() != null) {
            sql.append("   and o.amt >= :min_amount\n");
            params.addValue("min_amount", filter.minAmount());
        }
        if (filter.maxAmount() != null) {
            sql.append("   and o.amt <= :max_amount\n");
            params.addValue("max_amount", filter.maxAmount());
        }
        if (filter.query() != null) {
            // Только точное совпадение: LIKE '%...%' по операциям шлюза — это полный скан.
            sql.append("   and (to_char(o.id) = :query or o.ridbymerchant = :query or tr.rrn = :query)\n");
            params.addValue("query", filter.query());
        }
        if (beforeOrderId != null) {
            sql.append("   and o.id < :before_order_id\n");
            params.addValue("before_order_id", beforeOrderId);
        }
        sql.append(" group by o.id\n order by o.id desc\n fetch first :limit rows only");
        params.addValue("limit", limit);
        return jdbc.query(sql.toString(), params, (rs, rowNum) -> rs.getLong("order_id"));
    }

    // Все операции заказов, без окна сверху: история полная, даже если клиринг прошёл после
    // периода. Скоуп и Р-71 повторены — карточка приходит сюда с номером из адреса.
    public List<TxpgStatementRow> findRows(List<Long> orderIds, List<String> merchantRids, Instant operationsFrom) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        String schema = properties.getSchema();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("order_ids", orderIds)
                .addValue("merchant_rids", merchantRids)
                .addValue("unfinished_statuses", UNFINISHED_ORDER_STATUSES);
        // Токен — подзапросом: у покупателя, пробовавшего две карты, их два, и join задвоил бы операции.
        StringBuilder sql = new StringBuilder(COLUMNS).append("""
                       tk.displayname   card_mask
                  from %1$s.order_ o
                  join %1$s.tran    tr on tr.orderid = o.id
                  join %1$s.merchant m on m.id = o.merchantid and m.id = tr.merchantid
                  left join (select orderid, max(displayname) displayname
                               from %1$s.token
                              where orderid in (:order_ids)
                              group by orderid) tk on tk.orderid = o.id
                 where o.id in (:order_ids)
                """.formatted(schema)).append(merchantScope()).append(finishedOrdersOnly());
        if (operationsFrom != null) {
            sql.append("   and tr.id >= ").append(lowIdForTime("operations_from")).append('\n');
            params.addValue("operations_from", local(operationsFrom));
        }
        sql.append(" order by o.id desc, tr.origtime, tr.ridbyacq");
        return jdbc.query(sql.toString(), params, rowMapper);
    }

    // Итоги — по всем заказам периода, потоком и теми же правилами, что страница: второго набора
    // правил в SQL не заводить (Р-75).
    public void streamPeriodRows(EcomTransactionFilter filter, Consumer<TxpgStatementRow> sink) {
        String schema = properties.getSchema();
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = COLUMNS + """
                       null             card_mask
                  from %1$s.order_ o
                  join %1$s.tran    tr on tr.orderid = o.id
                  join %1$s.merchant m on m.id = o.merchantid and m.id = tr.merchantid
                 where o.id in (
                """.formatted(schema)
                + periodOrders(filter, params)
                + "       )\n"
                + merchantScope()
                + "   and tr.id >= " + lowIdForTime("date_from") + "\n"
                + " order by o.id desc, tr.origtime, tr.ridbyacq\n";
        jdbc.query(sql, params, (RowCallbackHandler) rs -> sink.accept(mapRow(rs, 0)));
    }

    // Период — по дате создания заказа (Р-74). Окно по tran.id — приём провайдера: id растёт со
    // временем и сужает скан.
    private String periodOrders(EcomTransactionFilter filter, MapSqlParameterSource params) {
        String schema = properties.getSchema();
        StringBuilder sql = new StringBuilder("""
                select o.id order_id
                  from %1$s.tran tr
                  join %1$s.order_   o on o.id = tr.orderid
                  join %1$s.merchant m on m.id = o.merchantid and m.id = tr.merchantid
                """.formatted(schema))
                .append(" where tr.id >= ").append(lowIdForTime("date_from")).append('\n');
        // Граница сверху — только у прошедших периодов: у текущего она упёрлась бы в часы базы
        // (lowIdForTime) и отрезала последние минуты, а скан до сегодня здесь всё равно нужен.
        Instant scanTo = filter.dateTo().plus(FIRST_OPERATION_LAG);
        if (scanTo.isBefore(clock.instant())) {
            sql.append("   and tr.id < ").append(lowIdForTime("scan_to")).append('\n');
            params.addValue("scan_to", local(scanTo));
        }
        sql.append(merchantScope());
        if (filter.paymentType() != null) {
            // В выборе заказов, а не после сборки: так фильтр работает и на странице, и в итогах (Р-87).
            sql.append("   and exists (select 1 from %1$s.tran pt where pt.orderid = o.id and (%2$s))\n"
                    .formatted(schema, anyOf("pt", filter.paymentType().signs())));
        }
        sql.append("""
                   and o.createtime >= :date_from
                   and o.createtime <  :date_to
                """).append(finishedOrdersOnly());
        params.addValue("date_from", local(filter.dateFrom()))
                .addValue("date_to", local(filter.dateTo()))
                .addValue("merchant_rids", filter.merchantRids())
                .addValue("unfinished_statuses", UNFINISHED_ORDER_STATUSES);
        return sql.toString();
    }

    // m в join и на заказе, и на операции (m.id = o.merchantid and m.id = tr.merchantid): иначе операция
    // чужого мерчанта попадёт в заказ своего (Р-97).
    private static String merchantScope() {
        return "   and m.rid in (:merchant_rids)\n";
    }

    // getLowIdForTime на будущем времени не работает (провайдер), а наши часы и пояс могут разойтись
    // с базой. Поэтому аргумент прижат к sysdate базы с запасом: окно снизу от этого только шире.
    private String lowIdForTime(String parameter) {
        return "(select %s.RDX_Action.getLowIdForTime(least(cast(:%s as date), sysdate - interval '5' minute)) from dual)"
                .formatted(properties.getSchema(), parameter);
    }

    // При мультиклиринге заказ остаётся Authorized и после списания (Р-76). Признак списания —
    // EcomOperationKind.CAPTURE_SIGNS, тот же список, что считает списание в деньгах (Р-86). Пустой статус —
    // незнакомый, а не незавершённый: NULL not in (…) в SQL не истина, без is null заказ пропадал (ECOM-NULL-STATUS).
    private String finishedOrdersOnly() {
        String captured = anyOf("c", EcomOperationKind.CAPTURE_SIGNS);
        return """
                   and (o.status is null
                        or o.status not in (:unfinished_statuses)
                        or (o.status = 'Authorized'
                            and exists (select 1
                                          from %1$s.tran c
                                         where c.orderid = o.id
                                           and (%2$s)
                                           and c.voidkind is null
                                           and c.pmoresultcode = 'Approved')))
                """.formatted(properties.getSchema(), captured);
    }

    // Литералы — константы EcomOperationKind, не ввод пользователя: только поэтому они подставляются
    // в текст запроса.
    static String anyOf(String alias, List<EcomOperationKind.TypePhase> signs) {
        return signs.stream()
                .map(sign -> "(%1$s.trantype = '%2$s' and %1$s.phase = '%3$s')".formatted(alias, sign.type(), sign.phase()))
                .collect(Collectors.joining(" or "));
    }

    private TxpgStatementRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TxpgStatementRow(
                rs.getString("order_id"),
                rs.getString("rid_by_merchant"),
                rs.getString("order_status"),
                rs.getString("order_prev_status"),
                rs.getString("description"),
                rs.getBigDecimal("order_amount"),
                rs.getString("order_ccy"),
                instant(rs, "order_created"),
                rs.getString("merchant_rid"),
                rs.getString("merchant_title"),
                rs.getString("card_mask"),
                rs.getString("tran_id"),
                rs.getString("rrn"),
                instant(rs, "tran_time"),
                rs.getString("result_code"),
                rs.getBigDecimal("tran_amount"),
                rs.getBigDecimal("clear_amount"),
                rs.getString("tran_ccy"),
                rs.getString("tran_type"),
                rs.getString("phase"),
                rs.getString("void_kind"),
                rs.getString("auth_kind"));
    }

    // Даты шлюза — местное время без пояса: в параметры уходит местное время, из колонок оно же
    // читается в поясе ecom.txpg.zone. Instant в параметре сдвинул бы период на пояс JVM.
    private LocalDateTime local(Instant value) {
        return LocalDateTime.ofInstant(value, properties.getZone());
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value != null ? value.toLocalDateTime().atZone(properties.getZone()).toInstant() : null;
    }
}
