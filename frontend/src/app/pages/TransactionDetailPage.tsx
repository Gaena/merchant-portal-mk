import React, { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router';
import axios from 'axios';
import { apiClient } from '../api/client';
import {
  Box,
  Paper,
  Typography,
  Button,
  Divider,
  Chip,
  Alert,
  AlertTitle,
  Stack,
  CircularProgress
} from '@mui/material';
import {
  ArrowBack as ArrowBackIcon,
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
import type { StatusHistoryEntry, Transaction } from '../types/transaction';
import { formatCurrency, formatDateTime, getPaymentMethodLabel } from '../utils/format';
import { getStatusColorScheme } from '../utils/statusColors';
import { buildTerminalIndex, terminalLabel, terminalSubLabel } from '../utils/terminals';
import { mapTransaction } from '../utils/mapTransaction';
import { readMoneyOperationFailure, type MoneyOperationFailure } from '../utils/moneyOperationError';
import type { TerminalOptionDto } from '../types/dto';

import { useLanguage } from '../context/LanguageContext';
import { useAuth } from '../context/AuthContext';
import { canRefund, canWriteLinks } from '../auth/actionAccess';
import { statusLabel, type TranslationDictionary } from '../i18n/translations';
import { ConfirmDialog } from '../components/ConfirmDialog';

/**
 * Остаток к возврату — правила `PaymentLinkService.refund` (Р-62): только `SUCCESS` и
 * `PARTIALLY_REFUNDED` (`isRefundable`), потолок — `capturedAmount`, у SMS и несписанных холдов
 * `amount`, минус уже возвращённое. Меняется правило на бэкенде — меняется и здесь.
 */
const refundableLeftOf = (tx: Transaction): number =>
  Math.max(0, toMinorUnits(tx.capturedAmount ?? tx.amount) - toMinorUnits(tx.refundedAmount ?? 0)) / 100;

// Копейки: `10.10 - 9.80` в double — `0.29999999999999893`, и бэкенд такой возврат отвергает.
const toMinorUnits = (amount: unknown): number => Math.round(Number(amount ?? 0) * 100);

// Неподтверждённый исход объясняет только движение денег: по операции в терминальном статусе бэкенд
// эквайера не спрашивает, а «всё ещё AUTHORIZED» не отличить от «не спросили».
const moneyMoved = (before: Transaction, after: Transaction): boolean =>
  before.status !== after.status
  || toMinorUnits(before.refundedAmount) !== toMinorUnits(after.refundedAmount)
  || toMinorUnits(before.capturedAmount) !== toMinorUnits(after.capturedAmount);

// Денежное событие подписано действием (списание, возврат): состояние после него видно по цвету.
const eventLabel = (tObj: TranslationDictionary, entry: StatusHistoryEntry): string => {
  if (entry.type === 'CAPTURED') return tObj.transactions.detail.eventCaptured;
  if (entry.type === 'REFUNDED') return tObj.transactions.detail.eventRefunded;
  if (entry.type === 'CREATED') return tObj.transactions.detail.eventCreated;
  return statusLabel(tObj, entry.status, entry.statusRaw);
};

const isRefundable = (tx: Transaction): boolean =>
  (tx.status === 'SUCCESS' || tx.status === 'PARTIALLY_REFUNDED') && refundableLeftOf(tx) > 0;

export const TransactionDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const { tObj } = useLanguage();
  const { user } = useAuth();
  // Кнопки денежных действий — только ролям, которым бэкенд их примет (Р-62).
  const mayRefund = canRefund(user?.role);
  const mayCapture = canWriteLinks(user?.role);

  const [cancelDialogOpen, setCancelDialogOpen] = useState(false);
  const [cancelBusy, setCancelBusy] = useState(false);
  const [cancelSuccess, setCancelSuccess] = useState(false);
  const [completeDialogOpen, setCompleteDialogOpen] = useState(false);
  const [completeBusy, setCompleteBusy] = useState(false);
  const [completeSuccess, setCompleteSuccess] = useState(false);
  const [actionError, setActionError] = useState<MoneyOperationFailure | null>(null);
  const [checkingStatus, setCheckingStatus] = useState(false);
  const [statusChecked, setStatusChecked] = useState(false);
  /** Сбой самой проверки статуса. Не исход денежной операции — `actionError` не трогает. */
  const [checkError, setCheckError] = useState<string | null>(null);
  /** Перечитали, а операция не изменилась: неподтверждённый исход так и остался неподтверждённым. */
  const [stillUnresolved, setStillUnresolved] = useState(false);
  // Ответ `GET /transactions/{id}` — единственный источник карточки (Р-65). Разбор — в `useMemo`:
  // подпись терминала подтягивается с индексом терминалов без второго запроса операции.
  const [rawTx, setRawTx] = useState<unknown>(null);
  const [loadingTx, setLoadingTx] = useState(true);
  const [loadFailed, setLoadFailed] = useState(false);
  const [terminalIndex, setTerminalIndex] = useState<Record<number, TerminalOptionDto>>({});

  const transaction = useMemo(
    () => (rawTx ? mapTransaction(rawTx, terminalIndex) : undefined),
    [rawTx, terminalIndex]
  );

  // Прямой заход по адресу истории не имеет — тогда «назад» ведёт на главную.
  const goBack = () => (location.key === 'default' ? navigate('/') : navigate(-1));

  // Пока исход не подтверждён, повтор закрыт: эквайер мог операцию уже выполнить. Запрет снимает
  // только проверка статуса, в которой видно движение денег (`moneyMoved`).
  const outcomeUnresolved = actionError?.outcome === 'unknown';

  // В `options` есть и заблокированные (Р-45): платёж через снятый терминал сохраняет подпись.
  useEffect(() => {
    const controller = new AbortController();
    apiClient.get('/api/v1/terminals/options', { signal: controller.signal })
      .then(res => setTerminalIndex(buildTerminalIndex(res.data)))
      .catch(() => {});
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (!id) return;
    const controller = new AbortController();
    setLoadingTx(true);
    setLoadFailed(false);
    setRawTx(null);
    setActionError(null);
    setCancelSuccess(false);
    setCompleteSuccess(false);
    apiClient.get(`/api/v1/transactions/${id}`, { signal: controller.signal })
      .then(res => setRawTx(res.data ?? null))
      .catch(err => {
        if (axios.isCancel(err)) return;
        setLoadFailed(true);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoadingTx(false);
      });
    return () => controller.abort();
  }, [id]);

  /** Перечитать операцию после денежного действия: статус и суммы на карточке — с сервера. */
  const reload = async () => {
    if (!id) return;
    try {
      const res = await apiClient.get(`/api/v1/transactions/${id}`);
      if (res.data) setRawTx(res.data);
    } catch (error) {
      console.warn('[transactions] не удалось перечитать операцию после действия:', error);
    }
  };

  // Отказ остаётся в окне подтверждения — туда мерчант смотрит (Р-61). При неподтверждённом исходе
  // (502) окно не даёт повторить: повтор может провести деньги дважды.
  const handleCancelTransaction = async () => {
    if (!transaction) return;
    setActionError(null);
    setCancelBusy(true);
    try {
      await apiClient.post(`/api/v1/transactions/${transaction.id}/refund`, {
        amount: refundableLeftOf(transaction),
        reason: 'Merchant refund request'
      });
      setCancelSuccess(true);
      setCancelDialogOpen(false);
      await reload();
    } catch (err: unknown) {
      setActionError(readMoneyOperationFailure(err, 'Failed to refund transaction on server'));
    } finally {
      setCancelBusy(false);
    }
  };

  const handleCompleteTransaction = async () => {
    if (!transaction) return;
    setActionError(null);
    setCompleteBusy(true);
    try {
      await apiClient.post(`/api/v1/transactions/${transaction.id}/complete`, {
        amount: Number(transaction.amount) || 0
      });
      setCompleteSuccess(true);
      setCompleteDialogOpen(false);
      await reload();
    } catch (err: unknown) {
      setActionError(readMoneyOperationFailure(err, 'Failed to complete DMS transaction on server'));
    } finally {
      setCompleteBusy(false);
    }
  };

  // Запрет на повтор снимает не любой ответ 200, а только движение денег (`moneyMoved`): по операции
  // в терминальном статусе бэкенд отвечает, не спрашивая эквайера. Сбой проверки — свой `checkError`,
  // не исход операции: `actionError` он не трогает.
  const handleCheckStatus = async () => {
    if (!transaction) return;
    const before = transaction;
    setCheckingStatus(true);
    setStatusChecked(false);
    setStillUnresolved(false);
    setCheckError(null);
    try {
      const res = await apiClient.get(`/api/v1/transactions/${transaction.id}/status`);
      if (!res.data) {
        setCheckError(tObj.transactions.detail.checkStatusFailed);
        return;
      }
      const after = mapTransaction(res.data, terminalIndex);
      setRawTx(res.data);
      if (outcomeUnresolved && !moneyMoved(before, after)) {
        setStillUnresolved(true);
        return;
      }
      setActionError(null);
      setStatusChecked(true);
    } catch (err: unknown) {
      const serverMessage = axios.isAxiosError(err) ? err.response?.data?.message : undefined;
      setCheckError(typeof serverMessage === 'string' && serverMessage
        ? serverMessage
        : tObj.transactions.detail.checkStatusFailed);
    } finally {
      setCheckingStatus(false);
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
            {loadFailed ? tObj.common.loadFailed : tObj.errors.notFoundTitle}
          </Typography>
          <Button
            variant="contained"
            startIcon={<ArrowBackIcon />}
            onClick={goBack}
            sx={{ mt: 2 }}
          >
            {tObj.common.back}
          </Button>
        </Paper>
      </Box>
    );
  }

  // Значения разобраны на границе — сравниваем напрямую, без `toUpperCase()` (P2-12).
  const isCompletableDms =
    mayCapture &&
    transaction.paymentMethod === 'DMS' &&
    (transaction.status === 'PENDING' || transaction.status === 'AUTHORIZED');

  const refundableLeft = refundableLeftOf(transaction);
  const refundable = mayRefund && isRefundable(transaction);
  const refundedSoFar = Number(transaction.refundedAmount ?? 0);

  const failureNotice = actionError && (
    <Alert severity={outcomeUnresolved ? 'warning' : 'error'} sx={{ mt: 2 }}>
      {outcomeUnresolved && (
        <AlertTitle sx={{ fontWeight: 700 }}>{tObj.transactions.detail.unresolvedTitle}</AlertTitle>
      )}
      {actionError.message}
      {outcomeUnresolved && ` ${tObj.transactions.detail.unresolvedHint}`}
    </Alert>
  );

  return (
    <Box sx={{ p: 4 }}>
      {cancelSuccess && (
        <Alert severity="success" sx={{ mb: 3 }} onClose={() => setCancelSuccess(false)}>
          {tObj.transactions.detail.eventRefunded}
        </Alert>
      )}
      {statusChecked && (
        <Alert severity="info" sx={{ mb: 3 }} onClose={() => setStatusChecked(false)}>
          {tObj.transactions.detail.statusChecked}
        </Alert>
      )}
      {checkError && (
        <Alert severity="error" sx={{ mb: 3 }} onClose={() => setCheckError(null)}>
          {checkError}
        </Alert>
      )}
      {/* Тот же отказ, что в окне: окно закрыли — след остался. Неподтверждённый исход — не ошибка. */}
      {actionError && (
        <Alert
          severity={outcomeUnresolved ? 'warning' : 'error'}
          sx={{ mb: 3 }}
          onClose={() => setActionError(null)}
        >
          {outcomeUnresolved && (
            <AlertTitle sx={{ fontWeight: 700 }}>{tObj.transactions.detail.unresolvedTitle}</AlertTitle>
          )}
          {actionError.message}
          {outcomeUnresolved && ` ${tObj.transactions.detail.unresolvedHint}`}
          {stillUnresolved && (
            <Typography variant="body2" sx={{ mt: 1, fontWeight: 600 }}>
              {tObj.transactions.detail.statusStillUnresolved}
            </Typography>
          )}
        </Alert>
      )}

      <Box sx={{ mb: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Button
          startIcon={<ArrowBackIcon />}
          onClick={goBack}
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
          {checkingStatus ? tObj.common.loading : tObj.transactions.detail.checkStatusAction}
        </Button>
      </Box>

      <Box sx={{ mb: 4 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
          <Box>
            <Typography variant="h4" sx={{ fontWeight: 700 }}>
              {tObj.transactions.detail.title}
            </Typography>
            {/* Идентификаторы, по которым мерчант узнаёт платёж (Р-58); внутренний UUID — внизу карточки. */}
            <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap', rowGap: 0.5 }}>
              <Typography variant="body2" color="text.secondary">
                {tObj.transactions.detail.providerOrderId}:{' '}
                <Box component="span" sx={{ fontFamily: 'monospace', fontWeight: 700, color: 'text.primary' }}>
                  {transaction.providerOrderId || '—'}
                </Box>
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {tObj.transactions.detail.ridByMerchant}:{' '}
                <Box component="span" sx={{ fontFamily: 'monospace', fontWeight: 700, color: 'text.primary' }}>
                  {transaction.ridByMerchant || '—'}
                </Box>
              </Typography>
            </Stack>
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
            label={statusLabel(tObj, transaction.status, transaction.statusRaw)}
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

      <Paper elevation={2} sx={{ overflow: 'hidden', mb: 3 }}>
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

        <Box sx={{ p: 4 }}>
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

          <Box sx={{ mb: 4 }}>
            <Typography variant="subtitle2" sx={{
              mb: 2.5,
              color: 'text.secondary',
              fontWeight: 700,
              textTransform: 'uppercase',
              fontSize: '0.75rem',
              letterSpacing: 1
            }}>
              {tObj.transactions.detail.identifiers}
            </Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)' }, gap: 3 }}>
              <Box>
                <Typography variant="caption" sx={{
                  color: 'text.secondary',
                  fontWeight: 600,
                  textTransform: 'uppercase',
                  fontSize: '0.7rem',
                  letterSpacing: 0.5
                }}>
                  {tObj.transactions.detail.providerOrderId}
                </Typography>
                <Typography variant="h6" sx={{
                  fontWeight: 700,
                  fontFamily: 'monospace',
                  color: 'primary.main',
                  mt: 0.5,
                  wordBreak: 'break-all'
                }}>
                  {transaction.providerOrderId || '—'}
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
                  {tObj.transactions.detail.ridByMerchant}
                </Typography>
                <Typography variant="h6" sx={{
                  fontWeight: 700,
                  fontFamily: 'monospace',
                  color: 'primary.main',
                  mt: 0.5,
                  wordBreak: 'break-all'
                }}>
                  {transaction.ridByMerchant || '—'}
                </Typography>
              </Box>
            </Box>
          </Box>

          <Divider sx={{ my: 4 }} />

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
            <Box sx={{
              display: 'grid',
              gridTemplateColumns: { xs: 'repeat(2, 1fr)', sm: 'repeat(auto-fit, minmax(160px, 1fr))' },
              gap: 2
            }}>
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
                  {transaction.currency || '—'}
                </Typography>
              </Box>

              {/* Комиссии в системе нет — строк «комиссия» и «к получению» тоже (Р-48). */}
              {transaction.capturedAmount !== undefined && transaction.capturedAmount !== transaction.amount && (
                <Box>
                  <Typography variant="caption" sx={{
                    color: 'text.secondary',
                    fontWeight: 600,
                    textTransform: 'uppercase',
                    fontSize: '0.7rem',
                    letterSpacing: 0.5
                  }}>
                    {tObj.transactions.detail.captureAmount}
                  </Typography>
                  <Typography variant="h6" sx={{ fontWeight: 700, color: 'text.primary', mt: 0.5 }}>
                    {formatCurrency(transaction.capturedAmount, transaction.currency)}
                  </Typography>
                </Box>
              )}

              {/* Без суммы возвратов исчезнувшая кнопка возврата выглядела бы поломкой. */}
              {refundedSoFar > 0 && (
                <Box>
                  <Typography variant="caption" sx={{
                    color: 'text.secondary',
                    fontWeight: 600,
                    textTransform: 'uppercase',
                    fontSize: '0.7rem',
                    letterSpacing: 0.5
                  }}>
                    {tObj.transactions.statuses.REFUNDED}
                  </Typography>
                  <Typography variant="h6" sx={{ fontWeight: 700, color: 'warning.dark', mt: 0.5 }}>
                    {formatCurrency(refundedSoFar, transaction.currency)}
                  </Typography>
                </Box>
              )}
            </Box>
          </Box>

          <Divider sx={{ my: 4 }} />

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
                  {tObj.transactions.columns.terminalLogin}
                </Typography>
                <Box sx={{
                  display: 'inline-block',
                  px: 1.5,
                  py: 0.75,
                  bgcolor: 'primary.main',
                  color: 'white',
                  borderRadius: 1,
                  fontFamily: 'monospace',
                  fontWeight: 700,
                  fontSize: '0.875rem'
                }}>
                  {terminalLabel(transaction)}
                </Box>
                {terminalSubLabel(transaction) && (
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                    {terminalSubLabel(transaction)}
                  </Typography>
                )}
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
                  {transaction.cardLast4 ? `*${transaction.cardLast4}` : '—'}
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
                  {transaction.description || '—'}
                </Typography>
              </Box>
            </Box>
            {/* Причина отказа словами эквайера (Р-24). */}
            {transaction.failureReason && (
              <Alert severity="error" sx={{ mt: 3 }}>{transaction.failureReason}</Alert>
            )}
          </Box>

          <Divider sx={{ my: 4 }} />

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
                  {transaction.customer || '—'}
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
                  {transaction.customerEmail || '—'}
                </Typography>
              </Box>

              {/* «Merchant Reference» не показывается (Р-60): у ссылок портала он всегда пуст. */}

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
                  label={transaction.clientIp || '—'}
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

          <Divider sx={{ my: 4 }} />

          {/* Внутренний UUID — последним: мерчанту он ничего не говорит, но по нему ищут в логах и в поддержке. */}
          <Box>
            <Typography variant="caption" sx={{
              color: 'text.secondary',
              fontWeight: 600,
              textTransform: 'uppercase',
              fontSize: '0.7rem',
              letterSpacing: 0.5
            }}>
              {tObj.transactions.columns.id}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ fontFamily: 'monospace', mt: 0.5, wordBreak: 'break-all' }}>
              {transaction.id}
            </Typography>
          </Box>
        </Box>
      </Paper>

      {/* Действия видны, только когда бэкенд их примет (Р-62): `isRefundable`, `isCompletableDms`. */}
      {(refundable || isCompletableDms || outcomeUnresolved) && (
        <Paper elevation={2} sx={{ p: 3, mb: 3, bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 3 }}>
            <Box sx={{ flex: 1 }}>
              <Typography variant="h6" sx={{ mb: 1, fontWeight: 600, color: 'text.primary' }}>
                Transaction Actions
              </Typography>
              {isCompletableDms && (
                <Typography variant="body2" color="text.secondary">
                  This DMS transaction has funds authorized on the customer's card. Complete it to capture the funds, or cancel to release the hold.
                </Typography>
              )}
              {!isCompletableDms && refundable && (
                <Typography variant="body2" color="text.secondary">
                  Cancel this transaction and initiate a refund to the customer. The amount will be reversed within 3-5 business days.
                </Typography>
              )}
              {/* После частичного возврата остаток назван здесь, а не только в окне подтверждения. */}
              {refundable && transaction.status === 'PARTIALLY_REFUNDED' && (
                <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                  {tObj.transactions.detail.refundableLeft}:{' '}
                  <Box component="span" sx={{ fontWeight: 700, color: 'text.primary' }}>
                    {formatCurrency(refundableLeft, transaction.currency)}
                  </Box>
                </Typography>
              )}
              {/* Кнопки погашены — экран обязан сказать почему, иначе это выглядит поломкой. */}
              {outcomeUnresolved && (
                <Typography variant="body2" color="warning.dark" sx={{ mt: 1, fontWeight: 600 }}>
                  {stillUnresolved ? tObj.transactions.detail.statusStillUnresolved : tObj.transactions.detail.unresolvedHint}
                </Typography>
              )}
            </Box>
            <Stack direction="row" spacing={1.5}>
              {/* Пока исход не выяснен, доступно только спросить эквайера — кнопка рядом с погашенными. */}
              {outcomeUnresolved && (
                <Button
                  variant="contained"
                  color="warning"
                  startIcon={<RefreshIcon />}
                  disabled={checkingStatus}
                  onClick={handleCheckStatus}
                  sx={{ py: 1.5, px: 3 }}
                >
                  {checkingStatus ? tObj.common.loading : tObj.transactions.detail.checkStatusAction}
                </Button>
              )}
              {isCompletableDms && !completeSuccess && (
                <Button
                  variant="outlined"
                  color="success"
                  startIcon={<CompleteIcon />}
                  disabled={outcomeUnresolved}
                  onClick={() => setCompleteDialogOpen(true)}
                  sx={{
                    py: 1.5,
                    px: 3,
                    '&:hover': { bgcolor: 'success.light', color: 'success.dark' }
                  }}
                >
                  {tObj.transactions.detail.completeAction}
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
              {refundable && (
                <Button
                  variant="outlined"
                  color="warning"
                  startIcon={<CancelIcon />}
                  disabled={outcomeUnresolved}
                  onClick={() => setCancelDialogOpen(true)}
                  sx={{
                    py: 1.5,
                    px: 3,
                    '&:hover': { bgcolor: 'warning.light', color: 'warning.dark' }
                  }}
                >
                  {tObj.transactions.detail.refundAction}
                </Button>
              )}
            </Stack>
          </Box>
        </Paper>
      )}

      <Paper elevation={2} sx={{ p: 4 }}>
        <Typography variant="h6" sx={{ mb: 1, fontWeight: 600 }}>
          Transaction Status History
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 4 }}>
          Track the complete lifecycle of this transaction from initiation to completion
        </Typography>
        
        <Box sx={{ position: 'relative' }}>
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
                
                <Box sx={{ flex: 1, pb: 2 }}>
                  <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 2, mb: 0.5 }}>
                    <Typography variant="body1" sx={{ fontWeight: 700, color: 'text.primary' }}>
                      {eventLabel(tObj, entry)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 500 }}>
                      {formatDateTime(entry.timestamp)}
                    </Typography>
                  </Box>
                  {/* Сумма и ссылка эквайера есть только у списания и возврата. */}
                  {(entry.amount !== undefined || entry.acquirerReference) && (
                    <Stack direction="row" spacing={2} sx={{ mt: 0.5, flexWrap: 'wrap', rowGap: 0.5 }}>
                      {entry.amount !== undefined && (
                        <Typography variant="body2" sx={{ fontWeight: 700, color: 'text.primary' }}>
                          {formatCurrency(entry.amount, transaction.currency)}
                        </Typography>
                      )}
                      {entry.acquirerReference && (
                        <Typography variant="body2" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                          {entry.acquirerReference}
                        </Typography>
                      )}
                    </Stack>
                  )}
                  {entry.note && (
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      {entry.note}
                    </Typography>
                  )}
                </Box>
              </Box>
            ))}
            {/* Пустая шкала без слов читалась бы как поломка: пусто — значит, ответ событий не принёс. */}
            {transaction.statusHistory.length === 0 && (
              <Typography variant="body2" color="text.secondary">
                {tObj.transactions.detail.historyEmpty}
              </Typography>
            )}
          </Box>
        </Box>
      </Paper>

      <ConfirmDialog
        open={completeDialogOpen}
        title={tObj.transactions.detail.completeTitle}
        question={tObj.transactions.detail.captureExplains}
        confirmLabel={tObj.transactions.detail.confirmCapture}
        confirmColor="success"
        confirmIcon={<CompleteIcon />}
        busy={completeBusy}
        confirmDisabled={outcomeUnresolved}
        onConfirm={handleCompleteTransaction}
        onCancel={() => setCompleteDialogOpen(false)}
      >
        <Box sx={{ mt: 2, p: 2, bgcolor: 'success.light', borderRadius: 1 }}>
          <Typography variant="body2" sx={{ fontWeight: 600 }}>
            {tObj.transactions.detail.providerOrderId}: {transaction.providerOrderId || '—'}
          </Typography>
          <Typography variant="body2" sx={{ fontWeight: 600 }}>
            {tObj.transactions.detail.ridByMerchant}: {transaction.ridByMerchant || '—'}
          </Typography>
          <Typography variant="body2">
            {tObj.transactions.detail.captureAmount}: {formatCurrency(transaction.amount, transaction.currency)}
          </Typography>
        </Box>
        {failureNotice}
      </ConfirmDialog>

      <ConfirmDialog
        open={cancelDialogOpen}
        title={tObj.transactions.detail.refundTitle}
        question={tObj.transactions.detail.refundQuestion}
        cancelLabel={tObj.transactions.detail.keepTransaction}
        confirmLabel={tObj.transactions.detail.confirmRefund}
        busy={cancelBusy}
        confirmDisabled={outcomeUnresolved}
        onConfirm={handleCancelTransaction}
        onCancel={() => setCancelDialogOpen(false)}
      >
        <Box sx={{ mt: 2, p: 2, bgcolor: 'error.light', borderRadius: 1 }}>
          <Typography variant="body2" sx={{ fontWeight: 600 }}>
            {tObj.transactions.detail.providerOrderId}: {transaction.providerOrderId || '—'}
          </Typography>
          <Typography variant="body2" sx={{ fontWeight: 600 }}>
            {tObj.transactions.detail.ridByMerchant}: {transaction.ridByMerchant || '—'}
          </Typography>
          <Typography variant="body2">
            {tObj.transactions.detail.refundAmount}: {formatCurrency(refundableLeft, transaction.currency)}
          </Typography>
        </Box>
        {failureNotice}
      </ConfirmDialog>
    </Box>
  );
};