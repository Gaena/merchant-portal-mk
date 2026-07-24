import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router';
import { apiClient } from '../api/client';
import {
  Box,
  Paper,
  Typography,
  Button,
  IconButton,
  Chip,
  Stack,
  Divider,
  Grid,
  Tooltip,
  Alert,
  Snackbar,
  LinearProgress,
  Avatar,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  DialogContentText,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from '@mui/material';
import {
  ArrowBack as BackIcon,
  ContentCopy as CopyIcon,
  Share as ShareIcon,
  Cancel as CancelIcon,
  CheckCircle as CheckCircleIcon,
  ErrorOutline as ExpiredIcon,
  Email as EmailIcon,
  WhatsApp as WhatsAppIcon,
  QrCode as QrCodeIcon,
  Person as PersonIcon,
  Link as LinkIcon,
  Schedule as TimeIcon,
  Replay as RepeatIcon,
  OpenInNew as OpenIcon,
  Receipt as ReceiptIcon,
  CreditCard as CardIcon,
  Security as SecurityIcon,
  Loop as UsageIcon,
  Done as DoneIcon,
  DoneAll as FinalizeIcon,
  Warning as WarningIcon,
  Lock as AuthorizedIcon,
  LockOpen as CaptureIcon,
} from '@mui/icons-material';
import {
  generateLinks,
  formatDateTime,
  formatTimeLeft,
  expiryPercent,
  statusConfig,
  getStatusConfig,
} from '../utils/payByLinkData';
import type {
  PaymentLink,
  LinkStatus,
  DmsStatus,
} from '../utils/payByLinkData';

// ─── Helpers ──────────────────────────────────────────────────────────────────

const InfoRow: React.FC<{ label: string; value: React.ReactNode; mono?: boolean }> = ({ label, value, mono }) => (
  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', py: 1.25, gap: 2 }}>
    <Typography variant="body2" color="text.secondary" sx={{ flexShrink: 0, minWidth: 140 }}>
      {label}
    </Typography>
    <Box sx={{ textAlign: 'right', flex: 1 }}>
      {typeof value === 'string' ? (
        <Typography variant="body2" sx={{ fontWeight: 600, fontFamily: mono ? 'monospace' : 'inherit', wordBreak: 'break-all' }}>
          {value}
        </Typography>
      ) : value}
    </Box>
  </Box>
);

interface TimelineEvent {
  label: string;
  time: string;
  icon: React.ReactNode;
  color: string;
  detail?: string;
}

const buildTimeline = (link: PaymentLink): TimelineEvent[] => {
  const events: TimelineEvent[] = [
    {
      label: 'Link Created',
      time: formatDateTime(link.createdAt),
      icon: <LinkIcon sx={{ fontSize: 16 }} />,
      color: '#1565c0',
      detail: `Expiry set to ${formatDateTime(link.expiresAt)}`,
    },
  ];

  if (link.sentVia?.includes('email') && link.customerEmail) {
    events.push({
      label: 'Sent by Email',
      time: formatDateTime(new Date(link.createdAt.getTime() + 2 * 60 * 1000)),
      icon: <EmailIcon sx={{ fontSize: 16 }} />,
      color: '#7b1fa2',
      detail: `Delivered to ${link.customerEmail}`,
    });
  }

  if (link.sentVia?.includes('whatsapp') && link.customerPhone) {
    events.push({
      label: 'Sent via WhatsApp',
      time: formatDateTime(new Date(link.createdAt.getTime() + 4 * 60 * 1000)),
      icon: <WhatsAppIcon sx={{ fontSize: 16 }} />,
      color: '#2e7d32',
      detail: `Sent to ${link.customerPhone}`,
    });
  }

  if (link.status === 'paid' && link.paidAt) {
    events.push({
      label: 'Payment Received',
      time: formatDateTime(link.paidAt),
      icon: <CheckCircleIcon sx={{ fontSize: 16 }} />,
      color: '#2e7d32',
      detail: `₼${link.amount.toFixed(2)} via ${link.cardNetwork} ···· ${link.cardLast4}`,
    });
    if (link.redirectUrl) {
      events.push({
        label: 'Customer Redirected',
        time: formatDateTime(new Date(link.paidAt.getTime() + 3 * 1000)),
        icon: <OpenIcon sx={{ fontSize: 16 }} />,
        color: '#546e7a',
        detail: link.redirectUrl,
      });
    }
  }

  if (link.status === 'expired') {
    events.push({
      label: 'Link Expired',
      time: formatDateTime(link.expiresAt),
      icon: <ExpiredIcon sx={{ fontSize: 16 }} />,
      color: '#546e7a',
      detail: 'No payment was received before expiry',
    });
  }

  if (link.status === 'cancelled') {
    events.push({
      label: 'Link Cancelled',
      time: formatDateTime(new Date(link.createdAt.getTime() + 30 * 60 * 1000)),
      icon: <CancelIcon sx={{ fontSize: 16 }} />,
      color: '#c62828',
      detail: 'Manually cancelled by merchant',
    });
  }

  return events;
};

// ─── Linked transactions ──────────────────────────────────────────────────────

interface LinkedTxn {
  id: string;
  timestamp: Date;
  type: string;       // SMS / DMS Auth / DMS Capture
  amount: number;
  cardNetwork: string;
  cardLast4: string;
  status: 'completed' | 'authorized' | 'captured' | 'failed' | 'refunded';
}

const txnStatusConfig = {
  completed:  { label: 'Completed',  color: '#2e7d32', bgcolor: 'rgba(46,125,50,0.1)' },
  authorized: { label: 'Authorized', color: '#e65100', bgcolor: 'rgba(230,81,0,0.1)' },
  captured:   { label: 'Captured',   color: '#1565c0', bgcolor: 'rgba(21,101,192,0.1)' },
  failed:     { label: 'Failed',     color: '#c62828', bgcolor: 'rgba(198,40,40,0.1)' },
  refunded:   { label: 'Refunded',   color: '#546e7a', bgcolor: 'rgba(84,110,122,0.1)' },
};

const buildLinkedTxns = (link: PaymentLink): LinkedTxn[] => {
  if (link.status !== 'paid') return [];

  const base = link.paidAt ?? link.createdAt;
  const card = `${link.cardNetwork ?? 'Visa'} ···· ${link.cardLast4 ?? '4242'}`;
  const txns: LinkedTxn[] = [];

  if (link.paymentType === 'sms') {
    txns.push({
      id: link.transactionId ?? `TXN-${link.shortCode}-001`,
      timestamp: base,
      type: 'SMS — Immediate Charge',
      amount: link.amount,
      cardNetwork: link.cardNetwork ?? 'Visa',
      cardLast4: link.cardLast4 ?? '4242',
      status: 'completed',
    });
  } else {
    // DMS: authorization entry
    txns.push({
      id: `${link.transactionId ?? `TXN-${link.shortCode}`}-AUTH`,
      timestamp: base,
      type: 'DMS — Authorization',
      amount: link.amount,
      cardNetwork: link.cardNetwork ?? 'Visa',
      cardLast4: link.cardLast4 ?? '4242',
      status: 'authorized',
    });
    // DMS: capture entry (if finalized)
    if (link.dmsStatus === 'finalized' && link.finalizedAt) {
      txns.push({
        id: `${link.transactionId ?? `TXN-${link.shortCode}`}-CAP`,
        timestamp: link.finalizedAt,
        type: 'DMS — Capture',
        amount: link.amount,
        cardNetwork: link.cardNetwork ?? 'Visa',
        cardLast4: link.cardLast4 ?? '4242',
        status: 'captured',
      });
    }
  }

  return txns;
};

const LinkedTransactions: React.FC<{ link: PaymentLink }> = ({ link }) => {
  const navigate = useNavigate();
  const [transactions, setTransactions] = useState<any[]>([]);

  useEffect(() => {
    if (link.id) {
      apiClient.get(`/api/v1/payment-links/${link.id}/transactions`)
        .then(res => {
          if (Array.isArray(res.data)) {
            setTransactions(res.data);
          }
        })
        .catch(() => {});
    }
  }, [link.id]);

  const fallbackTxns = buildLinkedTxns(link);
  const displayTxns = transactions.length > 0 ? transactions : fallbackTxns;

  return (
    <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, overflow: 'hidden' }}>
      <Box sx={{ px: 3, py: 2, display: 'flex', alignItems: 'center', gap: 1, borderBottom: '1px solid', borderColor: 'divider' }}>
        <ReceiptIcon color="action" fontSize="small" />
        <Typography variant="h6" sx={{ fontWeight: 700, fontSize: '1rem' }}>
          Linked Transactions
        </Typography>
        <Chip label={displayTxns.length} size="small" sx={{ ml: 0.5, height: 18, fontSize: '0.7rem' }} />
      </Box>

      {displayTxns.length === 0 ? (
        <Box sx={{ py: 5, textAlign: 'center' }}>
          <ReceiptIcon sx={{ fontSize: 40, color: 'text.disabled', mb: 1 }} />
          <Typography variant="body2" color="text.secondary">
            No transactions yet — they will appear once the customer opens or pays.
          </Typography>
        </Box>
      ) : (
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow sx={{ bgcolor: 'rgba(0,0,0,0.02)' }}>
                <TableCell sx={{ fontWeight: 600 }}>Transaction ID</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Date & Time</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Payer IP</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Device / User-Agent</TableCell>
                <TableCell sx={{ fontWeight: 600 }} align="right">Amount</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Status</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {displayTxns.map((txn: any) => {
                const isReal = !!txn.createdAt;
                const status = isReal ? (txn.status || 'PENDING').toUpperCase() : String(txn.status).toUpperCase();
                const statusColors: Record<string, { label: string; color: any; bgcolor: string }> = {
                  SUCCESS: { label: 'SUCCESS', color: 'success', bgcolor: 'rgba(46,125,50,0.1)' },
                  COMPLETED: { label: 'SUCCESS', color: 'success', bgcolor: 'rgba(46,125,50,0.1)' },
                  AUTHORIZED: { label: 'AUTHORIZED', color: 'warning', bgcolor: 'rgba(230,81,0,0.1)' },
                  PENDING: { label: 'PENDING', color: 'info', bgcolor: 'rgba(2,136,209,0.1)' },
                  FAILED: { label: 'FAILED', color: 'error', bgcolor: 'rgba(198,40,40,0.1)' },
                  REFUNDED: { label: 'REFUNDED', color: 'default', bgcolor: 'rgba(84,110,122,0.1)' },
                };
                const sc = statusColors[status] || { label: status, color: 'default', bgcolor: 'rgba(0,0,0,0.05)' };

                return (
                  <TableRow
                    key={txn.id}
                    hover
                    onClick={() => {
                      const statusLower = (status === 'SUCCESS' || status === 'COMPLETED') ? 'success' :
                                          status === 'AUTHORIZED' ? 'pending' :
                                          status === 'FAILED' ? '3d-failed' : 'canceled';
                      const txObj = {
                        id: String(txn.id),
                        timestamp: new Date(txn.createdAt || txn.timestamp || Date.now()),
                        customer: txn.customerName || link.customerName || 'N/A',
                        customerEmail: txn.customerEmail || link.customerEmail || 'N/A',
                        amount: Number(txn.amount ?? link.amount),
                        currency: txn.currency || link.currency || 'AZN',
                        status: statusLower as any,
                        paymentMethod: link.paymentType as any || 'sms',
                        description: link.description || 'Payment Link Transaction',
                        merchantReference: txn.merchantOrderId || txn.providerOrderId || txn.provider_order_id || txn.id,
                        providerOrderId: txn.providerOrderId || txn.provider_order_id,
                        cardLast4: txn.cardNumberMasked ? String(txn.cardNumberMasked).slice(-4) : (txn.cardLast4 || undefined),
                        fee: Number(txn.fee || 0),
                        terminalRid: link.merchantRid ? String(link.merchantRid) : (link.terminalId ? `TRM-${link.terminalId}` : '—'),
                        channel: 'ecommerce' as const,
                        clientIp: txn.clientIp,
                        userAgent: txn.userAgent,
                        statusHistory: [
                          {
                            status: 'pending' as const,
                            timestamp: new Date(txn.createdAt || txn.timestamp || Date.now()),
                            note: 'Payment attempt initiated by customer',
                          },
                          ...(status === 'SUCCESS' || status === 'COMPLETED' ? [{
                            status: 'success' as const,
                            timestamp: new Date(txn.createdAt || txn.timestamp || Date.now()),
                            note: 'Payment successfully completed',
                          }] : [])
                        ]
                      };
                      navigate(`/transactions/${txn.id}`, { state: { transaction: txObj } });
                    }}
                    sx={{ cursor: 'pointer', '&:hover': { bgcolor: 'rgba(0,0,0,0.04)' } }}
                  >
                    <TableCell>
                      <Typography variant="caption" sx={{ fontFamily: 'monospace', fontWeight: 700 }}>
                        {txn.id}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" color="text.secondary">
                        {formatDateTime(txn.createdAt ? new Date(txn.createdAt) : txn.timestamp)}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" sx={{ fontFamily: 'monospace', fontWeight: 600 }}>
                        {txn.clientIp || '127.0.0.1'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" color="text.secondary" sx={{ maxWidth: 180, display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {txn.userAgent || 'Macintosh; Intel Mac OS X'}
                      </Typography>
                    </TableCell>
                    <TableCell align="right">
                      <Typography variant="caption" sx={{ fontWeight: 700 }}>
                        ₼{(txn.amount ?? link.amount).toFixed(2)}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={sc.label}
                        size="small"
                        color={sc.color}
                        variant="outlined"
                        sx={{ fontWeight: 700, fontSize: '0.68rem', bgcolor: sc.bgcolor }}
                      />
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Paper>
  );
};

// ─── Component ────────────────────────────────────────────────────────────────

export const PayByLinkDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();

  const [link, setLink] = useState<PaymentLink | null>(location.state?.link ?? null);

  useEffect(() => {
    if (id) {
      apiClient.get(`/api/v1/payment-links/${id}`)
        .then(res => {
          const l = res.data;
          if (l && l.id) {
            const pt = String(l.paymentType || l.payment_type || 'sms').toLowerCase();
            const ut = String(l.usageType || l.usage_type || 'single').toLowerCase();
            setLink({
              id: l.id,
              shortCode: l.id.slice(0, 8).toUpperCase(),
              url: `${window.location.origin}/api/v1/payment-links/${l.id}/open`,
              status: (l.status || 'active').toLowerCase() as LinkStatus,
              amount: l.amount,
              currency: l.currency || 'AZN',
              description: l.description || 'Payment Link',
              customerName: l.customer?.fullName || l.customerName || 'N/A',
              customerEmail: l.customer?.email || l.customerEmail || 'N/A',
              customerPhone: l.customer?.phone || l.customerPhone || 'N/A',
              usageType: (ut === 'multiple' ? 'multiple' : 'single') as any,
              maxUses: l.maxPayments || 1,
              usedCount: l.currentPaymentsCount || 0,
              createdAt: new Date(l.createdAt),
              expiresAt: l.expiresAt ? new Date(l.expiresAt) : new Date(Date.now() + 86400000),
              paymentType: (pt === 'dms' ? 'dms' : 'sms') as any,
            });
          }
        })
        .catch(() => {});
    }
  }, [id]);

  const [snackbar, setSnackbar] = useState('');
  const [cancelDialogOpen, setCancelDialogOpen] = useState(false);
  const [finalizeDialogOpen, setFinalizeDialogOpen] = useState(false);

  const isDmsAuthorized =
    link?.paymentType === 'dms' &&
    link?.status === 'paid' &&
    link?.dmsStatus === 'authorized';

  if (!link) {
    return (
      <Box sx={{ textAlign: 'center', py: 10 }}>
        <Typography variant="h6" color="text.secondary">Payment link not found</Typography>
        <Button sx={{ mt: 2 }} onClick={() => navigate('/pay-by-link')}>Back to Pay by Link</Button>
      </Box>
    );
  }

  const cfg = getStatusConfig(link.status);
  const timeline = buildTimeline(link);
  const expPct = link.status === 'active' ? expiryPercent(link) : null;
  const isActive = link.status === 'active';

  const copy = (text: string, msg = 'Copied to clipboard') => {
    navigator.clipboard.writeText(text).catch(() => {});
    setSnackbar(msg);
  };

  const handleCancel = async () => {
    if (!link) return;
    try {
      await apiClient.patch(`/api/v1/payment-links/${link.id}`, { status: 'CANCELED' });
      setLink(prev => prev ? { ...prev, status: 'cancelled' } : prev);
      setCancelDialogOpen(false);
      setSnackbar('Payment link cancelled successfully');
    } catch (err: any) {
      const msg = err.response?.data?.message || 'Failed to cancel payment link on server';
      setSnackbar(msg);
    }
  };

  const handleFinalize = async () => {
    if (!link) return;
    const txId = link.transactionId || link.id;
    try {
      await apiClient.post(`/api/v1/transactions/${txId}/complete`, { amount: link.amount });
      setLink(prev => prev ? {
        ...prev,
        dmsStatus: 'finalized',
        finalizedAt: new Date(),
      } : prev);
      setFinalizeDialogOpen(false);
      setSnackbar('Payment finalized — funds captured successfully');
    } catch (err: any) {
      const msg = err.response?.data?.message || 'Failed to complete DMS transaction on server';
      setSnackbar(msg);
    }
  };

  return (
    <Box>
      {/* ── Page header ──────────────────────────────────────────────────── */}
      <Box sx={{ mb: 3.5, display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <IconButton onClick={() => navigate('/pay-by-link')} size="small" sx={{ border: '1px solid', borderColor: 'divider' }}>
            <BackIcon fontSize="small" />
          </IconButton>
          <Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap' }}>
              <Typography variant="h5" sx={{ fontWeight: 700 }}>
                {link.shortCode}
              </Typography>
              <Chip
                label={cfg.label}
                size="small"
                sx={{ fontWeight: 700, bgcolor: cfg.bgColor, color: cfg.textColor, fontSize: '0.75rem' }}
              />
            </Box>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }}>
              {link.description}
            </Typography>
          </Box>
        </Box>

        <Stack direction="row" spacing={1.5} flexWrap="wrap">
          <Tooltip title="Copy payment link">
            <span>
              <Button
                variant="outlined"
                startIcon={<CopyIcon />}
                onClick={() => copy(link.url)}
                disabled={!isActive}
              >
                Copy Link
              </Button>
            </span>
          </Tooltip>
          <Tooltip title="Share">
            <span>
              <Button
                variant="outlined"
                startIcon={<ShareIcon />}
                disabled={!isActive}
                onClick={() => {
                  const text = encodeURIComponent(`Hi ${link.customerName}, please complete your payment of ₼${link.amount.toFixed(2)}: ${link.url}`);
                  window.open(`https://wa.me/${link.customerPhone.replace(/\D/g, '')}?text=${text}`, '_blank');
                }}
              >
                Share
              </Button>
            </span>
          </Tooltip>
          {isDmsAuthorized && (
            <Button
              variant="outlined"
              color="success"
              startIcon={<FinalizeIcon />}
              onClick={() => setFinalizeDialogOpen(true)}
            >
              Finalize Payment
            </Button>
          )}
          {isActive && (
            <Button
              variant="outlined"
              color="error"
              startIcon={<CancelIcon />}
              onClick={() => setCancelDialogOpen(true)}
            >
              Cancel Link
            </Button>
          )}
        </Stack>
      </Box>

      <Grid container spacing={3}>

        {/* ── LEFT COLUMN ───────────────────────────────────────────────── */}
        <Grid size={{ xs: 12, lg: 8 }}>
          <Stack spacing={3}>

            {/* Amount hero card */}
            <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, overflow: 'hidden' }}>
              <Box sx={{
                px: 3, py: 2.5,
                background: link.status === 'paid'
                  ? 'linear-gradient(135deg, #1b5e20 0%, #2e7d32 100%)'
                  : link.status === 'active'
                  ? 'linear-gradient(135deg, #0d47a1 0%, #1976d2 100%)'
                  : 'linear-gradient(135deg, #37474f 0%, #546e7a 100%)',
                color: 'white',
                display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 2,
              }}>
                <Box>
                  <Typography variant="caption" sx={{ opacity: 0.8, textTransform: 'uppercase', letterSpacing: 1, fontSize: '0.7rem' }}>
                    {link.status === 'paid' ? 'Amount Received' : 'Amount Requested'}
                  </Typography>
                  <Typography variant="h3" sx={{ fontWeight: 800, mt: 0.25, lineHeight: 1 }}>
                    ₼{link.amount.toFixed(2)}
                  </Typography>
                  <Typography variant="body2" sx={{ opacity: 0.85, mt: 0.75 }}>{link.description}</Typography>
                </Box>
                <Box sx={{ textAlign: 'right' }}>
                  {link.status === 'paid' && link.paidAt && (
                    <Box>
                      <Typography variant="caption" sx={{ opacity: 0.8 }}>Paid on</Typography>
                      <Typography variant="body1" sx={{ fontWeight: 700 }}>{formatDateTime(link.paidAt)}</Typography>
                    </Box>
                  )}
                  {link.status === 'active' && (
                    <Box>
                      <Typography variant="caption" sx={{ opacity: 0.8 }}>Expires</Typography>
                      <Typography variant="body1" sx={{ fontWeight: 700 }}>{formatTimeLeft(link.expiresAt)}</Typography>
                      {expPct !== null && (
                        <LinearProgress
                          variant="determinate"
                          value={expPct}
                          sx={{ mt: 1, height: 4, borderRadius: 2, bgcolor: 'rgba(255,255,255,0.2)', '& .MuiLinearProgress-bar': { bgcolor: expPct < 20 ? '#ff5252' : expPct < 50 ? '#ffca28' : 'white' } }}
                        />
                      )}
                    </Box>
                  )}
                  {link.status === 'expired' && (
                    <Typography variant="body2" sx={{ opacity: 0.8 }}>Expired {formatDateTime(link.expiresAt)}</Typography>
                  )}
                  {link.status === 'cancelled' && (
                    <Typography variant="body2" sx={{ opacity: 0.8 }}>Manually cancelled</Typography>
                  )}
                </Box>
              </Box>

              {/* URL bar */}
              <Box sx={{ px: 3, py: 1.75, display: 'flex', alignItems: 'center', gap: 1.5, bgcolor: 'action.hover', borderTop: '1px solid', borderColor: 'divider' }}>
                <LinkIcon color="action" fontSize="small" />
                <Typography
                  variant="body2"
                  sx={{ flex: 1, fontFamily: 'monospace', fontWeight: 600, color: isActive ? 'primary.main' : 'text.secondary', wordBreak: 'break-all' }}
                >
                  {link.url}
                </Typography>
                <Tooltip title="Copy link">
                  <span>
                    <IconButton size="small" onClick={() => copy(link.url)} disabled={!isActive}>
                      <CopyIcon fontSize="small" />
                    </IconButton>
                  </span>
                </Tooltip>
                <Tooltip title="Open in new tab">
                  <span>
                    <IconButton size="small" disabled={!isActive} onClick={() => window.open(link.url, '_blank')}>
                      <OpenIcon fontSize="small" />
                    </IconButton>
                  </span>
                </Tooltip>
              </Box>
            </Paper>

            {/* Payment details (paid only) */}
            {link.status === 'paid' && (
              <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                  <ReceiptIcon color="action" fontSize="small" />
                  <Typography variant="h6" sx={{ fontWeight: 700, fontSize: '1rem' }}>Payment Details</Typography>
                </Box>
                <Divider sx={{ mb: 2 }} />
                <InfoRow
                  label="Payment Type"
                  value={
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, justifyContent: 'flex-end' }}>
                      <Chip
                        label={link.paymentType === 'dms' ? 'DMS' : 'SMS'}
                        size="small"
                        variant="outlined"
                        color={link.paymentType === 'dms' ? 'warning' : 'primary'}
                        sx={{ fontWeight: 700, fontFamily: 'monospace' }}
                      />
                      <Typography variant="caption" color="text.secondary">
                        {link.paymentType === 'dms' ? 'Authorize & Capture' : 'Immediate charge'}
                      </Typography>
                    </Box>
                  }
                />
                {link.paymentType === 'dms' && (
                  <>
                    <Divider sx={{ opacity: 0.5 }} />
                    <InfoRow
                      label="DMS Status"
                      value={
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, justifyContent: 'flex-end' }}>
                          {link.dmsStatus === 'authorized' ? (
                            <>
                              <AuthorizedIcon sx={{ fontSize: 16, color: 'warning.main' }} />
                              <Chip label="Authorized — Awaiting Capture" size="small" color="warning" sx={{ fontWeight: 600 }} />
                            </>
                          ) : (
                            <>
                              <CaptureIcon sx={{ fontSize: 16, color: 'success.main' }} />
                              <Chip label="Finalized — Captured" size="small" color="success" sx={{ fontWeight: 600 }} />
                            </>
                          )}
                        </Box>
                      }
                    />
                    {link.finalizedAt && (
                      <>
                        <Divider sx={{ opacity: 0.5 }} />
                        <InfoRow label="Finalized At" value={formatDateTime(link.finalizedAt)} />
                      </>
                    )}
                  </>
                )}
                <Divider sx={{ opacity: 0.5 }} />
                <InfoRow label="Transaction ID" value={link.transactionId ?? '—'} mono />
                <Divider sx={{ opacity: 0.5 }} />
                <InfoRow
                  label="Payment Method"
                  value={
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, justifyContent: 'flex-end' }}>
                      <CardIcon fontSize="small" color="action" />
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>
                        {link.cardNetwork} ···· {link.cardLast4}
                      </Typography>
                    </Box>
                  }
                />
                <Divider sx={{ opacity: 0.5 }} />
                <InfoRow label="Paid At" value={link.paidAt ? formatDateTime(link.paidAt) : '—'} />
                <Divider sx={{ opacity: 0.5 }} />
                <InfoRow
                  label="Payer IP"
                  value={
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, justifyContent: 'flex-end' }}>
                      <SecurityIcon fontSize="small" color="action" sx={{ fontSize: 14 }} />
                      <Typography variant="body2" sx={{ fontFamily: 'monospace', fontWeight: 600 }}>{link.payerIp}</Typography>
                    </Box>
                  }
                />
                <Divider sx={{ opacity: 0.5 }} />
                <InfoRow label="Amount" value={`₼${link.amount.toFixed(2)} ${link.currency}`} />
              </Paper>
            )}

            {/* Link settings */}
            <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                <UsageIcon color="action" fontSize="small" />
                <Typography variant="h6" sx={{ fontWeight: 700, fontSize: '1rem' }}>Link Settings</Typography>
              </Box>
              <Divider sx={{ mb: 2 }} />
              <InfoRow
                label="Payment Type"
                value={
                  <Chip
                    size="small"
                    label={link.paymentType === 'dms' ? 'DMS — Authorize & Capture' : 'SMS — Immediate Charge'}
                    color={link.paymentType === 'dms' ? 'warning' : 'primary'}
                    variant="outlined"
                    sx={{ fontWeight: 600 }}
                  />
                }
              />
              <Divider sx={{ opacity: 0.5 }} />
              <InfoRow label="Usage Type" value={
                <Chip
                  size="small"
                  label={link.usageType === 'single' ? 'Single Use' : 'Multiple Uses'}
                  variant="outlined"
                  sx={{ fontWeight: 600 }}
                />
              } />
              {link.usageType === 'multiple' && (
                <>
                  <Divider sx={{ opacity: 0.5 }} />
                  <InfoRow
                    label="Usage"
                    value={
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, justifyContent: 'flex-end' }}>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>{link.usedCount} / {link.maxUses}</Typography>
                        <LinearProgress
                          variant="determinate"
                          value={(link.usedCount / link.maxUses) * 100}
                          sx={{ width: 80, height: 6, borderRadius: 3 }}
                        />
                      </Box>
                    }
                  />
                </>
              )}
              <Divider sx={{ opacity: 0.5 }} />
              <InfoRow label="Created" value={formatDateTime(link.createdAt)} />
              <Divider sx={{ opacity: 0.5 }} />
              <InfoRow label="Expires" value={formatDateTime(link.expiresAt)} />
              {link.redirectUrl && (
                <>
                  <Divider sx={{ opacity: 0.5 }} />
                  <InfoRow
                    label="Redirect After Pay"
                    value={
                      <Typography variant="body2" sx={{ fontFamily: 'monospace', fontWeight: 600, color: 'primary.main', wordBreak: 'break-all' }}>
                        {link.redirectUrl}
                      </Typography>
                    }
                  />
                </>
              )}
              {link.note && (
                <>
                  <Divider sx={{ opacity: 0.5 }} />
                  <InfoRow
                    label="Internal Note"
                    value={
                      <Typography variant="body2" sx={{ fontStyle: 'italic', color: 'text.secondary' }}>
                        {link.note}
                      </Typography>
                    }
                  />
                </>
              )}
            </Paper>

            {/* Activity timeline */}
            <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2.5 }}>
                <TimeIcon color="action" fontSize="small" />
                <Typography variant="h6" sx={{ fontWeight: 700, fontSize: '1rem' }}>Activity Timeline</Typography>
              </Box>
              <Box sx={{ position: 'relative', pl: 3 }}>
                {/* Vertical line */}
                <Box sx={{
                  position: 'absolute', left: 11, top: 12, bottom: 12,
                  width: 2, bgcolor: 'divider', borderRadius: 1,
                }} />

                {timeline.map((event, idx) => (
                  <Box key={idx} sx={{ display: 'flex', gap: 2, mb: idx < timeline.length - 1 ? 3 : 0, position: 'relative' }}>
                    {/* Dot */}
                    <Box sx={{
                      width: 24, height: 24, borderRadius: '50%', flexShrink: 0,
                      bgcolor: event.color, display: 'flex', alignItems: 'center',
                      justifyContent: 'center', color: 'white', zIndex: 1,
                      boxShadow: `0 0 0 3px white, 0 0 0 4px ${event.color}33`,
                    }}>
                      {event.icon}
                    </Box>
                    {/* Content */}
                    <Box sx={{ flex: 1, pt: 0.25 }}>
                      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1.5, flexWrap: 'wrap' }}>
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>{event.label}</Typography>
                        <Typography variant="caption" color="text.disabled">{event.time}</Typography>
                      </Box>
                      {event.detail && (
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.25 }}>
                          {event.detail}
                        </Typography>
                      )}
                    </Box>
                  </Box>
                ))}
              </Box>
            </Paper>

            {/* Linked transactions */}
            <LinkedTransactions link={link} />

          </Stack>
        </Grid>

        {/* ── RIGHT COLUMN ──────────────────────────────────────────────── */}
        <Grid size={{ xs: 12, lg: 4 }}>
          <Stack spacing={3}>

            {/* Customer card */}
            <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                <PersonIcon color="action" fontSize="small" />
                <Typography variant="h6" sx={{ fontWeight: 700, fontSize: '1rem' }}>Customer</Typography>
              </Box>
              <Divider sx={{ mb: 2 }} />

              {link.customerName ? (
                <>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2.5 }}>
                    <Avatar sx={{ width: 48, height: 48, bgcolor: 'primary.main', fontWeight: 700 }}>
                      {link.customerName.split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase()}
                    </Avatar>
                    <Box>
                      <Typography variant="body1" sx={{ fontWeight: 700 }}>{link.customerName}</Typography>
                      <Typography variant="caption" color="text.secondary">{link.customerEmail}</Typography>
                    </Box>
                  </Box>

                  {link.customerEmail && (
                    <>
                      <InfoRow label="Email" value={link.customerEmail} />
                      <Divider sx={{ opacity: 0.5 }} />
                    </>
                  )}
                  {link.customerPhone && (
                    <InfoRow label="Phone" value={link.customerPhone} mono />
                  )}

                  {isActive && (
                    <>
                      <Divider sx={{ my: 2 }} />
                      <Stack spacing={1}>
                        <Button
                          fullWidth
                          variant="outlined"
                          size="small"
                          startIcon={<EmailIcon />}
                          href={`mailto:${link.customerEmail}?subject=Payment Request — ₼${link.amount.toFixed(2)}&body=Hi ${link.customerName},%0A%0APlease complete your payment using the link below:%0A${link.url}%0A%0AAmount: ₼${link.amount.toFixed(2)}%0ADescription: ${link.description}%0A%0AThank you.`}
                          disabled={!link.customerEmail}
                        >
                          Send by Email
                        </Button>
                        <Button
                          fullWidth
                          variant="outlined"
                          size="small"
                          color="success"
                          startIcon={<WhatsAppIcon />}
                          onClick={() => {
                            const txt = encodeURIComponent(`Hi ${link.customerName}, please pay ₼${link.amount.toFixed(2)} using this link: ${link.url}`);
                            window.open(`https://wa.me/${link.customerPhone.replace(/\D/g, '')}?text=${txt}`, '_blank');
                          }}
                          disabled={!link.customerPhone}
                        >
                          Send via WhatsApp
                        </Button>
                        <Button
                          fullWidth
                          variant="outlined"
                          size="small"
                          startIcon={<CopyIcon />}
                          onClick={() => copy(link.url)}
                        >
                          Copy Link
                        </Button>
                        <Button
                          fullWidth
                          variant="outlined"
                          size="small"
                          startIcon={<QrCodeIcon />}
                          onClick={() => setSnackbar('QR code feature coming soon')}
                        >
                          Generate QR Code
                        </Button>
                      </Stack>
                    </>
                  )}
                </>
              ) : (
                <Typography variant="body2" color="text.disabled" sx={{ fontStyle: 'italic', textAlign: 'center', py: 2 }}>
                  No customer info provided
                </Typography>
              )}
            </Paper>

            {/* Status & expiry card */}
            <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                <TimeIcon color="action" fontSize="small" />
                <Typography variant="h6" sx={{ fontWeight: 700, fontSize: '1rem' }}>Status</Typography>
              </Box>
              <Divider sx={{ mb: 2 }} />

              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 2 }}>
                <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: cfg.textColor, flexShrink: 0 }} />
                <Typography variant="body1" sx={{ fontWeight: 700, color: cfg.textColor }}>{cfg.label}</Typography>
              </Box>

              {link.status === 'active' && expPct !== null && (
                <Box>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.75 }}>
                    <Typography variant="caption" color="text.secondary">Time remaining</Typography>
                    <Typography variant="caption" sx={{ fontWeight: 700, color: expPct < 20 ? 'error.main' : 'text.primary' }}>
                      {formatTimeLeft(link.expiresAt)}
                    </Typography>
                  </Box>
                  <LinearProgress
                    variant="determinate"
                    value={expPct}
                    color={expPct < 20 ? 'error' : expPct < 50 ? 'warning' : 'primary'}
                    sx={{ height: 8, borderRadius: 4, mb: 1 }}
                  />
                  <Typography variant="caption" color="text.disabled">
                    Expires {formatDateTime(link.expiresAt)}
                  </Typography>
                </Box>
              )}

              {link.status === 'paid' && link.paymentType === 'sms' && (
                <Alert severity="success" icon={<DoneIcon fontSize="small" />} sx={{ mt: 1 }}>
                  Payment received on {link.paidAt ? formatDateTime(link.paidAt) : '—'}
                </Alert>
              )}
              {link.status === 'paid' && link.paymentType === 'dms' && link.dmsStatus === 'authorized' && (
                <Alert severity="warning" icon={<AuthorizedIcon fontSize="small" />} sx={{ mt: 1 }}>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>Funds Authorized</Typography>
                  <Typography variant="caption">
                    ₼{link.amount.toFixed(2)} is reserved on the customer's card. Press <strong>Finalize Payment</strong> to capture the funds.
                  </Typography>
                </Alert>
              )}
              {link.status === 'paid' && link.paymentType === 'dms' && link.dmsStatus === 'finalized' && (
                <Alert severity="success" icon={<FinalizeIcon fontSize="small" />} sx={{ mt: 1 }}>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>Payment Finalized</Typography>
                  <Typography variant="caption">
                    Funds captured on {link.finalizedAt ? formatDateTime(link.finalizedAt) : '—'}
                  </Typography>
                </Alert>
              )}

              {link.status === 'expired' && (
                <Alert severity="warning" icon={<WarningIcon fontSize="small" />} sx={{ mt: 1 }}>
                  Expired without payment on {formatDateTime(link.expiresAt)}
                </Alert>
              )}

              {link.status === 'cancelled' && (
                <Alert severity="error" icon={<CancelIcon fontSize="small" />} sx={{ mt: 1 }}>
                  This link was manually cancelled
                </Alert>
              )}
            </Paper>

            {/* Quick actions */}
            {(link.status === 'expired' || link.status === 'cancelled') && (
              <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
                <Typography variant="h6" sx={{ fontWeight: 700, fontSize: '1rem', mb: 2 }}>Quick Actions</Typography>
                <Divider sx={{ mb: 2 }} />
                <Button
                  fullWidth
                  variant="contained"
                  startIcon={<RepeatIcon />}
                  onClick={() => navigate('/pay-by-link', { state: { prefill: link } })}
                >
                  Create New Link (Same Details)
                </Button>
              </Paper>
            )}

          </Stack>
        </Grid>

      </Grid>

      {/* ── Finalize dialog ───────────────────────────────────────────────── */}
      <Dialog open={finalizeDialogOpen} onClose={() => setFinalizeDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>Finalize DMS Payment</DialogTitle>
        <DialogContent>
          <DialogContentText>
            This will capture <strong>₼{link.amount.toFixed(2)}</strong> that is currently authorized on the customer's card.
            The funds will be transferred to your account. This action cannot be undone.
          </DialogContentText>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5, gap: 1 }}>
          <Button onClick={() => setFinalizeDialogOpen(false)} variant="outlined">Cancel</Button>
          <Button onClick={handleFinalize} variant="contained" color="success" startIcon={<FinalizeIcon />}>
            Finalize &amp; Capture
          </Button>
        </DialogActions>
      </Dialog>

      {/* ── Cancel dialog ─────────────────────────────────────────────────── */}
      <Dialog open={cancelDialogOpen} onClose={() => setCancelDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>Cancel Payment Link</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Are you sure you want to cancel <strong>{link.shortCode}</strong>? The link will stop working immediately and customers will no longer be able to pay through it.
          </DialogContentText>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5, gap: 1 }}>
          <Button onClick={() => setCancelDialogOpen(false)} variant="outlined">Keep Link</Button>
          <Button onClick={handleCancel} variant="contained" color="error" startIcon={<CancelIcon />}>
            Cancel Link
          </Button>
        </DialogActions>
      </Dialog>

      {/* Snackbar */}
      <Snackbar
        open={!!snackbar}
        autoHideDuration={3000}
        onClose={() => setSnackbar('')}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity="success" onClose={() => setSnackbar('')} sx={{ width: '100%' }}>
          {snackbar}
        </Alert>
      </Snackbar>
    </Box>
  );
};
