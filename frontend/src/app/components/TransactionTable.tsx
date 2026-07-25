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
  TableSortLabel
} from '@mui/material';
import {
  CheckCircle as CheckCircleIcon,
  HourglassEmpty as HourglassIcon,
  Error as ErrorIcon,
  Replay as ReplayIcon,
  Cancel as CancelIcon,
  CreditCard as CreditCardIcon
} from '@mui/icons-material';
import type { Transaction, TransactionStatus } from '../types/transaction';
import { formatCurrency, formatDateTime, getPaymentMethodLabel } from '../utils/mockData';
import { getStatusColorScheme } from '../utils/statusColors';
import { useNavigate } from 'react-router';

import { useLanguage } from '../context/LanguageContext';

interface TransactionTableProps {
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
  if (!status) return 'default';
  const upper = String(status).toUpperCase();
  switch (upper) {
    case 'APPROVED':
    case 'SUCCESS':
      return 'success';
    case 'PENDING':
      return 'warning';
    case 'REFUNDED':
    case 'PARTIALLY_REFUNDED':
    case 'CANCELED':
      return 'info';
    case 'DECLINED':
    case 'FAILED':
    case '3D-FAILED':
      return 'error';
    default:
      return 'default';
  }
};

const getStatusIcon = (status: TransactionStatus) => {
  if (!status) return <CheckCircleIcon fontSize="small" />;
  const upper = String(status).toUpperCase();
  switch (upper) {
    case 'APPROVED':
    case 'SUCCESS':
      return <CheckCircleIcon fontSize="small" />;
    case 'PENDING':
      return <HourglassIcon fontSize="small" />;
    case 'REFUNDED':
    case 'PARTIALLY_REFUNDED':
      return <ReplayIcon fontSize="small" />;
    case 'CANCELED':
      return <CancelIcon fontSize="small" />;
    case 'DECLINED':
    case 'FAILED':
    case '3D-FAILED':
      return <ErrorIcon fontSize="small" />;
    default:
      return <CheckCircleIcon fontSize="small" />;
  }
};

export const TransactionTable: React.FC<TransactionTableProps> = ({
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
      <Paper elevation={0} sx={{ p: 4, textAlign: 'center', border: '1px solid', borderColor: 'divider' }}>
        <Typography variant="h6" color="text.secondary">
          No transactions found
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
            <TableRow sx={{ bgcolor: 'action.hover' }}>
              <TableCell>
                <TableSortLabel
                  active={orderBy === 'timestamp'}
                  direction={orderBy === 'timestamp' ? order : 'asc'}
                  onClick={() => createSortHandler('timestamp')}
                >
                  {tObj.transactions.columns.date}
                </TableSortLabel>
              </TableCell>
              <TableCell>
                <TableSortLabel
                  active={orderBy === 'id'}
                  direction={orderBy === 'id' ? order : 'asc'}
                  onClick={() => createSortHandler('id')}
                >
                  {tObj.transactions.columns.id}
                </TableSortLabel>
              </TableCell>
              <TableCell>{tObj.transactions.columns.customer}</TableCell>
              <TableCell align="right">
                <TableSortLabel
                  active={orderBy === 'amount'}
                  direction={orderBy === 'amount' ? order : 'asc'}
                  onClick={() => createSortHandler('amount')}
                >
                  {tObj.transactions.columns.amount}
                </TableSortLabel>
              </TableCell>
              <TableCell>
                <TableSortLabel
                  active={orderBy === 'status'}
                  direction={orderBy === 'status' ? order : 'asc'}
                  onClick={() => createSortHandler('status')}
                >
                  {tObj.transactions.columns.status}
                </TableSortLabel>
              </TableCell>
              <TableCell>Payment Method</TableCell>
              <TableCell>Terminal Name</TableCell>
              <TableCell>Provider / Order Ref</TableCell>
              <TableCell align="right">
                <TableSortLabel
                  active={orderBy === 'fee'}
                  direction={orderBy === 'fee' ? order : 'asc'}
                  onClick={() => createSortHandler('fee')}
                >
                  Fee
                </TableSortLabel>
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {paginatedTransactions.map((transaction) => (
              <TableRow
                key={transaction.id}
                hover
                onClick={() => navigate(`/transactions/${transaction.id}`)}
                sx={{ 
                  '&:last-child td, &:last-child th': { border: 0 },
                  cursor: 'pointer',
                  '&:hover': {
                    bgcolor: 'action.hover'
                  }
                }}
              >
                <TableCell>
                  <Typography variant="body2" sx={{ whiteSpace: 'nowrap' }}>
                    {formatDateTime(transaction.timestamp)}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>
                    {transaction.id}
                  </Typography>
                </TableCell>
                <TableCell>
                  {transaction.cardLast4 ? (
                    <Typography variant="body2" sx={{ fontFamily: 'monospace', fontWeight: 700 }}>
                      *{transaction.cardLast4}
                    </Typography>
                  ) : (
                    <Typography variant="body2" color="text.secondary">
                      N/A
                    </Typography>
                  )}
                </TableCell>
                <TableCell align="right">
                  <Typography variant="body2">
                    {formatCurrency(transaction.amount, transaction.currency)}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Chip
                    icon={
                      <Box 
                        sx={{ 
                          width: 10, 
                          height: 10, 
                          borderRadius: '50%', 
                          bgcolor: getStatusColorScheme(transaction.status).main 
                        }} 
                      />
                    }
                    label={transaction.status.toUpperCase()}
                    size="small"
                    sx={{ 
                      minWidth: 100,
                      bgcolor: getStatusColorScheme(transaction.status).light,
                      color: getStatusColorScheme(transaction.status).contrastText,
                      fontWeight: 600,
                      fontSize: '0.7rem',
                      '& .MuiChip-label': {
                        fontSize: '0.7rem'
                      },
                      '& .MuiChip-icon': {
                        marginLeft: '8px',
                        marginRight: '-4px'
                      }
                    }}
                  />
                </TableCell>
                <TableCell>
                  <Typography variant="body2">
                    {getPaymentMethodLabel(transaction.paymentMethod)}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Chip
                    label={(() => {
                      const isUuid = (s?: string) => Boolean(s && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(s));
                      if (transaction.terminalName && !isUuid(transaction.terminalName)) return transaction.terminalName;
                      if (transaction.terminalRid && !isUuid(transaction.terminalRid)) return transaction.terminalRid;
                      if (transaction.terminalId) return `Terminal #${transaction.terminalId}`;
                      return '—';
                    })()}
                    size="small"
                    variant="outlined"
                    sx={{
                      fontFamily: 'sans-serif',
                      fontWeight: 600,
                      fontSize: '0.75rem',
                      borderColor: 'divider',
                      bgcolor: 'background.paper'
                    }}
                  />
                </TableCell>
                <TableCell>
                  <Typography variant="body2" sx={{ fontFamily: 'monospace', fontWeight: 600 }}>
                    {transaction.providerOrderId || '—'}
                  </Typography>
                </TableCell>
                <TableCell align="right">
                  <Typography variant="body2" sx={{ fontWeight: 500 }}>
                    {formatCurrency(transaction.fee, transaction.currency)}
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