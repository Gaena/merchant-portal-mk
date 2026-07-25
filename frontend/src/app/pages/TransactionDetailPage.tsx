import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router';
import { apiClient } from '../api/client';
import {
  Box,
  Paper,
  Typography,
  Button,
  Divider,
  Chip,
  Card,
  CardContent,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Alert,
  Stack,
  CircularProgress
} from '@mui/material';
import {
  Timeline,
  TimelineItem,
  TimelineSeparator,
  TimelineConnector,
  TimelineContent,
  TimelineDot
} from '@mui/lab';
import {
  ArrowBack as ArrowBackIcon,
  CheckCircle as CheckCircleIcon,
  HourglassEmpty as HourglassIcon,
  Error as ErrorIcon,
  Cancel as CancelIcon,
  CreditCard as CreditCardIcon,
  Person as PersonIcon,
  Email as EmailIcon,
  Receipt as ReceiptIcon,
  CalendarToday as CalendarIcon,
  Description as DescriptionIcon,
  DoneAll as CompleteIcon,
  Refresh as RefreshIcon,
  Security as SecurityIcon,
  Laptop as LaptopIcon,
} from '@mui/icons-material';
import type { Transaction, TransactionStatus } from '../types/transaction';
import { formatCurrency, formatDateTime, getPaymentMethodLabel, getStatusLabel } from '../utils/mockData';
import { getStatusColorScheme } from '../utils/statusColors';

import { useLanguage } from '../context/LanguageContext';

interface TransactionDetailPageProps {
  transactions: Transaction[];
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

export const TransactionDetailPage: React.FC<TransactionDetailPageProps> = ({ transactions }) => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const { tObj } = useLanguage();
  const stateTx = location.state?.transaction as Transaction | undefined;

  const [cancelDialogOpen, setCancelDialogOpen] = useState(false);
  const [cancelSuccess, setCancelSuccess] = useState(false);
  const [completeDialogOpen, setCompleteDialogOpen] = useState(false);
  const [completeSuccess, setCompleteSuccess] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [checkingStatus, setCheckingStatus] = useState(false);
  const [fetchedTx, setFetchedTx] = useState<Transaction | null>(null);
  const [loadingTx, setLoadingTx] = useState(false);

  const transaction = stateTx || transactions.find(t => t.id === id) || fetchedTx || undefined;

  useEffect(() => {
    if (!stateTx && !transactions.find(t => t.id === id) && id) {
      setLoadingTx(true);
      Promise.allSettled([
        apiClient.get(`/api/v1/transactions/${id}`),
        apiClient.get('/api/v1/terminals')
      ])
        .then(([resTx, resTerm]) => {
          let terminalMap: Record<number, string> = {};
          if (resTerm.status === 'fulfilled' && Array.isArray(resTerm.value.data)) {
            resTerm.value.data.forEach((term: any) => {
              if (term.id) terminalMap[term.id] = term.name || `Terminal #${term.id}`;
            });
          }

          if (resTx.status === 'fulfilled' && resTx.value.data) {
            const t = resTx.value.data;
            const termName = t.terminalId ? terminalMap[t.terminalId] : undefined;
            const displayName = termName || (t.terminalId ? `Terminal #${t.terminalId}` : '—');
            const mapped: Transaction = {
              id: t.id,
              paymentLinkId: t.paymentLinkId,
              timestamp: t.createdAt ? new Date(t.createdAt) : new Date(),
              customer: t.customerName || t.customerEmail || 'Customer',
              customerEmail: t.customerEmail || 'N/A',
              customerPhone: t.customerPhone,
              amount: t.amount,
              refundedAmount: t.refundedAmount,
              currency: t.currency || 'AZN',
              status: String(t.status || 'APPROVED').toUpperCase() as any,
              paymentMethod: String(t.paymentType || 'SMS').toUpperCase() as any,
              description: t.description || t.merchantOrderId || 'Transaction',
              cardNumberMasked: t.cardNumberMasked,
              cardLast4: t.cardNumberMasked ? String(t.cardNumberMasked).slice(-4) : undefined,
              rrn: t.rrn,
              approvalCode: t.approvalCode,
              merchantRid: t.merchantRid,
              providerOrderId: t.providerOrderId || t.provider_order_id,
              terminalId: t.terminalId,
              terminalName: termName || displayName,
              clientIp: t.clientIp,
              userAgent: t.userAgent,
              fee: 0,
              statusHistory: [],
              terminalRid: displayName,
              channel: 'ecommerce'
            };
            setFetchedTx(mapped);
          }
        })
        .catch(() => {
          setFetchedTx(null);
        })
        .finally(() => {
          setLoadingTx(false);
        });
    }
  }, [id, stateTx, transactions]);

  const handleCancelTransaction = async () => {
    if (!transaction) return;
    setActionError(null);
    try {
      await apiClient.post(`/api/v1/transactions/${transaction.id}/refund`, {
        amount: transaction.amount,
        reason: 'Merchant refund request'
      });
      setCancelSuccess(true);
      setCancelDialogOpen(false);
      setTimeout(() => {
        navigate('/transactions');
      }, 2000);
    } catch (err: any) {
      const msg = err.response?.data?.message || 'Failed to refund transaction on server';
      setActionError(msg);
      setCancelDialogOpen(false);
    }
  };

  const handleCompleteTransaction = async () => {
    if (!transaction) return;
    setActionError(null);
    try {
      await apiClient.post(`/api/v1/transactions/${transaction.id}/complete`, {
        amount: Number(transaction.amount) || 0
      });
      setCompleteSuccess(true);
      setCompleteDialogOpen(false);
    } catch (err: any) {
      const msg = err.response?.data?.message || 'Failed to complete DMS transaction on server';
      setActionError(msg);
      setCompleteDialogOpen(false);
    }
  };

  const handleCheckStatus = async () => {
    if (!transaction) return;
    setCheckingStatus(true);
    setActionError(null);
    try {
      await apiClient.get(`/api/v1/transactions/${transaction.id}/status`);
      setCheckingStatus(false);
    } catch (err: any) {
      setCheckingStatus(false);
      const msg = err.response?.data?.message || 'Failed to check status from gateway';
      setActionError(msg);
    }
  };

  if (loadingTx) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '50vh' }}>
        <CircularProgress size={48} />
      </Box>
    );
  }

  if (!transaction) {
    return (
      <Box sx={{ p: 4 }}>
        <Paper sx={{ p: 4, textAlign: 'center' }}>
          <Typography variant="h5" color="text.secondary" gutterBottom>
            Transaction Not Found
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            The transaction you're looking for doesn't exist or has been removed.
          </Typography>
          <Button
            variant="contained"
            startIcon={<ArrowBackIcon />}
            onClick={() => navigate('/transactions')}
          >
            Back to Transactions
          </Button>
        </Paper>
      </Box>
    );
  }

  return (
    <Box sx={{ p: 4 }}>
      {/* Success / Error Alerts */}
      {cancelSuccess && (
        <Alert severity="success" sx={{ mb: 3 }}>
          Transaction refund initiated successfully. Redirecting...
        </Alert>
      )}
      {actionError && (
        <Alert severity="error" sx={{ mb: 3 }} onClose={() => setActionError(null)}>
          {actionError}
        </Alert>
      )}

      {/* Back Button & Actions */}
      <Box sx={{ mb: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Button
          startIcon={<ArrowBackIcon />}
          onClick={() => navigate('/transactions')}
          variant="outlined"
        >
          {tObj.common.back}
        </Button>
        <Button
          startIcon={<RefreshIcon />}
          onClick={handleCheckStatus}
          disabled={checkingStatus}
          variant="outlined"
          size="small"
        >
          {checkingStatus ? tObj.common.loading : tObj.common.refresh}
        </Button>
      </Box>

      {/* Header */}
      <Box sx={{ mb: 4 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
          <Box>
            <Typography variant="h4" sx={{ fontWeight: 700 }}>
              {tObj.transactions.detail.title}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
              {transaction.id}
            </Typography>
          </Box>
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
            label={getStatusLabel(transaction.status)}
            sx={{ 
              fontSize: '0.95rem', 
              px: 1.5, 
              py: 2.5, 
              fontWeight: 600,
              bgcolor: getStatusColorScheme(transaction.status).light,
              color: getStatusColorScheme(transaction.status).contrastText,
              '& .MuiChip-icon': {
                marginLeft: '8px',
                marginRight: '-4px'
              }
            }}
          />
        </Box>
      </Box>

      {/* Main Content */}
      {/* Transaction Information - Full Width */}
      <Paper elevation={2} sx={{ overflow: 'hidden', mb: 3 }}>
        {/* Header Section */}
        <Box sx={{ 
          p: 3, 
          borderBottom: '1px solid',
          borderColor: 'divider',
          bgcolor: 'grey.50'
        }}>
          <Typography variant="h6" sx={{ fontWeight: 700, color: 'text.primary' }}>
            Transaction Information
          </Typography>
        </Box>

        {/* Content Section */}
        <Box sx={{ p: 4 }}>
          {/* Featured Amount Section */}
          <Box sx={{ mb: 4 }}>
            <Typography variant="overline" sx={{ 
              color: 'text.secondary', 
              fontWeight: 700,
              letterSpacing: 1.2,
              fontSize: '0.75rem'
            }}>
              Transaction Amount
            </Typography>
            <Typography variant="h5" sx={{ 
              fontWeight: 700, 
              color: 'primary.main',
              mt: 0.5
            }}>
              {formatCurrency(transaction.amount, transaction.currency)}
            </Typography>
          </Box>

          <Divider sx={{ my: 4 }} />

          {/* Financial Details Grid */}
          <Box sx={{ mb: 4 }}>
            <Typography variant="subtitle2" sx={{
              mb: 2.5,
              color: 'text.secondary',
              fontWeight: 700,
              textTransform: 'uppercase',
              fontSize: '0.75rem',
              letterSpacing: 1
            }}>
              Financial Details
            </Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'repeat(2, 1fr)', sm: 'repeat(4, 1fr)' }, gap: 2 }}>
              <Box>
                <Typography variant="caption" sx={{
                  color: 'text.secondary',
                  fontWeight: 600,
                  textTransform: 'uppercase',
                  fontSize: '0.7rem',
                  letterSpacing: 0.5
                }}>
                  Currency
                </Typography>
                <Typography variant="h6" sx={{
                  fontWeight: 700,
                  color: 'text.primary',
                  mt: 0.5
                }}>
                  {transaction.currency}
                </Typography>
              </Box>

              <Box>
                <Typography variant="caption" sx={{
                  color: 'text.secondary',
                  fontWeight: 600,
                  textTransform: 'uppercase',
                  fontSize: '0.7rem',
                  letterSpacing: 0.5
                }}>
                  Processing Fee
                </Typography>
                <Typography variant="h6" sx={{
                  fontWeight: 700,
                  color: 'text.primary',
                  mt: 0.5
                }}>
                  {formatCurrency(transaction.fee, transaction.currency)}
                </Typography>
              </Box>

              <Box>
                <Typography variant="caption" sx={{
                  color: 'text.secondary',
                  fontWeight: 600,
                  textTransform: 'uppercase',
                  fontSize: '0.7rem',
                  letterSpacing: 0.5
                }}>
                  Net Amount
                </Typography>
                <Typography variant="h6" sx={{
                  fontWeight: 700,
                  color: 'success.dark',
                  mt: 0.5
                }}>
                  {formatCurrency(transaction.amount - transaction.fee, transaction.currency)}
                </Typography>
              </Box>

              <Box>
                <Typography variant="caption" sx={{
                  color: 'text.secondary',
                  fontWeight: 600,
                  textTransform: 'uppercase',
                  fontSize: '0.7rem',
                  letterSpacing: 0.5
                }}>
                  Transaction ID
                </Typography>
                <Typography variant="h6" sx={{
                  fontWeight: 700,
                  color: 'text.primary',
                  mt: 0.5
                }}>
                  {transaction.id}
                </Typography>
              </Box>
            </Box>
          </Box>

          <Divider sx={{ my: 4 }} />

          {/* Transaction Details Grid */}
          <Box sx={{ mb: 4 }}>
            <Typography variant="subtitle2" sx={{
              mb: 2.5,
              color: 'text.secondary',
              fontWeight: 700,
              textTransform: 'uppercase',
              fontSize: '0.75rem',
              letterSpacing: 1
            }}>
              Transaction Details
            </Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'repeat(2, 1fr)', md: 'repeat(4, 1fr)' }, gap: 3 }}>
              <Box>
                <Typography variant="caption" sx={{
                  color: 'text.secondary',
                  fontWeight: 600,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 0.5,
                  mb: 1
                }}>
                  <CalendarIcon sx={{ fontSize: 14 }} />
                  Date & Time
                </Typography>
                <Typography variant="body1" sx={{ fontWeight: 600, color: 'text.primary' }}>
                  {formatDateTime(transaction.timestamp)}
                </Typography>
              </Box>

              <Box>
                <Typography variant="caption" sx={{
                  color: 'text.secondary',
                  fontWeight: 600,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 0.5,
                  mb: 1
                }}>
                  <CreditCardIcon sx={{ fontSize: 14 }} />
                  Payment Method
                </Typography>
                <Typography variant="body1" sx={{ fontWeight: 600, color: 'text.primary' }}>
                  {getPaymentMethodLabel(transaction.paymentMethod)}
                </Typography>
              </Box>

              <Box>
                <Typography variant="caption" sx={{
                  color: 'text.secondary',
                  fontWeight: 600,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 0.5,
                  mb: 1
                }}>
                  <ReceiptIcon sx={{ fontSize: 14 }} />
                  Terminal Name
                </Typography>
                <Box sx={{
                  display: 'inline-block',
                  px: 1.5,
                  py: 0.75,
                  bgcolor: 'primary.main',
                  color: 'white',
                  borderRadius: 1,
                  fontFamily: 'sans-serif',
                  fontWeight: 700,
                  fontSize: '0.875rem'
                }}>
                  {(() => {
                    const isUuid = (s?: string) => Boolean(s && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(s));
                    if (transaction.terminalName && !isUuid(transaction.terminalName)) return transaction.terminalName;
                    if (transaction.terminalRid && !isUuid(transaction.terminalRid)) return transaction.terminalRid;
                    if (transaction.terminalId) return `Terminal #${transaction.terminalId}`;
                    return '—';
                  })()}
                </Box>
              </Box>

              <Box>
                <Typography variant="caption" sx={{
                  color: 'text.secondary',
                  fontWeight: 600,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 0.5,
                  mb: 1
                }}>
                  <CreditCardIcon sx={{ fontSize: 14 }} />
                  Card Number
                </Typography>
                <Typography variant="body1" sx={{ fontWeight: 700, fontFamily: 'monospace', color: 'text.primary' }}>
                  {transaction.cardLast4 ? `*${transaction.cardLast4}` : 'N/A'}
                </Typography>
              </Box>

              <Box>
                <Typography variant="caption" sx={{
                  color: 'text.secondary',
                  fontWeight: 600,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 0.5,
                  mb: 1
                }}>
                  <DescriptionIcon sx={{ fontSize: 14 }} />
                  Description
                </Typography>
                <Typography variant="body1" sx={{ fontWeight: 600, color: 'text.primary' }}>
                  {transaction.description}
                </Typography>
              </Box>

              <Box>
                <Typography variant="caption" sx={{
                  color: 'text.secondary',
                  fontWeight: 600,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 0.5,
                  mb: 1
                }}>
                  <ReceiptIcon sx={{ fontSize: 14 }} />
                  Provider Order ID
                </Typography>
                <Typography variant="body1" sx={{ fontWeight: 700, fontFamily: 'monospace', color: 'primary.main' }}>
                  {transaction.providerOrderId || '—'}
                </Typography>
              </Box>
            </Box>
          </Box>

          <Divider sx={{ my: 4 }} />

          {/* Customer Information Grid */}
          <Box>
            <Typography variant="subtitle2" sx={{
              mb: 2.5,
              color: 'text.secondary',
              fontWeight: 700,
              textTransform: 'uppercase',
              fontSize: '0.75rem',
              letterSpacing: 1
            }}>
              Customer Information
            </Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'repeat(2, 1fr)', md: 'repeat(3, 1fr)' }, gap: 3 }}>
              <Box>
                <Typography variant="caption" sx={{
                  color: 'text.secondary',
                  fontWeight: 600,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 0.5,
                  mb: 1
                }}>
                  <PersonIcon sx={{ fontSize: 14 }} />
                  Customer Name
                </Typography>
                <Typography variant="body1" sx={{ fontWeight: 600, color: 'text.primary' }}>
                  {transaction.customer}
                </Typography>
              </Box>

              <Box>
                <Typography variant="caption" sx={{
                  color: 'text.secondary',
                  fontWeight: 600,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 0.5,
                  mb: 1
                }}>
                  <EmailIcon sx={{ fontSize: 14 }} />
                  Customer Email
                </Typography>
                <Typography variant="body1" sx={{ fontWeight: 600, color: 'text.primary', wordBreak: 'break-word' }}>
                  {transaction.customerEmail}
                </Typography>
              </Box>

              {transaction.merchantReference && (
                <Box>
                  <Typography variant="caption" sx={{
                    color: 'text.secondary',
                    fontWeight: 600,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 0.5,
                    mb: 1
                  }}>
                    <ReceiptIcon sx={{ fontSize: 14 }} />
                    Merchant Reference
                  </Typography>
                  <Typography variant="body1" sx={{ fontWeight: 600, fontFamily: 'monospace', color: 'text.primary' }}>
                    {transaction.merchantReference}
                  </Typography>
                </Box>
              )}

              <Box>
                <Typography variant="caption" sx={{
                  color: 'text.secondary',
                  fontWeight: 600,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 0.5,
                  mb: 1
                }}>
                  <SecurityIcon sx={{ fontSize: 14 }} />
                  Payer IP Address
                </Typography>
                <Chip
                  label={transaction.clientIp || transaction.payerIp || '—'}
                  size="small"
                  variant="outlined"
                  color="info"
                  sx={{ fontFamily: 'monospace', fontWeight: 700 }}
                />
              </Box>

              <Box sx={{ gridColumn: { md: 'span 2' } }}>
                <Typography variant="caption" sx={{
                  color: 'text.secondary',
                  fontWeight: 600,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 0.5,
                  mb: 1
                }}>
                  <LaptopIcon sx={{ fontSize: 14 }} />
                  Payer Device / User-Agent
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 600, color: 'text.primary', fontSize: '0.8rem', wordBreak: 'break-all' }}>
                  {transaction.userAgent || '—'}
                </Typography>
              </Box>
            </Box>
          </Box>
        </Box>
      </Paper>

      {/* Cancelation Information - Show if canceled */}
      {transaction.status === 'canceled' && transaction.canceledBy && (
        <Paper elevation={0} sx={{ p: 3, mb: 3, bgcolor: 'rgba(3, 169, 244, 0.08)', border: '1px solid', borderColor: 'rgba(3, 169, 244, 0.2)' }}>
          <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 2 }}>
            <CancelIcon sx={{ color: 'info.main', mt: 0.5 }} />
            <Box sx={{ flex: 1 }}>
              <Typography variant="h6" sx={{ mb: 1, fontWeight: 600, color: 'info.dark' }}>
                Transaction Canceled
              </Typography>
              {transaction.canceledBy === 'api' ? (
                <Typography variant="body2" sx={{ color: 'info.dark' }}>
                  This transaction was canceled via API integration.
                </Typography>
              ) : (
                <Box>
                  <Typography variant="body2" sx={{ color: 'info.dark', mb: 1 }}>
                    This transaction was canceled by the customer.
                  </Typography>
                  <Box sx={{ mt: 2, p: 2, bgcolor: 'background.paper', borderRadius: 1 }}>
                    <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 600, display: 'block', mb: 0.5 }}>
                      Customer Details:
                    </Typography>
                    <Stack spacing={0.5}>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>
                        <PersonIcon sx={{ fontSize: 14, mr: 0.5, verticalAlign: 'middle' }} />
                        {transaction.canceledByCustomerName}
                      </Typography>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>
                        <EmailIcon sx={{ fontSize: 14, mr: 0.5, verticalAlign: 'middle' }} />
                        {transaction.canceledByCustomerEmail}
                      </Typography>
                    </Stack>
                  </Box>
                </Box>
              )}
            </Box>
          </Box>
        </Paper>
      )}

      {/* Transaction Actions - Only for e-commerce, not POS */}
      {transaction.channel !== 'pos' && transaction.status !== 'canceled' && (
        <Paper elevation={2} sx={{ p: 3, mb: 3, bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 3 }}>
            <Box sx={{ flex: 1 }}>
              <Typography variant="h6" sx={{ mb: 1, fontWeight: 600, color: 'text.primary' }}>
                Transaction Actions
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {String(transaction.paymentMethod || '').toUpperCase() === 'DMS' && ['PENDING', 'AUTHORIZED'].includes(String(transaction.status || '').toUpperCase())
                  ? 'This DMS transaction has funds authorized on the customer\'s card. Complete it to capture the funds, or cancel to release the hold.'
                  : 'Cancel this transaction and initiate a refund to the customer. The amount will be reversed within 3-5 business days.'}
              </Typography>
            </Box>
            <Stack direction="row" spacing={1.5}>
              {String(transaction.paymentMethod || '').toUpperCase() === 'DMS' && ['PENDING', 'AUTHORIZED'].includes(String(transaction.status || '').toUpperCase()) && !completeSuccess && (
                <Button
                  variant="outlined"
                  color="success"
                  startIcon={<CompleteIcon />}
                  onClick={() => setCompleteDialogOpen(true)}
                  sx={{
                    py: 1.5,
                    px: 3,
                    '&:hover': { bgcolor: 'success.light', color: 'success.dark' }
                  }}
                >
                  Complete
                </Button>
              )}
              {completeSuccess && (
                <Chip
                  icon={<CompleteIcon />}
                  label="Completed — funds captured"
                  color="success"
                  sx={{ fontWeight: 600, py: 2 }}
                />
              )}
              <Button
                variant="outlined"
                color="warning"
                startIcon={<CancelIcon />}
                onClick={() => setCancelDialogOpen(true)}
                sx={{
                  py: 1.5,
                  px: 3,
                  '&:hover': { bgcolor: 'warning.light', color: 'warning.dark' }
                }}
              >
                Cancel & Refund
              </Button>
            </Stack>
          </Box>
        </Paper>
      )}

      {/* Status History - Full Width Block */}
      <Paper elevation={2} sx={{ p: 4 }}>
        <Typography variant="h6" sx={{ mb: 1, fontWeight: 600 }}>
          Transaction Status History
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 4 }}>
          Track the complete lifecycle of this transaction from initiation to completion
        </Typography>
        
        {/* Status Timeline - Corporate Style */}
        <Box sx={{ position: 'relative' }}>
          {/* Connecting Line */}
          <Box 
            sx={{ 
              position: 'absolute',
              top: 24,
              left: 7,
              bottom: 24,
              width: 2,
              bgcolor: 'divider',
              zIndex: 0
            }}
          />
          
          {/* Timeline Items */}
          <Box sx={{ position: 'relative', zIndex: 1 }}>
            {transaction.statusHistory.map((entry, index) => (
              <Box 
                key={index} 
                sx={{ 
                  display: 'flex', 
                  gap: 3,
                  mb: index < transaction.statusHistory.length - 1 ? 4 : 0,
                  position: 'relative'
                }}
              >
                {/* Status Indicator Circle */}
                <Box 
                  sx={{ 
                    width: 16, 
                    height: 16,
                    borderRadius: '50%',
                    bgcolor: getStatusColorScheme(entry.status).main,
                    flexShrink: 0,
                    mt: 0.5,
                    border: '3px solid',
                    borderColor: 'background.paper',
                    boxShadow: '0 0 0 2px ' + getStatusColorScheme(entry.status).main
                  }}
                />
                
                {/* Status Content */}
                <Box sx={{ flex: 1, pb: 2 }}>
                  <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 2, mb: 0.5 }}>
                    <Typography variant="body1" sx={{ fontWeight: 700, color: 'text.primary' }}>
                      {getStatusLabel(entry.status)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 500 }}>
                      {formatDateTime(entry.timestamp)}
                    </Typography>
                  </Box>
                  {entry.note && (
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      {entry.note}
                    </Typography>
                  )}
                </Box>
              </Box>
            ))}
          </Box>
        </Box>
      </Paper>

      {/* Complete Dialog */}
      <Dialog
        open={completeDialogOpen}
        onClose={() => setCompleteDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Complete DMS Transaction</DialogTitle>
        <DialogContent>
          <DialogContentText>
            This will capture the authorized funds from the customer's card. The transaction will be marked as completed and funds transferred to your account.
          </DialogContentText>
          <Box sx={{ mt: 2, p: 2, bgcolor: 'success.light', borderRadius: 1 }}>
            <Typography variant="body2" sx={{ fontWeight: 600 }}>
              Transaction ID: {transaction.id}
            </Typography>
            <Typography variant="body2">
              Amount to capture: {formatCurrency(transaction.amount, transaction.currency)}
            </Typography>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCompleteDialogOpen(false)} variant="outlined">
            Cancel
          </Button>
          <Button onClick={handleCompleteTransaction} variant="contained" color="success" startIcon={<CompleteIcon />}>
            Confirm & Capture
          </Button>
        </DialogActions>
      </Dialog>

      {/* Cancel Dialog */}
      <Dialog
        open={cancelDialogOpen}
        onClose={() => setCancelDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Cancel Transaction</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Are you sure you want to cancel this transaction? This action cannot be undone.
          </DialogContentText>
          <Box sx={{ mt: 2, p: 2, bgcolor: 'error.light', borderRadius: 1 }}>
            <Typography variant="body2" sx={{ fontWeight: 600 }}>
              Transaction ID: {transaction.id}
            </Typography>
            <Typography variant="body2">
              Amount: {formatCurrency(transaction.amount, transaction.currency)}
            </Typography>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCancelDialogOpen(false)} variant="outlined">
            Keep Transaction
          </Button>
          <Button onClick={handleCancelTransaction} variant="contained" color="error">
            Confirm Cancellation
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};