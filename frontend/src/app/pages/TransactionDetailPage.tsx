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
  CircularProgress,
  Tooltip
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
import type { MoneyAction, StatusHistoryEntry } from '../types/transaction';
import { formatCurrency, formatDateTime, getPaymentMethodLabel } from '../utils/format';
import { getStatusColorScheme } from '../utils/statusColors';
import { buildTerminalIndex, terminalLabel, terminalSubLabel } from '../utils/terminals';
import { mapTransaction } from '../utils/mapTransaction';
import { readMoneyOperationFailure, type MoneyOperationFailure } from '../utils/moneyOperationError';
import type { TerminalOptionDto } from '../types/dto';

import { useLanguage } from '../context/LanguageContext';
import { statusLabel, type TranslationDictionary } from '../i18n/translations';
import { ConfirmDialog } from '../components/ConfirmDialog';

// Денежное событие подписано действием (списание, возврат): состояние после него видно по цвету.
const eventLabel = (tObj: TranslationDictionary, entry: StatusHistoryEntry): string => {
  if (entry.type === 'CAPTURED') return tObj.transactions.detail.eventCaptured;
  if (entry.type === 'REFUNDED') return tObj.transactions.detail.eventRefunded;
  if (entry.type === 'CREATED') return tObj.transactions.detail.eventCreated;
  return statusLabel(tObj, entry.status, entry.statusRaw);
};

export const TransactionDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const { tObj } = useLanguage();
  const d = tObj.transactions.detail;

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
  /** Итог неподтверждённой операции, который отмечает администратор: `true` — прошла, `false` — нет (Р-123). */
  const [resolveTarget, setResolveTarget] = useState<boolean | null>(null);
  const [resolveBusy, setResolveBusy] = useState(false);
  const [resolveError, setResolveError] = useState<string | null>(null);
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

  // Ответ 502 в открытом окне: повтор из этого окна закрыт сразу, дальше запрет держит сервер (Р-123) —
  // карточка перечитывается и показывает его в `actions`.
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
      // Возвращается весь остаток; потолок — с сервера, а не своим расчётом (Р-123).
      await apiClient.post(`/api/v1/transactions/${transaction.id}/refund`, {
        amount: transaction.actions?.refund?.maxAmount,
        reason: 'Merchant refund request'
      });
      setCancelSuccess(true);
      setCancelDialogOpen(false);
      await reload();
    } catch (err: unknown) {
      setActionError(readMoneyOperationFailure(err, 'Failed to refund transaction on server'));
      await reload();
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
        amount: transaction.actions?.capture?.maxAmount
      });
      setCompleteSuccess(true);
      setCompleteDialogOpen(false);
      await reload();
    } catch (err: unknown) {
      setActionError(readMoneyOperationFailure(err, 'Failed to complete DMS transaction on server'));
      await reload();
    } finally {
      setCompleteBusy(false);
    }
  };

  // Свежий статус у эквайера. Запрет на повтор после неподтверждённого исхода он не снимает — это делает
  // администратор (Р-123); сбой самой проверки — свой `checkError`, не исход операции.
  const handleCheckStatus = async () => {
    if (!transaction) return;
    setCheckingStatus(true);
    setStatusChecked(false);
    setCheckError(null);
    try {
      const res = await apiClient.get(`/api/v1/transactions/${transaction.id}/status`);
      if (!res.data) {
        setCheckError(d.checkStatusFailed);
        return;
      }
      setRawTx(res.data);
      setActionError(null);
      setStatusChecked(true);
    } catch (err: unknown) {
      const serverMessage = axios.isAxiosError(err) ? err.response?.data?.message : undefined;
      setCheckError(typeof serverMessage === 'string' && serverMessage
        ? serverMessage
        : d.checkStatusFailed);
    } finally {
      setCheckingStatus(false);
    }
  };

  // Итог сверки с провайдером — только администратор (сервер проверит роль сам). Ответ — перечитанная операция.
  const handleResolve = async () => {
    if (!transaction || resolveTarget === null) return;
    setResolveBusy(true);
    setResolveError(null);
    try {
      const res = await apiClient.post(`/api/v1/transactions/${transaction.id}/resolve-outcome`, {
        executed: resolveTarget,
      });
      if (res.data) setRawTx(res.data);
      setActionError(null);
      setResolveTarget(null);
    } catch (err: unknown) {
      const serverMessage = axios.isAxiosError(err) ? err.response?.data?.message : undefined;
      setResolveError(typeof serverMessage === 'string' && serverMessage ? serverMessage : d.resolveFailed);
    } finally {
      setResolveBusy(false);
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

  // Кнопки — как решил сервер (Р-123): null — кнопки нет, выключенная — с причиной. Своих правил здесь нет.
  const refundAction = transaction.actions?.refund ?? null;
  const captureAction = transaction.actions?.capture ?? null;
  const unresolved = transaction.actions?.unresolved ?? null;
  const reasonOf = (action: MoneyAction): string => (action.reason ? d.moneyReasons[action.reason] : d.moneyReasons.other);
  const refundableLeft = refundAction?.enabled ? refundAction.maxAmount : undefined;
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

      {/* Кнопка видна по смыслу и активна по правилам сервера; выключенная говорит почему (Р-123). */}
      {(refundAction || captureAction || unresolved) && (
        <Paper elevation={2} sx={{ p: 3, mb: 3, bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 3, flexWrap: 'wrap' }}>
            <Box sx={{ flex: 1, minWidth: 260 }}>
              <Typography variant="h6" sx={{ mb: 1, fontWeight: 600, color: 'text.primary' }}>
                Transaction Actions
              </Typography>
              {captureAction?.enabled && (
                <Typography variant="body2" color="text.secondary">
                  This DMS transaction has funds authorized on the customer's card. Complete it to capture the funds, or cancel to release the hold.
                </Typography>
              )}
              {!captureAction?.enabled && refundAction?.enabled && (
                <Typography variant="body2" color="text.secondary">
                  Cancel this transaction and initiate a refund to the customer. The amount will be reversed within 3-5 business days.
                </Typography>
              )}
              {/* После частичного возврата остаток назван здесь, а не только в окне подтверждения. */}
              {refundableLeft !== undefined && transaction.status === 'PARTIALLY_REFUNDED' && (
                <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                  {d.refundableLeft}:{' '}
                  <Box component="span" sx={{ fontWeight: 700, color: 'text.primary' }}>
                    {formatCurrency(refundableLeft, transaction.currency)}
                  </Box>
                </Typography>
              )}
              {unresolved && (
                <Alert severity="warning" sx={{ mt: 1.5 }}>
                  <AlertTitle sx={{ fontWeight: 700 }}>
                    {unresolved.state === 'IN_PROGRESS' ? d.unresolvedInProgressTitle : d.unresolvedTitle}
                  </AlertTitle>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    {unresolved.kind === 'CAPTURE' ? d.unresolvedKindCapture : unresolved.kind === 'REFUND' ? d.unresolvedKindRefund : '—'}
                    {': '}{formatCurrency(unresolved.amount, transaction.currency)}
                  </Typography>
                  <Typography variant="body2">
                    {d.unresolvedStartedAt}: {unresolved.startedAt ? formatDateTime(unresolved.startedAt) : '—'}
                    {' · '}{d.unresolvedStartedBy}: {unresolved.startedBy || '—'}
                  </Typography>
                  <Typography variant="body2" sx={{ mt: 0.5 }}>
                    {unresolved.state === 'IN_PROGRESS' ? d.unresolvedInProgressHint : d.unresolvedHint}
                  </Typography>
                  {unresolved.resolvable && (
                    <Stack direction="row" spacing={1} sx={{ mt: 1.5 }}>
                      <Button size="small" variant="outlined" color="success" onClick={() => setResolveTarget(true)}>
                        {d.resolveExecuted}
                      </Button>
                      <Button size="small" variant="outlined" color="inherit" onClick={() => setResolveTarget(false)}>
                        {d.resolveNotExecuted}
                      </Button>
                    </Stack>
                  )}
                </Alert>
              )}
            </Box>
            <Stack direction="row" spacing={1.5}>
              {captureAction && !completeSuccess && (
                // Подсказка на выключенной кнопке — через обёртку: выключенная кнопка событий мыши не получает.
                <Tooltip title={captureAction.enabled ? '' : reasonOf(captureAction)}>
                  <span>
                    <Button
                      variant="outlined"
                      color="success"
                      startIcon={<CompleteIcon />}
                      disabled={!captureAction.enabled || outcomeUnresolved}
                      onClick={() => setCompleteDialogOpen(true)}
                      sx={{
                        py: 1.5,
                        px: 3,
                        '&:hover': { bgcolor: 'success.light', color: 'success.dark' }
                      }}
                    >
                      {d.completeAction}
                    </Button>
                  </span>
                </Tooltip>
              )}
              {completeSuccess && (
                <Chip
                  icon={<CompleteIcon />}
                  label="Completed — funds captured"
                  color="success"
                  sx={{ fontWeight: 600, py: 2 }}
                />
              )}
              {refundAction && (
                <Tooltip title={refundAction.enabled ? '' : reasonOf(refundAction)}>
                  <span>
                    <Button
                      variant="outlined"
                      color="warning"
                      startIcon={<CancelIcon />}
                      disabled={!refundAction.enabled || outcomeUnresolved}
                      onClick={() => setCancelDialogOpen(true)}
                      sx={{
                        py: 1.5,
                        px: 3,
                        '&:hover': { bgcolor: 'warning.light', color: 'warning.dark' }
                      }}
                    >
                      {d.refundAction}
                    </Button>
                  </span>
                </Tooltip>
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
        confirmDisabled={outcomeUnresolved || !captureAction?.enabled}
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
            {d.captureAmount}: {formatCurrency(captureAction?.maxAmount ?? transaction.amount, transaction.currency)}
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
        confirmDisabled={outcomeUnresolved || !refundAction?.enabled}
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
            {d.refundAmount}: {formatCurrency(refundableLeft, transaction.currency)}
          </Typography>
        </Box>
        {failureNotice}
      </ConfirmDialog>

      {/* Итог неподтверждённой операции (Р-123): что именно отмечают — вид, сумма и кто отправил — в рамке. */}
      <ConfirmDialog
        open={resolveTarget !== null && unresolved !== null}
        title={resolveTarget ? d.resolveExecutedTitle : d.resolveNotExecutedTitle}
        question={resolveTarget ? d.resolveExecutedQuestion : d.resolveNotExecutedQuestion}
        confirmLabel={resolveTarget ? d.resolveExecuted : d.resolveNotExecuted}
        confirmColor={resolveTarget ? 'success' : 'warning'}
        busy={resolveBusy}
        onConfirm={handleResolve}
        onCancel={() => { setResolveTarget(null); setResolveError(null); }}
      >
        {unresolved && (
          <Box sx={{ mt: 2, p: 2, borderRadius: 1, border: '1px solid', borderColor: 'divider', bgcolor: 'action.hover' }}>
            <Typography variant="body2" sx={{ fontWeight: 600 }}>
              {d.providerOrderId}: {transaction.providerOrderId || '—'}
            </Typography>
            <Typography variant="body2">
              {unresolved.kind === 'CAPTURE' ? d.unresolvedKindCapture : unresolved.kind === 'REFUND' ? d.unresolvedKindRefund : '—'}
              {': '}{formatCurrency(unresolved.amount, transaction.currency)}
            </Typography>
            <Typography variant="body2">
              {d.unresolvedStartedBy}: {unresolved.startedBy || '—'}
            </Typography>
          </Box>
        )}
        {resolveError && <Alert severity="error" sx={{ mt: 2 }}>{resolveError}</Alert>}
      </ConfirmDialog>
    </Box>
  );
};