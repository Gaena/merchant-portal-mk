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
  Stack,
  CircularProgress
} from '@mui/material';
import {
  ArrowBack as ArrowBackIcon,
  CreditCard as CreditCardIcon,
  Person as PersonIcon,
  Email as EmailIcon,
  Receipt as ReceiptIcon,
  CalendarToday as CalendarIcon,
  Description as DescriptionIcon,
  Refresh as RefreshIcon,
  Security as SecurityIcon,
  Laptop as LaptopIcon,
} from '@mui/icons-material';
import type { StatusHistoryEntry } from '../types/transaction';
import { formatCurrency, formatDateTime, getPaymentMethodLabel } from '../utils/format';
import { getStatusColorScheme } from '../utils/statusColors';
import { buildTerminalIndex, terminalLabel, terminalSubLabel } from '../utils/terminals';
import { mapTransaction } from '../utils/mapTransaction';
import type { TerminalOptionDto } from '../types/dto';

import { useLanguage } from '../context/LanguageContext';
import { statusLabel, type TranslationDictionary } from '../i18n/translations';
import { MoneyActionsPanel } from '../components/MoneyActionsPanel';

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

  const [checkingStatus, setCheckingStatus] = useState(false);
  const [statusChecked, setStatusChecked] = useState(false);
  /** Сбой самой проверки статуса. Не исход денежной операции — `actionError` не трогает. */
  const [checkError, setCheckError] = useState<string | null>(null);
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

  const refundedSoFar = Number(transaction.refundedAmount ?? 0);

  return (
    <Box sx={{ p: 4 }}>
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

      {/* Возврат и списание — как решил сервер (Р-123): выключенная кнопка говорит почему. */}
      {transaction.actions && (
        <MoneyActionsPanel
          actions={transaction.actions}
          currency={transaction.currency}
          identifiers={[
            { label: d.providerOrderId, value: transaction.providerOrderId || '—' },
            { label: d.ridByMerchant, value: transaction.ridByMerchant || '—' },
          ]}
          baseUrl={`/api/v1/transactions/${transaction.id}`}
          onChanged={reload}
          description={transaction.actions.capture?.enabled ? (
            <Typography variant="body2" color="text.secondary">
              This DMS transaction has funds authorized on the customer's card. Complete it to capture the funds, or cancel to release the hold.
            </Typography>
          ) : transaction.actions.refund?.enabled ? (
            <Typography variant="body2" color="text.secondary">
              Cancel this transaction and initiate a refund to the customer. The amount will be reversed within 3-5 business days.
            </Typography>
          ) : undefined}
        />
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

    </Box>
  );
};