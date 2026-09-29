import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Alert, Box, Chip, CircularProgress, Stack, Typography } from '@mui/material';
import {
  AttachMoney as MoneyIcon,
  MoneyOff as RefundIcon,
  PointOfSale as TerminalIcon,
  Receipt as ReceiptIcon,
  TrendingUp as TrendingUpIcon,
} from '@mui/icons-material';
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';

import { apiClient } from '../api/client';
import { useLanguage } from '../context/LanguageContext';
import type { DashboardSummary } from '../types/dto';
import { parseTransactionStatus } from '../types/transaction';
import { formatCurrency } from '../utils/format';
import { getLinkStatusColors, parseLinkStatus } from '../utils/payByLinkData';
import { getStatusColorScheme } from '../utils/statusColors';
import { linkStatusLabel, statusLabel } from '../i18n/translations';
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
} from './DashboardParts';

/**
 * Вкладка «Статистика» Pay by Link (Р-91). Сводку считает `pbl` (Р-89): оплаты, созданные в периоде,
 * минус возвраты, проведённые в периоде; статусы — попытки оплаты. Период хранит страница: вкладка
 * размонтируется при переключении.
 */
export const LinkPaymentsStats: React.FC<{ period: PeriodKey; onPeriodChange: (period: PeriodKey) => void }> = ({
  period,
  onPeriodChange,
}) => {
  const { tObj } = useLanguage();
  const t = tObj.linkStats;

  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

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

  const currencies = summary?.totals ?? [];

  return (
    <Box>
      {/* Заголовка нет: его роль играет название вкладки. */}
      <Box sx={{ mb: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 2, flexWrap: 'wrap' }}>
        <Box sx={{ maxWidth: 720 }}>
          <Typography variant="body2" color="text.secondary">{t.subtitle}</Typography>
          {summary && (
            <Typography variant="caption" color="text.secondary">
              {tObj.home.period}: {formatInZone(summary.window.from, summary.window.zone)} — {formatInZone(summary.window.to, summary.window.zone)}
              {' · '}{summary.window.zone}
            </Typography>
          )}
        </Box>
        <Stack direction="row" spacing={1.5} alignItems="center">
          {loading && <CircularProgress size={24} />}
          <PeriodToggle value={period} onChange={onPeriodChange} />
        </Stack>
      </Box>

      {failed && <Alert severity="error" sx={{ mb: 3 }}>{t.loadFailed}</Alert>}
      {summary && currencies.length === 0 && !failed && <Alert severity="info" sx={{ mb: 3 }}>{t.empty}</Alert>}

      {/* Деньги — отдельным блоком на каждую валюту: общий итог поверх валют — число, которого нет. */}
      {summary && currencies.map(totals => (
        <Box key={totals.currency} sx={{ mb: 3 }}>
          <Chip label={totals.currency} size="small" color="primary" sx={{ mb: 2 }} />
          <MetricGrid>
            <MetricCard title={t.metrics.netRevenue} hint={t.metrics.netRevenueHint}
                        value={formatCurrency(Number(totals.netAmount), totals.currency)}
                        icon={<TrendingUpIcon sx={{ fontSize: 32 }} />} color="#2e7d32" />
            <MetricCard title={t.metrics.paidCount} hint={t.metrics.paidCountHint} value={String(totals.paidCount)}
                        icon={<ReceiptIcon sx={{ fontSize: 32 }} />} color="#1976d2" />
            <MetricCard title={t.metrics.refunded} hint={t.metrics.refundedHint}
                        value={formatCurrency(Number(totals.refundedAmount), totals.currency)}
                        icon={<RefundIcon sx={{ fontSize: 32 }} />} color="#9c27b0" />
            <MetricCard title={t.metrics.averagePayment}
                        value={formatCurrency(Number(totals.averagePaidAmount), totals.currency)}
                        icon={<MoneyIcon sx={{ fontSize: 32 }} />} color="#ed6c02" />
          </MetricGrid>
          <DailyNetChart
            title={t.charts.daily}
            currency={totals.currency}
            points={summary.dailyTotals
              .filter(day => day.currency === totals.currency)
              .map(day => ({ date: day.date, net: Number(day.netAmount) }))}
          />
        </Box>
      ))}

      {summary && (
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: 'repeat(3, 1fr)' }, gap: 3 }}>
          {/* Попытки, а не исходы платежей: каждое открытие ссылки заводит операцию, брошенная — FAILED. */}
          <Panel title={t.charts.attempts} hint={t.charts.attemptsHint}>
            <Box sx={{ width: '100%', height: 180, mb: 2 }}>
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Tooltip formatter={(value: any, name: any) => [value, statusLabel(tObj, parseTransactionStatus(name), String(name))]} />
                  <Pie data={summary.statusBreakdown.filter(item => item.count > 0)} cx="50%" cy="50%"
                       innerRadius={50} outerRadius={75} paddingAngle={2} dataKey="count" nameKey="status">
                    {summary.statusBreakdown.filter(item => item.count > 0).map(item => (
                      <Cell key={item.status} fill={getStatusColorScheme(parseTransactionStatus(item.status)).main} />
                    ))}
                  </Pie>
                </PieChart>
              </ResponsiveContainer>
            </Box>
            <Stack spacing={1}>
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
          </Panel>

          <Panel title={t.charts.terminals}>
            <Stack spacing={2.5}>
              {summary.topTerminals.map(terminal => {
                const top = Number(summary.topTerminals.find(item => item.currency === terminal.currency)?.netAmount) || 0;
                return (
                  <Box key={`${terminal.currency}-${terminal.terminalId}`}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                      {/* Подписать нечем — прочерк, а не внутренний id (Р-81). */}
                      <Box>
                        <Typography variant="body2" sx={{ fontWeight: 600, fontFamily: 'monospace' }}>
                          {terminal.terminalRid ?? terminal.terminalLogin ?? terminal.terminalName ?? '—'}
                        </Typography>
                        {terminal.terminalName
                          && terminal.terminalName !== (terminal.terminalRid ?? terminal.terminalLogin) && (
                          <Typography variant="caption" color="text.secondary">{terminal.terminalName}</Typography>
                        )}
                      </Box>
                      <Box sx={{ textAlign: 'right' }}>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>
                          {formatCurrency(Number(terminal.netAmount), terminal.currency)}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">{terminal.transactionCount}</Typography>
                      </Box>
                    </Box>
                    <TopBar width={barWidth(top, Number(terminal.netAmount))} />
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

          <Panel title={`${t.charts.links} · ${summary.paymentLinks.total}`} hint={t.charts.linksHint}>
            <Stack spacing={1.25}>
              {summary.paymentLinks.byStatus.map(item => {
                const parsed = parseLinkStatus(item.status);
                return (
                  <Box key={item.status} sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <Chip size="small" variant="outlined" label={linkStatusLabel(tObj, parsed, item.status)}
                          color={getLinkStatusColors(parsed).color} />
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>{item.count}</Typography>
                  </Box>
                );
              })}
            </Stack>
          </Panel>
        </Box>
      )}
    </Box>
  );
};
