import React, { useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import { useNavigate, useParams } from 'react-router';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import { ArrowBack as ArrowBackIcon } from '@mui/icons-material';
import { useLanguage } from '../context/LanguageContext';
import type { EcomOrder, EcomTerminal } from '../types/ecom';
import { ecomTerminalLabel, fetchEcomOrder, fetchEcomTerminals } from '../utils/ecom';
import { formatCurrency } from '../utils/mockData';
import { getStatusColorScheme } from '../utils/statusColors';

// Время операций — до секунд: авторизация и списание нередко идут в одну минуту.
const formatSeconds = (date: Date | null): string => {
  if (!date) return '—';
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${pad(date.getDate())}.${pad(date.getMonth() + 1)}.${String(date.getFullYear()).slice(-2)} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
};

const Field: React.FC<{ label: string; mono?: boolean; children: React.ReactNode }> = ({ label, mono, children }) => (
  <Box
    sx={{
      display: 'grid',
      gridTemplateColumns: { xs: '1fr', sm: '180px 1fr' },
      columnGap: 2,
      py: 1,
      borderBottom: '1px solid',
      borderColor: 'divider',
    }}
  >
    <Typography variant="body2" color="text.secondary">{label}</Typography>
    <Typography
      variant="body2"
      component="div"
      sx={{ fontFamily: mono ? 'monospace' : undefined, fontWeight: mono ? 600 : 400, wordBreak: 'break-word' }}
    >
      {children}
    </Typography>
  </Box>
);

/**
 * Карточка заказа из выписки провайдера: заказ по номеру у провайдера и вся его история операций
 * (`GET /api/v1/ecom/transactions/{orderId}`, `project_docs/ecom.md` §2.7). Своя карточка, а не
 * `/transactions/:id`: там операция портала по нашему UUID, с возвратом и списанием холда; здесь чужой
 * заказ только на чтение, и кнопок, двигающих деньги, у него нет и быть не может.
 */
export const EcommerceOrderDetailPage: React.FC = () => {
  const { orderId = '' } = useParams();
  const navigate = useNavigate();
  const { tObj } = useLanguage();
  const t = tObj.ecommerce;

  const [order, setOrder] = useState<EcomOrder | null>(null);
  const [terminals, setTerminals] = useState<EcomTerminal[]>([]);
  const [state, setState] = useState<'loading' | 'ready' | 'notFound' | 'failed'>('loading');

  useEffect(() => {
    const controller = new AbortController();
    setState('loading');
    fetchEcomOrder(orderId, controller.signal)
      .then(found => {
        setOrder(found);
        setState('ready');
      })
      .catch(err => {
        if (axios.isCancel(err)) return;
        setOrder(null);
        // 404 — заказа нет, он чужой или не завершён: для экрана это одно и то же (ecom.md §2.7).
        setState(axios.isAxiosError(err) && err.response?.status === 404 ? 'notFound' : 'failed');
      });
    fetchEcomTerminals(controller.signal).then(setTerminals).catch(() => setTerminals([]));
    return () => controller.abort();
  }, [orderId]);

  const terminalIndex = useMemo(
    () => new Map(terminals.map(terminal => [terminal.merchantRid, terminal])),
    [terminals]
  );

  const back = (
    <Button startIcon={<ArrowBackIcon />} onClick={() => navigate('/transactions/ecommerce')} sx={{ mb: 2 }}>
      {t.detail.back}
    </Button>
  );

  if (state === 'loading') {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 10 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (state !== 'ready' || !order) {
    return (
      <Box>
        {back}
        <Alert severity={state === 'notFound' ? 'info' : 'error'}>
          {state === 'notFound' ? t.detail.notFound : t.detail.loadFailed}
        </Alert>
      </Box>
    );
  }

  const scheme = getStatusColorScheme(order.status);
  const terminal = ecomTerminalLabel(order, terminalIndex);
  const money = (value: number | null) => (value === null ? '—' : formatCurrency(value, order.currency));

  return (
    <Box>
      {back}

      <Box sx={{ mb: 3, display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
        <Typography variant="h4" sx={{ fontWeight: 700 }}>
          {t.detail.title} <Box component="span" sx={{ fontFamily: 'monospace' }}>{order.orderId}</Box>
        </Typography>
        <Chip
          label={order.status ? t.statuses[order.status] : order.statusRaw || '—'}
          sx={{ bgcolor: scheme.light, color: scheme.contrastText, fontWeight: 700 }}
        />
      </Box>

      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' }, gap: 3, mb: 3 }}>
        <Paper elevation={0} sx={{ p: 3, border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
          <Typography variant="h6" sx={{ mb: 1 }}>{t.detail.identifiers}</Typography>
          <Field label={t.columns.orderId} mono>{order.orderId}</Field>
          <Field label={t.columns.ridByMerchant} mono>{order.ridByMerchant || '—'}</Field>
          <Field label={t.detail.rrn} mono>{order.rrn || '—'}</Field>
          <Field label={t.detail.providerStatus}>
            {order.providerStatus || '—'}
            {order.providerPrevStatus && (
              <Typography component="span" variant="body2" color="text.secondary">
                {' ← '}{order.providerPrevStatus}
              </Typography>
            )}
          </Field>
        </Paper>

        <Paper elevation={0} sx={{ p: 3, border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
          <Typography variant="h6" sx={{ mb: 1 }}>{t.detail.money}</Typography>
          <Field label={t.detail.orderAmount}>{money(order.amount)}</Field>
          <Field label={t.detail.captured}><b>{money(order.capturedAmount)}</b></Field>
          <Field label={t.detail.refunded}>{money(order.refundedAmount)}</Field>
          {order.declineCode && <Field label={t.detail.declineCode} mono>{order.declineCode}</Field>}
        </Paper>

        <Paper elevation={0} sx={{ p: 3, border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
          <Typography variant="h6" sx={{ mb: 1 }}>{t.detail.payment}</Typography>
          <Field label={t.detail.card} mono>{order.cardMask || '—'}</Field>
          <Field label={t.detail.terminal}>
            <Box component="span" sx={{ fontFamily: 'monospace', fontWeight: 600 }}>{terminal.label}</Box>
            {terminal.subLabel && (
              <Typography component="span" variant="body2" color="text.secondary">{` — ${terminal.subLabel}`}</Typography>
            )}
          </Field>
          <Field label={t.detail.createdAt}>{formatSeconds(order.createdAt)}</Field>
          <Field label={t.detail.lastOperationAt}>{formatSeconds(order.lastOperationAt)}</Field>
          {order.description && <Field label={t.detail.description}>{order.description}</Field>}
        </Paper>
      </Box>

      <Paper elevation={2}>
        <Typography variant="h6" sx={{ p: 2.5, pb: 1 }}>{t.detail.operations}</Typography>
        <TableContainer>
          <Table sx={{ minWidth: 960 }}>
            <TableHead>
              <TableRow sx={{ bgcolor: 'action.hover' }}>
                <TableCell>{t.detail.operationAt}</TableCell>
                <TableCell>{t.detail.kind}</TableCell>
                <TableCell>{t.detail.codes}</TableCell>
                <TableCell>{t.detail.result}</TableCell>
                <TableCell align="right">{t.detail.amount}</TableCell>
                <TableCell align="right">{t.detail.clearAmount}</TableCell>
                <TableCell>{t.detail.rrn}</TableCell>
                <TableCell>{t.detail.tranId}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {order.operations.map((operation, index) => {
                const approved = operation.resultCode === 'Approved';
                const currency = operation.currency ?? order.currency;
                return (
                  <TableRow key={operation.tranId ?? index}>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatSeconds(operation.at)}</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>{t.operationKinds[operation.kind]}</TableCell>
                    {/* Сырые коды провайдера рядом с разбором: словарь не утверждён, расхождение должно быть видно. */}
                    <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.8rem', color: 'text.secondary' }}>
                      {[operation.type, operation.phase, operation.voidKind].filter(Boolean).join(' / ') || '—'}
                    </TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={operation.resultCode || '—'}
                        color={approved ? 'success' : 'error'}
                        variant={approved ? 'outlined' : 'filled'}
                        sx={{ fontWeight: 600 }}
                      />
                    </TableCell>
                    <TableCell align="right">
                      {operation.amount === null ? '—' : formatCurrency(operation.amount, currency)}
                    </TableCell>
                    <TableCell
                      align="right"
                      sx={{ fontWeight: 600, color: (operation.clearAmount ?? 0) < 0 ? 'error.main' : undefined }}
                    >
                      {operation.clearAmount === null ? '—' : formatCurrency(operation.clearAmount, currency)}
                    </TableCell>
                    <TableCell sx={{ fontFamily: 'monospace' }}>{operation.rrn || '—'}</TableCell>
                    <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{operation.tranId || '—'}</TableCell>
                  </TableRow>
                );
              })}
              {order.operations.length === 0 && (
                <TableRow>
                  <TableCell colSpan={8} align="center" sx={{ py: 4 }}>
                    <Typography color="text.secondary">{t.detail.noOperations}</Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>
    </Box>
  );
};
