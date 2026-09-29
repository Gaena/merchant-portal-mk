import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import axios from 'axios';
import {
  Alert,
  Box,
  Button,
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
  OpenInNew as OpenIcon,
  PointOfSale as TerminalIcon,
  Receipt as ReceiptIcon,
  TrendingUp as TrendingUpIcon,
} from '@mui/icons-material';

import { useAuth } from '../context/AuthContext';
import { useLanguage } from '../context/LanguageContext';
import { ECOM_STATUSES, type EcomDashboard, type EcomOrder, type EcomTerminal } from '../types/ecom';
import { ecomTerminalLabel, ecomTerminalName, fetchEcomDashboard, fetchEcomPage, fetchEcomTerminals } from '../utils/ecom';
import { formatCurrency, formatDateTime } from '../utils/format';
import { getStatusColorScheme } from '../utils/statusColors';
import {
  barWidth,
  DailyNetChart,
  formatInZone,
  MetricCard,
  MetricGrid,
  Panel,
  PeriodToggle,
  periodWindow,
  TopBar,
  type PeriodKey,
} from '../components/DashboardParts';

const RECENT_LIMIT = 10;

/**
 * Главная (Р-91): оплаты картой по мерчантам логина компании из выписки `ecom`, у SYSTEM_ADMIN и AUDITOR —
 * по мерчантам всех компаний (Р-97). Цифры совпадают с вкладкой E-commerce за тот же период.
 * Каждое открытие — проход по периоду на боевой базе шлюза.
 */
export const HomePage: React.FC = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { tObj } = useLanguage();
  const t = tObj.home;
  const global = user?.role === 'SYSTEM_ADMIN' || user?.role === 'AUDITOR';

  const [period, setPeriod] = useState<PeriodKey>('days7');
  const [summary, setSummary] = useState<EcomDashboard | null>(null);
  const [recent, setRecent] = useState<EcomOrder[]>([]);
  const [terminals, setTerminals] = useState<EcomTerminal[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [recentFailed, setRecentFailed] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    const { from, to } = periodWindow(period);
    setLoading(true);
    fetchEcomDashboard(from, to, controller.signal)
      .then(data => {
        setSummary(data);
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
    // Последние заказы — первая страница выписки за период; порядок серверный, новые сверху.
    fetchEcomPage({
      dateFrom: from, dateTo: to, merchantRids: [], minAmount: '', maxAmount: '', query: '', status: null, paymentType: null,
    }, null, RECENT_LIMIT, controller.signal)
      .then(page => {
        setRecent(page.content);
        setRecentFailed(false);
      })
      .catch(err => {
        if (axios.isCancel(err)) return;
        setRecent([]);
        setRecentFailed(true);
      });
    return () => controller.abort();
  }, [period]);

  // Подписи терминалов — из нашей базы, без шлюза.
  useEffect(() => {
    const controller = new AbortController();
    fetchEcomTerminals(controller.signal).then(setTerminals).catch(() => setTerminals([]));
    return () => controller.abort();
  }, []);

  const terminalIndex = useMemo(() => new Map(terminals.map(terminal => [terminal.merchantRid, terminal])), [terminals]);
  const currencies = summary?.totals ?? [];
  const statusTotal = summary ? ECOM_STATUSES.reduce((sum, status) => sum + summary.statusCounts[status], 0) : 0;

  return (
    <Box>
      <Box sx={{ mb: 3, display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 2, flexWrap: 'wrap' }}>
        <Box sx={{ maxWidth: 760 }}>
          <Typography variant="h4" sx={{ fontWeight: 700, mb: 0.5 }}>{t.title}</Typography>
          <Typography variant="body1" color="text.secondary">{global ? t.subtitleAll : t.subtitle}</Typography>
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
            <PeriodToggle value={period} onChange={setPeriod} />
          </Stack>
          <Button size="small" endIcon={<OpenIcon />} onClick={() => navigate('/transactions/ecommerce')}>
            {t.openStatement}
          </Button>
        </Stack>
      </Box>

      {failed && <Alert severity="error" sx={{ mb: 3 }}>{t.loadFailed}</Alert>}
      {summary && currencies.length === 0 && !failed && <Alert severity="info" sx={{ mb: 3 }}>{t.empty}</Alert>}

      {/* Деньги — отдельным блоком на каждую валюту: заказ без валюты у провайдера — свой блок без знака. */}
      {summary && currencies.map(totals => (
        <Box key={totals.currency ?? '—'} sx={{ mb: 4 }}>
          <Chip label={totals.currency ?? '—'} size="small" color="primary" sx={{ mb: 2 }} />
          <MetricGrid>
            <MetricCard title={t.metrics.netRevenue} hint={t.metrics.netRevenueHint}
                        value={formatCurrency(totals.netAmount, totals.currency)}
                        icon={<TrendingUpIcon sx={{ fontSize: 32 }} />} color="#2e7d32" />
            <MetricCard title={t.metrics.paidCount} hint={t.metrics.paidCountHint} value={String(totals.paidCount)}
                        icon={<ReceiptIcon sx={{ fontSize: 32 }} />} color="#1976d2" />
            <MetricCard title={t.metrics.refunded} hint={t.metrics.refundedHint}
                        value={formatCurrency(totals.refundedAmount, totals.currency)}
                        icon={<RefundIcon sx={{ fontSize: 32 }} />} color="#9c27b0" />
            <MetricCard title={t.metrics.averagePayment}
                        value={formatCurrency(totals.averagePaidAmount, totals.currency)}
                        icon={<MoneyIcon sx={{ fontSize: 32 }} />} color="#ed6c02" />
          </MetricGrid>
          <DailyNetChart
            title={t.charts.daily}
            currency={totals.currency}
            points={summary.dailyTotals
              .filter(day => day.currency === totals.currency)
              .map(day => ({ date: day.date, net: day.netAmount }))}
          />
        </Box>
      ))}

      {summary && (
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'repeat(2, 1fr)' }, gap: 3, mb: 4 }}>
          <Panel title={`${t.charts.statuses} · ${statusTotal}`} hint={t.charts.statusesHint}>
            <Stack spacing={1}>
              {ECOM_STATUSES.map(status => (
                <Box key={status} sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Box sx={{ width: 12, height: 12, borderRadius: '50%', bgcolor: getStatusColorScheme(status).main }} />
                    <Typography variant="body2">{tObj.ecommerce.statuses[status]}</Typography>
                  </Box>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>{summary.statusCounts[status]}</Typography>
                </Box>
              ))}
            </Stack>
          </Panel>

          <Panel title={t.charts.terminals}>
            <Stack spacing={2.5}>
              {summary.topTerminals.map(terminal => {
                const top = summary.topTerminals.find(item => item.currency === terminal.currency)?.netAmount ?? 0;
                const label = ecomTerminalName(terminal);
                return (
                  <Box key={`${terminal.currency}-${terminal.merchantRid}`}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                      <Box>
                        <Typography variant="body2" sx={{ fontWeight: 600, fontFamily: 'monospace' }}>{label}</Typography>
                        {terminal.title && terminal.title !== label && (
                          <Typography variant="caption" color="text.secondary">{terminal.title}</Typography>
                        )}
                      </Box>
                      <Box sx={{ textAlign: 'right' }}>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>
                          {formatCurrency(terminal.netAmount, terminal.currency)}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">{terminal.orderCount}</Typography>
                      </Box>
                    </Box>
                    <TopBar width={barWidth(top, terminal.netAmount)} />
                  </Box>
                );
              })}
              {summary.topTerminals.length === 0 && (
                <Box sx={{ py: 4, textAlign: 'center' }}>
                  <TerminalIcon sx={{ fontSize: 40, color: 'text.disabled', mb: 1 }} />
                  <Typography color="text.secondary">{t.empty}</Typography>
                </Box>
              )}
            </Stack>
          </Panel>
        </Box>
      )}

      <Paper elevation={0} sx={{ p: 3, border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <Typography variant="h6" sx={{ fontWeight: 600, mb: 3 }}>{t.recentOrders.title}</Typography>
        {recent.length === 0 ? (
          <Box sx={{ py: 5, textAlign: 'center' }}>
            <ReceiptIcon sx={{ fontSize: 40, color: 'text.disabled', mb: 1 }} />
            <Typography color="text.secondary">{recentFailed ? tObj.common.loadFailed : t.recentOrders.empty}</Typography>
          </Box>
        ) : (
          <TableContainer sx={{ overflowX: 'auto' }}>
            <Table size="small">
              <TableHead>
                <TableRow sx={{ bgcolor: 'rgba(0,0,0,0.02)' }}>
                  <TableCell sx={{ fontWeight: 600 }}>{tObj.ecommerce.columns.createdAt}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{tObj.ecommerce.columns.orderId}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{tObj.ecommerce.columns.ridByMerchant}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{tObj.ecommerce.columns.terminal}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }} align="right">{tObj.ecommerce.columns.captured}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{tObj.ecommerce.columns.status}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {recent.map(order => {
                  const scheme = getStatusColorScheme(order.status);
                  return (
                    <TableRow
                      key={order.orderId}
                      hover
                      onClick={() => navigate(`/transactions/ecommerce/${encodeURIComponent(order.orderId)}`)}
                      sx={{ cursor: 'pointer' }}
                    >
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>
                        <Typography variant="caption" color="text.secondary">
                          {order.createdAt ? formatDateTime(order.createdAt) : '—'}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="caption" sx={{ fontFamily: 'monospace', fontWeight: 700 }}>{order.orderId}</Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>{order.ridByMerchant || '—'}</Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                          {ecomTerminalLabel(order, terminalIndex).label}
                        </Typography>
                      </TableCell>
                      <TableCell align="right">
                        <Typography variant="caption" sx={{ fontWeight: 700 }}>
                          {formatCurrency(order.capturedAmount, order.currency)}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip
                          size="small"
                          label={order.status ? tObj.ecommerce.statuses[order.status] : order.statusRaw || '—'}
                          sx={{ fontWeight: 700, fontSize: '0.65rem', height: 20, color: scheme.contrastText, bgcolor: scheme.light }}
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
