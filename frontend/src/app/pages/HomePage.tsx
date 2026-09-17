import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import axios from 'axios';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import {
  AttachMoney as MoneyIcon,
  MoneyOff as RefundIcon,
  OpenInNew as OpenIcon,
  PointOfSale as TerminalIcon,
  Receipt as ReceiptIcon,
  TrendingUp as TrendingUpIcon,
} from '@mui/icons-material';
import {
  Area,
  AreaChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { apiClient } from '../api/client';
import { useLanguage } from '../context/LanguageContext';
import { formatCurrency, formatDateTime } from '../utils/format';
import { parseTransactionStatus } from '../types/transaction';
import type { DashboardSummary, DashboardCurrencyTotals, TerminalOptionDto } from '../types/dto';
import { buildTerminalIndex, terminalLabel } from '../utils/terminals';
import { getStatusColorScheme } from '../utils/statusColors';
import { parseLinkStatus, getLinkStatusColors } from '../utils/payByLinkData';
import { linkStatusLabel, statusLabel, type Language } from '../i18n/translations';

// P3-7: страница ничего не считает — сводку отдаёт `GET /api/v1/dashboard/summary`, окно и часовой пояс
// приходят в ответе. Р-89: панель — по оплатам платёжных ссылок портала (весь эквайринг — выписка
// E-commerce), возвраты вычитаются по дате возврата, период выбирается.
const RECENT_LIMIT = 10;

type PeriodKey = 'today' | 'days7' | 'days30' | 'days90';

const PERIOD_DAYS: Record<PeriodKey, number> = { today: 1, days7: 7, days30: 30, days90: 90 };

// Целые сутки, включая сегодняшние, от полуночи браузера: на графике — целые столбики. Потолок
// бэкенда — 92 дня, 90 в него укладываются.
const periodWindow = (key: PeriodKey): { from: Date; to: Date } => {
  const from = new Date();
  from.setDate(from.getDate() - (PERIOD_DAYS[key] - 1));
  from.setHours(0, 0, 0, 0);
  return { from, to: new Date() };
};

const LOCALES: Record<Language, string> = { en: 'en-GB', az: 'az-Latn-AZ', ru: 'ru-RU' };

export const HomePage: React.FC = () => {
  const navigate = useNavigate();
  const { tObj, language } = useLanguage();
  const t = tObj.home;

  const [period, setPeriod] = useState<PeriodKey>('days7');
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [recent, setRecent] = useState<any[]>([]);
  // Транзакция несёт только `terminalId`; подпись терминала — его логин, и он приходит
  // отдельным лёгким фидом (см. `utils/terminals.ts`).
  const [terminalIndex, setTerminalIndex] = useState<Record<number, TerminalOptionDto>>({});
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [recentFailed, setRecentFailed] = useState(false);

  // Сводка — за выбранный период.
  useEffect(() => {
    const controller = new AbortController();
    const { from, to } = periodWindow(period);
    setLoading(true);
    apiClient.get<DashboardSummary>('/api/v1/dashboard/summary', {
      params: { from: from.toISOString(), to: to.toISOString() },
      signal: controller.signal,
    })
      .then(res => {
        setSummary(res.data);
        setFailed(false);
      })
      .catch(err => {
        if (axios.isCancel(err)) return;
        setSummary(null);
        setFailed(true);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [period]);

  // Последние платежи от периода не зависят: это первая страница списка, порядок серверный
  // (createdAt desc, P3-7), размер — явный.
  useEffect(() => {
    const controller = new AbortController();
    apiClient.get('/api/v1/transactions', { params: { page: 0, size: RECENT_LIMIT }, signal: controller.signal })
      .then(res => {
        setRecent(res.data?.content ?? []);
        setRecentFailed(false);
      })
      .catch(err => {
        if (axios.isCancel(err)) return;
        setRecent([]);
        setRecentFailed(true);
      });
    apiClient.get('/api/v1/terminals/options', { signal: controller.signal })
      .then(res => setTerminalIndex(buildTerminalIndex(res.data)))
      .catch(() => {});
    return () => controller.abort();
  }, []);

  const currencies = summary?.totals ?? [];
  const locale = LOCALES[language];

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 3, display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 2, flexWrap: 'wrap' }}>
        <Box sx={{ maxWidth: 760 }}>
          <Typography variant="h4" sx={{ fontWeight: 700, mb: 0.5 }}>
            {t.title}
          </Typography>
          <Typography variant="body1" color="text.secondary">
            {t.subtitle}
          </Typography>
          {summary && (
            <Typography variant="caption" color="text.secondary">
              {t.period}: {formatInZone(summary.window.from, summary.window.zone)} — {formatInZone(summary.window.to, summary.window.zone)}
              {' · '}{summary.window.zone}
            </Typography>
          )}
        </Box>
        <Stack spacing={1.5} alignItems={{ xs: 'flex-start', md: 'flex-end' }}>
          <Stack direction="row" spacing={1.5} alignItems="center">
            {loading && <CircularProgress size={24} />}
            <ToggleButtonGroup
              size="small"
              exclusive
              value={period}
              onChange={(_, value: PeriodKey | null) => { if (value) setPeriod(value); }}
            >
              {(Object.keys(PERIOD_DAYS) as PeriodKey[]).map(key => (
                <ToggleButton key={key} value={key}>{t.periods[key]}</ToggleButton>
              ))}
            </ToggleButtonGroup>
          </Stack>
          <Button size="small" endIcon={<OpenIcon />} onClick={() => navigate('/transactions/ecommerce')}>
            {t.openStatement}
          </Button>
        </Stack>
      </Box>

      {failed && <Alert severity="error" sx={{ mb: 3 }}>{t.loadFailed}</Alert>}

      {summary && currencies.length === 0 && !failed && (
        <Alert severity="info" sx={{ mb: 3 }}>{t.empty}</Alert>
      )}

      {/* Деньги — отдельным блоком на каждую валюту. Свести их в одну карточку нельзя:
          сложенные манаты с евро дают число, которого не существует. */}
      {summary && currencies.map(totals => (
        <Box key={totals.currency} sx={{ mb: 4 }}>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
            <Chip label={totals.currency} size="small" color="primary" />
          </Stack>

          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(4, 1fr)' },
              gap: 3,
              mb: 3,
            }}
          >
            <MetricCard
              title={t.metrics.netRevenue}
              hint={t.metrics.netRevenueHint}
              value={formatCurrency(Number(totals.netAmount), totals.currency)}
              icon={<TrendingUpIcon sx={{ fontSize: 32 }} />}
              color="#2e7d32"
            />
            <MetricCard
              title={t.metrics.paidCount}
              hint={t.metrics.paidCountHint}
              value={String(totals.paidCount)}
              icon={<ReceiptIcon sx={{ fontSize: 32 }} />}
              color="#1976d2"
            />
            <MetricCard
              title={t.metrics.refunded}
              hint={t.metrics.refundedHint}
              value={formatCurrency(Number(totals.refundedAmount), totals.currency)}
              icon={<RefundIcon sx={{ fontSize: 32 }} />}
              color="#9c27b0"
            />
            <MetricCard
              title={t.metrics.averagePayment}
              value={formatCurrency(Number(totals.averagePaidAmount), totals.currency)}
              icon={<MoneyIcon sx={{ fontSize: 32 }} />}
              color="#ed6c02"
            />
          </Box>

          <DailyChart summary={summary} totals={totals} title={t.charts.daily} locale={locale} />
        </Box>
      ))}

      {summary && (
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'repeat(3, 1fr)' }, gap: 3, mb: 4 }}>
          {/* Попытки, а не исходы платежей: каждое открытие ссылки заводит операцию, брошенная
              становится FAILED. Цвета — общие для всего приложения (`utils/statusColors.ts`). */}
          <Panel title={t.charts.attempts} hint={t.charts.attemptsHint}>
            <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'stretch', gap: 2 }}>
              <Box sx={{ width: '100%', height: 180 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Tooltip formatter={(value: any, name: any) => [value, statusLabel(tObj, parseTransactionStatus(name), String(name))]} />
                    <Pie
                      data={summary.statusBreakdown.filter(item => item.count > 0)}
                      cx="50%"
                      cy="50%"
                      innerRadius={50}
                      outerRadius={75}
                      paddingAngle={2}
                      dataKey="count"
                      nameKey="status"
                    >
                      {summary.statusBreakdown.filter(item => item.count > 0).map(item => (
                        <Cell key={item.status} fill={getStatusColorScheme(parseTransactionStatus(item.status)).main} />
                      ))}
                    </Pie>
                  </PieChart>
                </ResponsiveContainer>
              </Box>
              <Stack spacing={1}>
                {/* Все шесть статусов, включая нулевые: пропущенная доля читается как
                    «такого не бывает», а не как «за период не случилось». */}
                {summary.statusBreakdown.map(item => (
                  <Box key={item.status} sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <Box sx={{ width: 12, height: 12, borderRadius: '50%', bgcolor: getStatusColorScheme(parseTransactionStatus(item.status)).main }} />
                      <Typography variant="body2">{statusLabel(tObj, parseTransactionStatus(item.status), item.status)}</Typography>
                    </Box>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>{item.count}</Typography>
                  </Box>
                ))}
              </Stack>
            </Box>
          </Panel>

          <Panel title={t.charts.terminals}>
            <Stack spacing={2.5}>
              {summary.topTerminals.map(terminal => (
                <Box key={`${terminal.currency}-${terminal.terminalId}`}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                    {/* Логин — по нему мерчант терминал и опознаёт; имя — пояснение. Терминала,
                        которого уже нет, подписать нечем: прочерк, а не номер (Р-81). */}
                    <Box>
                      <Typography variant="body2" sx={{ fontWeight: 600, fontFamily: 'monospace' }}>
                        {terminal.terminalLogin ?? terminal.terminalName ?? '—'}
                      </Typography>
                      {terminal.terminalName && terminal.terminalName !== terminal.terminalLogin && (
                        <Typography variant="caption" color="text.secondary">
                          {terminal.terminalName}
                        </Typography>
                      )}
                    </Box>
                    <Box sx={{ textAlign: 'right' }}>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>
                        {formatCurrency(Number(terminal.netAmount), terminal.currency)}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {terminal.transactionCount}
                      </Typography>
                    </Box>
                  </Box>
                  <Box sx={{ height: 6, bgcolor: '#e0e0e0', borderRadius: 1, overflow: 'hidden' }}>
                    <Box sx={{ height: '100%', bgcolor: '#1976d2', width: terminalWidth(summary, terminal.currency, terminal.netAmount) }} />
                  </Box>
                </Box>
              ))}
              {summary.topTerminals.length === 0 && (
                <Box sx={{ py: 4, textAlign: 'center' }}>
                  <TerminalIcon sx={{ fontSize: 40, color: 'text.disabled', mb: 1 }} />
                  <Typography color="text.secondary">{t.empty}</Typography>
                </Box>
              )}
            </Stack>
          </Panel>

          {/* Созданные за период ссылки по их текущему статусу: сколько оплачено, истекло, отменено.
              Разбивка по типам (SMS/DMS, одно- и многоразовые) о работе ссылок ничего не говорила. */}
          <Panel title={`${t.charts.links} · ${summary.paymentLinks.total}`} hint={t.charts.linksHint}>
            <Stack spacing={1.25}>
              {summary.paymentLinks.byStatus.map(item => {
                const parsed = parseLinkStatus(item.status);
                return (
                  <Box key={item.status} sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <Chip
                      size="small"
                      label={linkStatusLabel(tObj, parsed, item.status)}
                      color={getLinkStatusColors(parsed).color}
                      variant="outlined"
                    />
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>{item.count}</Typography>
                  </Box>
                );
              })}
            </Stack>
          </Panel>
        </Box>
      )}

      {/* Последние платежи */}
      <Paper elevation={0} sx={{ p: 3, border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <Typography variant="h6" sx={{ fontWeight: 600, mb: 3 }}>
          {t.recentTransactions.title}
        </Typography>

        {/* «Платежей ещё нет» — только когда список действительно пуст, а не когда он не загрузился. */}
        {recent.length === 0 ? (
          <Box sx={{ py: 5, textAlign: 'center' }}>
            <ReceiptIcon sx={{ fontSize: 40, color: 'text.disabled', mb: 1 }} />
            <Typography color="text.secondary">
              {recentFailed ? tObj.common.loadFailed : t.recentTransactions.empty}
            </Typography>
          </Box>
        ) : (
          <TableContainer sx={{ overflowX: 'auto' }}>
            <Table size="small">
              <TableHead>
                <TableRow sx={{ bgcolor: 'rgba(0,0,0,0.02)' }}>
                  <TableCell sx={{ fontWeight: 600 }}>{t.recentTransactions.providerOrderId}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{t.recentTransactions.ridByMerchant}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{t.recentTransactions.date}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{t.recentTransactions.terminal}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }} align="right">{t.recentTransactions.amount}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{t.recentTransactions.status}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{t.recentTransactions.id}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {recent.map((tx: any) => {
                  const parsed = parseTransactionStatus(tx.status);
                  const raw = tx.status === null || tx.status === undefined ? undefined : String(tx.status);
                  const scheme = getStatusColorScheme(parsed);
                  return (
                    <TableRow
                      key={tx.id}
                      hover
                      // Без router state: карточка грузит себя сама (GET /api/v1/transactions/{id}, P3-7).
                      onClick={() => navigate(`/transactions/${tx.id}`)}
                      sx={{ cursor: 'pointer' }}
                    >
                      <TableCell>
                        <Typography variant="caption" sx={{ fontFamily: 'monospace', fontWeight: 700 }}>
                          {tx.providerOrderId || '—'}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="caption" sx={{ fontFamily: 'monospace', fontWeight: 700 }}>
                          {tx.ridByMerchant || '—'}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="caption" color="text.secondary">
                          {tx.createdAt ? formatDateTime(new Date(tx.createdAt)) : '—'}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                          {terminalLabel({
                            terminalLogin: terminalIndex[tx.terminalId]?.login,
                            terminalName: terminalIndex[tx.terminalId]?.name,
                            terminalId: tx.terminalId
                          })}
                        </Typography>
                      </TableCell>
                      <TableCell align="right">
                        <Typography variant="caption" sx={{ fontWeight: 700 }}>
                          {formatCurrency(Number(tx.amount) || 0, tx.currency)}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={statusLabel(tObj, parsed, raw)}
                          size="small"
                          sx={{ fontWeight: 700, fontSize: '0.65rem', height: 20,
                                color: scheme.contrastText, bgcolor: scheme.light }}
                        />
                      </TableCell>
                      <TableCell>
                        <Typography
                          variant="caption"
                          color="text.secondary"
                          sx={{ fontFamily: 'monospace', fontSize: '0.68rem' }}
                        >
                          {tx.id}
                        </Typography>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>
    </Box>
  );
};

// ─── мелкие куски разметки ────────────────────────────────────────────────────

const Panel: React.FC<{ title: string; hint?: string; children: React.ReactNode }> = ({ title, hint, children }) => (
  <Paper elevation={0} sx={{ p: 3, border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
    <Typography variant="h6" sx={{ fontWeight: 600, mb: hint ? 0.5 : 3 }}>{title}</Typography>
    {hint && (
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 2.5 }}>{hint}</Typography>
    )}
    {children}
  </Paper>
);

const MetricCard: React.FC<{ title: string; hint?: string; value: string; icon: React.ReactNode; color: string }> = ({
  title, hint, value, icon, color,
}) => (
  <Card elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
    <CardContent>
      <Box sx={{ p: 1.5, mb: 2, width: 'fit-content', borderRadius: 2, bgcolor: `${color}1a`, color }}>{icon}</Box>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>{title}</Typography>
      <Typography variant="h5" sx={{ fontWeight: 700 }}>{value}</Typography>
      {hint && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>{hint}</Typography>
      )}
    </CardContent>
  </Card>
);

// Один график на валюту: общая ось Y для манатов и евро — то же сложение разных денег,
// только нарисованное. Дата подписи — на языке интерфейса, а не `09-15`.
const DailyChart: React.FC<{ summary: DashboardSummary; totals: DashboardCurrencyTotals; title: string; locale: string }> = ({
  summary, totals, title, locale,
}) => {
  const formatDay = useMemo(
    () => new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short', timeZone: 'UTC' }),
    [locale]
  );
  const data = summary.dailyTotals
    .filter(day => day.currency === totals.currency)
    // `date` — календарная дата пояса отчёта без времени; читаем её как полночь UTC и форматируем в UTC,
    // чтобы пояс браузера не сдвинул подпись на соседний день.
    .map(day => ({ date: formatDay.format(new Date(`${day.date}T00:00:00Z`)), net: Number(day.netAmount), count: day.transactionCount }));

  return (
    <Panel title={`${title} · ${totals.currency}`}>
      <ResponsiveContainer width="100%" height={280}>
        <AreaChart data={data}>
          <CartesianGrid strokeDasharray="3 3" stroke="#e0e0e0" />
          <XAxis dataKey="date" stroke="#666" style={{ fontSize: '12px' }} />
          <YAxis stroke="#666" style={{ fontSize: '12px' }} />
          <Tooltip formatter={(value: any) => formatCurrency(Number(value), totals.currency)} />
          <Area type="monotone" dataKey="net" stroke="#1976d2" strokeWidth={2} fill="#1976d2" fillOpacity={0.15} />
        </AreaChart>
      </ResponsiveContainer>
    </Panel>
  );
};

// Границы периода — в зоне, в которой их считал сервер (`window.zone`), а не в зоне браузера:
// иначе подпись «Asia/Baku» стояла бы рядом с временем из другого пояса.
function formatInZone(iso: string, zone: string): string {
  const date = new Date(iso);
  try {
    return new Intl.DateTimeFormat('en-GB', {
      timeZone: zone, day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit',
    }).format(date);
  } catch {
    return formatDateTime(date);
  }
}

// Доля самого крупного терминала этой валюты. Ширина полосы — оформление, и сравнивается
// только внутри одной валюты; отрицательная выручка (одни возвраты за период) — минимальная полоса.
function terminalWidth(summary: DashboardSummary, currency: string, netAmount: string): string {
  const sameCurrency = summary.topTerminals.filter(item => item.currency === currency);
  const top = Number(sameCurrency[0]?.netAmount) || 0;
  if (top <= 0) return '5%';
  return `${Math.min(100, Math.max(5, (Number(netAmount) / top) * 100))}%`;
}
