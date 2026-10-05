import React, { useState } from 'react';
import axios from 'axios';
import { Alert, AlertTitle, Box, Button, Paper, Stack, TextField, Tooltip, Typography } from '@mui/material';
import { Cancel as CancelIcon, DoneAll as CompleteIcon } from '@mui/icons-material';
import { apiClient } from '../api/client';
import { useLanguage } from '../context/LanguageContext';
import type { MoneyAction, TransactionActions } from '../types/transaction';
import { formatCurrency, formatDateTime } from '../utils/format';
import { readMoneyOperationFailure, type MoneyOperationFailure } from '../utils/moneyOperationError';
import { ConfirmDialog } from './ConfirmDialog';

interface MoneyActionsPanelProps {
  /** Кнопки, как их решил сервер (Р-123, Р-124): своих правил здесь нет. */
  actions: TransactionActions;
  currency: string;
  /** Что именно двигают — рамкой в окнах подтверждения (P3-5a). */
  identifiers: { label: string; value: string }[];
  /**
   * Адрес операции: пути возврата, списания и итога (refund, complete, resolve-outcome) от него одинаковы у операции портала
   * (`/api/v1/transactions/{id}`) и у заказа выписки (`/api/v1/ecom/transactions/{orderId}`).
   */
  baseUrl: string;
  /** Перечитать владельца после действия и после отказа: кнопки, суммы и плашка — с сервера. */
  onChanged: () => Promise<void> | void;
  /** Пояснение над кнопками. */
  description?: React.ReactNode;
}

/**
 * Возврат, списание холда и итог неподтверждённой операции (Р-123, Р-125) — для карточки операции портала и
 * панели заказа выписки. Выключенная кнопка говорит причину; сумма — потолок с сервера: возвращается весь
 * остаток, списывается вся авторизованная сумма.
 */
export const MoneyActionsPanel: React.FC<MoneyActionsPanelProps> = ({
  actions,
  currency,
  identifiers,
  baseUrl,
  onChanged,
  description,
}) => {
  const { tObj } = useLanguage();
  const d = tObj.transactions.detail;

  const [dialog, setDialog] = useState<'refund' | 'capture' | null>(null);
  // Причина возврата — необязательная, только в журнал аудита (Р-126); бэкенд принимает до 255 символов.
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState<'refund' | 'capture' | null>(null);
  // Отказ остаётся в окне — туда смотрит мерчант (Р-61); после 502 повтор из этого окна закрыт сразу.
  const [failure, setFailure] = useState<MoneyOperationFailure | null>(null);
  const [resolveTarget, setResolveTarget] = useState<boolean | null>(null);
  const [resolveBusy, setResolveBusy] = useState(false);
  const [resolveError, setResolveError] = useState<string | null>(null);

  const { refund, capture, unresolved } = actions;
  const outcomeUnknown = failure?.outcome === 'unknown';
  const reasonOf = (action: MoneyAction): string => (action.reason ? d.moneyReasons[action.reason] : d.moneyReasons.other);
  const kindOf = (kind: string | null): string =>
    kind === 'CAPTURE' ? d.unresolvedKindCapture : kind === 'REFUND' ? d.unresolvedKindRefund : '—';

  if (!refund && !capture && !unresolved) {
    return null;
  }

  const run = async (kind: 'refund' | 'capture') => {
    const action = kind === 'refund' ? refund : capture;
    if (!action?.enabled) return;
    setFailure(null);
    setBusy(true);
    try {
      const url = kind === 'refund' ? `${baseUrl}/refund` : `${baseUrl}/complete`;
      const body = kind === 'refund' && reason.trim() ? { amount: action.maxAmount, reason: reason.trim() } : { amount: action.maxAmount };
      await apiClient.post(url, body);
      setDone(kind);
      setDialog(null);
      setReason('');
    } catch (err: unknown) {
      setFailure(readMoneyOperationFailure(err, kind === 'refund' ? 'Refund failed' : 'Capture failed'));
    } finally {
      setBusy(false);
      await onChanged();
    }
  };

  const resolve = async () => {
    if (resolveTarget === null) return;
    setResolveBusy(true);
    setResolveError(null);
    try {
      await apiClient.post(`${baseUrl}/resolve-outcome`, { executed: resolveTarget });
      setFailure(null);
      setResolveTarget(null);
      await onChanged();
    } catch (err: unknown) {
      const serverMessage = axios.isAxiosError(err) ? err.response?.data?.message : undefined;
      setResolveError(typeof serverMessage === 'string' && serverMessage ? serverMessage : d.resolveFailed);
    } finally {
      setResolveBusy(false);
    }
  };

  const failureNotice = failure && (
    <Alert severity={outcomeUnknown ? 'warning' : 'error'} sx={{ mt: 2 }}>
      {outcomeUnknown && <AlertTitle sx={{ fontWeight: 700 }}>{d.unresolvedTitle}</AlertTitle>}
      {failure.message}
      {outcomeUnknown && ` ${d.unresolvedHint}`}
    </Alert>
  );

  const frame = (amountLabel: string, amount: number | undefined) => (
    <Box sx={{ mt: 2, p: 2, borderRadius: 1, border: '1px solid', borderColor: 'divider', bgcolor: 'action.hover' }}>
      {identifiers.map(item => (
        <Typography key={item.label} variant="body2" sx={{ fontWeight: 600 }}>
          {item.label}: <Box component="span" sx={{ fontFamily: 'monospace' }}>{item.value}</Box>
        </Typography>
      ))}
      <Typography variant="body2">
        {amountLabel}: {amount === undefined ? '—' : formatCurrency(amount, currency)}
      </Typography>
    </Box>
  );

  // Подсказка на выключенной кнопке — через обёртку: выключенная кнопка событий мыши не получает.
  const button = (kind: 'refund' | 'capture', action: MoneyAction) => (
    <Tooltip title={action.enabled ? '' : reasonOf(action)}>
      <span>
        <Button
          variant="outlined"
          color={kind === 'refund' ? 'warning' : 'success'}
          startIcon={kind === 'refund' ? <CancelIcon /> : <CompleteIcon />}
          disabled={!action.enabled || outcomeUnknown}
          onClick={() => setDialog(kind)}
          sx={{ py: 1.5, px: 3 }}
        >
          {kind === 'refund' ? d.refundAction : d.completeAction}
        </Button>
      </span>
    </Tooltip>
  );

  return (
    <Paper elevation={2} sx={{ p: 3, mb: 3, bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 3, flexWrap: 'wrap' }}>
        <Box sx={{ flex: 1, minWidth: 260 }}>
          <Typography variant="h6" sx={{ mb: 1, fontWeight: 600, color: 'text.primary' }}>
            {d.actionsTitle}
          </Typography>
          {description}
          {refund?.enabled && refund.maxAmount !== undefined && (
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
              {d.refundableLeft}:{' '}
              <Box component="span" sx={{ fontWeight: 700, color: 'text.primary' }}>
                {formatCurrency(refund.maxAmount, currency)}
              </Box>
            </Typography>
          )}
          {done && (
            <Alert severity="success" sx={{ mt: 1.5 }} onClose={() => setDone(null)}>
              {done === 'refund' ? d.eventRefunded : d.eventCaptured}
            </Alert>
          )}
          {!dialog && failureNotice}
          {unresolved && (
            <Alert severity="warning" sx={{ mt: 1.5 }}>
              <AlertTitle sx={{ fontWeight: 700 }}>
                {unresolved.state === 'IN_PROGRESS' ? d.unresolvedInProgressTitle : d.unresolvedTitle}
              </AlertTitle>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                {kindOf(unresolved.kind)}: {formatCurrency(unresolved.amount, currency)}
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
          {capture && button('capture', capture)}
          {refund && button('refund', refund)}
        </Stack>
      </Box>

      <ConfirmDialog
        open={dialog === 'capture'}
        title={d.completeTitle}
        question={d.captureExplains}
        confirmLabel={d.confirmCapture}
        confirmColor="success"
        confirmIcon={<CompleteIcon />}
        busy={busy}
        confirmDisabled={outcomeUnknown || !capture?.enabled}
        onConfirm={() => run('capture')}
        onCancel={() => setDialog(null)}
      >
        {frame(d.captureAmount, capture?.maxAmount)}
        {failureNotice}
      </ConfirmDialog>

      <ConfirmDialog
        open={dialog === 'refund'}
        title={d.refundTitle}
        question={d.refundQuestion}
        cancelLabel={d.keepTransaction}
        confirmLabel={d.confirmRefund}
        busy={busy}
        confirmDisabled={outcomeUnknown || !refund?.enabled}
        onConfirm={() => run('refund')}
        onCancel={() => setDialog(null)}
      >
        {frame(d.refundAmount, refund?.maxAmount)}
        <TextField
          label={d.refundReason}
          helperText={d.refundReasonHint}
          value={reason}
          onChange={event => setReason(event.target.value)}
          disabled={busy}
          fullWidth
          multiline
          minRows={2}
          slotProps={{ htmlInput: { maxLength: 255 } }}
          sx={{ mt: 2 }}
        />
        {failureNotice}
      </ConfirmDialog>

      {/* Итог неподтверждённой операции: что именно отмечают — вид, сумма и кто отправил — в рамке. */}
      <ConfirmDialog
        open={resolveTarget !== null && unresolved !== null}
        title={resolveTarget ? d.resolveExecutedTitle : d.resolveNotExecutedTitle}
        question={resolveTarget ? d.resolveExecutedQuestion : d.resolveNotExecutedQuestion}
        confirmLabel={resolveTarget ? d.resolveExecuted : d.resolveNotExecuted}
        confirmColor={resolveTarget ? 'success' : 'warning'}
        busy={resolveBusy}
        onConfirm={resolve}
        onCancel={() => { setResolveTarget(null); setResolveError(null); }}
      >
        {unresolved && frame(kindOf(unresolved.kind), unresolved.amount)}
        {unresolved && (
          <Typography variant="body2" sx={{ mt: 1 }}>
            {d.unresolvedStartedBy}: {unresolved.startedBy || '—'}
          </Typography>
        )}
        {resolveError && <Alert severity="error" sx={{ mt: 2 }}>{resolveError}</Alert>}
      </ConfirmDialog>
    </Paper>
  );
};
