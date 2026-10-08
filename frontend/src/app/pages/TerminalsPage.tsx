import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import { apiClient } from '../api/client';
import { useDebounced } from '../hooks/useDebounced';
import { ConfirmDialog } from '../components/ConfirmDialog';
import {
  Box,
  Paper,
  Typography,
  Button,
  TextField,
  MenuItem,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert,
  Stack,
  TablePagination,
  InputAdornment,
  Autocomplete,
  FormControlLabel,
  Switch,
} from '@mui/material';
import {
  PointOfSale as POSIcon,
  Add as AddIcon,
  Block as BlockIcon,
  PlayArrow as UnblockIcon,
  Refresh as RefreshIcon,
  Business as BusinessIcon,
  Search as SearchIcon,
  Sync as SyncIcon,
} from '@mui/icons-material';
import { useLanguage } from '../context/LanguageContext';
import { useAuth } from '../context/AuthContext';
import { canCreateTerminals, canWriteTerminals } from '../auth/actionAccess';
import {
  checkExistingTerminal,
  checkSeverity,
  type TerminalCheckOutcome,
  type TerminalCheckResponse,
} from '../utils/terminalCheck';
import { isTerminalActive } from '../types/dto';
import { terminalLabel } from '../utils/terminals';
import type {
  TerminalDto,
  CompanyDto,
  TerminalStatus,
  ProviderTerminalOption,
  ProviderTerminalSyncOutcome,
} from '../types/dto';

// Подпись — правилом `utils/terminals.ts`: номер терминала у провайдера (Р-96), у заведённых до него — логин.
const terminalDtoLabel = (terminal: TerminalDto): string =>
  terminalLabel({ terminalRid: terminal.terminalRid, terminalLogin: terminal.login });

export const TerminalsPage: React.FC = () => {
  const { tObj } = useLanguage();
  const { user } = useAuth();
  // Справочник провайдера и «Тест» — только администратору; заводит терминалы тоже он (Р-80, Р-93).
  const isAdmin = user?.role === 'SYSTEM_ADMIN';
  const canCreate = canCreateTerminals(user?.role);
  // Кнопки, которым сервер откажет, не показываются (Р-62): роли — `TerminalService.TERMINAL_WRITE_ROLES`.
  const canWrite = canWriteTerminals(user?.role);
  // Компанию выбирает только SYSTEM_ADMIN; остальным список компаний — 403, их компания — из токена.
  const choosesCompany = user?.role === 'SYSTEM_ADMIN';
  const ownCompanyId = user?.companyId ?? '';

  const checkLabel = (outcome: TerminalCheckOutcome): string => ({
    OK: tObj.terminals.checkOk,
    INVALID_CREDENTIALS: tObj.terminals.checkInvalid,
    REJECTED: tObj.terminals.checkRejected,
    UNREACHABLE: tObj.terminals.checkUnreachable,
  })[outcome];
  const [terminals, setTerminals] = useState<TerminalDto[]>([]);
  const [companies, setCompanies] = useState<CompanyDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [editingTerminalId, setEditingTerminalId] = useState<number | null>(null);
  // dmsAllowed — разрешены ли DMS-ссылки (Р-132); меняет только SYSTEM_ADMIN.
  const [form, setForm] = useState({ name: '', companyId: '', dmsAllowed: true });
  /** Ошибка внутри открытого окна (заведение, правка); «Тест» и смена статуса — тоже из окна правки. */
  const [error, setError] = useState('');
  const [snackbar, setSnackbar] = useState('');
  const [creating, setCreating] = useState(false);
  const [editBusy, setEditBusy] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [checks, setChecks] = useState<Record<number, TerminalCheckResponse>>({});
  const [checking, setChecking] = useState<number | null>(null);
  // Справочник терминалов провайдера для формы заведения (Р-67, Р-79).
  const [providerTerminals, setProviderTerminals] = useState<ProviderTerminalOption[]>([]);
  const [providerState, setProviderState] = useState<'idle' | 'loading' | 'ready' | 'failed'>('idle');
  const [selectedProvider, setSelectedProvider] = useState<ProviderTerminalOption | null>(null);
  const [syncing, setSyncing] = useState(false);
  const [syncResult, setSyncResult] = useState<ProviderTerminalSyncOutcome | null>(null);
  // Поиск — серверный: клиентский фильтр видел бы только текущую страницу (P3-1).
  const debouncedSearch = useDebounced(searchQuery, 300);
  // `affectedLinks === null` — счёт ещё идёт или не удался. Разблокировка спрашивает наравне
  // с блокировкой: она тоже трогает чужие ссылки.
  const [statusChange, setStatusChange] = useState<
    { terminal: TerminalDto; nextStatus: TerminalStatus; affectedLinks: number | null } | null>(null);
  // Правка — только после подтверждения со списком изменений: форма меняет и компанию-владельца.
  const [editConfirm, setEditConfirm] = useState<string[] | null>(null);
  const [busy, setBusy] = useState(false);

  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(20);
  const [totalElements, setTotalElements] = useState(0);

  const fetchTerminals = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    // Итоги «Теста» сбрасываются: на новой странице те же строки — уже другие терминалы.
    setChecks({});
    try {
      const params: Record<string, unknown> = { page, size: rowsPerPage };
      if (debouncedSearch.trim()) params.search = debouncedSearch.trim();
      const res = await apiClient.get('/api/v1/terminals', { params, signal });
      const list = Array.isArray(res.data) ? res.data : (res.data?.content || []);
      setTerminals(list);
      setTotalElements(res.data?.totalElements ?? list.length);
    } catch (err) {
      if (axios.isCancel(err)) return;
      setTerminals([]);
      setTotalElements(0);
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, [page, rowsPerPage, debouncedSearch]);

  const fetchCompanies = useCallback(async () => {
    try {
      if (choosesCompany || user?.role === 'AUDITOR') {
        // Компании нужны для списка и подписей — одной страницей по потолку размера на сервере (200).
        const res = await apiClient.get('/api/v1/companies', { params: { page: 0, size: 200 } });
        const list = Array.isArray(res.data) ? res.data : (res.data?.content || []);
        setCompanies(Array.isArray(list) ? list : []);
      } else if (ownCompanyId) {
        // Своя компания — одиночным GET: он открыт всем ролям компании (AGENTS.md §6).
        const res = await apiClient.get(`/api/v1/companies/${ownCompanyId}`);
        setCompanies(res.data ? [res.data] : []);
      } else {
        setCompanies([]);
      }
    } catch {
      setCompanies([]);
    }
  }, [choosesCompany, ownCompanyId, user?.role]);

  const defaultCompanyId = () => (choosesCompany ? (companies[0]?.id ?? '') : ownCompanyId);

  // Отмена предыдущего запроса при каждом изменении параметров: без неё ответ на «ив» может
  // прийти позже ответа на «ива» и перезаписать более точный результат.
  useEffect(() => {
    const controller = new AbortController();
    fetchTerminals(controller.signal);
    return () => controller.abort();
  }, [fetchTerminals]);

  useEffect(() => {
    fetchCompanies();
  }, [fetchCompanies]);

  // Только мерчанты логина выбранной компании, ещё не заведённые у нас (Р-96); перечитывается при
  // смене компании.
  const loadProviderTerminals = async (companyId: string) => {
    setSelectedProvider(null);
    if (!companyId) {
      setProviderTerminals([]);
      setProviderState('idle');
      return;
    }
    setProviderState('loading');
    try {
      const res = await apiClient.get<ProviderTerminalOption[]>('/api/v1/terminals/provider-terminals', {
        params: { companyId },
      });
      setProviderTerminals(Array.isArray(res.data) ? res.data : []);
      setProviderState('ready');
    } catch {
      setProviderTerminals([]);
      setProviderState('failed');
    }
  };

  // Обновление справочника вне расписания — для терминала, только что заведённого у провайдера.
  const handleSyncDirectory = async () => {
    setSyncing(true);
    setSyncResult(null);
    try {
      const res = await apiClient.post<ProviderTerminalSyncOutcome>('/api/v1/ecom/provider-terminals/sync');
      setSyncResult(res.data);
      await loadProviderTerminals(form.companyId);
    } catch (err: any) {
      setError(err.response?.data?.message || tObj.terminals.providerTerminalLoadFailed);
    } finally {
      setSyncing(false);
    }
  };

  const handleOpenCreate = () => {
    setError('');
    setEditingTerminalId(null);
    const companyId = defaultCompanyId();
    setForm({ name: '', companyId, dmsAllowed: true });
    setSyncResult(null);
    loadProviderTerminals(companyId);
    setCreateOpen(true);
  };

  const handleOpenEdit = (term: TerminalDto) => {
    setError('');
    setEditingTerminalId(term.id);
    // Компания — та, что у терминала: первая из списка молча перевесила бы его при правке названия.
    setForm({
      name: term.name || '',
      companyId: term.companyId || '',
      dmsAllowed: term.dmsAllowed !== false,
    });
    setEditOpen(true);
  };

  // Название и логин сервер берёт из справочника по merchantRid; пароля у терминала нет (Р-93).
  const handleCreate = async () => {
    if (creating) return;
    if (!selectedProvider || !form.companyId) {
      setError(tObj.terminals.formIncomplete);
      return;
    }
    setError('');
    setCreating(true);
    try {
      await apiClient.post('/api/v1/terminals', {
        companyId: form.companyId,
        merchantRid: selectedProvider.rid,
        dmsAllowed: form.dmsAllowed,
      });
      // Перечитываем, а не дописываем: новый терминал может оказаться на другой странице.
      fetchTerminals();
      setCreateOpen(false);
      setForm({ name: '', companyId: defaultCompanyId(), dmsAllowed: true });
      setSnackbar(tObj.terminals.created);
    } catch (err: any) {
      setError(err.response?.data?.message || tObj.terminals.createFailed);
    } finally {
      setCreating(false);
    }
  };

  // «Тест» — пробный заказ с кредами компании (Р-93): исход всегда один из четырёх, даже при неверных
  // кредах или недоступном провайдере; ошибка — только отказ самого портала.
  const runCheck = async (terminalId: number) => {
    setChecking(terminalId);
    setError('');
    try {
      const result = await checkExistingTerminal(terminalId);
      setChecks(prev => ({ ...prev, [terminalId]: result }));
    } catch (err: any) {
      setError(err.response?.data?.message || tObj.terminals.checkFailed);
    } finally {
      setChecking(null);
    }
  };

  // Переключатель DMS — окна заведения и правки; заводит и меняет DMS только SYSTEM_ADMIN (Р-132).
  const dmsSwitch = (
    <Box>
      <FormControlLabel
        control={<Switch checked={form.dmsAllowed} onChange={e => setForm(f => ({ ...f, dmsAllowed: e.target.checked }))} />}
        label={tObj.terminals.dmsSwitch}
      />
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
        {tObj.terminals.dmsSwitchHint}
      </Typography>
    </Box>
  );

  // В заголовках окон — та же подпись; внутренний номер терминала не показывается (Р-81).
  const editingTerminal = terminals.find(t => t.id === editingTerminalId);
  const editingLogin = editingTerminal ? terminalDtoLabel(editingTerminal) : '';

  const handleUpdate = async () => {
    if (!editingTerminalId || editBusy) return;
    const original = terminals.find(t => t.id === editingTerminalId);
    if (!original) return;
    setError('');
    setEditBusy(true);
    try {
      // Только изменившееся: PATCH с прежними значениями пишет в журнал «Name changed from X to X».
      const payload: Record<string, string | boolean> = {};
      if (form.name.trim() !== (original.name || '')) payload.name = form.name.trim();
      if (form.companyId !== (original.companyId || '')) payload.companyId = form.companyId;
      if (isAdmin && form.dmsAllowed !== (original.dmsAllowed !== false)) payload.dmsAllowed = form.dmsAllowed;
      const res = await apiClient.patch(`/api/v1/terminals/${editingTerminalId}`, payload);
      setTerminals(prev => prev.map(t => (t.id === editingTerminalId ? res.data : t)));
      setEditOpen(false);
      setEditingTerminalId(null);
      setForm({ name: '', companyId: defaultCompanyId(), dmsAllowed: true });
      setSnackbar(tObj.terminals.updated);
    } catch (err: any) {
      setError(err.response?.data?.message || tObj.terminals.updateFailed);
    } finally {
      setEditBusy(false);
      setEditConfirm(null);
    }
  };

  // Окно называет, скольких ссылок коснётся смена статуса: `totalElements` из `pbl` — активных перед
  // блокировкой, приостановленных перед разблокировкой.
  const handleAskStatus = async (terminal: TerminalDto, nextStatus: TerminalStatus) => {
    setStatusChange({ terminal, nextStatus, affectedLinks: null });
    // Число дописывается в окно, только если оно всё ещё открыто про тот же терминал: ответ,
    // пришедший после «Отмена» (или после подтверждения), иначе открывал бы окно заново.
    const applyCount = (affectedLinks: number | null) =>
      setStatusChange(prev => (prev && prev.terminal.id === terminal.id && prev.nextStatus === nextStatus
        ? { ...prev, affectedLinks }
        : prev));
    try {
      const res = await apiClient.get('/api/v1/payment-links', {
        params: { terminal: terminal.id, status: nextStatus === 'BLOCKED' ? 'ACTIVE' : 'SUSPENDED', size: 1 },
      });
      const total = res.data?.totalElements;
      applyCount(typeof total === 'number' ? total : null);
    } catch {
      // Счёт — справка, а не условие: не смогли посчитать, диалог всё равно показывает, что делает.
      applyCount(null);
    }
  };

  const dmsLabel = (allowed: boolean) => (allowed ? tObj.terminals.dmsAllowed : tObj.terminals.dmsForbidden);

  // Пустой список — PATCH не уходит: даже пустой он оставил бы запись в журнале аудита.
  const pendingEditChanges = (): string[] => {
    const original = terminals.find(t => t.id === editingTerminalId);
    if (!original) return [];
    const changes: string[] = [];
    if (form.name.trim() !== (original.name || '')) {
      changes.push(`${tObj.terminals.name}: ${original.name || '—'} → ${form.name.trim()}`);
    }
    if (form.companyId !== (original.companyId || '')) {
      changes.push(`${tObj.terminals.company}: ${getCompanyName(original.companyId || '')} → ${getCompanyName(form.companyId)}`);
    }
    if (isAdmin && form.dmsAllowed !== (original.dmsAllowed !== false)) {
      changes.push(`${tObj.terminals.dmsColumn}: ${dmsLabel(original.dmsAllowed !== false)} → ${dmsLabel(form.dmsAllowed)}`);
    }
    return changes;
  };

  const handleAskUpdate = () => {
    if (!form.name.trim() || !form.companyId) {
      setError(tObj.terminals.formIncomplete);
      return;
    }
    setError('');
    const changes = pendingEditChanges();
    if (changes.length === 0) {
      setEditOpen(false);
      setSnackbar(tObj.terminals.editNothingChanged);
      return;
    }
    setEditConfirm(changes);
  };

  const setTerminalStatus = async (terminal: TerminalDto, status: 'ACTIVE' | 'BLOCKED') => {
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      const res = await apiClient.patch(`/api/v1/terminals/${terminal.id}`, { status });
      setTerminals(prev => prev.map(t => (t.id === terminal.id ? res.data : t)));
      setSnackbar(`${terminalDtoLabel(terminal)}: ${status === 'BLOCKED' ? tObj.terminals.blockedNotice : tObj.terminals.unblockedNotice}`);
    } catch (err: any) {
      setError(err.response?.data?.message || tObj.terminals.statusChangeFailed);
    } finally {
      setBusy(false);
      setStatusChange(null);
    }
  };

  const getCompanyName = (companyId: string) => {
    const comp = companies.find(c => c.id === companyId);
    return comp ? comp.name : companyId;
  };

  return (
    <Box>
      <Box sx={{ mb: 4, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 700, mb: 0.5, display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <POSIcon color="primary" fontSize="large" /> {tObj.terminals.title}
          </Typography>
          <Typography variant="body1" color="text.secondary">
            {tObj.terminals.subtitle}
          </Typography>
        </Box>
        <Stack direction="row" spacing={1.5}>
          <Button variant="outlined" startIcon={<RefreshIcon />} onClick={() => fetchTerminals()}>
            {tObj.common.refresh}
          </Button>
          {canCreate && (
            <Button variant="contained" startIcon={<AddIcon />} onClick={handleOpenCreate}>
              {tObj.terminals.addTerminal}
            </Button>
          )}
        </Stack>
      </Box>

      {snackbar && (
        <Alert severity="info" sx={{ mb: 3 }} onClose={() => setSnackbar('')}>
          {snackbar}
        </Alert>
      )}

      <Paper elevation={0} sx={{ p: 2, mb: 3, border: '1px solid', borderColor: 'divider' }}>
        <TextField
          size="small"
          placeholder={tObj.terminals.searchPlaceholder}
          value={searchQuery}
          onChange={e => { setSearchQuery(e.target.value); setPage(0); }}
          sx={{ minWidth: 320, width: { xs: '100%', sm: 420 } }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" />
              </InputAdornment>
            ),
          }}
        />
      </Paper>

      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, overflow: 'hidden' }}>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow sx={{ bgcolor: 'rgba(0,0,0,0.02)' }}>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.terminals.terminal}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.terminals.name}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.terminals.company}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.common.status}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.terminals.dmsColumn}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.common.date}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {terminals.map((term) => {
                const active = isTerminalActive(term);
                return (
                // Правка, «Тест» и блокировка — в окне по клику на строку, только тем, чьи изменения сервер примет (Р-62).
                <TableRow
                  key={term.id}
                  hover={canWrite}
                  onClick={canWrite ? () => handleOpenEdit(term) : undefined}
                  onKeyDown={canWrite ? e => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      handleOpenEdit(term);
                    }
                  } : undefined}
                  tabIndex={canWrite ? 0 : undefined}
                  title={canWrite ? tObj.terminals.editTerminal : undefined}
                  sx={{ ...(canWrite ? { cursor: 'pointer' } : {}), ...(active ? {} : { opacity: 0.6 }) }}
                >
                  <TableCell sx={{ fontFamily: 'monospace', fontWeight: 700, color: active ? 'primary.main' : 'text.disabled' }}>
                    {terminalDtoLabel(term)}
                  </TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{term.name}</TableCell>
                  <TableCell>
                    <Chip
                      icon={<BusinessIcon fontSize="small" />}
                      label={getCompanyName(term.companyId)}
                      variant="outlined"
                      size="small"
                      sx={{ fontWeight: 600 }}
                    />
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={active ? tObj.terminals.statuses.ACTIVE : tObj.terminals.statuses.BLOCKED}
                      size="small"
                      color={active ? 'success' : 'warning'}
                      variant={active ? 'outlined' : 'filled'}
                      sx={{ fontWeight: 600 }}
                    />
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={dmsLabel(term.dmsAllowed !== false)}
                      size="small"
                      color={term.dmsAllowed !== false ? 'default' : 'warning'}
                      variant="outlined"
                    />
                  </TableCell>
                  <TableCell sx={{ fontSize: '0.85rem', color: 'text.secondary' }}>
                    {term.createdAt ? new Date(term.createdAt).toLocaleString() : '—'}
                  </TableCell>
                </TableRow>
                );
              })}
              {terminals.length === 0 && !loading && (
                <TableRow>
                  <TableCell colSpan={6} align="center" sx={{ py: 6 }}>
                    <POSIcon sx={{ fontSize: 48, color: 'text.disabled', mb: 1 }} />
                    <Typography color="text.secondary">{tObj.terminals.empty}</Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          rowsPerPageOptions={[10, 20, 50, 100]}
          component="div"
          count={totalElements}
          rowsPerPage={rowsPerPage}
          page={page}
          onPageChange={(_, p) => setPage(p)}
          onRowsPerPageChange={e => { setRowsPerPage(parseInt(e.target.value, 10)); setPage(0); }}
        />
      </Paper>

      <ConfirmDialog
        open={statusChange !== null}
        maxWidth="xs"
        title={<>
          {statusChange?.nextStatus === 'BLOCKED' ? tObj.terminals.blockAction : tObj.terminals.unblockAction}
          {' '}{statusChange ? terminalDtoLabel(statusChange.terminal) : ''}
        </>}
        question={statusChange?.nextStatus === 'BLOCKED' ? tObj.terminals.blockExplains : tObj.terminals.unblockExplains}
        confirmLabel={statusChange?.nextStatus === 'BLOCKED' ? tObj.terminals.blockAction : tObj.terminals.unblockAction}
        confirmColor={statusChange?.nextStatus === 'BLOCKED' ? 'warning' : 'success'}
        busy={busy}
        onConfirm={() => statusChange && setTerminalStatus(statusChange.terminal, statusChange.nextStatus)}
        onCancel={() => setStatusChange(null)}
      >
        <Alert severity={statusChange?.affectedLinks ? 'warning' : 'info'} sx={{ mt: 2 }}>
          {statusChange?.affectedLinks === null
            ? tObj.terminals.blockLinksUnknown
            : `${statusChange?.nextStatus === 'BLOCKED'
                ? tObj.terminals.blockLinksAffected
                : tObj.terminals.unblockLinksAffected}: ${statusChange?.affectedLinks ?? 0}`}
        </Alert>
      </ConfirmDialog>

      <Dialog open={createOpen} onClose={() => { if (!creating) setCreateOpen(false); }} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>{tObj.terminals.createDialogTitle}</DialogTitle>
        <DialogContent>
          {error && <Alert severity="error" sx={{ mb: 2, mt: 1 }}>{error}</Alert>}
          <Stack spacing={2.5} sx={{ mt: 1 }}>
            {choosesCompany && (
              <TextField
                select
                fullWidth
                label={`${tObj.terminals.company} *`}
                value={form.companyId}
                onChange={e => {
                  const companyId = e.target.value;
                  setForm(f => ({ ...f, companyId }));
                  loadProviderTerminals(companyId);
                }}
                helperText={tObj.terminals.companyHint}
              >
                {companies.map((comp) => (
                  <MenuItem key={comp.id} value={comp.id}>
                    {comp.name} (ID: {comp.id})
                  </MenuItem>
                ))}
              </TextField>
            )}

            <Box>
              {/* Один терминал провайдера — одна компания: занятый сервер отклонит с объяснением. */}
              <Autocomplete
                options={providerTerminals}
                value={selectedProvider}
                loading={providerState === 'loading'}
                onChange={(_, value) => setSelectedProvider(value)}
                getOptionLabel={option => [option.terminalRid, option.title].filter(Boolean).join(' — ') || option.rid}
                isOptionEqualToValue={(option, value) => option.rid === value.rid}
                renderOption={({ key, ...optionProps }, option) => (
                  <li key={key} {...optionProps}>
                    <Box>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace', fontWeight: 700 }}>
                        {option.terminalRid}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {option.title || '—'} · {option.rid}
                      </Typography>
                    </Box>
                  </li>
                )}
                renderInput={params => (
                  <TextField
                    {...params}
                    label={`${tObj.terminals.providerTerminal} *`}
                    helperText={tObj.terminals.providerTerminalHint}
                  />
                )}
              />
              {selectedProvider && (
                <Box sx={{ mt: 1.5, p: 1.5, borderRadius: 1, bgcolor: 'action.hover' }}>
                  <Typography variant="body2">
                    {tObj.terminals.name}: <b>{selectedProvider.title || '—'}</b>
                  </Typography>
                  <Typography variant="body2">
                    {tObj.terminals.terminal}: <b style={{ fontFamily: 'monospace' }}>{selectedProvider.terminalRid}</b>
                  </Typography>
                </Box>
              )}
              {providerState === 'failed' && (
                <Alert severity="error" sx={{ mt: 1.5 }}>{tObj.terminals.providerTerminalLoadFailed}</Alert>
              )}
              {providerState === 'ready' && providerTerminals.length === 0 && (
                <Alert severity="info" sx={{ mt: 1.5 }}>{tObj.terminals.providerTerminalEmpty}</Alert>
              )}
              {syncResult && (
                <Alert severity={syncResult.applied ? 'success' : 'warning'} sx={{ mt: 1.5 }}>
                  {syncResult.applied
                    ? `${tObj.terminals.syncApplied}: ${syncResult.seen}`
                    : `${tObj.terminals.syncSkipped}: ${syncResult.skippedBecause ?? '—'}`}
                </Alert>
              )}
              <Button
                size="small"
                startIcon={<SyncIcon />}
                disabled={syncing}
                onClick={handleSyncDirectory}
                sx={{ mt: 1 }}
              >
                {syncing ? tObj.common.loading : tObj.terminals.syncDirectory}
              </Button>
            </Box>
            {dmsSwitch}
          </Stack>
        </DialogContent>
        <DialogActions sx={{ p: 2.5 }}>
          <Button onClick={() => setCreateOpen(false)} disabled={creating}>{tObj.common.cancel}</Button>
          <Button variant="contained" onClick={handleCreate} disabled={creating}>
            {creating ? tObj.common.loading : tObj.terminals.registerAction}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={editOpen} onClose={() => { if (!editBusy) setEditOpen(false); }} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>{tObj.terminals.editDialogTitle} {editingLogin}</DialogTitle>
        <DialogContent>
          {error && <Alert severity="error" sx={{ mb: 2, mt: 1 }}>{error}</Alert>}
          <Stack spacing={2.5} sx={{ mt: 1 }}>
            {/* Перенос в другую компанию — только SYSTEM_ADMIN, остальным сервер откажет. */}
            {choosesCompany && (
              <TextField
                select
                fullWidth
                label={`${tObj.terminals.company} *`}
                value={form.companyId}
                onChange={e => setForm(f => ({ ...f, companyId: e.target.value }))}
                helperText={tObj.terminals.companyHint}
              >
                {companies.map((comp) => (
                  <MenuItem key={comp.id} value={comp.id}>
                    {comp.name} (ID: {comp.id})
                  </MenuItem>
                ))}
              </TextField>
            )}

            <TextField
              label={`${tObj.terminals.name} *`}
              value={form.name}
              onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
              disabled={editingTerminal?.providerLinked === true}
              helperText={editingTerminal?.providerLinked === true ? tObj.terminals.nameFromProvider : undefined}
              fullWidth
            />
            {isAdmin && dmsSwitch}
            {/* «Тест» — пробный заказ с кредами компании (Р-70, Р-93), только администратору; итог — здесь же. */}
            {isAdmin && editingTerminal && (
              <Box>
                <Button
                  variant="outlined"
                  size="small"
                  disabled={checking === editingTerminal.id}
                  onClick={() => runCheck(editingTerminal.id)}
                >
                  {checking === editingTerminal.id ? tObj.common.loading : tObj.terminals.testAction}
                </Button>
                {checks[editingTerminal.id] && (
                  <Alert severity={checkSeverity(checks[editingTerminal.id].outcome)} sx={{ mt: 1.5 }}>
                    {checkLabel(checks[editingTerminal.id].outcome)}
                    {checks[editingTerminal.id].message ? ` — ${checks[editingTerminal.id].message}` : ''}
                  </Alert>
                )}
              </Box>
            )}
          </Stack>
        </DialogContent>
        <DialogActions sx={{ p: 2.5 }}>
          {/* Блокировка — из окна правки, со своим подтверждением (Р-37): число затронутых ссылок — в нём. */}
          {editingTerminal && (isTerminalActive(editingTerminal) ? (
            <Button color="warning" startIcon={<BlockIcon />} disabled={editBusy || busy}
                    onClick={() => handleAskStatus(editingTerminal, 'BLOCKED')} sx={{ mr: 'auto' }}>
              {tObj.terminals.blockAction}
            </Button>
          ) : (
            <Button color="success" startIcon={<UnblockIcon />} disabled={editBusy || busy}
                    onClick={() => handleAskStatus(editingTerminal, 'ACTIVE')} sx={{ mr: 'auto' }}>
              {tObj.terminals.unblockAction}
            </Button>
          ))}
          <Button onClick={() => setEditOpen(false)} disabled={editBusy}>{tObj.common.cancel}</Button>
          <Button variant="contained" onClick={handleAskUpdate} disabled={editBusy}>{tObj.common.save}</Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={editConfirm !== null}
        title={<>{tObj.terminals.editConfirmTitle} {editingLogin}</>}
        question={tObj.terminals.editConfirmQuestion}
        confirmLabel={tObj.common.confirm}
        confirmColor="primary"
        busy={editBusy}
        onConfirm={handleUpdate}
        onCancel={() => setEditConfirm(null)}
      >
        <Box sx={{ mt: 2, p: 2, borderRadius: 1, border: '1px solid', borderColor: 'divider', bgcolor: 'action.hover' }}>
          <Stack spacing={1}>
            {(editConfirm ?? []).map(change => (
              <Typography key={change} variant="body2">{change}</Typography>
            ))}
          </Stack>
        </Box>
      </ConfirmDialog>
    </Box>
  );
};

