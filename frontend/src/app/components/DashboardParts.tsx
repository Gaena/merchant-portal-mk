import React, { useMemo } from 'react';
import { Box, Card, CardContent, Paper, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material';
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useLanguage } from '../context/LanguageContext';
import type { Language } from '../i18n/translations';
import { formatCurrency, formatDateTime } from '../utils/format';

/**
 * Общие куски двух панелей (Р-91): главной — по выписке провайдера, и статистики оплат по ссылкам во вкладке
 * Pay by Link. Считают обе на сервере; здесь только разметка.
 */

export type PeriodKey = 'today' | 'days7' | 'days30' | 'days90';

const PERIOD_DAYS: Record<PeriodKey, number> = { today: 1, days7: 7, days30: 30, days90: 90 };

// Целые сутки, включая сегодняшние, от полуночи браузера: на графике — целые столбики. Потолок обоих
// бэкендов — 92 дня, 90 в него укладываются.
export const periodWindow = (key: PeriodKey): { from: Date; to: Date } => {
  const from = new Date();
  from.setDate(from.getDate() - (PERIOD_DAYS[key] - 1));
  from.setHours(0, 0, 0, 0);
  return { from, to: new Date() };
};

export const PeriodToggle: React.FC<{ value: PeriodKey; onChange: (value: PeriodKey) => void }> = ({ value, onChange }) => {
  const { tObj } = useLanguage();
  return (
    <ToggleButtonGroup
      size="small"
      exclusive
      value={value}
      onChange={(_, next: PeriodKey | null) => { if (next) onChange(next); }}
    >
      {(Object.keys(PERIOD_DAYS) as PeriodKey[]).map(key => (
        <ToggleButton key={key} value={key}>{tObj.common.periods[key]}</ToggleButton>
      ))}
    </ToggleButtonGroup>
  );
};

export const LOCALES: Record<Language, string> = { en: 'en-GB', az: 'az-Latn-AZ', ru: 'ru-RU' };

export const Panel: React.FC<{ title: string; hint?: string; children: React.ReactNode }> = ({ title, hint, children }) => (
  <Paper elevation={0} sx={{ p: 3, border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
    <Typography variant="h6" sx={{ fontWeight: 600, mb: hint ? 0.5 : 3 }}>{title}</Typography>
    {hint && (
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 2.5 }}>{hint}</Typography>
    )}
    {children}
  </Paper>
);

export const MetricCard: React.FC<{ title: string; hint?: string; value: string; icon: React.ReactNode; color: string }> = ({
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

export const MetricGrid: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(4, 1fr)' }, gap: 3, mb: 3 }}>
    {children}
  </Box>
);

// Один график на валюту: общая ось Y для манатов и евро — то же сложение разных денег, только нарисованное.
// `date` — календарная дата пояса отчёта без времени; читаем её как полночь UTC и форматируем в UTC, чтобы
// пояс браузера не сдвинул подпись на соседний день.
export const DailyNetChart: React.FC<{
  title: string;
  currency: string | null;
  points: { date: string; net: number }[];
}> = ({ title, currency, points }) => {
  const { language } = useLanguage();
  const formatDay = useMemo(
    () => new Intl.DateTimeFormat(LOCALES[language], { day: 'numeric', month: 'short', timeZone: 'UTC' }),
    [language]
  );
  const data = points.map(point => ({ date: formatDay.format(new Date(`${point.date}T00:00:00Z`)), net: point.net }));
  return (
    <Panel title={currency ? `${title} · ${currency}` : title}>
      <ResponsiveContainer width="100%" height={280}>
        <AreaChart data={data}>
          <CartesianGrid strokeDasharray="3 3" stroke="#e0e0e0" />
          <XAxis dataKey="date" stroke="#666" style={{ fontSize: '12px' }} />
          <YAxis stroke="#666" style={{ fontSize: '12px' }} />
          <Tooltip formatter={(value: any) => formatCurrency(Number(value), currency)} />
          <Area type="monotone" dataKey="net" stroke="#1976d2" strokeWidth={2} fill="#1976d2" fillOpacity={0.15} />
        </AreaChart>
      </ResponsiveContainer>
    </Panel>
  );
};

// Границы периода — в зоне, в которой их считал сервер, а не в зоне браузера: иначе подпись пояса стояла
// бы рядом со временем из другого пояса.
export function formatInZone(iso: string, zone: string): string {
  const date = new Date(iso);
  try {
    return new Intl.DateTimeFormat('en-GB', {
      timeZone: zone || undefined, day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit',
    }).format(date);
  } catch {
    return formatDateTime(date);
  }
}

// Доля самого крупного значения внутри одной валюты. Ширина полосы — оформление; отрицательная или нулевая
// выручка — минимальная полоса.
export function barWidth(top: number, value: number): string {
  if (top <= 0) return '5%';
  return `${Math.min(100, Math.max(5, (value / top) * 100))}%`;
}

export const TopBar: React.FC<{ width: string }> = ({ width }) => (
  <Box sx={{ height: 6, bgcolor: '#e0e0e0', borderRadius: 1, overflow: 'hidden' }}>
    <Box sx={{ height: '100%', bgcolor: '#1976d2', width }} />
  </Box>
);
