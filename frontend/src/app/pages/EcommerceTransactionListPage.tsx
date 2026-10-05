import React, { useEffect, useMemo, useRef, useState } from 'react';
import axios from 'axios';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router';
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  Drawer,
  IconButton,
  InputAdornment,
  ListItemText,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import {
  Close as CloseIcon,
  FileDownload as FileDownloadIcon,
  OpenInNew as OpenInNewIcon,
  Refresh as RefreshIcon,
  Search as SearchIcon,
} from '@mui/icons-material';
import { EcomOrderDetails } from '../components/EcomOrderDetails';
import { useLanguage } from '../context/LanguageContext';
import {
  ECOM_PAYMENT_TYPES,
  ECOM_STATUSES,
  type EcomOrder,
  type EcomPaymentType,
  type EcomQuery,
  type EcomStats,
  type EcomStatus,
  type EcomTerminal,
} from '../types/ecom';
import {
  ecomTerminalLabel,
  ecomTerminalName,
  fetchEcomPage,
  fetchEcomStats,
  fetchEcomTerminals,
  periodProblem,
} from '../utils/ecom';
import { exportEcomOrdersToExcel } from '../utils/exportExcel';
import { formatCurrency, formatDateTime } from '../utils/format';
import { getStatusColorScheme } from '../utils/statusColors';

const PAGE_SIZES = [25, 50, 100];

// Значение для <input type="datetime-local">: местное время браузера без пояса.
const toLocalInput = (date: Date | null): string => {
  if (!date) return '';
  const shifted = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return shifted.toISOString().slice(0, 16);
};

const fromLocalInput = (value: string): Date | null => {
  if (!value) return null;
  const parsed = new Date(value);
  return isNaN(parsed.getTime()) ? null : parsed;
};

// По умолчанию — последние семь суток целиком: выписку смотрят днями. Конец — конец сегодняшнего дня,
// а не «сейчас»: иначе «Обновить» через час не показал бы заказы, созданные после открытия страницы.
const defaultPeriod = (): { from: Date; to: Date } => {
  const from = new Date();
  from.setDate(from.getDate() - 6);
  from.setHours(0, 0, 0, 0);
  const to = new Date();
  to.setHours(23, 59, 59, 999);
  return { from, to };
};

// На экране — черновик, в запрос уходит только применённая копия (Р-88): каждый запрос — проход по
// периоду на боевой базе провайдера, и отмена в браузере его там не останавливает.
type Filters = {
  dateFrom: Date | null;
  dateTo: Date | null;
  merchantRids: string[];
  minAmount: string;
  maxAmount: string;
  search: string;
  status: EcomStatus | null;
  paymentType: EcomPaymentType | null;
};

const defaultFilters = (): Filters => {
  const { from, to } = defaultPeriod();
  return {
    dateFrom: from, dateTo: to, merchantRids: [], minAmount: '', maxAmount: '', search: '',
    status: null, paymentType: null,
  };
};

const sameDate = (a: Date | null, b: Date | null) => (a?.getTime() ?? null) === (b?.getTime() ?? null);

const sameFilters = (a: Filters, b: Filters): boolean =>
  sameDate(a.dateFrom, b.dateFrom)
  && sameDate(a.dateTo, b.dateTo)
  && [...a.merchantRids].sort().join('\n') === [...b.merchantRids].sort().join('\n')
  && a.minAmount === b.minAmount
  && a.maxAmount === b.maxAmount
  && a.search === b.search
  && a.status === b.status
  && a.paymentType === b.paymentType;

// Текст отказа сервера (400 за период, 403 без компании) или null — тогда на экране общая подпись.
const serverMessage = (err: unknown): string | null =>
  axios.isAxiosError(err) && typeof err.response?.data?.message === 'string' ? err.response.data.message : null;

/**
 * Выписка провайдера из сервиса `ecom` (Р-65, `project_docs/modules/ecom.md` §2). Всё считает сервер: итоги —
 * отдельным `/stats` по всему периоду, а не по загруженным строкам. Страница курсорная, «показать ещё»:
 * общего числа строк у выписки нет намеренно.
 */
export const EcommerceTransactionListPage: React.FC = () => {
  const { tObj } = useLanguage();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const t = tObj.ecommerce;

  // Поля ниже — черновик; запросы идут только по `applied`. При открытии страницы оба совпадают,
  // и выписка за период по умолчанию грузится один раз.
  const [initialFilters] = useState(defaultFilters);
  const [dateFrom, setDateFrom] = useState<Date | null>(initialFilters.dateFrom);
  const [dateTo, setDateTo] = useState<Date | null>(initialFilters.dateTo);
  const [merchantRids, setMerchantRids] = useState<string[]>(initialFilters.merchantRids);
  const [minAmount, setMinAmount] = useState(initialFilters.minAmount);
  const [maxAmount, setMaxAmount] = useState(initialFilters.maxAmount);
  const [search, setSearch] = useState(initialFilters.search);
  const [status, setStatus] = useState<EcomStatus | null>(initialFilters.status);
  const [paymentType, setPaymentType] = useState<EcomPaymentType | null>(initialFilters.paymentType);
  const [applied, setApplied] = useState<Filters>(initialFilters);
  const [pageSize, setPageSize] = useState(PAGE_SIZES[0]);

  const [terminals, setTerminals] = useState<EcomTerminal[]>([]);
  const [orders, setOrders] = useState<EcomOrder[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [stats, setStats] = useState<EcomStats | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<{ message: string | null } | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  // Номер выборки: «показать ещё», начатое до смены фильтров, не должно дописать строки старой выборки.
  const generation = useRef(0);

  const problem = periodProblem(dateFrom, dateTo);

  const draft = useMemo<Filters>(
    () => ({ dateFrom, dateTo, merchantRids, minAmount, maxAmount, search, status, paymentType }),
    [dateFrom, dateTo, merchantRids, minAmount, maxAmount, search, status, paymentType]
  );
  const dirty = !sameFilters(draft, applied);

  // Период применённых фильтров всегда валиден: с ошибкой периода «Применить» недоступна.
  const query = useMemo<EcomQuery | null>(() => (
    !applied.dateFrom || !applied.dateTo
      ? null
      : {
          dateFrom: applied.dateFrom, dateTo: applied.dateTo, merchantRids: applied.merchantRids,
          minAmount: applied.minAmount, maxAmount: applied.maxAmount, query: applied.search,
          status: applied.status, paymentType: applied.paymentType,
        }
  ), [applied]);

  // Итоги зависят только от периода, терминалов и типа оплаты (`modules/ecom.md` §2.5): смена суммы,
  // поиска и статуса их не перезапрашивает.
  const { dateFrom: appliedFrom, dateTo: appliedTo, merchantRids: appliedRids, paymentType: appliedType } = applied;
  const statsQuery = useMemo(() => (
    !appliedFrom || !appliedTo
      ? null
      : { dateFrom: appliedFrom, dateTo: appliedTo, merchantRids: appliedRids, paymentType: appliedType }
  ), [appliedFrom, appliedTo, appliedRids, appliedType]);

  const applyFilters = () => {
    if (!problem && dirty) setApplied(draft);
  };

  const resetFilters = () => {
    const defaults = defaultFilters();
    setDateFrom(defaults.dateFrom);
    setDateTo(defaults.dateTo);
    setMerchantRids(defaults.merchantRids);
    setMinAmount(defaults.minAmount);
    setMaxAmount(defaults.maxAmount);
    setSearch(defaults.search);
    setStatus(defaults.status);
    setPaymentType(defaults.paymentType);
    setApplied(defaults);
  };

  const terminalIndex = useMemo(
    () => new Map(terminals.map(terminal => [terminal.merchantRid, terminal])),
    [terminals]
  );

  // Заказ — панелью поверх выписки, его номер — в адресе: «Назад» браузера закрывает панель, а фильтры и
  // подгруженные строки остаются на месте, без нового прохода по базе шлюза.
  const openOrderId = searchParams.get('order') || null;
  // Номер держится и на время закрытия, иначе панель уезжала бы пустой.
  const lastOrderId = useRef<string | null>(null);
  if (openOrderId) lastOrderId.current = openOrderId;
  const drawerOrderId = openOrderId ?? lastOrderId.current;

  const openOrder = (orderId: string) => {
    const next = new URLSearchParams(searchParams);
    next.set('order', orderId);
    setSearchParams(next, { state: { orderFromList: true } });
  };

  // Деньги в панели сдвинулись — после её закрытия выписка перечитывается, иначе строка останется со старым
  // статусом. Без этого закрытие панели шлюз не трогает.
  const moneyMoved = useRef(false);
  useEffect(() => {
    if (openOrderId === null && moneyMoved.current) {
      moneyMoved.current = false;
      setReloadKey(key => key + 1);
    }
  }, [openOrderId]);

  // Открытую отсюда панель закрывает шаг назад: иначе «Назад» браузера открыл бы её снова. Открытую по
  // ссылке — замена адреса.
  const closeOrder = () => {
    if ((location.state as { orderFromList?: boolean } | null)?.orderFromList) {
      navigate(-1);
      return;
    }
    const next = new URLSearchParams(searchParams);
    next.delete('order');
    setSearchParams(next, { replace: true });
  };

  useEffect(() => {
    const controller = new AbortController();
    fetchEcomTerminals(controller.signal).then(setTerminals).catch(() => setTerminals([]));
    return () => controller.abort();
  }, []);

  useEffect(() => {
    generation.current += 1;
    if (!query) {
      // Период невалиден: запроса не будет, и спиннер от прерванного предыдущего запроса
      // нужно погасить здесь — его `finally` при отмене этого не делает.
      setOrders([]);
      setNextCursor(null);
      setLoading(false);
      setError(null);
      return;
    }
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    fetchEcomPage(query, null, pageSize, controller.signal)
      .then(page => {
        setOrders(page.content);
        setNextCursor(page.nextCursor);
      })
      .catch(err => {
        if (axios.isCancel(err)) return;
        setOrders([]);
        setNextCursor(null);
        setError({ message: serverMessage(err) });
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [query, pageSize, reloadKey]);

  useEffect(() => {
    if (!statsQuery) {
      setStats(null);
      return;
    }
    const controller = new AbortController();
    fetchEcomStats(statsQuery, controller.signal)
      .then(setStats)
      .catch(err => {
        if (!axios.isCancel(err)) setStats(null);
      });
    return () => controller.abort();
  }, [statsQuery, reloadKey]);

  const loadMore = async () => {
    if (!query || !nextCursor) return;
    const started = generation.current;
    setLoadingMore(true);
    try {
      const page = await fetchEcomPage(query, nextCursor, pageSize);
      if (generation.current !== started) return;
      setOrders(prev => [...prev, ...page.content]);
      setNextCursor(page.nextCursor);
    } catch (err) {
      if (generation.current === started) setError({ message: serverMessage(err) });
    } finally {
      setLoadingMore(false);
    }
  };

  const terminalText = (order: EcomOrder) => ecomTerminalLabel(order, terminalIndex).label;

  const statusChip = (order: EcomOrder) => {
    const scheme = getStatusColorScheme(order.status);
    return (
      <Chip
        size="small"
        label={order.status ? t.statuses[order.status] : order.statusRaw || '—'}
        icon={<Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: scheme.main }} />}
        sx={{
          minWidth: 110,
          bgcolor: scheme.light,
          color: scheme.contrastText,
          fontWeight: 600,
          '& .MuiChip-label': { fontSize: '0.7rem' },
          '& .MuiChip-icon': { ml: '8px', mr: '-4px' },
        }}
      />
    );
  };

  const fieldSx = { '& .MuiOutlinedInput-root': { bgcolor: 'background.paper' } };

  return (
    <Box>
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" gutterBottom>
          {t.title}
        </Typography>
        <Typography variant="body1" color="text.secondary">
          {t.subtitle}
        </Typography>
      </Box>

      <Paper
        component="form"
        onSubmit={(e: React.FormEvent) => {
          e.preventDefault();
          applyFilters();
        }}
        elevation={0}
        sx={{ p: 3, mb: 3, border: '1px solid', borderColor: 'divider', borderRadius: 2 }}
      >
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr 2fr' }, gap: 2 }}>
          <TextField
            label={t.periodFrom}
            type="datetime-local"
            value={toLocalInput(dateFrom)}
            onChange={e => setDateFrom(fromLocalInput(e.target.value))}
            InputLabelProps={{ shrink: true }}
            sx={fieldSx}
          />
          <TextField
            label={t.periodTo}
            type="datetime-local"
            value={toLocalInput(dateTo)}
            onChange={e => setDateTo(fromLocalInput(e.target.value))}
            InputLabelProps={{ shrink: true }}
            sx={fieldSx}
          />
          <TextField
            placeholder={t.search}
            value={search}
            onChange={e => setSearch(e.target.value)}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon color="action" />
                </InputAdornment>
              ),
            }}
            sx={fieldSx}
          />
          <TextField
            select
            label={t.terminals}
            value={merchantRids}
            onChange={e => {
              const value = e.target.value as unknown;
              setMerchantRids(Array.isArray(value) ? value : String(value).split(','));
            }}
            SelectProps={{
              multiple: true,
              renderValue: selected => {
                const rids = selected as string[];
                if (rids.length === 0) return <Typography color="text.secondary">{t.allTerminals}</Typography>;
                return rids.map(rid => {
                  const terminal = terminalIndex.get(rid);
                  return terminal ? ecomTerminalName(terminal) : rid;
                }).join(', ');
              },
              displayEmpty: true,
            }}
            InputLabelProps={{ shrink: true }}
            sx={fieldSx}
          >
            {terminals.map(terminal => (
              <MenuItem key={terminal.merchantRid} value={terminal.merchantRid}>
                <Checkbox checked={merchantRids.includes(terminal.merchantRid)} sx={{ mr: 1 }} />
                <ListItemText
                  primary={ecomTerminalName(terminal)}
                  secondary={terminal.title && terminal.title !== ecomTerminalName(terminal) ? terminal.title : undefined}
                  primaryTypographyProps={{ fontFamily: 'monospace', fontWeight: 500 }}
                />
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label={t.minAmount}
            type="number"
            value={minAmount}
            onChange={e => setMinAmount(e.target.value)}
            sx={fieldSx}
          />
          <TextField
            label={t.maxAmount}
            type="number"
            value={maxAmount}
            onChange={e => setMaxAmount(e.target.value)}
            sx={fieldSx}
          />
          {/* Статус отбирает сервер после сборки заказа (Р-87): на экране ничего не отсеивается. */}
          <TextField
            select
            label={t.statusFilter}
            value={status ?? ''}
            onChange={e => setStatus((e.target.value || null) as EcomStatus | null)}
            SelectProps={{ displayEmpty: true }}
            InputLabelProps={{ shrink: true }}
            sx={fieldSx}
          >
            <MenuItem value="">{t.allStatuses}</MenuItem>
            {ECOM_STATUSES.map(value => (
              <MenuItem key={value} value={value}>{t.statuses[value]}</MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label={t.paymentTypeFilter}
            value={paymentType ?? ''}
            onChange={e => setPaymentType((e.target.value || null) as EcomPaymentType | null)}
            SelectProps={{ displayEmpty: true }}
            InputLabelProps={{ shrink: true }}
            sx={fieldSx}
          >
            <MenuItem value="">{t.allPaymentTypes}</MenuItem>
            {ECOM_PAYMENT_TYPES.map(value => (
              <MenuItem key={value} value={value}>{t.paymentTypes[value]}</MenuItem>
            ))}
          </TextField>
        </Box>
        {problem ? (
          <Alert severity="warning" sx={{ mt: 2 }}>
            {problem === 'tooLong' ? t.periodTooLong : t.periodInvalid}
          </Alert>
        ) : (
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.5 }}>
            {t.periodHint}
          </Typography>
        )}
        <Box sx={{ mt: 2, display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
          <Button type="submit" variant="contained" disabled={!!problem || !dirty}>
            {t.applyFilters}
          </Button>
          <Button type="button" variant="text" onClick={resetFilters}>
            {t.resetFilters}
          </Button>
          {dirty && !problem && (
            <Typography variant="body2" color="warning.main">
              {t.filtersChanged}
            </Typography>
          )}
        </Box>
      </Paper>

      {stats && (
        <Paper elevation={0} sx={{ p: 3, mb: 3, border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={4} sx={{ mb: 2 }}>
            <Box>
              <Typography variant="body2" color="text.secondary">{t.stats.orders}</Typography>
              <Typography variant="h4" sx={{ fontWeight: 700 }}>{stats.orderCount}</Typography>
            </Box>
            {stats.totals.map(total => (
              <Stack key={total.currency ?? '—'} direction="row" spacing={4}>
                <Box>
                  <Typography variant="body2" color="text.secondary">{t.stats.captured}</Typography>
                  <Typography variant="h5" sx={{ fontWeight: 700, color: 'success.dark' }}>
                    {formatCurrency(total.capturedAmount, total.currency)}
                  </Typography>
                </Box>
                <Box>
                  <Typography variant="body2" color="text.secondary">{t.stats.refunded}</Typography>
                  <Typography variant="h5" sx={{ fontWeight: 700, color: 'secondary.dark' }}>
                    {formatCurrency(total.refundedAmount, total.currency)}
                  </Typography>
                </Box>
              </Stack>
            ))}
          </Stack>
          {/* Разбивка по статусам — цифры, не кнопки: фильтр статуса — в форме. */}
          <Box sx={{ display: 'flex', flexWrap: 'wrap', columnGap: 3, rowGap: 1 }}>
            {ECOM_STATUSES.filter(value => stats.statusCounts[value] > 0).map(value => (
              <Box key={value} sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: getStatusColorScheme(value).main }} />
                <Typography variant="body2" color="text.secondary">{t.statuses[value]}:</Typography>
                <Typography variant="body2" sx={{ fontWeight: 700 }}>{stats.statusCounts[value]}</Typography>
              </Box>
            ))}
          </Box>
        </Paper>
      )}

      <Box sx={{ mb: 3, display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
        <Button
          variant="contained"
          startIcon={<FileDownloadIcon />}
          disabled={orders.length === 0}
          onClick={() => exportEcomOrdersToExcel(orders, terminalText)}
        >
          {t.exportLoaded}
        </Button>
        <Button variant="outlined" startIcon={<RefreshIcon />} onClick={() => setReloadKey(key => key + 1)}>
          {tObj.common.refresh}
        </Button>
        <TextField
          select
          size="small"
          value={pageSize}
          onChange={e => setPageSize(Number(e.target.value))}
          sx={{ width: 90 }}
        >
          {PAGE_SIZES.map(size => (
            <MenuItem key={size} value={size}>{size}</MenuItem>
          ))}
        </TextField>
        <Typography variant="body2" color="text.secondary">
          {t.loaded}: <b>{orders.length}</b>
        </Typography>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 3 }}>{error.message ?? t.loadFailed}</Alert>}

      <Paper elevation={2}>
        <TableContainer>
          <Table sx={{ minWidth: 1100 }}>
            <TableHead>
              <TableRow sx={{ bgcolor: 'action.hover' }}>
                <TableCell>{t.columns.createdAt}</TableCell>
                <TableCell>{t.columns.orderId}</TableCell>
                <TableCell>{t.columns.ridByMerchant}</TableCell>
                <TableCell>{t.columns.card}</TableCell>
                <TableCell align="right">{t.columns.amount}</TableCell>
                <TableCell align="right">{t.columns.captured}</TableCell>
                <TableCell>{t.columns.status}</TableCell>
                <TableCell>{t.columns.terminal}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {orders.map(order => {
                const terminal = ecomTerminalLabel(order, terminalIndex);
                const open = () => openOrder(order.orderId);
                return (
                  <TableRow
                    key={order.orderId}
                    hover
                    tabIndex={0}
                    onClick={open}
                    onKeyDown={e => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        open();
                      }
                    }}
                    sx={{ cursor: 'pointer' }}
                  >
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>
                      {order.createdAt ? formatDateTime(order.createdAt) : '—'}
                    </TableCell>
                    <TableCell sx={{ fontFamily: 'monospace', fontWeight: 700 }}>{order.orderId}</TableCell>
                    <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{order.ridByMerchant || '—'}</TableCell>
                    <TableCell sx={{ fontFamily: 'monospace' }}>{order.cardMask || '—'}</TableCell>
                    <TableCell align="right">
                      {order.amount === null ? '—' : formatCurrency(order.amount, order.currency)}
                    </TableCell>
                    <TableCell align="right" sx={{ fontWeight: 600 }}>
                      {formatCurrency(order.capturedAmount, order.currency)}
                    </TableCell>
                    <TableCell>
                      {statusChip(order)}
                      {order.providerStatus && (
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.25 }}>
                          {order.providerStatus}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={terminal.label}
                        size="small"
                        variant="outlined"
                        sx={{ fontFamily: 'monospace', fontWeight: 600, fontSize: '0.75rem' }}
                      />
                      {terminal.subLabel && (
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.25 }}>
                          {terminal.subLabel}
                        </Typography>
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
              {orders.length === 0 && !loading && (
                <TableRow>
                  <TableCell colSpan={8} align="center" sx={{ py: 6 }}>
                    {/* С фильтром по статусу пустая страница с курсором — не «ничего нет», а «в просмотренной
                        части нет»: сервер упёрся в потолок просмотра, дальше ищет «показать ещё». */}
                    <Typography color="text.secondary">{nextCursor ? t.noMatchesYet : t.empty}</Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
        {(loading || nextCursor) && (
          <Box sx={{ p: 2, display: 'flex', justifyContent: 'center' }}>
            {loading ? (
              <CircularProgress size={28} />
            ) : (
              <Button variant="outlined" onClick={loadMore} disabled={loadingMore}>
                {loadingMore ? tObj.common.loading : t.loadMore}
              </Button>
            )}
          </Box>
        )}
      </Paper>

      <Drawer
        anchor="right"
        open={openOrderId !== null}
        onClose={closeOrder}
        slotProps={{ paper: { sx: { width: { xs: '100%', md: 'min(1100px, 92vw)' }, p: { xs: 2, md: 3 } } } }}
      >
        {drawerOrderId && (
          <EcomOrderDetails
            orderId={drawerOrderId}
            knownTerminals={terminals}
            onMoneyMoved={() => { moneyMoved.current = true; }}
            actions={
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1 }}>
                <Button
                  component={Link}
                  to={`/transactions/ecommerce/${encodeURIComponent(drawerOrderId)}`}
                  target="_blank"
                  rel="noopener"
                  startIcon={<OpenInNewIcon />}
                >
                  {t.detail.openInNewTab}
                </Button>
                <IconButton aria-label={tObj.common.close} onClick={closeOrder}>
                  <CloseIcon />
                </IconButton>
              </Box>
            }
          />
        )}
      </Drawer>
    </Box>
  );
};
