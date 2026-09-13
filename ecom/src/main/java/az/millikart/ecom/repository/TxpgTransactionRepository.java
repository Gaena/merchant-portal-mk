package az.millikart.ecom.repository;

import az.millikart.ecom.config.TxpgDataSourceConfig;
import az.millikart.ecom.config.TxpgProperties;
import az.millikart.ecom.dto.EcomOperationResponse;
import az.millikart.ecom.dto.EcomStatsResponse;
import az.millikart.ecom.dto.EcomTerminalResponse;
import az.millikart.ecom.dto.EcomTransactionFilter;
import az.millikart.ecom.dto.EcomTransactionResponse;
import az.millikart.ecom.service.EcomStatusResolver;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Чтение платежей из схемы шлюза провайдера.
 *
 * Четыре правила, на которых держится всё остальное:
 *
 * 1. **Одна строка на заказ.** `tran` даёт строку на операцию, у DMS-платежа их минимум две.
 *    Поэтому операции сворачиваются группировкой по `orderid`, а не отдаются как есть.
 * 2. **Токен свёрнут подзапросом.** Токен привязан к заказу, а не к операции: клиент, попробовавший
 *    две карты, оставляет два токена, и прямой join размножил бы каждую операцию вдвое.
 * 3. **Скоуп обязателен.** `m.rid in (:merchant_rids)` стоит в запросе всегда, список приходит из
 *    таблицы привязок. Ни одного пути, на котором этот фильтр можно не подставить, здесь нет.
 * 4. **`merchant` через inner join.** Условие на таблицу из LEFT JOIN, вынесенное в WHERE, и так
 *    превращает его в INNER — но написанное явно оно не станет миной для того, кто через полгода
 *    снимет фильтр по мерчанту и не поймёт, куда делись заказы.
 *
 * Окно по `tr.id` — их собственный приём: идентификатор монотонно растёт по времени, и
 * `getLowIdForTime` / `getHighIdForTime` сужают скан по первичному ключу вместо диапазона по
 * времени. Быстро, но это контракт, которого нет в документации, и его гарантии надо получить
 * письменно.
 *
 * Следствие окна: заказ, по которому в периоде не было ни одной операции, сюда не попадает.
 * Созданный, но не оплаченный платёж в выписке за период не виден — намеренно, потому что
 * объединять его вторым запросом по `o.createtime` без утверждённого словаря статусов значит
 * гадать, чем такой заказ кончился.
 *
 * Секретов в выборке нет: `o.password` — это то, чем подписываются обращения к заказу, и пара
 * «номер заказа плюс пароль» даёт доступ к чужим операциям. В выписку, в экспорт и в логи он
 * не попадает никогда.
 */
// ОБЯЗАТЕЛЬНО при замене запроса на присланный провайдером: выписка берёт **только завершённые**
// заказы. Кнопка «Тест» у терминала заводит у провайдера настоящий неоплаченный заказ
// (TerminalCheckService в pbl), и без этого фильтра каждое её нажатие появилось бы у мерчанта
// строкой в выписке.
@Repository
public class TxpgTransactionRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final TxpgProperties properties;

    public TxpgTransactionRepository(@Qualifier(TxpgDataSourceConfig.TXPG_JDBC) NamedParameterJdbcTemplate jdbc,
                                     TxpgProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    /** Общая часть: окно по id и свёрнутые до заказа операции. */
    private String aggregateCte() {
        String schema = properties.getSchema();
        return """
                with b as (
                  select %1$s.RDX_Action.getLowIdForTime (:date_from) lo,
                         %1$s.RDX_Action.getHighIdForTime(:date_to)   hi
                    from dual
                ),
                t as (
                  select tr.orderid, tr.terminalid, tr.rrn, tr.ridbypmo, tr.origtime,
                         tr.trantype, tr.phase, tr.pmoresultcode, tr.tranamt, tr.tranccy
                    from %1$s.tran tr
                   cross join b
                   where tr.id between b.lo and b.hi
                ),
                agg as (
                  select orderid,
                         min(origtime)                                              first_op_time,
                         max(origtime)                                              last_op_time,
                         count(*)                                                   op_count,
                         max(terminalid) keep (dense_rank first order by origtime)   terminal_id,
                         max(rrn)        keep (dense_rank first order by origtime)   rrn,
                         max(tranccy)    keep (dense_rank first order by origtime)   tran_ccy,
                         sum(case when pmoresultcode = 'Approved'
                                   and trantype in ('Purchase','Capture')
                                  then tranamt else 0 end)                          captured_amt,
                         sum(case when pmoresultcode = 'Approved'
                                   and trantype in ('Refund','Reversal')
                                  then tranamt else 0 end)                          refunded_amt,
                         max(case when pmoresultcode <> 'Approved'
                                  then pmoresultcode end)                           decline_code,
                         max(case when pmoresultcode = 'Approved'
                                   and trantype in ('Authorization','Auth')
                                  then 1 else 0 end)                                has_approved_auth
                    from t
                   group by orderid
                )
                """.formatted(schema);
    }

    public List<EcomTransactionResponse> findPage(EcomTransactionFilter filter, Cursor cursor) {
        String schema = properties.getSchema();
        StringBuilder sql = new StringBuilder(aggregateCte());
        sql.append("""
                select o.id                order_id,
                       m.rid               merchant_rid,
                       m.title             merchant_title,
                       o.ridbymerchant     rid_by_merchant,
                       o.status            order_status,
                       o.prevstatus        order_prev_status,
                       o.description       description,
                       o.amt               order_amount,
                       o.ccy               order_ccy,
                       o.createtime        order_created,
                       o.srcemail          src_email,
                       o.srcmobile         src_mobile,
                       a.first_op_time,
                       a.last_op_time,
                       a.op_count,
                       a.terminal_id,
                       a.rrn,
                       a.captured_amt,
                       a.refunded_amt,
                       a.decline_code,
                       a.has_approved_auth,
                       tk.displayname      card_mask
                  from agg a
                  join %1$s.order_   o on o.id = a.orderid
                  join %1$s.merchant m on m.id = o.merchantid
                  left join (
                        select orderid,
                               max(displayname) keep (dense_rank first order by id desc) displayname
                          from %1$s.token
                         group by orderid
                  ) tk on tk.orderid = o.id
                 where m.rid in (:merchant_rids)
                """.formatted(schema));

        MapSqlParameterSource params = baseParams(filter);

        if (filter.terminalIds() != null && !filter.terminalIds().isEmpty()) {
            sql.append("   and a.terminal_id in (:terminal_ids)\n");
            params.addValue("terminal_ids", filter.terminalIds());
        }
        if (filter.minAmount() != null) {
            sql.append("   and o.amt >= :min_amount\n");
            params.addValue("min_amount", filter.minAmount());
        }
        if (filter.maxAmount() != null) {
            sql.append("   and o.amt <= :max_amount\n");
            params.addValue("max_amount", filter.maxAmount());
        }
        if (filter.query() != null && !filter.query().isBlank()) {
            // Поиск по тому, чем мерчант оперирует: номер заказа, его собственная ссылка, RRN
            // и почта плательщика. Ни одного LIKE '%...%' по фактовой таблице: на операционной
            // базе шлюза это полный скан.
            sql.append("""
                       and ( to_char(o.id)     = :query
                          or o.ridbymerchant   = :query
                          or a.rrn             = :query
                          or lower(o.srcemail) = lower(:query) )
                    """);
            params.addValue("query", filter.query().trim());
        }
        if (cursor != null) {
            // Keyset: строго то же выражение, что и в order by, иначе страница поедет.
            sql.append("   and (a.last_op_time < :cursor_ts"
                    + " or (a.last_op_time = :cursor_ts and o.id < :cursor_id))\n");
            params.addValue("cursor_ts", Timestamp.from(cursor.lastOperationAt()));
            params.addValue("cursor_id", cursor.orderId());
        }

        sql.append(" order by a.last_op_time desc, o.id desc\n");
        sql.append(" fetch first :page_size rows only");
        params.addValue("page_size", filter.pageSize());

        return jdbc.query(sql.toString(), params, TRANSACTION_MAPPER);
    }

    public List<EcomOperationResponse> findOperations(List<String> merchantRids, String orderId) {
        String schema = properties.getSchema();
        // Скоуп и здесь: карточка заказа открывается по номеру из адреса, и без проверки мерчанта
        // подобранный номер показал бы операции чужого платежа.
        String sql = """
                select tr.ridbypmo   operation_id,
                       tr.origtime   at,
                       tr.trantype   type,
                       tr.phase      phase,
                       tr.pmoresultcode result_code,
                       tr.tranamt    amount,
                       tr.tranccy    currency,
                       tr.rrn        rrn,
                       tr.terminalid terminal_id
                  from %1$s.tran tr
                  join %1$s.order_   o on o.id = tr.orderid
                  join %1$s.merchant m on m.id = o.merchantid
                 where tr.orderid = :order_id
                   and m.rid in (:merchant_rids)
                 order by tr.origtime
                """.formatted(schema);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("order_id", orderId)
                .addValue("merchant_rids", merchantRids);

        return jdbc.query(sql, params, (rs, rowNum) -> new EcomOperationResponse(
                rs.getString("operation_id"),
                instant(rs, "at"),
                rs.getString("type"),
                rs.getString("phase"),
                rs.getString("result_code"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getString("rrn"),
                rs.getString("terminal_id")
        ));
    }

    public EcomStatsResponse findStats(EcomTransactionFilter filter) {
        String schema = properties.getSchema();
        String sql = aggregateCte() + """
                select count(*)                                                    order_count,
                       sum(a.captured_amt)                                         captured_total,
                       sum(a.refunded_amt)                                         refunded_total,
                       count(case when a.captured_amt > 0 then 1 end)              success_count,
                       count(case when a.captured_amt = 0
                                   and a.has_approved_auth = 0
                                   and a.op_count > 0 then 1 end)                  failed_count,
                       count(case when a.captured_amt = 0
                                   and a.has_approved_auth = 1 then 1 end)         pending_count,
                       max(o.ccy)                                                  currency
                  from agg a
                  join %1$s.order_   o on o.id = a.orderid
                  join %1$s.merchant m on m.id = o.merchantid
                 where m.rid in (:merchant_rids)
                """.formatted(schema);

        return jdbc.queryForObject(sql, baseParams(filter), (rs, rowNum) -> new EcomStatsResponse(
                rs.getLong("order_count"),
                rs.getLong("success_count"),
                rs.getLong("failed_count"),
                rs.getLong("pending_count"),
                zeroIfNull(rs.getBigDecimal("captured_total")),
                zeroIfNull(rs.getBigDecimal("refunded_total")),
                rs.getString("currency")
        ));
    }

    public List<EcomTerminalResponse> findTerminals(EcomTransactionFilter filter) {
        String schema = properties.getSchema();
        String sql = aggregateCte() + """
                select a.terminal_id, count(*) order_count
                  from agg a
                  join %1$s.order_   o on o.id = a.orderid
                  join %1$s.merchant m on m.id = o.merchantid
                 where m.rid in (:merchant_rids)
                   and a.terminal_id is not null
                 group by a.terminal_id
                 order by a.terminal_id
                """.formatted(schema);

        return jdbc.query(sql, baseParams(filter), (rs, rowNum) -> new EcomTerminalResponse(
                rs.getString("terminal_id"),
                rs.getLong("order_count")
        ));
    }

    private MapSqlParameterSource baseParams(EcomTransactionFilter filter) {
        return new MapSqlParameterSource()
                .addValue("merchant_rids", filter.merchantRids())
                .addValue("date_from", Timestamp.from(filter.dateFrom()))
                .addValue("date_to", Timestamp.from(filter.dateTo()));
    }

    /** Позиция в выписке: время последней операции заказа и его номер. Второе разрешает совпадения. */
    public record Cursor(Instant lastOperationAt, String orderId) {
    }

    private static final RowMapper<EcomTransactionResponse> TRANSACTION_MAPPER = (rs, rowNum) -> {
        BigDecimal captured = zeroIfNull(rs.getBigDecimal("captured_amt"));
        BigDecimal refunded = zeroIfNull(rs.getBigDecimal("refunded_amt"));
        int operations = rs.getInt("op_count");
        boolean approvedAuth = rs.getInt("has_approved_auth") == 1;

        return new EcomTransactionResponse(
                rs.getString("order_id"),
                rs.getString("merchant_rid"),
                rs.getString("merchant_title"),
                rs.getString("rid_by_merchant"),
                EcomStatusResolver.resolve(captured, refunded, approvedAuth, operations).name(),
                rs.getString("order_status"),
                rs.getString("order_prev_status"),
                rs.getBigDecimal("order_amount"),
                captured,
                refunded,
                rs.getString("order_ccy"),
                rs.getString("description"),
                instant(rs, "order_created"),
                instant(rs, "first_op_time"),
                instant(rs, "last_op_time"),
                operations,
                rs.getString("terminal_id"),
                rs.getString("card_mask"),
                rs.getString("rrn"),
                rs.getString("decline_code"),
                rs.getString("src_email"),
                rs.getString("src_mobile")
        );
    };

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value != null ? value.toInstant() : null;
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
