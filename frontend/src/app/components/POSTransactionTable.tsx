import React from 'react';
import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  Paper,
  Chip,
  Box,
  Typography,
  TableSortLabel,
  Tooltip
} from '@mui/material';
import {
  CheckCircle as CheckCircleIcon,
  HourglassEmpty as HourglassIcon,
  Error as ErrorIcon,
  Cancel as CancelIcon,
  CloudOff as OfflineIcon,
  LocationOn as LocationIcon,
  Person as PersonIcon,
  Receipt as ReceiptIcon,
  CreditCard as CreditCardIcon,
  Contactless as ContactlessIcon,
  SwapHoriz as SwipeIcon,
  Edit as ManualIcon
} from '@mui/icons-material';
import type { Transaction, TransactionStatus, POSPaymentType } from '../types/transaction';
import { formatCurrency, formatDateTime, getPaymentMethodLabel } from '../utils/mockData';
import { useNavigate } from 'react-router';

interface POSTransactionTableProps {
  transactions: Transaction[];
  page: number;
  rowsPerPage: number;
  onPageChange: (page: number) => void;
  onRowsPerPageChange: (rowsPerPage: number) => void;
  orderBy: keyof Transaction;
  order: 'asc' | 'desc';
  onSort: (column: keyof Transaction) => void;
}

const getStatusColor = (status: TransactionStatus): 'success' | 'warning' | 'error' | 'default' | 'info' => {
  const colors: Record<TransactionStatus, 'success' | 'warning' | 'error' | 'default' | 'info'> = {
    success: 'success',
    pending: 'warning',
    '3d-failed': 'error',
    canceled: 'info'
  };
  return colors[status] || 'default';
};

const getStatusIcon = (status: TransactionStatus) => {
  const icons: Record<TransactionStatus, React.ReactNode> = {
    success: <CheckCircleIcon fontSize="small" />,
    pending: <HourglassIcon fontSize="small" />,
    '3d-failed': <ErrorIcon fontSize="small" />,
    canceled: <CancelIcon fontSize="small" />
  };
  return icons[status];
};

const getPOSPaymentTypeIcon = (type?: POSPaymentType) => {
  if (!type) return null;
  const icons: Record<POSPaymentType, React.ReactNode> = {
    chip: <CreditCardIcon fontSize="small" />,
    contactless: <ContactlessIcon fontSize="small" />,
    swipe: <SwipeIcon fontSize="small" />,
    manual: <ManualIcon fontSize="small" />
  };
  return icons[type];
};

const getPOSPaymentTypeLabel = (type?: POSPaymentType) => {
  if (!type) return '-';
  const labels: Record<POSPaymentType, string> = {
    chip: 'Chip & PIN',
    contactless: 'Contactless',
    swipe: 'Swipe',
    manual: 'Manual'
  };
  return labels[type];
};

export const POSTransactionTable: React.FC<POSTransactionTableProps> = ({
  transactions,
  page,
  rowsPerPage,
  onPageChange,
  onRowsPerPageChange,
  orderBy,
  order,
  onSort
}) => {
  const navigate = useNavigate();

  const handleChangePage = (_event: unknown, newPage: number) => {
    onPageChange(newPage);
  };

  const handleChangeRowsPerPage = (event: React.ChangeEvent<HTMLInputElement>) => {
    onRowsPerPageChange(parseInt(event.target.value, 10));
  };

  const createSortHandler = (column: keyof Transaction) => {
    onSort(column);
  };

  const paginatedTransactions = transactions.slice(
    page * rowsPerPage,
    page * rowsPerPage + rowsPerPage
  );

  if (transactions.length === 0) {
    return (
      <Paper elevation={2} sx={{ p: 4, textAlign: 'center' }}>
        <Typography variant="h6" color="text.secondary">
          No POS transactions found
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          Try adjusting your filters to see more results
        </Typography>
      </Paper>
    );
  }

  return (
    <Paper elevation={2}>
      <TableContainer>
        <Table sx={{ minWidth: 1200 }}>
          <TableHead>
            <TableRow>
              <TableCell>
                <TableSortLabel
                  active={orderBy === 'timestamp'}
                  direction={orderBy === 'timestamp' ? order : 'asc'}
                  onClick={() => createSortHandler('timestamp')}
                >
                  Date & Time
                </TableSortLabel>
              </TableCell>
              <TableCell>Transaction ID</TableCell>
              <TableCell>Receipt #</TableCell>
              <TableCell>Location</TableCell>
              <TableCell>Cashier</TableCell>
              <TableCell>Entry Mode</TableCell>
              <TableCell>
                <TableSortLabel
                  active={orderBy === 'amount'}
                  direction={orderBy === 'amount' ? order : 'asc'}
                  onClick={() => createSortHandler('amount')}
                >
                  Amount
                </TableSortLabel>
              </TableCell>
              <TableCell>
                <TableSortLabel
                  active={orderBy === 'status'}
                  direction={orderBy === 'status' ? order : 'asc'}
                  onClick={() => createSortHandler('status')}
                >
                  Status
                </TableSortLabel>
              </TableCell>
              <TableCell>Payment Method</TableCell>
              <TableCell>Batch ID</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {paginatedTransactions.map((txn) => (
              <TableRow
                key={txn.id}
                hover
                sx={{ cursor: 'pointer' }}
                onClick={() => navigate(`/transactions/${txn.id}`)}
              >
                {/* Date & Time */}
                <TableCell>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    {txn.isOffline && (
                      <Tooltip title="Offline transaction">
                        <OfflineIcon fontSize="small" color="warning" />
                      </Tooltip>
                    )}
                    <Typography variant="body2">
                      {formatDateTime(txn.timestamp)}
                    </Typography>
                  </Box>
                </TableCell>

                {/* Transaction ID */}
                <TableCell>
                  <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.875rem' }}>
                    {txn.id}
                  </Typography>
                </TableCell>

                {/* Receipt Number */}
                <TableCell>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                    <ReceiptIcon fontSize="small" color="action" />
                    <Typography variant="body2">
                      {txn.receiptNumber || '-'}
                    </Typography>
                  </Box>
                </TableCell>

                {/* Location */}
                <TableCell>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                    <LocationIcon fontSize="small" color="action" />
                    <Typography variant="body2">
                      {txn.locationName || '-'}
                    </Typography>
                  </Box>
                </TableCell>

                {/* Cashier */}
                <TableCell>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                    <PersonIcon fontSize="small" color="action" />
                    <Box>
                      <Typography variant="body2">
                        {txn.cashierName || '-'}
                      </Typography>
                      {txn.cashierId && (
                        <Typography variant="caption" color="text.secondary">
                          {txn.cashierId}
                        </Typography>
                      )}
                    </Box>
                  </Box>
                </TableCell>

                {/* Entry Mode */}
                <TableCell>
                  <Chip
                    icon={getPOSPaymentTypeIcon(txn.posPaymentType) || undefined}
                    label={getPOSPaymentTypeLabel(txn.posPaymentType)}
                    size="small"
                    variant="outlined"
                  />
                </TableCell>

                {/* Amount */}
                <TableCell>
                  <Typography variant="body2" fontWeight="medium">
                    {formatCurrency(txn.amount, txn.currency)}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Fee: {formatCurrency(txn.fee, txn.currency)}
                  </Typography>
                </TableCell>

                {/* Status */}
                <TableCell>
                  <Chip
                    icon={getStatusIcon(txn.status)}
                    label={txn.status === '3d-failed' ? '3D-Failed' : txn.status.charAt(0).toUpperCase() + txn.status.slice(1)}
                    color={getStatusColor(txn.status)}
                    size="small"
                  />
                </TableCell>

                {/* Payment Method */}
                <TableCell>
                  <Typography variant="body2">
                    {getPaymentMethodLabel(txn.paymentMethod)}
                  </Typography>
                  {txn.cardLast4 && (
                    <Typography variant="caption" color="text.secondary">
                      •••• {txn.cardLast4}
                    </Typography>
                  )}
                </TableCell>

                {/* Batch ID */}
                <TableCell>
                  <Typography variant="caption" color="text.secondary">
                    {txn.batchId || '-'}
                  </Typography>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      <TablePagination
        rowsPerPageOptions={[10, 25, 50, 100]}
        component="div"
        count={transactions.length}
        rowsPerPage={rowsPerPage}
        page={page}
        onPageChange={handleChangePage}
        onRowsPerPageChange={handleChangeRowsPerPage}
      />
    </Paper>
  );
};
