import React, { useState, useMemo, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router';
import { apiClient } from '../api/client';
import { useLanguage } from '../context/LanguageContext';
import {
  Box,
  Paper,
  Typography,
  Button,
  TextField,
  MenuItem,
  Chip,
  Stack,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  InputAdornment,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  Tooltip,
  Divider,
  Alert,
  Snackbar,
  ToggleButton,
  ToggleButtonGroup,
  FormControlLabel,
  Switch,
  LinearProgress,
  Card,
  CardContent,
  Grid,
} from '@mui/material';
import {
  Link as LinkIcon,
  Add as AddIcon,
  ContentCopy as CopyIcon,
  Share as ShareIcon,
  Cancel as CancelIcon,
  CheckCircle as CheckCircleIcon,
  Schedule as ScheduleIcon,
  ErrorOutline as ExpiredIcon,
  Send as SendIcon,
  QrCode as QrCodeIcon,
  WhatsApp as WhatsAppIcon,
  Email as EmailIcon,
  Refresh as RefreshIcon,
  Visibility as ViewIcon,
  FilterList as FilterIcon,
  Close as CloseIcon,
  AttachMoney as AmountIcon,
  Person as PersonIcon,
  AccessTime as TimeIcon,
  TrendingUp as TrendingUpIcon,
} from '@mui/icons-material';

import {
  generateLinks,
  formatDateTime,
  formatTimeLeft,
  statusConfig as sharedStatusConfig,
} from '../utils/payByLinkData';
import type {
  PaymentLink,
  LinkStatus,
  LinkUsageType,
  PaymentType,
} from '../utils/payByLinkData';

// ─── Local status config with icons ──────────────────────────────────────────

const statusConfig: Record<string, { label: string; color: 'success' | 'info' | 'default' | 'error'; icon: React.ReactNode }> = {
  active: { label: 'Active', color: 'success', icon: <CheckCircleIcon sx={{ fontSize: 14 }} /> },
  paid: { label: 'Paid', color: 'info', icon: <CheckCircleIcon sx={{ fontSize: 14 }} /> },
  completed: { label: 'Completed', color: 'info', icon: <CheckCircleIcon sx={{ fontSize: 14 }} /> },
  expired: { label: 'Expired', color: 'default', icon: <ExpiredIcon sx={{ fontSize: 14 }} /> },
  cancelled: { label: 'Cancelled', color: 'error', icon: <CancelIcon sx={{ fontSize: 14 }} /> },
  canceled: { label: 'Canceled', color: 'error', icon: <CancelIcon sx={{ fontSize: 14 }} /> },
};

const getStatusConfig = (status?: string, tObj?: any) => {
  const st = (status || '').toLowerCase();
  const s = tObj?.payByLink?.statuses;
  if (st === 'active') return { label: s?.active || 'Active', color: 'success' as const, icon: <CheckCircleIcon sx={{ fontSize: 14 }} /> };
  if (st === 'paid') return { label: s?.paid || 'Paid', color: 'info' as const, icon: <CheckCircleIcon sx={{ fontSize: 14 }} /> };
  if (st === 'completed') return { label: s?.completed || 'Completed', color: 'info' as const, icon: <CheckCircleIcon sx={{ fontSize: 14 }} /> };
  if (st === 'expired') return { label: s?.expired || 'Expired', color: 'default' as const, icon: <ExpiredIcon sx={{ fontSize: 14 }} /> };
  if (st === 'canceled' || st === 'cancelled') return { label: s?.canceled || 'Canceled', color: 'error' as const, icon: <CancelIcon sx={{ fontSize: 14 }} /> };
  return { label: status || 'Unknown', color: 'default' as const, icon: <ExpiredIcon sx={{ fontSize: 14 }} /> };
};

// ─── Component ───────────────────────────────────────────────────────────────

export const PayByLinkPage: React.FC = () => {
  const navigate = useNavigate();
  const { tObj } = useLanguage();
  const [links, setLinks] = useState<PaymentLink[]>([]);
  const [createOpen, setCreateOpen] = useState(false);
  const [shareOpen, setShareOpen] = useState(false);
  const [selectedLink, setSelectedLink] = useState<PaymentLink | null>(null);
  const [statusFilter, setStatusFilter] = useState<LinkStatus | 'all'>('all');
  const [searchQuery, setSearchQuery] = useState('');
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string }>({ open: false, message: '' });

  // Create form state
  const [form, setForm] = useState({
    amount: '',
    currency: 'AZN',
    description: '',
    customerName: '',
    customerEmail: '',
    customerPhone: '',
    usageType: 'single' as LinkUsageType,
    maxUses: '1',
    expiry: '24h',
    redirectUrl: '',
    note: '',
    paymentType: 'sms' as PaymentType,
    sendEmail: true,
  });
  const [formError, setFormError] = useState('');
  const [generating, setGenerating] = useState(false);
  const [newlyCreatedLink, setNewlyCreatedLink] = useState<PaymentLink | null>(null);

  // Stats
  const stats = useMemo(() => ({
    total: links.length,
    active: links.filter(l => l.status === 'active').length,
    paid: links.filter(l => l.status === 'paid').length,
    expired: links.filter(l => l.status === 'expired').length,
    totalRevenue: links.filter(l => l.status === 'paid').reduce((s, l) => s + l.amount, 0),
    conversionRate: links.length > 0
      ? ((links.filter(l => l.status === 'paid').length / links.length) * 100).toFixed(1)
      : '0',
  }), [links]);

  // Filtered list
  const filtered = useMemo(() => {
    return links.filter(l => {
      if (statusFilter !== 'all' && l.status !== statusFilter) return false;
      if (searchQuery) {
        const q = searchQuery.toLowerCase();
        return (
          l.shortCode.toLowerCase().includes(q) ||
          l.customerName.toLowerCase().includes(q) ||
          l.customerEmail.toLowerCase().includes(q) ||
          l.description.toLowerCase().includes(q)
        );
      }
      return true;
    });
  }, [links, statusFilter, searchQuery]);

  const paginated = filtered.slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage);

  const handleCopy = (url: string, label = 'Link copied to clipboard') => {
    navigator.clipboard.writeText(url).catch(() => {});
    setSnackbar({ open: true, message: label });
  };

  const handleShare = (link: PaymentLink) => {
    setSelectedLink(link);
    setShareOpen(true);
  };

  const handleCancel = async (id: string) => {
    try {
      await apiClient.patch(`/api/v1/payment-links/${id}`, { status: 'CANCELED' });
      setLinks(prev => prev.map(l => l.id === id ? { ...l, status: 'cancelled' } : l));
      setSnackbar({ open: true, message: 'Payment link cancelled' });
    } catch {
      setLinks(prev => prev.map(l => l.id === id ? { ...l, status: 'cancelled' } : l));
      setSnackbar({ open: true, message: 'Payment link cancelled (local)' });
    }
  };

  const [terminals, setTerminals] = useState<any[]>([]);

  const [totalElements, setTotalElements] = useState(0);

  const fetchPaymentLinks = useCallback(() => {
    apiClient.get(`/api/v1/payment-links?page=${page}&size=${rowsPerPage}`)
      .then(res => {
        const rawContent = Array.isArray(res.data) ? res.data : (res.data?.content || []);
        setTotalElements(res.data?.totalElements ?? rawContent.length);
        if (Array.isArray(rawContent)) {
          const mapped: PaymentLink[] = rawContent.map((l: any) => {
            const pt = String(l.paymentType || l.payment_type || 'sms').toLowerCase();
            const ut = String(l.usageType || l.usage_type || 'single').toLowerCase();
            return {
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
              usageType: (ut === 'multiple' ? 'multiple' : 'single') as LinkUsageType,
              maxUses: l.maxPayments || 1,
              usedCount: l.currentPaymentsCount || 0,
              createdAt: new Date(l.createdAt),
              expiresAt: l.expiresAt ? new Date(l.expiresAt) : new Date(Date.now() + 86400000),
              paymentType: (pt === 'dms' ? 'dms' : 'sms') as PaymentType,
            };
          });
          setLinks(mapped);
        }
      })
      .catch(() => {
        setLinks([]);
      });
  }, [page, rowsPerPage]);

  useEffect(() => {
    fetchPaymentLinks();

    // Fetch active terminals
    apiClient.get('/api/v1/terminals')
      .then(res => {
        const rawContent = Array.isArray(res.data) ? res.data : (res.data?.content || []);
        if (Array.isArray(rawContent) && rawContent.length > 0) {
          setTerminals(rawContent);
        }
      })
      .catch(() => {});
  }, [fetchPaymentLinks]);

  const handleGenerate = async () => {
    const selectedTerminal = form.terminalId ? Number(form.terminalId) : terminals[0]?.id;
    if (!selectedTerminal) {
      setFormError('В вашей системе нет активных терминалов эквайринга. Зарегистрируйте терминал в Settings перед генерацией ссылки.');
      return;
    }
    if (!form.amount || isNaN(Number(form.amount)) || Number(form.amount) <= 0) {
      setFormError('Please enter a valid amount.');
      return;
    }
    if (!form.description.trim()) {
      setFormError('Please add a description or invoice reference.');
      return;
    }
    setFormError('');
    setGenerating(true);

    try {
      const payload: any = {
        terminal: selectedTerminal,
        amount: parseFloat(form.amount),
        currency: form.currency || 'AZN',
        description: form.description,
        paymentType: form.paymentType.toUpperCase(),
        usageType: form.usageType.toUpperCase(),
      };

      if (form.usageType === 'multiple') {
        payload.maxPayments = parseInt(form.maxUses) || 5;
      }

      if (form.customerName || form.customerEmail || form.customerPhone) {
        payload.customer = {
          fullName: form.customerName || 'N/A',
          email: form.customerEmail || 'customer@example.com',
          phone: form.customerPhone || '+994500000000',
        };
      }

      const res = await apiClient.post('/api/v1/payment-links', payload);
      const created = res.data;

      const newLink: PaymentLink = {
        id: created.id,
        shortCode: created.id.slice(0, 8).toUpperCase(),
        url: `${window.location.origin}/api/v1/payment-links/${created.id}/open`,
        status: (created.status || 'active').toLowerCase() as LinkStatus,
        amount: created.amount,
        currency: created.currency || 'AZN',
        description: created.description,
        customerName: created.customer?.fullName || created.customerName || 'N/A',
        customerEmail: created.customer?.email || created.customerEmail || 'N/A',
        customerPhone: created.customer?.phone || created.customerPhone || 'N/A',
        usageType: (created.usageType || 'single').toLowerCase() as LinkUsageType,
        maxUses: created.maxPayments || 1,
        usedCount: 0,
        createdAt: new Date(),
        expiresAt: created.expiresAt ? new Date(created.expiresAt) : new Date(Date.now() + 86400000),
        paymentType: (created.paymentType || 'sms').toLowerCase() as PaymentType,
      };

      setLinks(prev => [newLink, ...prev]);
      setNewlyCreatedLink(newLink);
      setGenerating(false);
      setForm({
        amount: '', currency: 'AZN', description: '', customerName: '',
        customerEmail: '', customerPhone: '', usageType: 'single', maxUses: '1',
        expiry: '24h', redirectUrl: '', note: '', sendEmail: true, paymentType: 'sms',
      });
    } catch (err: any) {
      setGenerating(false);
      const serverMsg = err.response?.data?.message || err.response?.data?.error;
      setFormError(serverMsg || 'Failed to create payment link on backend.');
    }
  };

  const handleCloseCreate = () => {
    setCreateOpen(false);
    setNewlyCreatedLink(null);
    setFormError('');
  };

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 4, display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 600, mb: 0.5 }}>
            {tObj.payByLink.title}
          </Typography>
          <Typography variant="body1" color="text.secondary">
            {tObj.payByLink.subtitle}
          </Typography>
        </Box>
        <Button
          variant="contained"
          size="large"
          startIcon={<AddIcon />}
          onClick={() => setCreateOpen(true)}
          sx={{ fontWeight: 600 }}
        >
          {tObj.payByLink.createButton}
        </Button>
      </Box>

      {/* Stats Cards */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        {[
          {
            label: 'Active Links',
            value: stats.active,
            icon: <LinkIcon sx={{ fontSize: 32 }} />,
            color: '#1976d2',
            bgcolor: 'rgba(25, 118, 210, 0.08)',
            sub: 'Awaiting payment',
          },
          {
            label: 'Paid',
            value: stats.paid,
            icon: <CheckCircleIcon sx={{ fontSize: 32 }} />,
            color: '#2e7d32',
            bgcolor: 'rgba(46, 125, 50, 0.08)',
            sub: `Conversion ${stats.conversionRate}%`,
          },
          {
            label: 'Revenue Collected',
            value: `₼${stats.totalRevenue.toLocaleString('en', { minimumFractionDigits: 2 })}`,
            icon: <AmountIcon sx={{ fontSize: 32 }} />,
            color: '#7b1fa2',
            bgcolor: 'rgba(123, 31, 162, 0.08)',
            sub: 'From paid links',
          },
          {
            label: 'Total Created',
            value: stats.total,
            icon: <TrendingUpIcon sx={{ fontSize: 32 }} />,
            color: '#e65100',
            bgcolor: 'rgba(230, 81, 0, 0.08)',
            sub: `${stats.expired} expired`,
          },
        ].map((s) => (
          <Grid key={s.label} size={{ xs: 12, sm: 6, lg: 3 }}>
            <Card elevation={0} sx={{ border: '1px solid', borderColor: 'divider', height: '100%' }}>
              <CardContent sx={{ p: 3 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
                  <Box sx={{ p: 1.5, borderRadius: 2, bgcolor: s.bgcolor, color: s.color }}>
                    {s.icon}
                  </Box>
                </Box>
                <Typography variant="h4" sx={{ fontWeight: 700, mb: 0.25, color: 'text.primary' }}>
                  {s.value}
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 500 }}>
                  {s.label}
                </Typography>
                <Typography variant="caption" color="text.disabled">
                  {s.sub}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      {/* Filters & Table */}
      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider' }}>
        {/* Toolbar */}
        <Box sx={{ p: 2.5, display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap', borderBottom: '1px solid', borderColor: 'divider' }}>
          <FilterIcon color="action" />
          <TextField
            size="small"
            placeholder="Search by code, customer, description…"
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            sx={{ minWidth: 280 }}
            InputProps={{
              endAdornment: searchQuery && (
                <InputAdornment position="end">
                  <IconButton size="small" onClick={() => setSearchQuery('')}>
                    <CloseIcon fontSize="small" />
                  </IconButton>
                </InputAdornment>
              ),
            }}
          />
          <ToggleButtonGroup
            size="small"
            exclusive
            value={statusFilter}
            onChange={(_, v) => { if (v !== null) { setStatusFilter(v); setPage(0); } }}
          >
            <ToggleButton value="all">All ({links.length})</ToggleButton>
            <ToggleButton value="active">Active ({stats.active})</ToggleButton>
            <ToggleButton value="paid">Paid ({stats.paid})</ToggleButton>
            <ToggleButton value="expired">Expired ({stats.expired})</ToggleButton>
          </ToggleButtonGroup>
          <Box sx={{ ml: 'auto' }}>
            <Typography variant="body2" color="text.secondary">
              {filtered.length} link{filtered.length !== 1 ? 's' : ''}
            </Typography>
          </Box>
        </Box>

        {/* Table */}
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow sx={{ bgcolor: 'rgba(0,0,0,0.02)' }}>
                <TableCell sx={{ fontWeight: 600 }}>{tObj.payByLink.table.linkId}</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>{tObj.payByLink.table.customer}</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>{tObj.payByLink.descriptionLabel.replace(' *', '')}</TableCell>
                <TableCell sx={{ fontWeight: 600 }} align="right">{tObj.payByLink.table.amount}</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>{tObj.payByLink.table.type}</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>{tObj.payByLink.table.status}</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>{tObj.payByLink.table.usage}</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>{tObj.payByLink.table.expires}</TableCell>
                <TableCell sx={{ fontWeight: 600 }} align="center">{tObj.payByLink.table.actions}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {paginated.map(link => {
                const cfg = getStatusConfig(link.status, tObj);
                const expiryProgress = link.status === 'active'
                  ? Math.max(0, Math.min(100, ((link.expiresAt.getTime() - Date.now()) / (link.expiresAt.getTime() - link.createdAt.getTime())) * 100))
                  : null;

                return (
                  <TableRow
                    key={link.id}
                    hover
                    onClick={() => navigate(`/pay-by-link/${link.id}`, { state: { link } })}
                    sx={{ cursor: 'pointer', '&:hover': { bgcolor: 'rgba(0,0,0,0.02)' } }}
                  >
                    {/* Link */}
                    <TableCell>
                      <Box>
                        <Typography variant="body2" sx={{ fontFamily: 'monospace', fontWeight: 700, color: 'primary.main', letterSpacing: 0.5 }}>
                          {link.shortCode}
                        </Typography>
                        <Typography variant="caption" color="text.disabled" sx={{ fontSize: '0.7rem' }}>
                          {link.url}
                        </Typography>
                      </Box>
                    </TableCell>

                    {/* Customer */}
                    <TableCell>
                      {link.customerName ? (
                        <Box>
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>
                            {link.customerName}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            {link.customerEmail}
                          </Typography>
                        </Box>
                      ) : (
                        <Typography variant="body2" color="text.disabled" sx={{ fontStyle: 'italic' }}>
                          Not specified
                        </Typography>
                      )}
                    </TableCell>

                    {/* Description */}
                    <TableCell>
                      <Typography variant="body2" sx={{ maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {link.description}
                      </Typography>
                    </TableCell>

                    {/* Amount */}
                    <TableCell align="right">
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>
                        ₼{link.amount.toFixed(2)}
                      </Typography>
                    </TableCell>

                    {/* Payment Type */}
                    <TableCell>
                      <Chip
                        label={link.paymentType?.toUpperCase() ?? 'SMS'}
                        size="small"
                        variant="outlined"
                        color={link.paymentType === 'dms' ? 'warning' : 'primary'}
                        sx={{ fontWeight: 700, fontFamily: 'monospace', fontSize: '0.7rem' }}
                      />
                    </TableCell>

                    {/* Status */}
                    <TableCell>
                      <Chip
                        icon={cfg.icon as React.ReactElement}
                        label={cfg.label}
                        color={cfg.color}
                        size="small"
                        variant={link.status === 'active' ? 'filled' : 'outlined'}
                        sx={{ fontWeight: 600 }}
                      />
                    </TableCell>

                    {/* Usage */}
                    <TableCell>
                      {link.usageType === 'multiple' ? (
                        <Box>
                          <Typography variant="caption" sx={{ fontWeight: 600 }}>
                            {link.usedCount}/{link.maxUses} uses
                          </Typography>
                          <LinearProgress
                            variant="determinate"
                            value={(link.usedCount / link.maxUses) * 100}
                            sx={{ mt: 0.5, height: 4, borderRadius: 2 }}
                          />
                        </Box>
                      ) : (
                        <Typography variant="caption" color="text.secondary">
                          Single-use
                        </Typography>
                      )}
                    </TableCell>

                    {/* Expires */}
                    <TableCell>
                      {link.status === 'paid' ? (
                        <Box>
                          <Typography variant="caption" color="success.main" sx={{ fontWeight: 600 }}>
                            Paid
                          </Typography>
                          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                            {link.paidAt && formatDateTime(link.paidAt)}
                          </Typography>
                        </Box>
                      ) : (
                        <Box>
                          <Typography variant="caption" sx={{ fontWeight: 600, color: link.status === 'active' && expiryProgress !== null && expiryProgress < 20 ? 'error.main' : 'text.primary' }}>
                            {link.status === 'active' ? formatTimeLeft(link.expiresAt) : formatDateTime(link.expiresAt)}
                          </Typography>
                          {link.status === 'active' && expiryProgress !== null && (
                            <LinearProgress
                              variant="determinate"
                              value={expiryProgress}
                              color={expiryProgress < 20 ? 'error' : expiryProgress < 50 ? 'warning' : 'primary'}
                              sx={{ mt: 0.5, height: 3, borderRadius: 2 }}
                            />
                          )}
                        </Box>
                      )}
                    </TableCell>

                    {/* Actions */}
                    <TableCell align="center" onClick={e => e.stopPropagation()}>
                      <Stack direction="row" spacing={0.5} justifyContent="center">
                        <Tooltip title="Copy link">
                          <span>
                            <IconButton size="small" onClick={() => handleCopy(link.url)} disabled={link.status !== 'active'}>
                              <CopyIcon fontSize="small" />
                            </IconButton>
                          </span>
                        </Tooltip>
                        <Tooltip title="Share">
                          <span>
                            <IconButton size="small" onClick={() => handleShare(link)} disabled={link.status !== 'active'}>
                              <ShareIcon fontSize="small" />
                            </IconButton>
                          </span>
                        </Tooltip>
                        <Tooltip title="Cancel link">
                          <span>
                            <IconButton
                              size="small"
                              color="error"
                              onClick={() => handleCancel(link.id)}
                              disabled={link.status !== 'active'}
                            >
                              <CancelIcon fontSize="small" />
                            </IconButton>
                          </span>
                        </Tooltip>
                      </Stack>
                    </TableCell>
                  </TableRow>
                );
              })}
              {paginated.length === 0 && (
                <TableRow>
                  <TableCell colSpan={8} align="center" sx={{ py: 6 }}>
                    <LinkIcon sx={{ fontSize: 48, color: 'text.disabled', mb: 1 }} />
                    <Typography color="text.secondary">No payment links found</Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>

        <TablePagination
          rowsPerPageOptions={[10, 25, 50]}
          component="div"
          count={totalElements || filtered.length}
          rowsPerPage={rowsPerPage}
          page={page}
          onPageChange={(_, p) => setPage(p)}
          onRowsPerPageChange={e => { setRowsPerPage(parseInt(e.target.value)); setPage(0); }}
        />
      </Paper>

      {/* ── Create Dialog ──────────────────────────────────────────────────── */}
      <Dialog open={createOpen} onClose={handleCloseCreate} maxWidth="sm" fullWidth scroll="paper">
        <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1.5, pb: 1 }}>
          <Box sx={{ p: 1, borderRadius: 1.5, bgcolor: 'primary.light', color: 'white', display: 'flex' }}>
            <LinkIcon />
          </Box>
          <Box>
            <Typography variant="h6" sx={{ fontWeight: 700 }}>Create Payment Link</Typography>
            <Typography variant="caption" color="text.secondary">
              Generate a secure link and share it with your customer
            </Typography>
          </Box>
        </DialogTitle>

        <Divider />

        <DialogContent sx={{ pt: 3 }}>
          {/* Success state */}
          {newlyCreatedLink ? (
            <Box>
              <Alert severity="success" sx={{ mb: 3 }}>
                Payment link created successfully! Share it with your customer.
              </Alert>
              <Paper variant="outlined" sx={{ p: 2.5, mb: 3, borderRadius: 2, bgcolor: 'action.hover' }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, textTransform: 'uppercase', letterSpacing: 0.5 }}>
                  Payment Link
                </Typography>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 0.75 }}>
                  <Typography
                    variant="body1"
                    sx={{ flex: 1, fontFamily: 'monospace', fontWeight: 700, color: 'primary.main', wordBreak: 'break-all' }}
                  >
                    {newlyCreatedLink.url}
                  </Typography>
                  <Tooltip title="Copy">
                    <IconButton size="small" onClick={() => handleCopy(newlyCreatedLink.url)}>
                      <CopyIcon />
                    </IconButton>
                  </Tooltip>
                </Box>
              </Paper>
              <Stack spacing={1.5}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" color="text.secondary">Amount</Typography>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>₼{newlyCreatedLink.amount.toFixed(2)}</Typography>
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" color="text.secondary">Expires</Typography>
                  <Typography variant="body2">{formatDateTime(newlyCreatedLink.expiresAt)}</Typography>
                </Box>
                {newlyCreatedLink.customerEmail && (
                  <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                    <Typography variant="body2" color="text.secondary">Customer</Typography>
                    <Typography variant="body2">{newlyCreatedLink.customerEmail}</Typography>
                  </Box>
                )}
              </Stack>
              <Stack direction="row" spacing={1.5} sx={{ mt: 3 }}>
                <Button
                  variant="outlined"
                  startIcon={<EmailIcon />}
                  fullWidth
                  onClick={() => handleCopy(`mailto:${newlyCreatedLink.customerEmail}?subject=Payment Request&body=Please use this link to complete your payment: ${newlyCreatedLink.url}`)}
                >
                  Send Email
                </Button>
                <Button
                  variant="outlined"
                  color="success"
                  startIcon={<WhatsAppIcon />}
                  fullWidth
                  onClick={() => handleCopy(`https://wa.me/${newlyCreatedLink.customerPhone}?text=Please use this link to complete your payment: ${newlyCreatedLink.url}`)}
                >
                  WhatsApp
                </Button>
              </Stack>
            </Box>
          ) : (
            <Stack spacing={3}>
              {terminals.length === 0 ? (
                <Alert severity="warning">
                  ⚠️ В системе нет зарегистрированных терминалов. Для создания ссылок зарегистрируйте компанию и терминал в разделе <strong>Settings</strong>.
                </Alert>
              ) : (
                <TextField
                  select
                  fullWidth
                  label="Select Terminal *"
                  value={form.terminalId || (terminals[0]?.id ?? '')}
                  onChange={e => setForm(f => ({ ...f, terminalId: e.target.value }))}
                  helperText="Эквайринговый терминал, через который пройдет платеж"
                >
                  {terminals.map((t: any) => (
                    <MenuItem key={t.id} value={t.id}>
                      {t.name || `Terminal #${t.id}`} (ID: {t.terminalId || t.id})
                    </MenuItem>
                  ))}
                </TextField>
              )}

              {formError && <Alert severity="error">{formError}</Alert>}

              {/* Amount */}
              <Box>
                <Typography variant="subtitle2" sx={{ mb: 1.5, fontWeight: 700, color: 'text.primary' }}>
                  Payment Amount *
                </Typography>
                <Box sx={{ display: 'flex', gap: 1.5 }}>
                  <TextField
                    fullWidth
                    label="Amount"
                    type="number"
                    placeholder="0.00"
                    value={form.amount}
                    onChange={e => setForm(f => ({ ...f, amount: e.target.value }))}
                    InputProps={{
                      startAdornment: <InputAdornment position="start">₼</InputAdornment>,
                    }}
                    inputProps={{ min: 0, step: '0.01' }}
                  />
                  <TextField
                    select
                    label="Currency"
                    value={form.currency}
                    onChange={e => setForm(f => ({ ...f, currency: e.target.value }))}
                    sx={{ minWidth: 110 }}
                  >
                    <MenuItem value="AZN">AZN</MenuItem>
                    <MenuItem value="USD">USD</MenuItem>
                    <MenuItem value="EUR">EUR</MenuItem>
                  </TextField>
                </Box>
              </Box>

              {/* Description */}
              <TextField
                fullWidth
                label="Description / Invoice Reference *"
                placeholder="e.g. Invoice #INV-2024-0001 — Annual subscription"
                value={form.description}
                onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
                helperText="This will be visible to the customer on the payment page"
              />

              {/* Customer */}
              <Box>
                <Typography variant="subtitle2" sx={{ mb: 1.5, fontWeight: 700, color: 'text.primary' }}>
                  Customer Info
                </Typography>
                <Stack spacing={2}>
                  <TextField
                    fullWidth
                    label="Customer Name"
                    placeholder="Full name"
                    value={form.customerName}
                    onChange={e => setForm(f => ({ ...f, customerName: e.target.value }))}
                    InputProps={{ startAdornment: <InputAdornment position="start"><PersonIcon color="action" /></InputAdornment> }}
                  />
                  <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
                    <TextField
                      fullWidth
                      label="Email"
                      type="email"
                      placeholder="customer@email.com"
                      value={form.customerEmail}
                      onChange={e => setForm(f => ({ ...f, customerEmail: e.target.value }))}
                    />
                    <TextField
                      fullWidth
                      label="Phone"
                      placeholder="+994 50 000 0000"
                      value={form.customerPhone}
                      onChange={e => setForm(f => ({ ...f, customerPhone: e.target.value }))}
                    />
                  </Box>
                </Stack>
              </Box>

              {/* Link Options */}
              <Box>
                <Typography variant="subtitle2" sx={{ mb: 1.5, fontWeight: 700, color: 'text.primary' }}>
                  Link Settings
                </Typography>
                <Stack spacing={2}>
                  {/* Payment type — SMS vs DMS */}
                  <Box>
                    <Typography variant="body2" sx={{ mb: 0.75, fontWeight: 600, color: 'text.primary' }}>
                      Payment Type
                    </Typography>
                    <ToggleButtonGroup
                      exclusive
                      fullWidth
                      size="small"
                      value={form.paymentType}
                      onChange={(_, v) => { if (v) setForm(f => ({ ...f, paymentType: v })); }}
                    >
                      <ToggleButton value="sms">SMS — Charge Immediately</ToggleButton>
                      <ToggleButton value="dms">DMS — Authorize &amp; Capture</ToggleButton>
                    </ToggleButtonGroup>
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.75 }}>
                      {form.paymentType === 'sms'
                        ? 'SMS: funds are charged immediately when customer pays.'
                        : 'DMS: funds are reserved (authorized) on the card. You must manually finalize the payment to capture them.'}
                    </Typography>
                  </Box>

                  {/* Expiry */}
                  <TextField
                    select
                    fullWidth
                    label="Link Expiry"
                    value={form.expiry}
                    onChange={e => setForm(f => ({ ...f, expiry: e.target.value }))}
                    InputProps={{ startAdornment: <InputAdornment position="start"><TimeIcon color="action" /></InputAdornment> }}
                  >
                    <MenuItem value="1h">1 Hour</MenuItem>
                    <MenuItem value="24h">24 Hours</MenuItem>
                    <MenuItem value="72h">3 Days</MenuItem>
                    <MenuItem value="7d">7 Days</MenuItem>
                    <MenuItem value="30d">30 Days</MenuItem>
                  </TextField>

                  {/* Usage type */}
                  <Box>
                    <Typography variant="body2" sx={{ mb: 1, color: 'text.secondary' }}>Usage Type</Typography>
                    <ToggleButtonGroup
                      exclusive
                      fullWidth
                      size="small"
                      value={form.usageType}
                      onChange={(_, v) => { if (v) setForm(f => ({ ...f, usageType: v })); }}
                    >
                      <ToggleButton value="single">Single Use</ToggleButton>
                      <ToggleButton value="multiple">Multiple Uses</ToggleButton>
                    </ToggleButtonGroup>
                    {form.usageType === 'multiple' && (
                      <TextField
                        fullWidth
                        label="Max number of payments"
                        type="number"
                        value={form.maxUses}
                        onChange={e => setForm(f => ({ ...f, maxUses: e.target.value }))}
                        sx={{ mt: 1.5 }}
                        inputProps={{ min: 2, max: 100 }}
                        helperText="Link deactivates after this many successful payments"
                      />
                    )}
                  </Box>

                  {/* Redirect URL */}
                  <TextField
                    fullWidth
                    label="Redirect URL after payment (optional)"
                    placeholder="https://yourstore.az/thank-you"
                    value={form.redirectUrl}
                    onChange={e => setForm(f => ({ ...f, redirectUrl: e.target.value }))}
                    helperText="Customer is redirected here after a successful payment"
                  />

                  {/* Note */}
                  <TextField
                    fullWidth
                    label="Internal note (optional)"
                    placeholder="Visible only to you, not the customer"
                    value={form.note}
                    onChange={e => setForm(f => ({ ...f, note: e.target.value }))}
                    multiline
                    rows={2}
                  />

                  {/* Send email toggle */}
                  <FormControlLabel
                    control={
                      <Switch
                        checked={form.sendEmail}
                        onChange={e => setForm(f => ({ ...f, sendEmail: e.target.checked }))}
                      />
                    }
                    label={
                      <Box>
                        <Typography variant="body2">Send link to customer by email</Typography>
                        <Typography variant="caption" color="text.secondary">
                          Requires customer email to be filled in
                        </Typography>
                      </Box>
                    }
                  />
                </Stack>
              </Box>
            </Stack>
          )}
        </DialogContent>

        <Divider />
        <DialogActions sx={{ p: 2.5, gap: 1 }}>
          {newlyCreatedLink ? (
            <Button onClick={handleCloseCreate} variant="contained" fullWidth>
              Done
            </Button>
          ) : (
            <>
              <Button onClick={handleCloseCreate} variant="outlined" sx={{ minWidth: 100 }}>
                Cancel
              </Button>
              <Button
                onClick={handleGenerate}
                variant="contained"
                startIcon={generating ? undefined : <LinkIcon />}
                disabled={generating}
                sx={{ minWidth: 180 }}
              >
                {generating ? 'Generating…' : 'Generate Link'}
              </Button>
            </>
          )}
        </DialogActions>
      </Dialog>

      {/* ── Share Dialog ───────────────────────────────────────────────────── */}
      <Dialog open={shareOpen && !!selectedLink} onClose={() => setShareOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>Share Payment Link</DialogTitle>
        <DialogContent>
          {selectedLink && (
            <Stack spacing={2.5}>
              <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, bgcolor: 'action.hover' }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, textTransform: 'uppercase', letterSpacing: 0.5 }}>
                  {selectedLink.shortCode}
                </Typography>
                <Typography variant="body2" sx={{ fontFamily: 'monospace', color: 'primary.main', mt: 0.5, wordBreak: 'break-all' }}>
                  {selectedLink.url}
                </Typography>
                <Stack direction="row" spacing={1} sx={{ mt: 1.5 }}>
                  <Typography variant="caption" color="text.secondary">
                    ₼{selectedLink.amount.toFixed(2)} · expires {formatTimeLeft(selectedLink.expiresAt)}
                  </Typography>
                </Stack>
              </Paper>

              <Button
                fullWidth
                variant="outlined"
                startIcon={<CopyIcon />}
                onClick={() => { handleCopy(selectedLink.url); setShareOpen(false); }}
              >
                Copy Link
              </Button>

              <Button
                fullWidth
                variant="outlined"
                startIcon={<EmailIcon />}
                href={`mailto:${selectedLink.customerEmail}?subject=Payment Request — ₼${selectedLink.amount.toFixed(2)}&body=Hi ${selectedLink.customerName},%0A%0APlease complete your payment using the link below:%0A${selectedLink.url}%0A%0AAmount: ₼${selectedLink.amount.toFixed(2)}%0ADescription: ${selectedLink.description}%0A%0AThank you.`}
                target="_blank"
                disabled={!selectedLink.customerEmail}
              >
                Send by Email
              </Button>

              <Button
                fullWidth
                variant="outlined"
                color="success"
                startIcon={<WhatsAppIcon />}
                onClick={() => {
                  const text = encodeURIComponent(`Hi ${selectedLink.customerName}, please complete your payment of ₼${selectedLink.amount.toFixed(2)} using this link: ${selectedLink.url}`);
                  window.open(`https://wa.me/${selectedLink.customerPhone.replace(/\D/g, '')}?text=${text}`, '_blank');
                }}
                disabled={!selectedLink.customerPhone}
              >
                Send via WhatsApp
              </Button>

              <Button
                fullWidth
                variant="outlined"
                startIcon={<QrCodeIcon />}
                onClick={() => handleCopy(`QR code for ${selectedLink.url} — feature coming soon`, 'QR code feature coming soon')}
              >
                Generate QR Code
              </Button>
            </Stack>
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={() => setShareOpen(false)} variant="contained" fullWidth>
            Close
          </Button>
        </DialogActions>
      </Dialog>

      {/* Snackbar */}
      <Snackbar
        open={snackbar.open}
        autoHideDuration={3000}
        onClose={() => setSnackbar(s => ({ ...s, open: false }))}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity="success" onClose={() => setSnackbar(s => ({ ...s, open: false }))} sx={{ width: '100%' }}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
};
