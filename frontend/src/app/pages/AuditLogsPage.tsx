import React, { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Typography,
  Paper,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
  MenuItem,
  Stack,
  InputAdornment,
  CircularProgress,
  TablePagination
} from '@mui/material';
import {
  History as HistoryIcon,
  Search as SearchIcon,
  Refresh as RefreshIcon
} from '@mui/icons-material';
import axios from 'axios';
import { useNavigate } from 'react-router';
import { apiClient } from '../api/client';
import { useLanguage } from '../context/LanguageContext';
import { useDebounced } from '../hooks/useDebounced';

import type { AuditLogDto } from '../types/dto';

/**
 * Цвет результата: отказ — ошибка, неподтверждённая эквайером операция — предупреждение (P3-2:
 * такая запись не имеет права выглядеть обычным успехом), успех — зелёный.
 */
const outcomeColor = (outcome?: string): 'success' | 'error' | 'warning' | 'default' => {
  switch (outcome) {
    case 'SUCCESS': return 'success';
    case 'DENIED': return 'error';
    case 'UNRESOLVED': return 'warning';
    default: return 'default';
  }
};

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// Одна строка карточки: подпись слева, значение справа; на узком экране — одно под другим.
const DetailRow: React.FC<{ label: string; mono?: boolean; children: React.ReactNode }> = ({ label, mono, children }) => (
  <Box
    sx={{
      display: 'grid',
      gridTemplateColumns: { xs: '1fr', sm: '170px 1fr' },
      columnGap: 2,
      rowGap: 0.25,
      py: 1,
      borderBottom: '1px solid',
      borderColor: 'divider',
    }}
  >
    <Typography variant="body2" color="text.secondary">{label}</Typography>
    <Typography variant="body2" component="div" sx={{ fontFamily: mono ? 'monospace' : undefined, wordBreak: 'break-word' }}>
      {children}
    </Typography>
  </Box>
);

export const AuditLogsPage: React.FC = () => {
  const { tObj } = useLanguage();
  const navigate = useNavigate();
  const [auditLogsList, setAuditLogsList] = useState<AuditLogDto[]>([]);
  // Карточка записи. Всё, что в ней показано, уже пришло строкой списка, поэтому отдельного
  // запроса нет: окно лишь показывает запись целиком — детали в таблице бывают длинными.
  const [selectedLog, setSelectedLog] = useState<AuditLogDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [entityTypeFilter, setEntityTypeFilter] = useState('all');
  const [outcomeFilter, setOutcomeFilter] = useState('all');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  // Поиск и фильтры — серверные (P3-1): журнал больше не обрезается первыми 200 записями,
  // клиентская фильтрация видела бы только загруженную страницу. 300 мс задержки на ввод.
  const debouncedSearch = useDebounced(searchQuery, 300);

  // Пагинация как на остальных страницах (P3-1): раньше её не было вовсе, страница тянула
  // size=200 и за двухсотой записью журнал молча заканчивался.
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(20);
  const [totalElements, setTotalElements] = useState(0);

  const outcomeLabels: Record<string, string> = {
    SUCCESS: tObj.auditLogs.outcomeSuccess,
    DENIED: tObj.auditLogs.outcomeDenied,
    UNRESOLVED: tObj.auditLogs.outcomeUnresolved,
  };

  // Переход к объекту записи — только там, где у объекта есть своя карточка: операция и
  // платёжная ссылка (их entityId — UUID). У терминалов, компаний и пользователей карточек нет.
  const selectedTarget = (() => {
    const entityId = selectedLog?.entityId;
    if (!entityId || !UUID_PATTERN.test(entityId)) return null;
    if (selectedLog?.entityType === 'TRANSACTION') {
      return { path: `/transactions/${entityId}`, label: tObj.auditLogs.openTransaction };
    }
    if (selectedLog?.entityType === 'PAYMENT_LINK') {
      return { path: `/pay-by-link/${entityId}`, label: tObj.auditLogs.openPaymentLink };
    }
    return null;
  })();

  const fetchAuditLogs = useCallback((signal?: AbortSignal) => {
    setLoading(true);
    const params: Record<string, unknown> = { page, size: rowsPerPage };
    if (debouncedSearch.trim()) params.search = debouncedSearch.trim();
    if (entityTypeFilter !== 'all') params.entityType = entityTypeFilter;
    if (outcomeFilter !== 'all') params.outcome = outcomeFilter;
    // Дата из пикера — локальный день пользователя; границы дня переводятся в instant, чтобы
    // событие в 01:00 по Баку не выпало из «своего» дня из-за UTC.
    if (fromDate) params.from = new Date(`${fromDate}T00:00:00`).toISOString();
    if (toDate) params.to = new Date(`${toDate}T23:59:59.999`).toISOString();
    apiClient.get('/api/v1/audit-logs', { params, signal })
      .then(res => {
        setAuditLogsList(Array.isArray(res.data?.content) ? res.data.content : []);
        setTotalElements(res.data?.totalElements ?? 0);
      })
      .catch(err => {
        // Гонка ответов: устаревший запрос отменён эффектом ниже, его исход не трогает экран.
        if (axios.isCancel(err)) return;
        setAuditLogsList([]);
        setTotalElements(0);
      })
      .finally(() => {
        if (!signal?.aborted) setLoading(false);
      });
  }, [page, rowsPerPage, debouncedSearch, entityTypeFilter, outcomeFilter, fromDate, toDate]);

  // Отмена предыдущего запроса при каждом изменении параметров: без неё ответ на «ив» может
  // прийти позже ответа на «ива» и перезаписать более точный результат. «Обновить» идёт тем же
  // путём (через `reloadKey`), иначе его ответ без сигнала перекрывал бы ответ на новый фильтр.
  const [reloadKey, setReloadKey] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    fetchAuditLogs(controller.signal);
    return () => controller.abort();
  }, [fetchAuditLogs, reloadKey]);

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 4, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 600, mb: 0.5, display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <HistoryIcon color="primary" fontSize="large" />
            {tObj.auditLogs.title}
          </Typography>
          <Typography variant="body1" color="text.secondary">
            {tObj.auditLogs.subtitle}
          </Typography>
        </Box>
        <Button variant="outlined" startIcon={<RefreshIcon />} onClick={() => setReloadKey(k => k + 1)}>
          {tObj.common.refresh}
        </Button>
      </Box>

      {/* Filters Bar */}
      <Paper elevation={0} sx={{ p: 2, mb: 3, border: '1px solid', borderColor: 'divider', display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <TextField
          size="small"
          placeholder={tObj.auditLogs.searchPlaceholder}
          value={searchQuery}
          onChange={e => { setSearchQuery(e.target.value); setPage(0); }}
          sx={{ minWidth: 320 }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" />
              </InputAdornment>
            ),
          }}
        />
        <TextField
          select
          size="small"
          label={tObj.auditLogs.filterEntity}
          value={entityTypeFilter}
          onChange={e => { setEntityTypeFilter(e.target.value); setPage(0); }}
          sx={{ minWidth: 200 }}
        >
          <MenuItem value="all">{tObj.common.all}</MenuItem>
          <MenuItem value="COMPANY">{tObj.companies.title}</MenuItem>
          <MenuItem value="TERMINAL">{tObj.terminals.title}</MenuItem>
          <MenuItem value="USER">{tObj.users.title}</MenuItem>
          {/* AUTH — входы, блокировки после неудач, лимит по IP, кража refresh-токена: то, ради
              чего аудитор открывает эту страницу. AUDIT_LOG — отказы в чтении самого журнала. */}
          <MenuItem value="AUTH">{tObj.auditLogs.entityAuth}</MenuItem>
          <MenuItem value="PAYMENT_LINK">{tObj.payByLink.title}</MenuItem>
          <MenuItem value="TRANSACTION">{tObj.transactions.title}</MenuItem>
          <MenuItem value="AUDIT_LOG">{tObj.auditLogs.entityAuditLog}</MenuItem>
        </TextField>
        <TextField
          select
          size="small"
          label={tObj.auditLogs.filterOutcome}
          value={outcomeFilter}
          onChange={e => { setOutcomeFilter(e.target.value); setPage(0); }}
          sx={{ minWidth: 180 }}
        >
          <MenuItem value="all">{tObj.common.all}</MenuItem>
          <MenuItem value="SUCCESS">{tObj.auditLogs.outcomeSuccess}</MenuItem>
          <MenuItem value="DENIED">{tObj.auditLogs.outcomeDenied}</MenuItem>
          <MenuItem value="UNRESOLVED">{tObj.auditLogs.outcomeUnresolved}</MenuItem>
        </TextField>
        <TextField
          size="small"
          type="date"
          label={tObj.auditLogs.dateFrom}
          value={fromDate}
          onChange={e => { setFromDate(e.target.value); setPage(0); }}
          InputLabelProps={{ shrink: true }}
          sx={{ minWidth: 170 }}
        />
        <TextField
          size="small"
          type="date"
          label={tObj.auditLogs.dateTo}
          value={toDate}
          onChange={e => { setToDate(e.target.value); setPage(0); }}
          InputLabelProps={{ shrink: true }}
          sx={{ minWidth: 170 }}
        />
      </Paper>

      {/* Logs Table */}
      <TableContainer component={Paper} variant="outlined">
        {loading ? (
          <Box sx={{ p: 6, textAlign: 'center' }}>
            <CircularProgress />
          </Box>
        ) : (
          <Table>
            <TableHead>
              <TableRow sx={{ bgcolor: 'action.hover' }}>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.auditLogs.timestamp}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.auditLogs.action}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.auditLogs.outcome}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.auditLogs.user}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.auditLogs.ip}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.auditLogs.resource}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.auditLogs.entityId}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.common.details}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {auditLogsList.map((log: AuditLogDto) => (
                <TableRow
                  key={log.id || Math.random()}
                  hover
                  tabIndex={0}
                  onClick={() => setSelectedLog(log)}
                  onKeyDown={e => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      setSelectedLog(log);
                    }
                  }}
                  sx={{ cursor: 'pointer' }}
                >
                  <TableCell sx={{ fontSize: '0.8rem', whiteSpace: 'nowrap' }}>
                    {log.createdAt ? new Date(log.createdAt).toLocaleString() : 'N/A'}
                  </TableCell>
                  <TableCell>
                    <Chip label={log.action} size="small" color="info" variant="outlined" />
                  </TableCell>
                  <TableCell>
                    {/* Отказ не имеет права выглядеть как успех — до P3-1 колонки не было, и на
                        экране DENIED был неотличим от обычной записи. */}
                    <Chip
                      label={outcomeLabels[log.outcome as string] || log.outcome || '—'}
                      size="small"
                      color={outcomeColor(log.outcome)}
                      variant={log.outcome === 'SUCCESS' ? 'outlined' : 'filled'}
                    />
                  </TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{log.performedBy || 'System'}</TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{log.clientIp || '—'}</TableCell>
                  <TableCell>{log.entityType || '—'}</TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{log.entityId || '—'}</TableCell>
                  <TableCell sx={{ fontSize: '0.8rem', color: 'text.secondary' }}>{log.details || '—'}</TableCell>
                </TableRow>
              ))}
              {auditLogsList.length === 0 && (
                <TableRow>
                  <TableCell colSpan={8} align="center" sx={{ py: 6 }}>
                    <Typography color="text.secondary">No audit logs recorded yet.</Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        )}
        <TablePagination
          rowsPerPageOptions={[10, 20, 50, 100]}
          component="div"
          count={totalElements}
          rowsPerPage={rowsPerPage}
          page={page}
          onPageChange={(_, p) => setPage(p)}
          onRowsPerPageChange={e => { setRowsPerPage(parseInt(e.target.value, 10)); setPage(0); }}
        />
      </TableContainer>

      <Dialog open={selectedLog !== null} onClose={() => setSelectedLog(null)} maxWidth="sm" fullWidth>
        {selectedLog && (
          <>
            <DialogTitle sx={{ fontWeight: 700 }}>{tObj.auditLogs.detailsTitle}</DialogTitle>
            <DialogContent dividers>
              <Stack direction="row" spacing={1} sx={{ mb: 1 }}>
                <Chip label={selectedLog.action} size="small" color="info" variant="outlined" />
                <Chip
                  label={outcomeLabels[selectedLog.outcome as string] || selectedLog.outcome || '—'}
                  size="small"
                  color={outcomeColor(selectedLog.outcome)}
                  variant={selectedLog.outcome === 'SUCCESS' ? 'outlined' : 'filled'}
                />
              </Stack>
              <DetailRow label={tObj.auditLogs.timestamp}>
                {selectedLog.createdAt ? (
                  <>
                    {new Date(selectedLog.createdAt).toLocaleString()}
                    <Typography component="span" variant="caption" color="text.secondary" sx={{ display: 'block', fontFamily: 'monospace' }}>
                      {selectedLog.createdAt}
                    </Typography>
                  </>
                ) : '—'}
              </DetailRow>
              <DetailRow label={tObj.auditLogs.user}>{selectedLog.performedBy || 'System'}</DetailRow>
              <DetailRow label={tObj.auditLogs.ip} mono>{selectedLog.clientIp || '—'}</DetailRow>
              <DetailRow label={tObj.auditLogs.company} mono>{selectedLog.companyId || '—'}</DetailRow>
              <DetailRow label={tObj.auditLogs.resource}>{selectedLog.entityType || '—'}</DetailRow>
              <DetailRow label={tObj.auditLogs.entityId} mono>{selectedLog.entityId || '—'}</DetailRow>
              <DetailRow label={tObj.auditLogs.recordId} mono>{selectedLog.id ?? '—'}</DetailRow>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 2, mb: 1 }}>
                {tObj.common.details}
              </Typography>
              <Box
                sx={{
                  p: 1.5,
                  bgcolor: 'action.hover',
                  borderRadius: 1,
                  fontFamily: 'monospace',
                  fontSize: '0.8rem',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                }}
              >
                {selectedLog.details || '—'}
              </Box>
            </DialogContent>
            <DialogActions>
              {selectedTarget && (
                <Button onClick={() => navigate(selectedTarget.path)}>{selectedTarget.label}</Button>
              )}
              <Button variant="contained" onClick={() => setSelectedLog(null)}>{tObj.common.close}</Button>
            </DialogActions>
          </>
        )}
      </Dialog>
    </Box>
  );
};
