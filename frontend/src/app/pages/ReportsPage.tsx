import React, { useState, useMemo } from 'react';
import {
  Box,
  Paper,
  Typography,
  Button,
  Card,
  CardContent,
  Grid,
  Tabs,
  Tab,
  Chip,
  Stack,
  Divider,
  MenuItem,
  TextField,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  LinearProgress,
  IconButton,
  Tooltip,
} from '@mui/material';
import {
  BarChart,
  Bar,
  AreaChart,
  Area,
  LineChart,
  Line,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip as RechartTooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import {
  FileDownload as DownloadIcon,
  PictureAsPdf as PdfIcon,
  TableChart as ExcelIcon,
  TrendingUp as TrendingUpIcon,
  TrendingDown as TrendingDownIcon,
  Assessment as ReportIcon,
  Receipt as ReceiptIcon,
  CheckCircle as SuccessIcon,
  ErrorOutline as FailIcon,
  AccountBalance as SettlementIcon,
  Refresh as RefreshIcon,
  Schedule as ScheduleIcon,
  DateRange as DateRangeIcon,
} from '@mui/icons-material';

// ─── Types ────────────────────────────────────────────────────────────────────

type Period = '7d' | '30d' | '90d' | '12m' | 'custom';
type ReportTab = 0 | 1 | 2 | 3;

// ─── Mock data generators ─────────────────────────────────────────────────────

const generateDailyRevenue = (days: number) =>
  Array.from({ length: days }, (_, i) => {
    const d = new Date();
    d.setDate(d.getDate() - (days - 1 - i));
    const label = days <= 30
      ? d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short' })
      : d.toLocaleDateString('en-GB', { month: 'short', year: '2-digit' });
    const base = 4000 + Math.sin(i / 3) * 1200 + Math.random() * 800;
    return {
      date: label,
      revenue: Math.round(base * 100) / 100,
      transactions: Math.round(base / 90),
      successful: Math.round(base / 90 * 0.87),
      failed: Math.round(base / 90 * 0.13),
      refunded: Math.round(base / 90 * 0.04),
    };
  });

const generateMonthly = () =>
  ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'].map((m, i) => ({
    month: m,
    revenue: Math.round((3500 + Math.sin(i / 2) * 1500 + Math.random() * 600) * 100) / 100,
    ecommerce: Math.round((2100 + Math.random() * 400) * 100) / 100,
    pos: Math.round((1400 + Math.random() * 300) * 100) / 100,
    transactions: 420 + Math.floor(Math.random() * 180),
  }));

const paymentMethodShare = [
  { name: 'Visa', value: 44, color: '#1a237e' },
  { name: 'Mastercard', value: 31, color: '#b71c1c' },
  { name: 'AmEx', value: 12, color: '#1565c0' },
  { name: 'Other', value: 13, color: '#546e7a' },
];

const settlementRows = [
  { id: 'SET-2024-0891', period: '01–07 Jun 2025', transactions: 312, grossAmount: 28450.00, fees: 284.50, netAmount: 28165.50, status: 'completed', paidAt: '09 Jun 2025' },
  { id: 'SET-2024-0890', period: '25–31 May 2025', transactions: 298, grossAmount: 26830.00, fees: 268.30, netAmount: 26561.70, status: 'completed', paidAt: '02 Jun 2025' },
  { id: 'SET-2024-0889', period: '18–24 May 2025', transactions: 341, grossAmount: 31200.00, fees: 312.00, netAmount: 30888.00, status: 'completed', paidAt: '26 May 2025' },
  { id: 'SET-2024-0888', period: '11–17 May 2025', transactions: 276, grossAmount: 24100.50, fees: 241.01, netAmount: 23859.49, status: 'completed', paidAt: '19 May 2025' },
  { id: 'SET-2024-0887', period: '04–10 May 2025', transactions: 289, grossAmount: 25670.00, fees: 256.70, netAmount: 25413.30, status: 'completed', paidAt: '12 May 2025' },
  { id: 'SET-2024-0886', period: '27 Apr–03 May 2025', transactions: 305, grossAmount: 27300.00, fees: 273.00, netAmount: 27027.00, status: 'completed', paidAt: '05 May 2025' },
];

const scheduledReports = [
  { name: 'Weekly Revenue Summary', frequency: 'Every Monday', format: 'Excel', lastSent: '16 Jun 2025', recipients: 2 },
  { name: 'Monthly Settlement Report', frequency: '1st of month', format: 'PDF', lastSent: '01 Jun 2025', recipients: 3 },
  { name: 'Daily Transaction Log', frequency: 'Every day 08:00', format: 'Excel', lastSent: 'Today 08:00', recipients: 1 },
];

// ─── Summary metric card ──────────────────────────────────────────────────────

interface MetricCardProps {
  label: string;
  value: string;
  sub: string;
  trend: number;
  color: string;
  bgcolor: string;
  icon: React.ReactNode;
}

const MetricCard: React.FC<MetricCardProps> = ({ label, value, sub, trend, color, bgcolor, icon }) => (
  <Card elevation={0} sx={{ border: '1px solid', borderColor: 'divider', height: '100%' }}>
    <CardContent sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
        <Box sx={{ p: 1.5, borderRadius: 2, bgcolor, color }}>{icon}</Box>
        <Chip
          icon={trend >= 0 ? <TrendingUpIcon sx={{ fontSize: '14px !important' }} /> : <TrendingDownIcon sx={{ fontSize: '14px !important' }} />}
          label={`${trend >= 0 ? '+' : ''}${trend}%`}
          size="small"
          sx={{
            bgcolor: trend >= 0 ? 'rgba(46,125,50,0.1)' : 'rgba(211,47,47,0.1)',
            color: trend >= 0 ? '#2e7d32' : '#d32f2f',
            fontWeight: 700,
            fontSize: '0.7rem',
          }}
        />
      </Box>
      <Typography variant="h4" sx={{ fontWeight: 700, mb: 0.25 }}>{value}</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 500 }}>{label}</Typography>
      <Typography variant="caption" color="text.disabled">{sub}</Typography>
    </CardContent>
  </Card>
);

// ─── Main component ───────────────────────────────────────────────────────────

export const ReportsPage: React.FC = () => {
  const [period, setPeriod] = useState<Period>('30d');
  const [tab, setTab] = useState<ReportTab>(0);
  const [exporting, setExporting] = useState(false);

  const periodDays: Record<Period, number> = { '7d': 7, '30d': 30, '90d': 90, '12m': 365, custom: 30 };

  const dailyData = useMemo(() => generateDailyRevenue(periodDays[period]), [period]);
  const monthlyData = useMemo(() => generateMonthly(), []);

  const totals = useMemo(() => {
    const t = dailyData.reduce((acc, d) => ({
      revenue: acc.revenue + d.revenue,
      transactions: acc.transactions + d.transactions,
      successful: acc.successful + d.successful,
      failed: acc.failed + d.failed,
    }), { revenue: 0, transactions: 0, successful: 0, failed: 0 });
    return {
      ...t,
      successRate: t.transactions > 0 ? ((t.successful / t.transactions) * 100).toFixed(1) : '0',
      avgTicket: t.successful > 0 ? (t.revenue / t.successful).toFixed(2) : '0',
    };
  }, [dailyData]);

  const handleExport = async (format: 'excel' | 'pdf') => {
    setExporting(true);
    await new Promise(r => setTimeout(r, 800));
    setExporting(false);
  };

  const chartData = period === '12m' ? monthlyData.map(m => ({ date: m.month, ...m })) : dailyData;

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 4, display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 600, mb: 0.5 }}>Reports & Analytics</Typography>
          <Typography variant="body1" color="text.secondary">
            Revenue trends, transaction breakdowns, and settlement summaries
          </Typography>
        </Box>
        <Stack direction="row" spacing={1.5}>
          <Button
            variant="outlined"
            startIcon={<ExcelIcon />}
            disabled={exporting}
            onClick={() => handleExport('excel')}
          >
            Export Excel
          </Button>
          <Button
            variant="outlined"
            startIcon={<PdfIcon />}
            disabled={exporting}
            onClick={() => handleExport('pdf')}
          >
            Export PDF
          </Button>
        </Stack>
      </Box>

      {/* Period selector */}
      <Paper elevation={0} sx={{ p: 2, mb: 3, border: '1px solid', borderColor: 'divider', display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
        <DateRangeIcon color="action" />
        <Typography variant="body2" sx={{ fontWeight: 600 }}>Period:</Typography>
        {(['7d', '30d', '90d', '12m'] as Period[]).map(p => (
          <Button
            key={p}
            size="small"
            variant={period === p ? 'contained' : 'outlined'}
            onClick={() => setPeriod(p)}
            sx={{ minWidth: 70 }}
          >
            {{ '7d': 'Last 7d', '30d': 'Last 30d', '90d': 'Last 90d', '12m': '12 Months' }[p]}
          </Button>
        ))}
        <Divider orientation="vertical" flexItem />
        <Box sx={{ display: 'flex', gap: 1.5 }}>
          <TextField size="small" type="date" label="From" InputLabelProps={{ shrink: true }} sx={{ width: 160 }} />
          <TextField size="small" type="date" label="To" InputLabelProps={{ shrink: true }} sx={{ width: 160 }} />
          <Button size="small" variant="outlined">Apply</Button>
        </Box>
      </Paper>

      {/* KPI Cards */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <MetricCard
            label="Total Revenue"
            value={`₼${totals.revenue.toLocaleString('en', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`}
            sub="Successful transactions only"
            trend={12.4}
            color="#1565c0"
            bgcolor="rgba(21,101,192,0.08)"
            icon={<TrendingUpIcon sx={{ fontSize: 32 }} />}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <MetricCard
            label="Total Transactions"
            value={totals.transactions.toLocaleString()}
            sub={`${totals.successRate}% success rate`}
            trend={8.2}
            color="#2e7d32"
            bgcolor="rgba(46,125,50,0.08)"
            icon={<ReceiptIcon sx={{ fontSize: 32 }} />}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <MetricCard
            label="Avg. Ticket Size"
            value={`₼${totals.avgTicket}`}
            sub="Per successful transaction"
            trend={3.1}
            color="#7b1fa2"
            bgcolor="rgba(123,31,162,0.08)"
            icon={<ReportIcon sx={{ fontSize: 32 }} />}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <MetricCard
            label="Failed Transactions"
            value={totals.failed.toLocaleString()}
            sub={`${totals.transactions > 0 ? (100 - parseFloat(totals.successRate)).toFixed(1) : 0}% of total`}
            trend={-2.8}
            color="#c62828"
            bgcolor="rgba(198,40,40,0.08)"
            icon={<FailIcon sx={{ fontSize: 32 }} />}
          />
        </Grid>
      </Grid>

      {/* Tabs */}
      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider' }}>
        <Tabs
          value={tab}
          onChange={(_, v) => setTab(v)}
          sx={{ borderBottom: '1px solid', borderColor: 'divider', px: 2 }}
        >
          <Tab label="Revenue Overview" />
          <Tab label="Transaction Analysis" />
          <Tab label="Payment Methods" />
          <Tab label="Settlements" />
        </Tabs>

        <Box sx={{ p: 3 }}>

          {/* ── Tab 0: Revenue Overview ─────────────────────────────────── */}
          {tab === 0 && (
            <Stack spacing={4}>
              {/* Revenue trend */}
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 600, mb: 0.5 }}>Revenue Trend</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                  Daily revenue for the selected period
                </Typography>
                <ResponsiveContainer width="100%" height={280}>
                  <AreaChart data={chartData}>
                    <CartesianGrid key="cg-revenue" strokeDasharray="3 3" stroke="#e0e0e0" />
                    <XAxis key="x-revenue" dataKey="date" stroke="#888" style={{ fontSize: 11 }} tick={{ dy: 6 }} interval={Math.floor(chartData.length / 8)} />
                    <YAxis key="y-revenue" stroke="#888" style={{ fontSize: 11 }} tickFormatter={v => `₼${(v / 1000).toFixed(0)}k`} />
                    <RechartTooltip key="tt-revenue" formatter={(v: number) => [`₼${v.toLocaleString('en', { minimumFractionDigits: 2 })}`, 'Revenue']} />
                    <Legend key="leg-revenue" />
                    <Area key="area-revenue-trend" type="monotone" dataKey="revenue" stroke="#1976d2" strokeWidth={2.5} fill="#1976d2" fillOpacity={0.15} name="Revenue (AZN)" />
                  </AreaChart>
                </ResponsiveContainer>
              </Box>

              {/* E-commerce vs POS (12m view) */}
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 600, mb: 0.5 }}>Channel Breakdown</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                  E-commerce vs POS revenue by month
                </Typography>
                <ResponsiveContainer width="100%" height={240}>
                  <BarChart data={monthlyData}>
                    <CartesianGrid key="cg-channel" strokeDasharray="3 3" stroke="#e0e0e0" />
                    <XAxis key="x-channel" dataKey="month" stroke="#888" style={{ fontSize: 11 }} />
                    <YAxis key="y-channel" stroke="#888" style={{ fontSize: 11 }} tickFormatter={v => `₼${(v / 1000).toFixed(0)}k`} />
                    <RechartTooltip key="tt-channel" formatter={(v: number) => `₼${v.toLocaleString('en', { minimumFractionDigits: 2 })}`} />
                    <Legend key="leg-channel" />
                    <Bar key="bar-ecommerce" dataKey="ecommerce" name="E-commerce" fill="#1976d2" radius={[4, 4, 0, 0]} stackId="a" />
                    <Bar key="bar-pos" dataKey="pos" name="POS" fill="#42a5f5" radius={[4, 4, 0, 0]} stackId="a" />
                  </BarChart>
                </ResponsiveContainer>
              </Box>
            </Stack>
          )}

          {/* ── Tab 1: Transaction Analysis ─────────────────────────────── */}
          {tab === 1 && (
            <Stack spacing={4}>
              {/* Volume */}
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 600, mb: 0.5 }}>Transaction Volume</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                  Successful vs failed transactions over time
                </Typography>
                <ResponsiveContainer width="100%" height={260}>
                  <BarChart data={chartData}>
                    <CartesianGrid key="cg-volume" strokeDasharray="3 3" stroke="#e0e0e0" />
                    <XAxis key="x-volume" dataKey="date" stroke="#888" style={{ fontSize: 11 }} interval={Math.floor(chartData.length / 8)} />
                    <YAxis key="y-volume" stroke="#888" style={{ fontSize: 11 }} />
                    <RechartTooltip key="tt-volume" />
                    <Legend key="leg-volume" />
                    <Bar key="bar-successful" dataKey="successful" name="Successful" fill="#4caf50" radius={[4, 4, 0, 0]} stackId="x" />
                    <Bar key="bar-failed" dataKey="failed" name="Failed" fill="#f44336" radius={[4, 4, 0, 0]} stackId="x" />
                  </BarChart>
                </ResponsiveContainer>
              </Box>

              {/* Success rate */}
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 600, mb: 0.5 }}>Success Rate</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                  Percentage of transactions that completed successfully
                </Typography>
                <ResponsiveContainer width="100%" height={200}>
                  <LineChart data={chartData}>
                    <CartesianGrid key="cg-rate" strokeDasharray="3 3" stroke="#e0e0e0" />
                    <XAxis key="x-rate" dataKey="date" stroke="#888" style={{ fontSize: 11 }} interval={Math.floor(chartData.length / 8)} />
                    <YAxis key="y-rate" stroke="#888" style={{ fontSize: 11 }} domain={[70, 100]} tickFormatter={v => `${v}%`} />
                    <RechartTooltip key="tt-rate" formatter={(v: number) => [`${v.toFixed(1)}%`, 'Success Rate']} />
                    <Line
                      key="line-success-rate"
                      type="monotone"
                      dataKey={(d) => d.transactions > 0 ? +((d.successful / d.transactions) * 100).toFixed(1) : 0}
                      stroke="#2e7d32"
                      strokeWidth={2.5}
                      dot={false}
                      name="Success Rate"
                    />
                  </LineChart>
                </ResponsiveContainer>
              </Box>
            </Stack>
          )}

          {/* ── Tab 2: Payment Methods ──────────────────────────────────── */}
          {tab === 2 && (
            <Grid container spacing={4}>
              <Grid size={{ xs: 12, md: 5 }}>
                <Typography variant="h6" sx={{ fontWeight: 600, mb: 0.5 }}>Card Network Share</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                  Distribution by card network
                </Typography>
                <ResponsiveContainer width="100%" height={280}>
                  <PieChart>
                    <Pie
                      key="pie-payment-methods"
                      data={paymentMethodShare}
                      cx="50%"
                      cy="50%"
                      innerRadius={70}
                      outerRadius={110}
                      paddingAngle={3}
                      dataKey="value"
                    >
                      {paymentMethodShare.map((entry, index) => (
                        <Cell key={`pm-cell-${entry.name}-${index}`} fill={entry.color} />
                      ))}
                    </Pie>
                    <RechartTooltip key="tt-pie" formatter={(v: number) => [`${v}%`, 'Share']} />
                    <Legend key="leg-pie" />
                  </PieChart>
                </ResponsiveContainer>
              </Grid>
              <Grid size={{ xs: 12, md: 7 }}>
                <Typography variant="h6" sx={{ fontWeight: 600, mb: 0.5 }}>Breakdown</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                  Volume and revenue per network
                </Typography>
                <Stack spacing={2}>
                  {paymentMethodShare.map(pm => (
                    <Box key={pm.name}>
                      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.75 }}>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                          <Box sx={{ width: 12, height: 12, borderRadius: '50%', bgcolor: pm.color }} />
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>{pm.name}</Typography>
                        </Box>
                        <Box sx={{ display: 'flex', gap: 3 }}>
                          <Typography variant="body2" color="text.secondary">{pm.value}%</Typography>
                          <Typography variant="body2" sx={{ fontWeight: 700, minWidth: 80, textAlign: 'right' }}>
                            ₼{(totals.revenue * pm.value / 100).toLocaleString('en', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                          </Typography>
                        </Box>
                      </Box>
                      <LinearProgress
                        variant="determinate"
                        value={pm.value}
                        sx={{ height: 8, borderRadius: 4, bgcolor: 'action.hover', '& .MuiLinearProgress-bar': { bgcolor: pm.color, borderRadius: 4 } }}
                      />
                    </Box>
                  ))}
                </Stack>

                <Divider sx={{ my: 3 }} />

                <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 2 }}>Entry Mode</Typography>
                <Stack spacing={2}>
                  {[
                    { label: 'Chip & PIN', value: 38, color: '#1976d2' },
                    { label: 'Contactless / NFC', value: 35, color: '#26a69a' },
                    { label: 'Online (3D Secure)', value: 19, color: '#7b1fa2' },
                    { label: 'Magnetic Swipe', value: 5, color: '#f57c00' },
                    { label: 'Manual Entry', value: 3, color: '#78909c' },
                  ].map(em => (
                    <Box key={em.label}>
                      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                        <Typography variant="body2">{em.label}</Typography>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>{em.value}%</Typography>
                      </Box>
                      <LinearProgress
                        variant="determinate"
                        value={em.value}
                        sx={{ height: 6, borderRadius: 3, bgcolor: 'action.hover', '& .MuiLinearProgress-bar': { bgcolor: em.color, borderRadius: 3 } }}
                      />
                    </Box>
                  ))}
                </Stack>
              </Grid>
            </Grid>
          )}

          {/* ── Tab 3: Settlements ──────────────────────────────────────── */}
          {tab === 3 && (
            <Stack spacing={3}>
              {/* Summary row */}
              <Grid container spacing={3}>
                {[
                  { label: 'Total Settled', value: `₼${settlementRows.reduce((s, r) => s + r.netAmount, 0).toLocaleString('en', { minimumFractionDigits: 2 })}`, sub: 'Last 6 settlements', color: '#2e7d32', bgcolor: 'rgba(46,125,50,0.08)', icon: <SettlementIcon sx={{ fontSize: 28 }} /> },
                  { label: 'Total Fees', value: `₼${settlementRows.reduce((s, r) => s + r.fees, 0).toLocaleString('en', { minimumFractionDigits: 2 })}`, sub: '1% processing fee', color: '#e65100', bgcolor: 'rgba(230,81,0,0.08)', icon: <ReceiptIcon sx={{ fontSize: 28 }} /> },
                  { label: 'Avg. Settlement', value: `₼${(settlementRows.reduce((s, r) => s + r.netAmount, 0) / settlementRows.length).toLocaleString('en', { minimumFractionDigits: 2 })}`, sub: 'Per weekly batch', color: '#1565c0', bgcolor: 'rgba(21,101,192,0.08)', icon: <TrendingUpIcon sx={{ fontSize: 28 }} /> },
                ].map(s => (
                  <Grid key={s.label} size={{ xs: 12, sm: 4 }}>
                    <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 2 }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1 }}>
                        <Box sx={{ p: 1, borderRadius: 1.5, bgcolor: s.bgcolor, color: s.color }}>{s.icon}</Box>
                        <Typography variant="body2" color="text.secondary">{s.label}</Typography>
                      </Box>
                      <Typography variant="h5" sx={{ fontWeight: 700 }}>{s.value}</Typography>
                      <Typography variant="caption" color="text.disabled">{s.sub}</Typography>
                    </Paper>
                  </Grid>
                ))}
              </Grid>

              {/* Settlements table */}
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 600, mb: 2 }}>Settlement History</Typography>
                <TableContainer component={Paper} variant="outlined">
                  <Table size="small">
                    <TableHead>
                      <TableRow sx={{ bgcolor: 'rgba(0,0,0,0.02)' }}>
                        <TableCell sx={{ fontWeight: 600 }}>Batch ID</TableCell>
                        <TableCell sx={{ fontWeight: 600 }}>Period</TableCell>
                        <TableCell sx={{ fontWeight: 600 }} align="right">Transactions</TableCell>
                        <TableCell sx={{ fontWeight: 600 }} align="right">Gross</TableCell>
                        <TableCell sx={{ fontWeight: 600 }} align="right">Fees (1%)</TableCell>
                        <TableCell sx={{ fontWeight: 600 }} align="right">Net Paid</TableCell>
                        <TableCell sx={{ fontWeight: 600 }}>Paid At</TableCell>
                        <TableCell sx={{ fontWeight: 600 }}>Status</TableCell>
                        <TableCell sx={{ fontWeight: 600 }} align="center">Download</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {settlementRows.map(row => (
                        <TableRow key={row.id} hover>
                          <TableCell>
                            <Typography variant="body2" sx={{ fontFamily: 'monospace', fontWeight: 700 }}>{row.id}</Typography>
                          </TableCell>
                          <TableCell><Typography variant="body2">{row.period}</Typography></TableCell>
                          <TableCell align="right"><Typography variant="body2">{row.transactions}</Typography></TableCell>
                          <TableCell align="right"><Typography variant="body2">₼{row.grossAmount.toLocaleString('en', { minimumFractionDigits: 2 })}</Typography></TableCell>
                          <TableCell align="right"><Typography variant="body2" color="error.main">−₼{row.fees.toLocaleString('en', { minimumFractionDigits: 2 })}</Typography></TableCell>
                          <TableCell align="right"><Typography variant="body2" sx={{ fontWeight: 700, color: 'success.main' }}>₼{row.netAmount.toLocaleString('en', { minimumFractionDigits: 2 })}</Typography></TableCell>
                          <TableCell><Typography variant="body2">{row.paidAt}</Typography></TableCell>
                          <TableCell>
                            <Chip label="Completed" color="success" size="small" variant="outlined" sx={{ fontWeight: 600 }} />
                          </TableCell>
                          <TableCell align="center">
                            <Tooltip title="Download PDF">
                              <IconButton size="small"><PdfIcon fontSize="small" color="action" /></IconButton>
                            </Tooltip>
                            <Tooltip title="Download Excel">
                              <IconButton size="small"><ExcelIcon fontSize="small" color="action" /></IconButton>
                            </Tooltip>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Box>

              {/* Scheduled Reports */}
              <Box>
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
                  <Typography variant="h6" sx={{ fontWeight: 600 }}>Scheduled Reports</Typography>
                  <Button size="small" variant="outlined" startIcon={<ScheduleIcon />}>
                    Add Schedule
                  </Button>
                </Box>
                <Stack spacing={1.5}>
                  {scheduledReports.map(r => (
                    <Paper key={r.name} variant="outlined" sx={{ p: 2, borderRadius: 2, display: 'flex', alignItems: 'center', gap: 2 }}>
                      <ScheduleIcon color="action" />
                      <Box sx={{ flex: 1 }}>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>{r.name}</Typography>
                        <Typography variant="caption" color="text.secondary">
                          {r.frequency} · {r.recipients} recipient{r.recipients > 1 ? 's' : ''} · Last sent: {r.lastSent}
                        </Typography>
                      </Box>
                      <Chip label={r.format} size="small" variant="outlined" />
                      <IconButton size="small"><RefreshIcon fontSize="small" /></IconButton>
                    </Paper>
                  ))}
                </Stack>
              </Box>
            </Stack>
          )}
        </Box>
      </Paper>
    </Box>
  );
};
