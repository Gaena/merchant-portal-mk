import React, { useState, useMemo } from 'react';
import {
  Box,
  Button,
  Stack,
  Typography,
  Alert,
  Snackbar
} from '@mui/material';
import {
  FileDownload as FileDownloadIcon,
  Refresh as RefreshIcon,
  AutorenewOutlined as AutoIcon
} from '@mui/icons-material';
import { StatsOverview } from '../components/StatsOverview';
import { FilterPanel } from '../components/FilterPanel';
import { TransactionTable } from '../components/TransactionTable';
import type { Transaction, TransactionFilters } from '../types/transaction';
import { exportTransactionsToExcel } from '../utils/exportExcel';

interface TransactionListPageProps {
  transactions: Transaction[];
  filters: TransactionFilters;
  onFilterChange: (filters: TransactionFilters) => void;
  autoRefresh: boolean;
  onToggleAutoRefresh: () => void;
  newTransactionCount: number;
  onRefresh: () => void;
}

import { useLanguage } from '../context/LanguageContext';

export const TransactionListPage: React.FC<TransactionListPageProps> = ({
  transactions,
  filters,
  onFilterChange,
  autoRefresh,
  onToggleAutoRefresh,
  newTransactionCount,
  onRefresh
}) => {
  const { tObj } = useLanguage();
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(25);
  const [orderBy, setOrderBy] = useState<keyof Transaction>('timestamp');
  const [order, setOrder] = useState<'asc' | 'desc'>('desc');
  const [isExporting, setIsExporting] = useState(false);
  const [showSuccessMessage, setShowSuccessMessage] = useState(false);

  // Filter transactions
  const filteredTransactions = useMemo(() => {
    return transactions.filter(txn => {
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

      // Search query filter
      if (filters.searchQuery) {
        const query = filters.searchQuery.toLowerCase();
        const matchesId = txn.id.toLowerCase().includes(query);
        const matchesCustomer = txn.customer.toLowerCase().includes(query);
        const matchesEmail = txn.customerEmail.toLowerCase().includes(query);
        const matchesRef = txn.merchantReference?.toLowerCase().includes(query);
        const matchesProviderOrderId = txn.providerOrderId?.toLowerCase().includes(query);

        if (!matchesId && !matchesCustomer && !matchesEmail && !matchesRef && !matchesProviderOrderId) return false;
      }

      return true;
    });
  }, [transactions, filters]);

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

  const handleSort = (column: keyof Transaction) => {
    const isAsc = orderBy === column && order === 'asc';
    setOrder(isAsc ? 'desc' : 'asc');
    setOrderBy(column);
  };

  const handleExport = async () => {
    setIsExporting(true);
    try {
      // Simulate a slight delay for better UX
      await new Promise(resolve => setTimeout(resolve, 500));
      exportTransactionsToExcel(sortedTransactions, 'merchant_transactions');
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
          {tObj.transactions.title}
        </Typography>
        <Typography variant="body1" color="text.secondary">
          {tObj.transactions.subtitle}
        </Typography>
      </Box>

      {/* Stats Overview */}
      <StatsOverview transactions={filteredTransactions} />

      {/* Filters */}
      <FilterPanel
        filters={filters}
        onFilterChange={onFilterChange}
        totalTransactions={transactions.length}
        filteredTransactions={filteredTransactions.length}
      />

      {/* Action Bar - After filters */}
      <Box sx={{ mb: 3, display: 'flex', justifyContent: 'flex-start', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
        <Button
          variant="contained"
          startIcon={<FileDownloadIcon />}
          onClick={handleExport}
          disabled={isExporting || sortedTransactions.length === 0}
        >
          {isExporting ? tObj.common.loading : tObj.common.export}
        </Button>
        <Button
          variant="outlined"
          startIcon={<RefreshIcon />}
          onClick={onRefresh}
        >
          {tObj.common.refresh}
        </Button>
      </Box>

      {/* Transaction Table */}
      <TransactionTable
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
          Transactions exported successfully!
        </Alert>
      </Snackbar>
    </Box>
  );
};