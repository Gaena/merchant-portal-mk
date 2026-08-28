import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import axios from 'axios';
import {
  Alert,
  Box,
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
  Typography,
} from '@mui/material';
import {
  AttachMoney as MoneyIcon,
  MoneyOff as RefundIcon,
  PointOfSale as TerminalIcon,
  Receipt as ReceiptIcon,
  TrendingUp as TrendingUpIcon,
} from '@mui/icons-material';
import {
  Area,
  AreaChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { apiClient } from '../api/client';
import { useLanguage } from '../context/LanguageContext';
import { formatCurrency, formatDateTime } from '../utils/mockData';
import { parseTransactionStatus } from '../types/transaction';
import type { DashboardSummary, DashboardCurrencyTotals } from '../types/dto';

// P3-7: страница больше ничего не считает. До этого она тянула две выборки без пагинации
// (то есть двадцать строк по умолчанию), сводила их в браузере и подписывала результат
// «All system transactions» и «Real…». Теперь всё считает база одним запросом, а окно и часовой
// пояс приходят в ответе — подпись под графиками берётся оттуда, а не из константы.
const RECENT_LIMIT = 10;

const STATUS_COLORS: Record<string, string> = {
  SUCCESS: '#2e7d32',
  AUTHORIZED: '#ed6c02',
  PENDING: '#0288d1',
  REFUNDED: '#9c27b0',
  PARTIALLY_REFUNDED: '#7b1fa2',
  FAILED: '#c62828',
};

export const HomePage: React.FC = () => {
  const navigate = useNavigate();
  const { tObj } = useLanguage();

  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [recent, setRecent] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    Promise.all([
      apiClient.get<DashboardSummary>('/api/v1/dashboard/summary', { signal: controller.signal }),
      // Ровно столько строк, сколько показываем, и с явным размером страницы: без него
      // сервер отдаёт свои двадцать, а таблица молча режет их до десяти. Порядок — серверный
      // (createdAt desc, P3-7), иначе «последние» были бы просто какими-то.
      apiClient.get('/api/v1/transactions', {
        params: { page: 0, size: RECENT_LIMIT },
        signal: controller.signal,
      }),
    ])
      .then(([summaryRes, recentRes]) => {
        setSummary(summaryRes.data);
        setRecent(recentRes.data?.content ?? []);
        setFailed(false);
      })
      .catch(err => {
        if (axios.isCancel(err)) return;
        setFailed(true);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, []);

  const currencies = summary?.totals ?? [];

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 4, display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 700, mb: 0.5 }}>
            {tObj.home.title}
          </Typography>
          <Typography variant="body1" color="text.secondary">
            {tObj.home.subtitle}
          </Typography>
          {summary && (
            <Typography variant="caption" color="text.secondary">
              {tObj.home.period}: {formatDateTime(new Date(summary.window.from))} — {formatDateTime(new Date(summary.window.to))}
              {' · '}{summary.window.zone}
            </Typography>
          )}
        </Box>
        {loading && <CircularProgress size={28} />}
      </Box>

      {failed && <Alert severity="error" sx={{ mb: 3 }}>{tObj.home.loadFailed}</Alert>}

      {summary && currencies.length === 0 && !failed && (
        <Alert severity="info" sx={{ mb: 3 }}>{tObj.home.empty}</Alert>
      )}

      {/* Деньги — отдельным блоком на каждую валюту. Свести их в одну карточку нельзя:
          сложенные манаты с евро дают число, которого не существует. */}
      {currencies.map(totals => (
        <Box key={totals.currency} sx={{ mb: 4 }}>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
            <Chip label={totals.currency} size="small" color="primary" />
            <Typography variant="caption" color="text.secondary">
              {tObj.home.metrics.netRevenueHint}
            </Typography>
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
              title={tObj.home.metrics.netRevenue}
              value={formatCurrency(Number(totals.netAmount), totals.currency)}
              icon={<TrendingUpIcon sx={{ fontSize: 32 }} />}
              color="#2e7d32"
            />
            <MetricCard
              title={tObj.home.metrics.paidCount}
              value={String(totals.paidCount)}
              icon={<ReceiptIcon sx={{ fontSize: 32 }} />}
              color="#1976d2"
            />
            <MetricCard
              title={tObj.home.metrics.refunded}
              value={formatCurrency(Number(totals.refundedAmount), totals.currency)}
              icon={<RefundIcon sx={{ fontSize: 32 }} />}
              color="#9c27b0"
            />
            <MetricCard
              title={tObj.home.metrics.averagePayment}
              value={formatCurrency(Number(totals.averagePaidAmount), totals.currency)}
              icon={<MoneyIcon sx={{ fontSize: 32 }} />}
              color="#ed6c02"
            />
          </Box>

          <DailyChart summary={summary} totals={totals} title={tObj.home.charts.daily} />
        </Box>
      ))}

      {/* Исходы и часы */}
      {summary && (
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'repeat(2, 1fr)' }, gap: 3, mb: 4 }}>
          <Panel title={tObj.home.charts.statuses}>
            <Box sx={{ display: 'flex', flexDirection: { xs: 'column', md: 'row' }, alignItems: 'center', gap: 3 }}>
              <ResponsiveContainer width="50%" height={200}>
                <PieChart>
                  <Tooltip />
                  <Pie
                    data={summary.statusBreakdown.filter(item => item.count > 0)}
                    cx="50%"
                    cy="50%"
                    innerRadius={60}
                    outerRadius={80}
                    paddingAngle={2}
                    dataKey="count"
                    nameKey="status"
                  >
                    {summary.statusBreakdown.filter(item => item.count > 0).map(item => (
                      <Cell key={item.status} fill={STATUS_COLORS[item.status] ?? '#9e9e9e'} />
                    ))}
                  </Pie>
                </PieChart>
              </ResponsiveContainer>
              <Stack spacing={1.5} sx={{ flex: 1 }}>
                {/* Все шесть статусов, включая нулевые: пропущенная доля читается как
                    «такого не бывает», а не как «за период не случилось». */}
                {summary.statusBreakdown.map(item => (
                  <Box key={item.status} sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <Box sx={{ width: 12, height: 12, borderRadius: '50%', bgcolor: STATUS_COLORS[item.status] ?? '#9e9e9e' }} />
                      <Typography variant="body2">{item.status}</Typography>
                    </Box>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>{item.count}</Typography>
                  </Box>
                ))}
              </Stack>
            </Box>
          </Panel>

          <Panel title={tObj.home.charts.hourly}>
            <ResponsiveContainer width="100%" height={250}>
              <LineChart data={summary.hourlyTotals}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e0e0e0" />
                <XAxis dataKey="hour" stroke="#666" style={{ fontSize: '12px' }} />
                <YAxis stroke="#666" allowDecimals={false} style={{ fontSize: '12px' }} />
                <Tooltip />
                <Line type="monotone" dataKey="transactionCount" stroke="#1976d2" strokeWidth={3} dot={{ r: 3 }} />
              </LineChart>
            </ResponsiveContainer>
          </Panel>
        </Box>
      )}

      {/* Терминалы и ссылки */}
      {summary && (
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'repeat(2, 1fr)' }, gap: 3, mb: 4 }}>
          <Panel title={tObj.home.charts.terminals}>
            <Stack spacing={2.5}>
              {summary.topTerminals.map(terminal => (
                <Box key={`${terminal.currency}-${terminal.terminalId}`}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                    {/* Имя из таблицы терминалов; если терминала уже нет — только его номер,
                        без придуманного префикса и подставного имени. */}
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {terminal.terminalName ?? `#${terminal.terminalId}`}
                    </Typography>
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
                  <Typography color="text.secondary">{tObj.home.empty}</Typography>
                </Box>
              )}
            </Stack>
          </Panel>

          <Panel title={`${tObj.home.charts.links} · ${summary.paymentLinks.total}`}>
            {/* Два независимых разбиения, а не один график: тип платежа и тип использования —
                разные оси по одному и тому же набору ссылок, и в общих осях их столбцы
                складывались бы в удвоенное число ссылок. */}
            <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>{tObj.home.charts.byPaymentType}</Typography>
            <Stack spacing={1} sx={{ mb: 3 }}>
              {summary.paymentLinks.byPaymentType.map(item => (
                <CountRow key={item.paymentType} label={item.paymentType} count={item.count} />
              ))}
            </Stack>
            <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>{tObj.home.charts.byUsageType}</Typography>
            <Stack spacing={1}>
              {summary.paymentLinks.byUsageType.map(item => (
                <CountRow key={item.usageType} label={item.usageType} count={item.count} />
              ))}
            </Stack>
          </Panel>
        </Box>
      )}

      {/* Последние платежи */}
      <Paper elevation={0} sx={{ p: 3, border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <Box sx={{ mb: 3, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="h6" sx={{ fontWeight: 600 }}>
            {tObj.home.recentTransactions.title}
          </Typography>
          <Chip label={recent.length} size="small" color="primary" variant="outlined" />
        </Box>

        {recent.length === 0 ? (
          <Box sx={{ py: 5, textAlign: 'center' }}>
            <ReceiptIcon sx={{ fontSize: 40, color: 'text.disabled', mb: 1 }} />
            <Typography color="text.secondary">{tObj.home.recentTransactions.empty}</Typography>
          </Box>
        ) : (
          <TableContainer sx={{ overflowX: 'auto' }}>
            <Table size="small">
              <TableHead>
                <TableRow sx={{ bgcolor: 'rgba(0,0,0,0.02)' }}>
                  <TableCell sx={{ fontWeight: 600 }}>{tObj.home.recentTransactions.id}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{tObj.home.recentTransactions.date}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{tObj.home.recentTransactions.terminal}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{tObj.home.recentTransactions.ip}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{tObj.home.recentTransactions.device}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }} align="right">{tObj.home.recentTransactions.amount}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{tObj.home.recentTransactions.status}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {recent.map((tx: any) => {
                  const parsed = parseTransactionStatus(tx.status);
                  const raw = tx.status === null || tx.status === undefined ? undefined : String(tx.status);
                  return (
                    <TableRow
                      key={tx.id}
                      hover
                      // Без router state: карточка грузит себя сама (GET /api/v1/transactions/{id},
                      // P3-7). Прежний переход собирал историю статусов из двух событий с одним
                      // временем, нулевую комиссию и подставное описание — и карточка рисовала
                      // это как факт.
                      onClick={() => navigate(`/transactions/${tx.id}`)}
                      sx={{ cursor: 'pointer' }}
                    >
                      <TableCell>
                        <Typography variant="caption" sx={{ fontFamily: 'monospace', fontWeight: 700 }}>{tx.id}</Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="caption" color="text.secondary">
                          {tx.createdAt ? formatDateTime(new Date(tx.createdAt)) : '—'}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="caption">{tx.terminalId ?? '—'}</Typography>
                      </TableCell>
                      {/* Настоящее значение или прочерк: прежде вместо незаписанного
                          подставлялись адрес и браузер по умолчанию. */}
                      <TableCell>
                        <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>{tx.clientIp || '—'}</Typography>
                      </TableCell>
                      <TableCell sx={{ maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        <Typography variant="caption" color="text.secondary">{tx.userAgent || '—'}</Typography>
                      </TableCell>
                      <TableCell align="right">
                        <Typography variant="caption" sx={{ fontWeight: 700 }}>
                          {formatCurrency(Number(tx.amount) || 0, tx.currency)}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={parsed ?? raw ?? '—'}
                          size="small"
                          sx={{ fontWeight: 700, fontSize: '0.65rem', height: 20,
                                color: '#fff', bgcolor: STATUS_COLORS[parsed ?? ''] ?? '#9e9e9e' }}
                        />
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

const Panel: React.FC<{ title: string; children: React.ReactNode }> = ({ title, children }) => (
  <Paper elevation={0} sx={{ p: 3, border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
    <Typography variant="h6" sx={{ fontWeight: 600, mb: 3 }}>{title}</Typography>
    {children}
  </Paper>
);

const MetricCard: React.FC<{ title: string; value: string; icon: React.ReactNode; color: string }> = ({
  title, value, icon, color,
}) => (
  <Card elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
    <CardContent>
      <Box sx={{ p: 1.5, mb: 2, width: 'fit-content', borderRadius: 2, bgcolor: `${color}1a`, color }}>{icon}</Box>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>{title}</Typography>
      <Typography variant="h5" sx={{ fontWeight: 700 }}>{value}</Typography>
    </CardContent>
  </Card>
);

const CountRow: React.FC<{ label: string; count: number }> = ({ label, count }) => (
  <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
    <Typography variant="body2">{label}</Typography>
    <Typography variant="body2" sx={{ fontWeight: 600 }}>{count}</Typography>
  </Box>
);

// Один график на валюту: общая ось Y для манатов и евро — то же сложение разных денег,
// только нарисованное.
const DailyChart: React.FC<{ summary: DashboardSummary; totals: DashboardCurrencyTotals; title: string }> = ({
  summary, totals, title,
}) => {
  const data = summary.dailyTotals
    .filter(day => day.currency === totals.currency)
    .map(day => ({ date: day.date.slice(5), net: Number(day.netAmount), count: day.transactionCount }));

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

// Доля самого крупного терминала этой валюты. Ширина полосы — оформление, и сравнивается
// только внутри одной валюты.
function terminalWidth(summary: DashboardSummary, currency: string, netAmount: string): string {
  const sameCurrency = summary.topTerminals.filter(item => item.currency === currency);
  const top = Number(sameCurrency[0]?.netAmount) || 0;
  if (top <= 0) return '5%';
  return `${Math.min(100, Math.max(5, (Number(netAmount) / top) * 100))}%`;
}
