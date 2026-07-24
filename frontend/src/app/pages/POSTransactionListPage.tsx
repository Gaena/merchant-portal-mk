import React, { useState, useMemo } from 'react';
import {
  Box,
  Button,
  Stack,
  Typography,
  Alert,
  Snackbar,
  Paper,
  Grid,
  Chip
} from '@mui/material';
import {
  FileDownload as FileDownloadIcon,
  Refresh as RefreshIcon,
  CloudOff as OfflineIcon,
  LocationOn as LocationIcon,
  Person as PersonIcon
} from '@mui/icons-material';
import { StatsOverview } from '../components/StatsOverview';
import { POSFilterPanel } from '../components/POSFilterPanel';
import { POSTransactionTable } from '../components/POSTransactionTable';
import type { Transaction, TransactionFilters } from '../types/transaction';
import { exportTransactionsToExcel } from '../utils/exportExcel';

interface POSTransactionListPageProps {
  transactions: Transaction[];
  filters: TransactionFilters;
  onFilterChange: (filters: TransactionFilters) => void;
  autoRefresh: boolean;
  onToggleAutoRefresh: () => void;
  newTransactionCount: number;
  onRefresh: () => void;
}

export const POSTransactionListPage: React.FC<POSTransactionListPageProps> = ({
  transactions,
  filters,
  onFilterChange,
  autoRefresh,
  onToggleAutoRefresh,
  newTransactionCount,
  onRefresh
}) => {
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(25);
  const [orderBy, setOrderBy] = useState<keyof Transaction>('timestamp');
  const [order, setOrder] = useState<'asc' | 'desc'>('desc');
  const [isExporting, setIsExporting] = useState(false);
  const [showSuccessMessage, setShowSuccessMessage] = useState(false);

  // Filter to show only POS transactions
  const posTransactions = useMemo(() => {
    return transactions.filter(txn => txn.channel === 'pos');
  }, [transactions]);

  // Apply additional filters
  const filteredTransactions = useMemo(() => {
    return posTransactions.filter(txn => {
      // Date range filter
      if (filters.dateFrom && txn.timestamp < filters.dateFrom) return false;
      if (filters.dateTo && txn.timestamp > filters.dateTo) return false;

      // Status filter
      if (filters.status !== 'all' && txn.status !== filters.status) return false;

      // Payment method filter
      if (filters.paymentMethod !== 'all' && txn.paymentMethod !== filters.paymentMethod) return false;

      // Terminal RID filter
      if (filters.terminalRid.length > 0 && !filters.terminalRid.includes(txn.terminalRid)) return false;

      // Amount range filter
      if (filters.minAmount && txn.amount < parseFloat(filters.minAmount)) return false;
      if (filters.maxAmount && txn.amount > parseFloat(filters.maxAmount)) return false;

      // POS-specific filters
      if (filters.posPaymentType && filters.posPaymentType !== 'all' && txn.posPaymentType !== filters.posPaymentType) return false;
      if (filters.cashierId && filters.cashierId !== 'all' && txn.cashierId !== filters.cashierId) return false;
      if (filters.locationName && filters.locationName !== 'all' && txn.locationName !== filters.locationName) return false;
      if (filters.batchId && filters.batchId !== 'all' && txn.batchId !== filters.batchId) return false;

      // Search query filter
      if (filters.searchQuery) {
        const query = filters.searchQuery.toLowerCase();
        const matchesId = txn.id.toLowerCase().includes(query);
        const matchesCustomer = txn.customer.toLowerCase().includes(query);
        const matchesEmail = txn.customerEmail.toLowerCase().includes(query);
        const matchesRef = txn.merchantReference?.toLowerCase().includes(query);
        const matchesProviderOrderId = txn.providerOrderId?.toLowerCase().includes(query);
        const matchesReceipt = txn.receiptNumber?.toLowerCase().includes(query);
        const matchesCashier = txn.cashierName?.toLowerCase().includes(query);
        const matchesLocation = txn.locationName?.toLowerCase().includes(query);

        if (!matchesId && !matchesCustomer && !matchesEmail && !matchesRef && !matchesProviderOrderId &&
            !matchesReceipt && !matchesCashier && !matchesLocation) return false;
      }

      return true;
    });
  }, [posTransactions, filters]);

  // Sort transactions
  const sortedTransactions = useMemo(() => {
    const sorted = [...filteredTransactions];
    sorted.sort((a, b) => {
      let aValue = a[orderBy];
      let bValue = b[orderBy];

      // Handle date comparison
      if (aValue instanceof Date && bValue instanceof Date) {
        aValue = aValue.getTime() as any;
        bValue = bValue.getTime() as any;
      }

      if (aValue < bValue) return order === 'asc' ? -1 : 1;
      if (aValue > bValue) return order === 'asc' ? 1 : -1;
      return 0;
    });
    return sorted;
  }, [filteredTransactions, orderBy, order]);

  // Extract unique values for filters
  const uniqueCashiers = useMemo(() => {
    const cashiers = posTransactions
      .map(txn => txn.cashierName)
      .filter((name): name is string => !!name);
    return Array.from(new Set(cashiers)).sort();
  }, [posTransactions]);

  const uniqueLocations = useMemo(() => {
    const locations = posTransactions
      .map(txn => txn.locationName)
      .filter((name): name is string => !!name);
    return Array.from(new Set(locations)).sort();
  }, [posTransactions]);

  const uniqueTerminalRids = useMemo(() => {
    const terminals = posTransactions.map(txn => txn.terminalRid);
    return Array.from(new Set(terminals)).sort();
  }, [posTransactions]);

  // Calculate POS-specific stats
  const offlineCount = filteredTransactions.filter(txn => txn.isOffline).length;
  const locationCount = new Set(filteredTransactions.map(txn => txn.locationName)).size;
  const cashierCount = new Set(filteredTransactions.map(txn => txn.cashierName)).size;

  const handleSort = (column: keyof Transaction) => {
    const isAsc = orderBy === column && order === 'asc';
    setOrder(isAsc ? 'desc' : 'asc');
    setOrderBy(column);
  };

  const handleExport = async () => {
    setIsExporting(true);
    try {
      await new Promise(resolve => setTimeout(resolve, 500));
      exportTransactionsToExcel(sortedTransactions, 'pos_transactions');
      setShowSuccessMessage(true);
    } catch (error) {
      console.error('Export failed:', error);
    } finally {
      setIsExporting(false);
    }
  };

  return (
    <Box>
      {/* Page Header */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" gutterBottom>
          POS Transactions
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Point-of-sale transactions with cashier and location tracking
        </Typography>
      </Box>

      {/* POS-Specific Quick Stats */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid size={{ xs: 12, sm: 4 }}>
          <Paper sx={{ p: 2 }}>
            <Stack direction="row" alignItems="center" spacing={2}>
              <LocationIcon color="primary" sx={{ fontSize: 40 }} />
              <Box>
                <Typography variant="h5">{locationCount}</Typography>
                <Typography variant="body2" color="text.secondary">
                  Active Locations
                </Typography>
              </Box>
            </Stack>
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, sm: 4 }}>
          <Paper sx={{ p: 2 }}>
            <Stack direction="row" alignItems="center" spacing={2}>
              <PersonIcon color="primary" sx={{ fontSize: 40 }} />
              <Box>
                <Typography variant="h5">{cashierCount}</Typography>
                <Typography variant="body2" color="text.secondary">
                  Active Cashiers
                </Typography>
              </Box>
            </Stack>
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, sm: 4 }}>
          <Paper sx={{ p: 2 }}>
            <Stack direction="row" alignItems="center" spacing={2}>
              <OfflineIcon color={offlineCount > 0 ? 'warning' : 'disabled'} sx={{ fontSize: 40 }} />
              <Box>
                <Typography variant="h5">{offlineCount}</Typography>
                <Typography variant="body2" color="text.secondary">
                  Offline Transactions
                </Typography>
              </Box>
            </Stack>
          </Paper>
        </Grid>
      </Grid>

      {/* Stats Overview */}
      <StatsOverview transactions={filteredTransactions} />

      {/* Filters */}
      <POSFilterPanel
        filters={filters}
        onFilterChange={onFilterChange}
        totalTransactions={posTransactions.length}
        filteredTransactions={filteredTransactions.length}
        cashiers={uniqueCashiers}
        locations={uniqueLocations}
        terminalRids={uniqueTerminalRids}
      />

      {/* Action Bar */}
      <Box sx={{ mb: 3, display: 'flex', justifyContent: 'flex-start', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
        <Button
          variant="contained"
          startIcon={<FileDownloadIcon />}
          onClick={handleExport}
          disabled={isExporting || sortedTransactions.length === 0}
        >
          {isExporting ? 'Exporting...' : 'Export to Excel'}
        </Button>
        <Button
          variant="outlined"
          startIcon={<RefreshIcon />}
          onClick={onRefresh}
        >
          Refresh
        </Button>
        {offlineCount > 0 && (
          <Chip
            icon={<OfflineIcon />}
            label={`${offlineCount} offline transaction${offlineCount !== 1 ? 's' : ''} pending sync`}
            color="warning"
            variant="outlined"
          />
        )}
      </Box>

      {/* Transaction Table */}
      <POSTransactionTable
        transactions={sortedTransactions}
        page={page}
        rowsPerPage={rowsPerPage}
        onPageChange={setPage}
        onRowsPerPageChange={(rows) => {
          setRowsPerPage(rows);
          setPage(0);
        }}
        orderBy={orderBy}
        order={order}
        onSort={handleSort}
      />

      {/* Success Message */}
      <Snackbar
        open={showSuccessMessage}
        autoHideDuration={3000}
        onClose={() => setShowSuccessMessage(false)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert onClose={() => setShowSuccessMessage(false)} severity="success" sx={{ width: '100%' }}>
          POS transactions exported successfully!
        </Alert>
      </Snackbar>
    </Box>
  );
};