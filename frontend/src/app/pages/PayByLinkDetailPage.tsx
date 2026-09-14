import React, { useState, useEffect, useCallback } from 'react';
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
  AlertTitle,
  Snackbar,
  LinearProgress,
  Avatar,
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
  Loop as UsageIcon,
  Done as DoneIcon,
  DoneAll as FinalizeIcon,
  Warning as WarningIcon,
  Lock as AuthorizedIcon,
  LockOpen as CaptureIcon,
} from '@mui/icons-material';
import {
  formatDateTime,
  formatTimeLeft,
  expiryPercent,
  getLinkStatusColors,
  parseLinkStatus,
  parseLinkUsageType,
  parsePaymentType,
} from '../utils/payByLinkData';
import type { PaymentLink } from '../utils/payByLinkData';
import type { TerminalOptionDto } from '../types/dto';
import { buildTerminalIndex, terminalLabel, terminalSubLabel } from '../utils/terminals';
import { readMoneyOperationFailure, type MoneyOperationFailure } from '../utils/moneyOperationError';
import { linkStatusLabel } from '../i18n/translations';
import { ConfirmDialog } from '../components/ConfirmDialog';

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

  // Условие — по наличию самой даты оплаты: статуса `paid` у бэкенда нет (Р-33), а `paidAt`
  // приходит только вместе с реальными данными о платеже.
  if (link.paidAt) {
    // Данных о карте API не отдаёт, поэтому суффикс всегда пуст. Условие оставлено намеренно:
    // подставлять сюда `Visa ···· 4242` нельзя (Р-48), а появится поле — строка заработает.
    const card = link.cardNetwork && link.cardLast4
      ? ` via ${link.cardNetwork} ···· ${link.cardLast4}`
      : '';
    events.push({
      label: 'Payment Received',
      time: formatDateTime(link.paidAt),
      icon: <CheckCircleIcon sx={{ fontSize: 16 }} />,
      color: '#2e7d32',
      detail: `₼${link.amount.toFixed(2)}${card}`,
    });
    // Событие «Customer Redirected» отсюда удалено вместе с P2-15 (Р-48). Времени у него не
    // было: оно бралось как «оплата + 3 секунды». Придуманная отметка времени события в
    // платёжном портале недопустима, а `redirectUrl` API не отдаёт вовсе.
  }

  if (link.status === 'EXPIRED') {
    events.push({
      label: 'Link Expired',
      time: formatDateTime(link.expiresAt),
      icon: <ExpiredIcon sx={{ fontSize: 16 }} />,
      color: '#546e7a',
      detail: 'No payment was received before expiry',
    });
  }

  // Проверка сравнивалась с написанием через две `l`, а маппинг клал одну — пометка
  // об отменённой ссылке не показывалась никогда (P2-13).
  if (link.status === 'CANCELED') {
    events.push({
      label: 'Link Cancelled',
      // Момента отмены бэкенд не отдаёт, и выдумывать его («создано + 30 минут», как было
      // в мок-генераторе) нельзя: у события на ленте нет времени, пока его нет в API.
      time: '—',
      icon: <CancelIcon sx={{ fontSize: 16 }} />,
      color: '#c62828',
      detail: 'Manually cancelled by merchant',
    });
  }

  return events;
};

// ─── Linked transactions ──────────────────────────────────────────────────────
//
// Таблица показывает **только** ответ `GET /api/v1/payment-links/{id}/transactions` —
// настоящие транзакции с настоящими идентификаторами.
//
// Здесь стоял генератор запасных строк: при наличии даты оплаты он сочинял транзакцию —
// платёжную карту по умолчанию, идентификатор, собранный из короткого кода ссылки, и пары
// SMS / DMS-Auth / DMS-Capture, выведенные из полей ссылки, а не из настоящих платежей.
// Не был виден он только потому, что дата оплаты из API не приходила. P2-15 это поле
// включает, поэтому генератор удалён **до** включения (Р-48): выдуманная транзакция на
// карточке платежа — не «заглушка», а ложные данные об операции с деньгами.

const LinkedTransactions: React.FC<{ link: PaymentLink }> = ({ link }) => {
  const navigate = useNavigate();
  const { tObj } = useLanguage();
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

  const displayTxns = transactions;

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
                <TableCell sx={{ fontWeight: 600 }}>{tObj.transactions.columns.providerOrderId}</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>{tObj.transactions.columns.ridByMerchant}</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Date & Time</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Payer IP</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Device / User-Agent</TableCell>
                <TableCell sx={{ fontWeight: 600 }} align="right">Amount</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Status</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>{tObj.transactions.columns.id}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {displayTxns.map((txn: any) => {
                const status = String(txn.status ?? 'PENDING').toUpperCase();
                const statusColors: Record<string, { label: string; color: any; bgcolor: string }> = {
                  SUCCESS: { label: 'SUCCESS', color: 'success', bgcolor: 'rgba(46,125,50,0.1)' },
                  AUTHORIZED: { label: 'AUTHORIZED', color: 'warning', bgcolor: 'rgba(230,81,0,0.1)' },
                  PENDING: { label: 'PENDING', color: 'info', bgcolor: 'rgba(2,136,209,0.1)' },
                  FAILED: { label: 'FAILED', color: 'error', bgcolor: 'rgba(198,40,40,0.1)' },
                  PARTIALLY_REFUNDED: { label: 'PARTIALLY REFUNDED', color: 'default', bgcolor: 'rgba(84,110,122,0.1)' },
                  REFUNDED: { label: 'REFUNDED', color: 'default', bgcolor: 'rgba(84,110,122,0.1)' },
                };
                const sc = statusColors[status] || { label: status, color: 'default', bgcolor: 'rgba(0,0,0,0.05)' };

                return (
                  <TableRow
                    key={txn.id}
                    hover
                    // Без router state: карточка грузит себя сама (GET /api/v1/transactions/{id},
                    // P3-7) — как это давно делает таблица последних платежей на главной. Здесь
                    // собирался целый объект операции, и в нём была выдуманная история статусов:
                    // «создано» и «оплачено» с одним и тем же временем и подписями, которых никто
                    // не писал. Настоящую историю отдаёт сам ответ по операции.
                    onClick={() => navigate(`/transactions/${txn.id}`)}
                    sx={{ cursor: 'pointer', '&:hover': { bgcolor: 'rgba(0,0,0,0.04)' } }}
                  >
                    <TableCell>
                      <Typography variant="caption" sx={{ fontFamily: 'monospace', fontWeight: 700 }}>
                        {txn.providerOrderId || txn.provider_order_id || '—'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" sx={{ fontFamily: 'monospace', fontWeight: 700 }}>
                        {txn.ridByMerchant || txn.rid_by_merchant || '—'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" color="text.secondary">
                        {formatDateTime(txn.createdAt ? new Date(txn.createdAt) : txn.timestamp)}
                      </Typography>
                    </TableCell>
                    {/* Адрес и устройство плательщика бэкенд заполняет не всегда. Пусто — это
                        «не записано»; подставлять сюда `127.0.0.1` и правдоподобный
                        User-Agent, как было до P2-15, значит приписывать платежу
                        обстоятельства, которых никто не наблюдал (Р-48). */}
                    <TableCell>
                      <Typography variant="caption" sx={{ fontFamily: 'monospace', fontWeight: 600 }}>
                        {txn.clientIp || '—'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" color="text.secondary" sx={{ maxWidth: 180, display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {txn.userAgent || '—'}
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
                    <TableCell>
                      <Typography
                        variant="caption"
                        color="text.secondary"
                        sx={{ fontFamily: 'monospace', fontSize: '0.68rem' }}
                      >
                        {txn.id}
                      </Typography>
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

import { useLanguage } from '../context/LanguageContext';

export const PayByLinkDetailPage: React.FC = () => {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const location = useLocation();
  const { tObj } = useLanguage();
  const stateLink = location.state?.link as PaymentLink | undefined;

  const [link, setLink] = useState<PaymentLink | null>(stateLink || null);
  // Терминалы берутся все, включая заблокированные: ссылка, созданная на снятом с обслуживания
  // терминале, должна сохранить его подпись.
  const [terminalIndex, setTerminalIndex] = useState<Record<number, TerminalOptionDto>>({});

  useEffect(() => {
    const controller = new AbortController();
    apiClient.get('/api/v1/terminals/options', { signal: controller.signal })
      .then(res => setTerminalIndex(buildTerminalIndex(res.data)))
      .catch(() => {});
    return () => controller.abort();
  }, []);

  const fetchLink = useCallback(() => {
    if (!id) return;
    apiClient.get(`/api/v1/payment-links/${id}`)
      .then(res => {
        const l = res.data;
        if (l && l.id) {
          setLink({
            id: l.id,
            shortCode: l.id.slice(0, 8).toUpperCase(),
            url: `${window.location.origin}/api/v1/payment-links/${l.id}/open`,
            status: parseLinkStatus(l.status),
            statusRaw: l.status === null || l.status === undefined ? undefined : String(l.status),
            amount: l.amount,
            currency: l.currency || 'AZN',
            description: l.description || 'Payment Link',
            customerName: l.customer?.fullName || l.customerName || 'N/A',
            customerEmail: l.customer?.email || l.customerEmail || 'N/A',
            customerPhone: l.customer?.phone || l.customerPhone || 'N/A',
            usageType: parseLinkUsageType(l.usageType),
            maxUses: l.maxPayments || 1,
            usedCount: l.currentPaymentsCount || 0,
            // Сколько из платежей возвращено (P2-16, Р-50). Возврат использование не отменяет:
            // usedCount при возврате не уменьшается, это отдельная цифра рядом с ним.
            refundedCount: l.refundedPaymentsCount || 0,
            createdAt: new Date(l.createdAt),
            expiresAt: l.expiresAt ? new Date(l.expiresAt) : new Date(Date.now() + 86400000),
            paymentType: parsePaymentType(l.paymentType),
            // Дата последнего успешного платежа (P2-15) — то же поле и то же значение, что
            // в списке. Пусто — платежей не было.
            paidAt: l.lastPaidAt ? new Date(l.lastPaidAt) : undefined,
            // `PaymentLinkResponse.terminal` — эквайринговый терминал ссылки. Маппинг его
            // не переносил, и карточка терминал не показывала вовсе.
            terminalId: typeof l.terminal === 'number' ? l.terminal : undefined,
          });
        }
      })
      .catch(() => {});
  }, [id]);

  useEffect(() => {
    fetchLink();
  }, [fetchLink]);

  const [snackbar, setSnackbar] = useState<{ text: string; error?: boolean } | null>(null);
  const [cancelDialogOpen, setCancelDialogOpen] = useState(false);
  const [cancelBusy, setCancelBusy] = useState(false);
  const [finalizeDialogOpen, setFinalizeDialogOpen] = useState(false);
  const [finalizeBusy, setFinalizeBusy] = useState(false);
  /**
   * Отказ списания холда остаётся в окне подтверждения, а не улетает снекбаром: снекбар
   * уходит через несколько секунд, а неподтверждённый исход — это то, что мерчант обязан
   * увидеть и разобрать. Разбор исхода — в `utils/moneyOperationError.ts`.
   */
  const [finalizeError, setFinalizeError] = useState<MoneyOperationFailure | null>(null);

  // Проверки по статусу ссылки здесь нет: `paid` бэкенд не присылает, а стадия DMS живёт
  // в собственном поле (`dmsStatus`), не в статусе ссылки.
  const isDmsAuthorized =
    link?.paymentType === 'DMS' &&
    link?.dmsStatus === 'authorized';

  if (!link) {
    return (
      <Box sx={{ textAlign: 'center', py: 10 }}>
        <Typography variant="h6" color="text.secondary">Payment link not found</Typography>
        <Button sx={{ mt: 2 }} onClick={() => navigate('/pay-by-link')}>Back to Pay by Link</Button>
      </Box>
    );
  }

  const statusColors = getLinkStatusColors(link.status);
  const statusText = linkStatusLabel(tObj, link.status, link.statusRaw);
  const timeline = buildTimeline(link);
  const expPct = link.status === 'ACTIVE' ? expiryPercent(link) : null;
  const isActive = link.status === 'ACTIVE';

  const copy = (text: string, msg = 'Copied to clipboard') => {
    navigator.clipboard.writeText(text).catch(() => {});
    setSnackbar({ text: msg });
  };

  /**
   * Отмена ссылки (Р-34). Как и на списке, локальной правки состояния здесь нет: и при успехе,
   * и при отказе карточка перечитывается с сервера, поэтому «отменена» на экране означает
   * «отменена на бэкенде», а не «мы отправили запрос».
   */
  const handleCancel = async () => {
    if (!link) return;
    setCancelBusy(true);
    try {
      await apiClient.patch(`/api/v1/payment-links/${link.id}`, { status: 'CANCELED' });
      setSnackbar({ text: tObj.payByLink.linkCancelledSuccess, error: false });
    } catch (err: any) {
      setSnackbar({
        text: err.response?.data?.message || err.response?.data?.error || tObj.payByLink.linkCancelFailed,
        error: true,
      });
    } finally {
      // Диалог закрывается в обоих случаях: после отказа он спрашивал бы про отмену ссылки,
      // которую бэкенд отменять отказался, а текст отказа виден в snackbar.
      setCancelBusy(false);
      setCancelDialogOpen(false);
      fetchLink();
    }
  };

  const handleFinalize = async () => {
    if (!link) return;
    const txId = link.transactionId || link.id;
    setFinalizeBusy(true);
    setFinalizeError(null);
    try {
      await apiClient.post(`/api/v1/transactions/${txId}/complete`, { amount: link.amount });
      setLink(prev => prev ? {
        ...prev,
        dmsStatus: 'finalized',
        finalizedAt: new Date(),
      } : prev);
      setFinalizeDialogOpen(false);
      setSnackbar({ text: 'Payment finalized — funds captured successfully' });
    } catch (err: unknown) {
      setFinalizeError(readMoneyOperationFailure(err, 'Failed to complete DMS transaction on server'));
    } finally {
      setFinalizeBusy(false);
    }
  };

  // Исход списания не подтверждён: повторять нельзя, а состояние холда видно по статусу
  // транзакции в таблице связанных операций ниже.
  const finalizeUnresolved = finalizeError?.outcome === 'unknown';

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
                label={statusText}
                size="small"
                sx={{ fontWeight: 700, bgcolor: statusColors.bgColor, color: statusColors.textColor, fontSize: '0.75rem' }}
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
              {tObj.payByLinkDetail.finalizeDMS}
            </Button>
          )}
          {isActive && (
            <Button
              variant="outlined"
              color="error"
              startIcon={<CancelIcon />}
              onClick={() => setCancelDialogOpen(true)}
            >
              {tObj.payByLinkDetail.cancelLink}
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
                background: link.status === 'COMPLETED'
                  ? 'linear-gradient(135deg, #1b5e20 0%, #2e7d32 100%)'
                  : link.status === 'ACTIVE'
                  ? 'linear-gradient(135deg, #0d47a1 0%, #1976d2 100%)'
                  : 'linear-gradient(135deg, #37474f 0%, #546e7a 100%)',
                color: 'white',
                display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 2,
              }}>
                <Box>
                  <Typography variant="caption" sx={{ opacity: 0.8, textTransform: 'uppercase', letterSpacing: 1, fontSize: '0.7rem' }}>
                    {link.status === 'COMPLETED' ? 'Amount Received' : 'Amount Requested'}
                  </Typography>
                  <Typography variant="h3" sx={{ fontWeight: 800, mt: 0.25, lineHeight: 1 }}>
                    ₼{link.amount.toFixed(2)}
                  </Typography>
                  <Typography variant="body2" sx={{ opacity: 0.85, mt: 0.75 }}>{link.description}</Typography>
                </Box>
                <Box sx={{ textAlign: 'right' }}>
                  {link.paidAt && (
                    <Box>
                      <Typography variant="caption" sx={{ opacity: 0.8 }}>Paid on</Typography>
                      <Typography variant="body1" sx={{ fontWeight: 700 }}>{formatDateTime(link.paidAt)}</Typography>
                    </Box>
                  )}
                  {link.status === 'ACTIVE' && (
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
                  {link.status === 'EXPIRED' && (
                    <Typography variant="body2" sx={{ opacity: 0.8 }}>Expired {formatDateTime(link.expiresAt)}</Typography>
                  )}
                  {link.status === 'CANCELED' && (
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

            {/* Payment details — показываются, когда пришла сама дата оплаты */}
            {link.paidAt && (
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
                        label={link.paymentType ?? '—'}
                        size="small"
                        variant="outlined"
                        color={link.paymentType === 'DMS' ? 'warning' : link.paymentType === 'SMS' ? 'primary' : 'default'}
                        sx={{ fontWeight: 700, fontFamily: 'monospace' }}
                      />
                      <Typography variant="caption" color="text.secondary">
                        {link.paymentType === 'DMS' ? 'Authorize & Capture' : link.paymentType === 'SMS' ? 'Immediate charge' : '—'}
                      </Typography>
                    </Box>
                  }
                />
                {/* Стадия DMS показывается, только когда она **известна**. Раньше здесь стоял
                    тернарник, у которого ветка «иначе» означала «Finalized — Captured»: поле
                    `dmsStatus` маппинг из API не заполняет, поэтому с приходом даты оплаты
                    (P2-15) каждая DMS-ссылка объявлялась бы captured — про холд, который на
                    самом деле может висеть неснятым. Настоящая стадия видна по статусу
                    транзакции в таблице связанных операций (`AUTHORIZED` / `SUCCESS`). */}
                {link.paymentType === 'DMS' && link.dmsStatus && (
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
                {/* Строк «Transaction ID», «Payment Method» (карта) и «Payer IP» здесь больше нет
                    (P2-15, Р-48). Ни одно из трёх полей API по ссылке не отдаёт, и до включения
                    даты оплаты весь блок просто не рисовался — а с ней он показал бы прочерк,
                    `undefined ···· undefined` и пустую строку соответственно. Идентификаторы и
                    карты настоящих платежей есть ниже, в таблице связанных операций, и берутся
                    оттуда, откуда их присылает бэкенд. */}
                <Divider sx={{ opacity: 0.5 }} />
                <InfoRow label="Paid At" value={formatDateTime(link.paidAt)} />
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
              {/* Терминал ссылки — первой строкой: через него пойдут все платежи по ней.
                  Подпись — логин, имя идёт под ним (см. `utils/terminals.ts`). */}
              <InfoRow
                label={tObj.payByLinkDetail.summary.terminal}
                value={
                  <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end' }}>
                    <Typography variant="body2" sx={{ fontFamily: 'monospace', fontWeight: 700 }}>
                      {terminalLabel({
                        terminalLogin: terminalIndex[link.terminalId as number]?.login,
                        terminalName: terminalIndex[link.terminalId as number]?.name,
                        terminalId: link.terminalId
                      })}
                    </Typography>
                    {terminalSubLabel({
                      terminalLogin: terminalIndex[link.terminalId as number]?.login,
                      terminalName: terminalIndex[link.terminalId as number]?.name
                    }) && (
                      <Typography variant="caption" color="text.secondary">
                        {terminalIndex[link.terminalId as number]?.name}
                      </Typography>
                    )}
                  </Box>
                }
              />
              <Divider sx={{ opacity: 0.5 }} />
              <InfoRow
                label="Payment Type"
                value={
                  <Chip
                    size="small"
                    label={link.paymentType === 'DMS' ? 'DMS — Authorize & Capture' : link.paymentType === 'SMS' ? 'SMS — Immediate Charge' : '—'}
                    color={link.paymentType === 'DMS' ? 'warning' : link.paymentType === 'SMS' ? 'primary' : 'default'}
                    variant="outlined"
                    sx={{ fontWeight: 600 }}
                  />
                }
              />
              <Divider sx={{ opacity: 0.5 }} />
              <InfoRow label="Usage Type" value={
                <Chip
                  size="small"
                  label={link.usageType === 'SINGLE' ? 'Single Use' : link.usageType === 'MULTIPLE' ? 'Multiple Uses' : '—'}
                  variant="outlined"
                  sx={{ fontWeight: 600 }}
                />
              } />
              {link.usageType === 'MULTIPLE' && (
                <>
                  <Divider sx={{ opacity: 0.5 }} />
                  <InfoRow
                    label="Usage"
                    value={
                      <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 0.5 }}>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, justifyContent: 'flex-end' }}>
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>{link.usedCount} / {link.maxUses}</Typography>
                          <LinearProgress
                            variant="determinate"
                            value={(link.usedCount / link.maxUses) * 100}
                            sx={{ width: 80, height: 6, borderRadius: 3 }}
                          />
                        </Box>
                        {/* Возврат не отменяет использование (P2-16, Р-49): usedCount выше
                            не уменьшается, а сколько из платежей вернули — отдельной строкой,
                            и только когда возвраты были (Р-50). */}
                        {link.refundedCount > 0 && (
                          <Typography variant="caption" color="text.secondary">
                            {tObj.payByLinkDetail.summary.refundedOfUsed}: {link.refundedCount}
                          </Typography>
                        )}
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
                          onClick={() => setSnackbar({ text: 'QR code feature coming soon' })}
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
                <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: statusColors.textColor, flexShrink: 0 }} />
                <Typography variant="body1" sx={{ fontWeight: 700, color: statusColors.textColor }}>{statusText}</Typography>
              </Box>

              {link.status === 'ACTIVE' && expPct !== null && (
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

              {link.paidAt && link.paymentType === 'SMS' && (
                <Alert severity="success" icon={<DoneIcon fontSize="small" />} sx={{ mt: 1 }}>
                  Payment received on {formatDateTime(link.paidAt)}
                </Alert>
              )}
              {link.paymentType === 'DMS' && link.dmsStatus === 'authorized' && (
                <Alert severity="warning" icon={<AuthorizedIcon fontSize="small" />} sx={{ mt: 1 }}>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>Funds Authorized</Typography>
                  <Typography variant="caption">
                    {/* Подсказка называет кнопку её настоящей подписью (P3-5a): подпись
                        теперь переводится, а зашитое «Finalize Payment» указывало бы на
                        кнопку, которой на азербайджанском и русском экране нет. */}
                    ₼{link.amount.toFixed(2)} is reserved on the customer's card. Press <strong>{tObj.payByLinkDetail.finalizeDMS}</strong> to capture the funds.
                  </Typography>
                </Alert>
              )}
              {link.paymentType === 'DMS' && link.dmsStatus === 'finalized' && (
                <Alert severity="success" icon={<FinalizeIcon fontSize="small" />} sx={{ mt: 1 }}>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>Payment Finalized</Typography>
                  <Typography variant="caption">
                    Funds captured on {link.finalizedAt ? formatDateTime(link.finalizedAt) : '—'}
                  </Typography>
                </Alert>
              )}

              {link.status === 'EXPIRED' && (
                <Alert severity="warning" icon={<WarningIcon fontSize="small" />} sx={{ mt: 1 }}>
                  Expired without payment on {formatDateTime(link.expiresAt)}
                </Alert>
              )}

              {link.status === 'CANCELED' && (
                <Alert severity="error" icon={<CancelIcon fontSize="small" />} sx={{ mt: 1 }}>
                  This link was manually cancelled
                </Alert>
              )}
            </Paper>

            {/* Quick actions */}
            {(link.status === 'EXPIRED' || link.status === 'CANCELED') && (
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

      {/* ── Finalize dialog ─────────────────────────────────────────────────
          Сумма ушла из фразы в рамку рядом с коротким кодом (P3-5a): так подтверждение
          списания холда выглядит одинаково здесь и на карточке транзакции, а текст
          обходится без подстановки внутрь предложения. */}
      <ConfirmDialog
        open={finalizeDialogOpen}
        maxWidth="xs"
        title={tObj.payByLinkDetail.finalizeDMS}
        question={tObj.transactions.detail.captureExplains}
        confirmLabel={tObj.transactions.detail.confirmCapture}
        confirmColor="success"
        confirmIcon={<FinalizeIcon />}
        busy={finalizeBusy}
        confirmDisabled={finalizeUnresolved}
        onConfirm={handleFinalize}
        onCancel={() => { setFinalizeDialogOpen(false); setFinalizeError(null); }}
      >
        <Box sx={{ mt: 2, p: 2, borderRadius: 1, border: '1px solid', borderColor: 'divider', bgcolor: 'action.hover' }}>
          <Typography variant="body2" color="text.secondary">
            {tObj.payByLinkDetail.summary.shortCode}
          </Typography>
          <Typography variant="body2" sx={{ fontWeight: 700, fontFamily: 'monospace' }}>
            {link.shortCode}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            {tObj.transactions.detail.captureAmount}
          </Typography>
          <Typography variant="body2" sx={{ fontWeight: 700 }}>
            ₼{link.amount.toFixed(2)}
          </Typography>
        </Box>
        {finalizeError && (
          <Alert severity={finalizeUnresolved ? 'warning' : 'error'} sx={{ mt: 2 }}>
            {finalizeUnresolved && (
              <AlertTitle sx={{ fontWeight: 700 }}>{tObj.transactions.detail.unresolvedTitle}</AlertTitle>
            )}
            {finalizeError.message}
            {finalizeUnresolved && ` ${tObj.transactions.detail.unresolvedHint}`}
          </Alert>
        )}
      </ConfirmDialog>

      {/* ── Cancel dialog ───────────────────────────────────────────────────
          Слово в слово то же окно, что в списке (P3-5a). */}
      <ConfirmDialog
        open={cancelDialogOpen}
        maxWidth="xs"
        title={tObj.payByLink.cancelConfirmTitle}
        question={tObj.payByLink.cancelConfirmText}
        cancelLabel={tObj.payByLink.keepLink}
        confirmLabel={tObj.payByLinkDetail.cancelLink}
        confirmIcon={<CancelIcon />}
        busy={cancelBusy}
        onConfirm={handleCancel}
        onCancel={() => setCancelDialogOpen(false)}
      >
        <Box sx={{ mt: 2, p: 2, borderRadius: 1, border: '1px solid', borderColor: 'divider', bgcolor: 'action.hover' }}>
          <Typography variant="body2" color="text.secondary">
            {tObj.payByLinkDetail.summary.shortCode}
          </Typography>
          <Typography variant="body2" sx={{ fontWeight: 700, fontFamily: 'monospace' }}>
            {link.shortCode}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            {tObj.payByLink.table.amount}
          </Typography>
          <Typography variant="body2" sx={{ fontWeight: 700 }}>
            ₼{link.amount.toFixed(2)}
          </Typography>
        </Box>
      </ConfirmDialog>

      {/* Snackbar */}
      <Snackbar
        open={!!snackbar}
        autoHideDuration={snackbar?.error ? 10000 : 3000}
        onClose={() => setSnackbar(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          severity={snackbar?.error ? 'error' : 'success'}
          onClose={() => setSnackbar(null)}
          sx={{ width: '100%' }}
        >
          {snackbar?.text}
        </Alert>
      </Snackbar>
    </Box>
  );
};
