import React from 'react';
import { Box, Paper, Typography, Chip, Grid } from '@mui/material';
import {
  TrendingUp as TrendingUpIcon,
  Receipt as ReceiptIcon,
  CheckCircle as CheckCircleIcon,
  HourglassEmpty as HourglassIcon,
  Cancel as CancelIcon,
  ArrowUpward as ArrowUpwardIcon,
  ArrowDownward as ArrowDownwardIcon
} from '@mui/icons-material';
import type { Transaction } from '../types/transaction';
import { formatCurrency } from '../utils/mockData';

interface StatsOverviewProps {
  transactions: Transaction[];
}

export const StatsOverview: React.FC<StatsOverviewProps> = ({ transactions }) => {
  const totalAmount = transactions
    .filter(t => t.status === 'success')
    .reduce((sum, t) => sum + (t.currency === 'AZN' ? t.amount : 0), 0);
  
  const totalTransactions = transactions.length;
  const successCount = transactions.filter(t => t.status === 'success').length;
  const pendingCount = transactions.filter(t => t.status === 'pending').length;
  const canceledCount = transactions.filter(t => t.status === 'canceled').length;
  const failedCount = transactions.filter(t => t.status === '3d-failed').length;

  // Calculate completion rate
  const completionRate = totalTransactions > 0 
    ? ((successCount / totalTransactions) * 100).toFixed(1) 
    : '0';

  // Primary stats - shown first and larger
  const primaryStats = [
    {
      title: 'Total Revenue',
      subtitle: 'Successful transactions only',
      value: formatCurrency(totalAmount, 'AZN'),
      icon: <TrendingUpIcon />,
      color: '#2e7d32',
      bgColor: '#e8f5e9',
      trend: '+12.5%',
      trendUp: true
    },
    {
      title: 'Total Transactions',
      subtitle: 'Last 10 minutes',
      value: totalTransactions.toString(),
      icon: <ReceiptIcon />,
      color: '#1565c0',
      bgColor: '#e3f2fd',
      trend: '+8.2%',
      trendUp: true
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
      subtitle: 'Awaiting confirmation',
      value: pendingCount.toString(),
      icon: <HourglassIcon />,
      color: '#ef6c00',
      bgColor: '#fff3e0'
    },
    {
      title: 'Canceled',
      subtitle: 'Canceled by user',
      value: canceledCount.toString(),
      icon: <CancelIcon />,
      color: '#1565c0',
      bgColor: '#e3f2fd'
    },
    {
      title: '3D-Failed',
      subtitle: 'Authentication failed',
      value: failedCount.toString(),
      icon: <CancelIcon />,
      color: '#c62828',
      bgColor: '#ffebee'
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
                <Chip
                  icon={stat.trendUp ? <ArrowUpwardIcon sx={{ fontSize: 14 }} /> : <ArrowDownwardIcon sx={{ fontSize: 14 }} />}
                  label={stat.trend}
                  size="small"
                  sx={{
                    bgcolor: stat.trendUp ? '#e8f5e9' : '#ffebee',
                    color: stat.trendUp ? '#2e7d32' : '#c62828',
                    fontWeight: 600,
                    fontSize: '0.75rem',
                    '& .MuiChip-icon': {
                      color: stat.trendUp ? '#2e7d32' : '#c62828'
                    }
                  }}
                />
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