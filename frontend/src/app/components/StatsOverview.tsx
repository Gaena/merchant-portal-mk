import React from 'react';
import { Box, Paper, Typography, Grid } from '@mui/material';
import {
  TrendingUp as TrendingUpIcon,
  Receipt as ReceiptIcon,
  CheckCircle as CheckCircleIcon,
  HourglassEmpty as HourglassIcon,
  Error as ErrorIcon,
  Replay as ReplayIcon
} from '@mui/icons-material';
import type { Transaction } from '../types/transaction';
import { formatCurrency } from '../utils/mockData';

interface StatsOverviewProps {
  transactions: Transaction[];
}

export const StatsOverview: React.FC<StatsOverviewProps> = ({ transactions }) => {
  // Считаем по словарю бэкенда (`TransactionStatus`, шесть значений). Прежние 'success' /
  // 'pending' / 'canceled' / '3d-failed' не совпадали ни с одним реальным статусом, поэтому
  // выручка и все счётчики всегда были нулевыми (P2-12).
  const totalAmount = transactions
    .filter(t => t.status === 'SUCCESS')
    .reduce((sum, t) => sum + (t.currency === 'AZN' ? t.amount : 0), 0);

  const totalTransactions = transactions.length;
  const successCount = transactions.filter(t => t.status === 'SUCCESS').length;
  // AUTHORIZED — деньги захолдированы, но не списаны: для мерчанта это «в процессе».
  const pendingCount = transactions.filter(t => t.status === 'PENDING' || t.status === 'AUTHORIZED').length;
  const failedCount = transactions.filter(t => t.status === 'FAILED').length;
  const refundedCount = transactions.filter(
    t => t.status === 'REFUNDED' || t.status === 'PARTIALLY_REFUNDED'
  ).length;

  // Calculate completion rate
  const completionRate = totalTransactions > 0 
    ? ((successCount / totalTransactions) * 100).toFixed(1) 
    : '0';

  // Primary stats - shown first and larger.
  // Без trend: прежние '+12.5%' и '+8.2%' были зашиты в код и выглядели как настоящая
  // аналитика. Сравнивать не с чем — за период сравнения данных здесь нет.
  const primaryStats = [
    {
      title: 'Total Revenue',
      subtitle: 'Successful transactions only',
      value: formatCurrency(totalAmount, 'AZN'),
      icon: <TrendingUpIcon />,
      color: '#2e7d32',
      bgColor: '#e8f5e9'
    },
    {
      title: 'Total Transactions',
      // Компонент получает уже отфильтрованный список, а не «последние 10 минут».
      subtitle: 'Matching current filters',
      value: totalTransactions.toString(),
      icon: <ReceiptIcon />,
      color: '#1565c0',
      bgColor: '#e3f2fd'
    }
  ];

  // Secondary stats - status breakdowns
  const secondaryStats = [
    {
      title: 'Success',
      subtitle: `${completionRate}% success rate`,
      value: successCount.toString(),
      icon: <CheckCircleIcon />,
      color: '#2e7d32',
      bgColor: '#e8f5e9'
    },
    {
      title: 'Pending',
      subtitle: 'Awaiting confirmation or capture',
      value: pendingCount.toString(),
      icon: <HourglassIcon />,
      color: '#ef6c00',
      bgColor: '#fff3e0'
    },
    {
      title: 'Failed',
      subtitle: 'Payment did not go through',
      value: failedCount.toString(),
      icon: <ErrorIcon />,
      color: '#c62828',
      bgColor: '#ffebee'
    },
    {
      title: 'Refunded',
      subtitle: 'Full and partial refunds',
      value: refundedCount.toString(),
      icon: <ReplayIcon />,
      color: '#7b1fa2',
      bgColor: '#f3e5f5'
    }
  ];

  return (
    <Box sx={{ mb: 4 }}>
      {/* Primary Stats Row */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        {primaryStats.map((stat, index) => (
          <Grid size={{ xs: 12, sm: 6 }} key={index} sx={{ display: 'flex' }}>
            <Paper
              elevation={0}
              sx={{
                p: 3,
                display: 'flex',
                flexDirection: 'column',
                gap: 2,
                height: 140,
                width: '100%',
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 2,
                transition: 'all 0.2s ease-in-out',
                '&:hover': {
                  transform: 'translateY(-2px)',
                  boxShadow: 3,
                  borderColor: stat.color
                }
              }}
            >
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                  <Box
                    sx={{
                      p: 1.5,
                      borderRadius: 1.5,
                      bgcolor: stat.bgColor,
                      color: stat.color,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      '& svg': { fontSize: 28 }
                    }}
                  >
                    {stat.icon}
                  </Box>
                  <Box>
                    <Typography 
                      variant="body2" 
                      color="text.secondary" 
                      sx={{ fontWeight: 500, mb: 0.5 }}
                    >
                      {stat.title}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {stat.subtitle}
                    </Typography>
                  </Box>
                </Box>
              </Box>
              <Typography className="text-[32px]" 
                variant="h3" 
                sx={{ 
                  fontWeight: 700,
                  color: 'text.primary',
                  lineHeight: 1,
                  fontSize: '36px'
                }}
              >
                {stat.value}
              </Typography>
            </Paper>
          </Grid>
        ))}
      </Grid>

      {/* Secondary Stats Row */}
      <Grid container spacing={3}>
        {secondaryStats.map((stat, index) => (
          <Grid size={{ xs: 12, sm: 6, md: 3 }} key={index} sx={{ display: 'flex' }}>
            <Paper
              elevation={0}
              sx={{
                p: 2.5,
                display: 'flex',
                alignItems: 'center',
                gap: 2,
                height: 110,
                width: '100%',
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 2,
                transition: 'all 0.2s ease-in-out',
                '&:hover': {
                  transform: 'translateY(-2px)',
                  boxShadow: 2,
                  borderColor: stat.color
                }
              }}
            >
              <Box
                sx={{
                  p: 1.5,
                  borderRadius: 1.5,
                  bgcolor: stat.bgColor,
                  color: stat.color,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  minWidth: 56,
                  minHeight: 56,
                  '& svg': { fontSize: 28 }
                }}
              >
                {stat.icon}
              </Box>
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <Typography 
                  variant="body2" 
                  color="text.secondary" 
                  sx={{ fontWeight: 500, mb: 0.5 }}
                >
                  {stat.title}
                </Typography>
                <Typography 
                  variant="h4" 
                  sx={{ 
                    fontWeight: 700,
                    color: 'text.primary',
                    mb: 0.5,
                    lineHeight: 1
                  }}
                >
                  {stat.value}
                </Typography>
                <Typography 
                  variant="caption" 
                  color="text.secondary"
                  sx={{ 
                    display: 'block',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap'
                  }}
                >
                  {stat.subtitle}
                </Typography>
              </Box>
            </Paper>
          </Grid>
        ))}
      </Grid>
    </Box>
  );
};