import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import { apiClient } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { useDebounced } from '../hooks/useDebounced';
import { ConfirmDialog } from '../components/ConfirmDialog';
import {
  Box,
  Paper,
  Typography,
  Button,
  TextField,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert,
  Stack,
  TablePagination,
  Tooltip,
  InputAdornment,
  Autocomplete,
  MenuItem,
} from '@mui/material';
import {
  Business as BusinessIcon,
  Add as AddIcon,
  Delete as DeleteIcon,
  Refresh as RefreshIcon,
  CheckCircle as CheckCircleIcon,
  Block as BlockIcon,
  Search as SearchIcon,
  Edit as EditIcon,
  Sync as SyncIcon,
} from '@mui/icons-material';

import { useLanguage } from '../context/LanguageContext';
import type { CompanyDto, ProviderLoginOption, ProviderTerminalSyncOutcome } from '../types/dto';

type CompanyStatus = 'ACTIVE' | 'INACTIVE';

// VÖEN — ровно 10 цифр, зеркало проверки CreateCompanyRequest/UpdateCompanyRequest (Р-129).
const TAX_ID = /^\d{10}$/;

// Статус без значения — активный: так его читают и список, и форма правки.
const statusOf = (company: CompanyDto): CompanyStatus =>
  (company.status === 'ACTIVE' || !company.status ? 'ACTIVE' : 'INACTIVE');

export const CompaniesPage: React.FC = () => {
  const { user } = useAuth();
  const { tObj } = useLanguage();
  // isAdmin только прячет кнопки, которые вернут 403: права проверяет бэкенд, страницу — RoleRoute.
  const isAdmin = user?.role === 'SYSTEM_ADMIN';

  const [companies, setCompanies] = useState<CompanyDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);
  const [form, setForm] = useState({ id: '', name: '', providerLogin: '', providerPassword: '', taxId: '' });
  const [error, setError] = useState('');
  const [snackbar, setSnackbar] = useState('');
  // Удаление необратимо из портала: удалённую компанию API не находит, вернуть её нечем.
  const [pendingDelete, setPendingDelete] = useState<CompanyDto | null>(null);
  const [confirmBusy, setConfirmBusy] = useState(false);
  // Пароль в форме правки пуст: прочитать его нельзя, только заменить (Р-93).
  const [editCompany, setEditCompany] = useState<CompanyDto | null>(null);
  const [editForm, setEditForm] = useState<{ name: string; providerLogin: string; providerPassword: string; taxId: string; status: CompanyStatus }>(
    { name: '', providerLogin: '', providerPassword: '', taxId: '', status: 'ACTIVE' });
  const [editError, setEditError] = useState('');
  const [editConfirm, setEditConfirm] = useState<string[] | null>(null);
  const [editBusy, setEditBusy] = useState(false);
  // Логин сверяется со справочником (Р-94); новый логин провайдера попадёт туда по расписанию
  // или сразу по кнопке синхронизации.
  const [syncing, setSyncing] = useState(false);
  const [syncResult, setSyncResult] = useState<ProviderTerminalSyncOutcome | null>(null);
  const [syncError, setSyncError] = useState('');
  // Логин выбирается из свободных логинов справочника (Р-95); серверная проверка остаётся —
  // логин могут занять или выключить.
  const [loginOptions, setLoginOptions] = useState<ProviderLoginOption[]>([]);
  const [loginOptionsState, setLoginOptionsState] = useState<'idle' | 'loading' | 'ready' | 'failed'>('idle');
  const [searchQuery, setSearchQuery] = useState('');
  // Поиск — серверный: клиентский фильтр видел бы только текущую страницу (P3-1).
  const debouncedSearch = useDebounced(searchQuery, 300);

  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(20);
  const [totalElements, setTotalElements] = useState(0);

  const fetchCompanies = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    try {
      const params: Record<string, unknown> = { page, size: rowsPerPage };
      if (debouncedSearch.trim()) params.search = debouncedSearch.trim();
      const res = await apiClient.get('/api/v1/companies', { params, signal });
      const list = Array.isArray(res.data) ? res.data : (res.data?.content || []);
      setCompanies(list);
      setTotalElements(res.data?.totalElements ?? list.length);
    } catch (err) {
      if (axios.isCancel(err)) return;
      setCompanies([]);
      setTotalElements(0);
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, [page, rowsPerPage, debouncedSearch]);

  // Отмена предыдущего запроса при каждом изменении параметров: без неё ответ на «ив» может
  // прийти позже ответа на «ива» и перезаписать более точный результат.
  useEffect(() => {
    const controller = new AbortController();
    fetchCompanies(controller.signal);
    return () => controller.abort();
  }, [fetchCompanies]);

  // Креды к провайдеру обязательны (Р-93); VÖEN — нет, но если задан, то как проверит бэкенд (Р-129).
  const handleCreate = async () => {
    if (!form.id.trim() || !form.name.trim() || !form.providerLogin.trim() || !form.providerPassword.trim()) {
      setError(tObj.companies.formIncomplete);
      return;
    }
    if (form.taxId.trim() && !TAX_ID.test(form.taxId.trim())) {
      setError(tObj.companies.taxIdInvalid);
      return;
    }
    setError('');
    try {
      await apiClient.post('/api/v1/companies', {
        id: form.id.trim(),
        name: form.name.trim(),
        providerLogin: form.providerLogin.trim(),
        providerPassword: form.providerPassword.trim(),
        taxId: form.taxId.trim() || null,
      });
      // Перечитываем, а не дописываем: новая компания может оказаться на другой странице.
      fetchCompanies();
      setCreateOpen(false);
      setForm({ id: '', name: '', providerLogin: '', providerPassword: '', taxId: '' });
      setSnackbar(tObj.companies.created);
    } catch (err: any) {
      setError(err.response?.data?.message || tObj.companies.createFailed);
    }
  };

  const loadLoginOptions = async () => {
    setLoginOptionsState('loading');
    try {
      const res = await apiClient.get<ProviderLoginOption[]>('/api/v1/companies/provider-logins');
      setLoginOptions(Array.isArray(res.data) ? res.data : []);
      setLoginOptionsState('ready');
    } catch {
      setLoginOptions([]);
      setLoginOptionsState('failed');
    }
  };

  const openEdit = (company: CompanyDto) => {
    setEditCompany(company);
    setEditForm({
      name: company.name ?? '', providerLogin: company.providerLogin ?? '', providerPassword: '',
      taxId: company.taxId ?? '', status: statusOf(company),
    });
    setEditError('');
    setSyncResult(null);
    setSyncError('');
    loadLoginOptions();
  };

  const openCreate = () => {
    setError('');
    setSyncResult(null);
    setSyncError('');
    setForm(f => ({ ...f, providerLogin: '' }));
    loadLoginOptions();
    setCreateOpen(true);
  };

  const syncProviderDirectory = async () => {
    setSyncing(true);
    setSyncResult(null);
    setSyncError('');
    try {
      const res = await apiClient.post<ProviderTerminalSyncOutcome>('/api/v1/ecom/provider-terminals/sync');
      setSyncResult(res.data);
      await loadLoginOptions();
    } catch (err: any) {
      setSyncError(err.response?.data?.message || tObj.terminals.providerTerminalLoadFailed);
    } finally {
      setSyncing(false);
    }
  };

  // current — логин самой компании: среди свободных его нет, но остаться на нём можно.
  const loginPicker = (value: string, onChange: (login: string) => void, current?: string | null) => {
    const options = current && !loginOptions.some(option => option.login === current)
      ? [{ login: current, merchants: [] }, ...loginOptions]
      : loginOptions;
    return (
      <Box>
        <Autocomplete
          options={options}
          value={options.find(option => option.login === value) ?? null}
          loading={loginOptionsState === 'loading'}
          onChange={(_, option) => onChange(option?.login ?? '')}
          getOptionLabel={option => option.login}
          isOptionEqualToValue={(option, selected) => option.login === selected.login}
          renderOption={({ key, ...optionProps }, option) => (
            <li key={key} {...optionProps}>
              <Box>
                <Typography variant="body2" sx={{ fontFamily: 'monospace', fontWeight: 700 }}>{option.login}</Typography>
                <Typography variant="caption" color="text.secondary">
                  {option.merchants.length > 0 ? option.merchants.join(', ') : '—'}
                </Typography>
              </Box>
            </li>
          )}
          renderInput={params => (
            <TextField {...params} label={`${tObj.companies.providerLogin} *`} helperText={tObj.companies.providerLoginHint} />
          )}
        />
        {loginOptionsState === 'failed' && <Alert severity="error" sx={{ mt: 1 }}>{tObj.common.loadFailed}</Alert>}
        {loginOptionsState === 'ready' && options.length === 0 && (
          <Alert severity="info" sx={{ mt: 1 }}>{tObj.companies.providerLoginEmpty}</Alert>
        )}
      </Box>
    );
  };

  const directorySync = (
    <Box>
      <Button size="small" startIcon={<SyncIcon />} disabled={syncing} onClick={syncProviderDirectory}>
        {syncing ? tObj.common.loading : tObj.terminals.syncDirectory}
      </Button>
      {syncResult?.logins && (
        <Alert severity={syncResult.logins.applied ? 'success' : 'warning'} sx={{ mt: 1 }}>
          {syncResult.logins.applied
            ? `${tObj.companies.loginsSyncApplied}: ${syncResult.logins.logins}`
            : `${tObj.companies.loginsSyncSkipped}: ${syncResult.logins.skippedBecause ?? '—'}`}
        </Alert>
      )}
      {syncError && <Alert severity="error" sx={{ mt: 1 }}>{syncError}</Alert>}
    </Box>
  );

  // Только изменившиеся поля; пустой пароль бэкенд читает как «не менять».
  const editPayload = (): Record<string, string> => {
    if (!editCompany) return {};
    const payload: Record<string, string> = {};
    const name = editForm.name.trim();
    const login = editForm.providerLogin.trim();
    if (name !== (editCompany.name ?? '')) payload.name = name;
    if (login && login !== (editCompany.providerLogin ?? '')) payload.providerLogin = login;
    if (editForm.providerPassword.trim()) payload.providerPassword = editForm.providerPassword.trim();
    const taxId = editForm.taxId.trim();
    if (taxId && taxId !== (editCompany.taxId ?? '')) payload.taxId = taxId;
    if (editForm.status !== statusOf(editCompany)) payload.status = editForm.status;
    return payload;
  };

  const statusLabel = (status: CompanyStatus) => (status === 'ACTIVE' ? tObj.common.active : tObj.common.inactive);

  const editChanges = (payload: Record<string, string>): string[] => {
    if (!editCompany) return [];
    const changes: string[] = [];
    if (payload.name !== undefined) changes.push(`${tObj.companies.name}: ${editCompany.name || '—'} → ${payload.name}`);
    if (payload.providerLogin !== undefined) {
      changes.push(`${tObj.companies.providerLogin}: ${editCompany.providerLogin || '—'} → ${payload.providerLogin}`);
    }
    if (payload.providerPassword !== undefined) changes.push(tObj.companies.providerPasswordReplaced);
    if (payload.taxId !== undefined) changes.push(`${tObj.companies.taxId}: ${editCompany.taxId || '—'} → ${payload.taxId}`);
    if (payload.status !== undefined) {
      changes.push(`${tObj.companies.status}: ${statusLabel(statusOf(editCompany))} → ${statusLabel(payload.status as CompanyStatus)}`);
    }
    return changes;
  };

  // Без изменений PATCH не уходит: он всё равно оставил бы запись в журнале аудита.
  const askEdit = () => {
    if (!editCompany) return;
    if (!editForm.name.trim() || (!editForm.providerLogin.trim() && editCompany.providerLogin)) {
      setEditError(tObj.companies.formIncomplete);
      return;
    }
    // Стереть VÖEN бэкенд не даёт (пустое — «не менять»), поэтому пустое поле у заданного — тоже ошибка.
    const taxId = editForm.taxId.trim();
    if (taxId ? !TAX_ID.test(taxId) : Boolean(editCompany.taxId)) {
      setEditError(tObj.companies.taxIdInvalid);
      return;
    }
    setEditError('');
    const changes = editChanges(editPayload());
    if (changes.length === 0) {
      setEditCompany(null);
      setSnackbar(tObj.companies.editNothingChanged);
      return;
    }
    setEditConfirm(changes);
  };

  const saveEdit = async () => {
    if (!editCompany || editBusy) return;
    setEditBusy(true);
    try {
      const res = await apiClient.patch<CompanyDto>(`/api/v1/companies/${editCompany.id}`, editPayload());
      setCompanies(prev => prev.map(c => (c.id === editCompany.id ? res.data : c)));
      setEditCompany(null);
      setSnackbar(tObj.companies.updated);
    } catch (err: any) {
      setEditError(err.response?.data?.message || tObj.companies.updateFailed);
    } finally {
      setEditBusy(false);
      setEditConfirm(null);
    }
  };

  const editPending = editConfirm !== null ? editPayload() : {};
  const credentialsChanging = editPending.providerLogin !== undefined || editPending.providerPassword !== undefined;

  const applyDelete = async (company: CompanyDto) => {
    try {
      await apiClient.delete(`/api/v1/companies/${company.id}`);
      // Перечитываем страницу: после удаления на неё поднимается строка со следующей.
      fetchCompanies();
      setSnackbar('Company deleted successfully');
    } catch (err: any) {
      setSnackbar(err.response?.data?.message || 'Failed to delete company');
    }
  };

  // Единственный путь DELETE на сервер; confirmBusy не пускает второй клик вторым запросом.
  const runDelete = async () => {
    if (!pendingDelete || confirmBusy) return;
    setConfirmBusy(true);
    try {
      await applyDelete(pendingDelete);
    } finally {
      setConfirmBusy(false);
      setPendingDelete(null);
    }
  };

  return (
    <Box>
      <Box sx={{ mb: 4, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 700, mb: 0.5, display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <BusinessIcon color="primary" fontSize="large" /> {tObj.companies.title}
          </Typography>
          <Typography variant="body1" color="text.secondary">
            {tObj.companies.subtitle}
          </Typography>
        </Box>
        <Stack direction="row" spacing={1.5}>
          <Button variant="outlined" startIcon={<RefreshIcon />} onClick={() => fetchCompanies()}>
            {tObj.common.refresh}
          </Button>
          {isAdmin && (
            <Button variant="contained" startIcon={<AddIcon />} onClick={openCreate}>
              {tObj.companies.addCompany}
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
          placeholder={tObj.companies.searchPlaceholder}
          value={searchQuery}
          onChange={e => { setSearchQuery(e.target.value); setPage(0); }}
          sx={{ minWidth: 320, width: { xs: '100%', sm: 400 } }}
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
                <TableCell sx={{ fontWeight: 700 }}>{tObj.companies.companyId}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.companies.name}</TableCell>
                {isAdmin && <TableCell sx={{ fontWeight: 700 }}>{tObj.companies.providerLogin}</TableCell>}
                <TableCell sx={{ fontWeight: 700 }}>{tObj.companies.taxId}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.companies.status}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.common.date}</TableCell>
                <TableCell sx={{ fontWeight: 700 }} align="center">{tObj.common.actions}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {companies.map((comp) => {
                const isActive = statusOf(comp) === 'ACTIVE';
                return (
                  <TableRow key={comp.id} hover>
                    <TableCell sx={{ fontFamily: 'monospace', fontWeight: 700, color: 'primary.main' }}>
                      {comp.id}
                    </TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>{comp.name}</TableCell>
                    {isAdmin && (
                      <TableCell sx={{ fontFamily: 'monospace' }}>{comp.providerLogin || '—'}</TableCell>
                    )}
                    <TableCell sx={{ fontFamily: 'monospace' }}>{comp.taxId || '—'}</TableCell>
                    <TableCell>
                      <Chip
                        icon={isActive ? <CheckCircleIcon fontSize="small" /> : <BlockIcon fontSize="small" />}
                        label={isActive ? tObj.common.active : tObj.common.inactive}
                        color={isActive ? 'success' : 'default'}
                        size="small"
                        sx={{ fontWeight: 600 }}
                      />
                    </TableCell>
                    <TableCell sx={{ fontSize: '0.85rem', color: 'text.secondary' }}>
                      {comp.createdAt ? new Date(comp.createdAt).toLocaleString() : 'N/A'}
                    </TableCell>
                    <TableCell align="center">
                      {isAdmin && (
                        <Tooltip title={tObj.companies.editCompany}>
                          <IconButton color="primary" size="small" onClick={() => openEdit(comp)}>
                            <EditIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      )}
                      {isAdmin && (
                        <Tooltip title={tObj.common.delete}>
                          <IconButton color="error" size="small" onClick={() => setPendingDelete(comp)}>
                            <DeleteIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
              {companies.length === 0 && !loading && (
                <TableRow>
                  <TableCell colSpan={isAdmin ? 7 : 6} align="center" sx={{ py: 6 }}>
                    <BusinessIcon sx={{ fontSize: 48, color: 'text.disabled', mb: 1 }} />
                    <Typography color="text.secondary">No companies found in directory.</Typography>
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

      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>{tObj.companies.createDialogTitle}</DialogTitle>
        <DialogContent>
          {error && <Alert severity="error" sx={{ mb: 2, mt: 1 }}>{error}</Alert>}
          <Stack spacing={2.5} sx={{ mt: 1 }}>
            <TextField
              label={tObj.companies.companyId}
              value={form.id}
              onChange={e => setForm(f => ({ ...f, id: e.target.value }))}
              placeholder="e.g. comp_001"
              fullWidth
            />
            <TextField
              label={tObj.companies.name}
              value={form.name}
              onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
              placeholder="e.g. Acme Supermarket LLC"
              fullWidth
            />
            {loginPicker(form.providerLogin, login => setForm(f => ({ ...f, providerLogin: login })))}
            {directorySync}
            <TextField
              label={`${tObj.companies.providerPassword} *`}
              type="password"
              value={form.providerPassword}
              onChange={e => setForm(f => ({ ...f, providerPassword: e.target.value }))}
              helperText={tObj.companies.providerPasswordHint}
              autoComplete="new-password"
              fullWidth
            />
            <TextField
              label={tObj.companies.taxId}
              value={form.taxId}
              onChange={e => setForm(f => ({ ...f, taxId: e.target.value }))}
              helperText={tObj.companies.taxIdHint}
              slotProps={{ htmlInput: { inputMode: 'numeric', maxLength: 10 } }}
              fullWidth
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ p: 2.5 }}>
          <Button onClick={() => setCreateOpen(false)}>{tObj.common.cancel}</Button>
          <Button variant="contained" onClick={handleCreate}>{tObj.common.create}</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={editCompany !== null} onClose={() => { if (!editBusy) setEditCompany(null); }} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>{tObj.companies.editCompany}: {editCompany?.id}</DialogTitle>
        <DialogContent>
          {editError && <Alert severity="error" sx={{ mb: 2, mt: 1 }}>{editError}</Alert>}
          <Stack spacing={2.5} sx={{ mt: 1 }}>
            <TextField
              label={`${tObj.companies.name} *`}
              value={editForm.name}
              onChange={e => setEditForm(f => ({ ...f, name: e.target.value }))}
              fullWidth
            />
            {loginPicker(editForm.providerLogin,
              login => setEditForm(f => ({ ...f, providerLogin: login })),
              editCompany?.providerLogin)}
            {directorySync}
            <TextField
              label={tObj.companies.newProviderPassword}
              type="password"
              value={editForm.providerPassword}
              onChange={e => setEditForm(f => ({ ...f, providerPassword: e.target.value }))}
              helperText={tObj.companies.newProviderPasswordHint}
              autoComplete="new-password"
              fullWidth
            />
            <TextField
              label={tObj.companies.taxId}
              value={editForm.taxId}
              onChange={e => setEditForm(f => ({ ...f, taxId: e.target.value }))}
              helperText={tObj.companies.taxIdHint}
              slotProps={{ htmlInput: { inputMode: 'numeric', maxLength: 10 } }}
              fullWidth
            />
            <TextField
              select
              label={tObj.companies.status}
              value={editForm.status}
              onChange={e => setEditForm(f => ({ ...f, status: e.target.value === 'INACTIVE' ? 'INACTIVE' : 'ACTIVE' }))}
              helperText={tObj.companies.statusWarning}
              fullWidth
            >
              <MenuItem value="ACTIVE">{tObj.common.active}</MenuItem>
              <MenuItem value="INACTIVE">{tObj.common.inactive}</MenuItem>
            </TextField>
          </Stack>
        </DialogContent>
        <DialogActions sx={{ p: 2.5 }}>
          <Button onClick={() => setEditCompany(null)} disabled={editBusy}>{tObj.common.cancel}</Button>
          <Button variant="contained" onClick={askEdit} disabled={editBusy}>{tObj.common.save}</Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={editConfirm !== null}
        title={tObj.companies.editConfirmTitle}
        question={tObj.companies.editConfirmQuestion}
        confirmLabel={tObj.common.confirm}
        confirmColor="primary"
        busy={editBusy}
        onConfirm={saveEdit}
        onCancel={() => setEditConfirm(null)}
      >
        {editCompany && (
          <Box sx={{ mt: 2, p: 2, borderRadius: 1, border: '1px solid', borderColor: 'divider', bgcolor: 'action.hover' }}>
            <Typography variant="body2" sx={{ fontWeight: 700 }}>{editCompany.name}</Typography>
            <Typography variant="body2" sx={{ fontFamily: 'monospace', color: 'text.secondary', mb: 1 }}>
              {editCompany.id}
            </Typography>
            <Stack spacing={1}>
              {(editConfirm ?? []).map(change => (
                <Typography key={change} variant="body2">{change}</Typography>
              ))}
            </Stack>
          </Box>
        )}
        {credentialsChanging && <Alert severity="warning" sx={{ mt: 2 }}>{tObj.companies.credentialsWarning}</Alert>}
        {editPending.status !== undefined && <Alert severity="info" sx={{ mt: 2 }}>{tObj.companies.statusWarning}</Alert>}
      </ConfirmDialog>

      <ConfirmDialog
        open={pendingDelete !== null}
        title={tObj.companies.deleteTitle}
        question={tObj.companies.deleteQuestion}
        confirmLabel={tObj.common.delete}
        confirmColor="error"
        busy={confirmBusy}
        onConfirm={runDelete}
        onCancel={() => setPendingDelete(null)}
      >
        {pendingDelete && (
          <Box sx={{ mt: 2, p: 2, borderRadius: 1, border: '1px solid', borderColor: 'divider', bgcolor: 'action.hover' }}>
            <Typography variant="body2" sx={{ fontWeight: 700 }}>{pendingDelete.name}</Typography>
            <Typography variant="body2" sx={{ fontFamily: 'monospace', color: 'text.secondary' }}>
              {pendingDelete.id}
            </Typography>
          </Box>
        )}
        <Alert severity="warning" sx={{ mt: 2 }}>{tObj.companies.deleteIrreversible}</Alert>
      </ConfirmDialog>
    </Box>
  );
};

