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
  Switch,
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
} from '@mui/material';
import {
  Business as BusinessIcon,
  Add as AddIcon,
  Delete as DeleteIcon,
  Refresh as RefreshIcon,
  CheckCircle as CheckCircleIcon,
  Block as BlockIcon,
  Search as SearchIcon,
} from '@mui/icons-material';

import { useLanguage } from '../context/LanguageContext';
import type { TranslationDictionary } from '../i18n/translations';
import type { CompanyDto } from '../types/dto';

/** Действие, ждущее подтверждения. Пока оно не подтверждено, на сервер ничего не уходит. */
type PendingAction =
  | { kind: 'delete'; company: CompanyDto }
  | { kind: 'status'; company: CompanyDto; nextStatus: 'ACTIVE' | 'INACTIVE' };

export const CompaniesPage: React.FC = () => {
  const { user } = useAuth();
  const { tObj } = useLanguage();
  // Кто видит страницу — решает RoleRoute (auth/routeAccess.ts: SYSTEM_ADMIN и AUDITOR по матрице
  // AGENTS.md §6). Здесь isAdmin только прячет запись: POST/PATCH/DELETE /companies — только SYSTEM_ADMIN,
  // и бэкенд это проверяет сам; UI лишь не показывает кнопки, которые вернут 403.
  const isAdmin = user?.role === 'SYSTEM_ADMIN';

  const [companies, setCompanies] = useState<CompanyDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);
  const [form, setForm] = useState({ id: '', name: '' });
  const [error, setError] = useState('');
  const [snackbar, setSnackbar] = useState('');
  // Ни удаление, ни смена статуса не выполняются по клику: сначала окно подтверждения.
  // Удаление компании к тому же необратимо из портала — updateCompany на удалённой отвечает
  // «Company not found», воскресить её через API нечем.
  const [pending, setPending] = useState<PendingAction | null>(null);
  const [confirmBusy, setConfirmBusy] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  // Поиск — серверный (P3-1): клиентский фильтр видел только текущую страницу. 300 мс задержки,
  // чтобы не слать запрос на каждую букву.
  const debouncedSearch = useDebounced(searchQuery, 300);

  // Страница берётся с сервера (P2-1): `/api/v1/companies` отвечает `PagedResponse`.
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
      // Гонка ответов: устаревший запрос отменён эффектом ниже, его исход не трогает экран.
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

  const applyStatus = async (company: CompanyDto, newStatus: 'ACTIVE' | 'INACTIVE') => {
    try {
      await apiClient.patch(`/api/v1/companies/${company.id}`, { name: company.name, status: newStatus });
      setCompanies(prev => prev.map(c => c.id === company.id ? { ...c, status: newStatus } : c));
      setSnackbar(`Company status updated to ${newStatus}`);
    } catch (err: any) {
      setSnackbar(err.response?.data?.message || 'Failed to update company status');
    }
  };

  const handleCreate = async () => {
    if (!form.id.trim() || !form.name.trim()) {
      setError('Company ID and Name are required');
      return;
    }
    setError('');
    try {
      await apiClient.post('/api/v1/companies', {
        id: form.id.trim(),
        name: form.name.trim(),
      });
      // Не дописываем строку в массив: список постраничный и отсортирован сервером по имени —
      // новая компания может принадлежать другой странице.
      fetchCompanies();
      setCreateOpen(false);
      setForm({ id: '', name: '' });
      setSnackbar('Company created successfully');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to create company');
    }
  };

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

  // Единственное место, откуда действие уходит на сервер. Кнопка заблокирована на время
  // запроса: второй клик по «Удалить» иначе ушёл бы вторым DELETE.
  const runPending = async () => {
    if (!pending || confirmBusy) return;
    setConfirmBusy(true);
    try {
      if (pending.kind === 'delete') {
        await applyDelete(pending.company);
      } else {
        await applyStatus(pending.company, pending.nextStatus);
      }
    } finally {
      setConfirmBusy(false);
      setPending(null);
    }
  };

  return (
    <Box>
      {/* Header */}
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
            <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreateOpen(true)}>
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

      {/* Search Bar */}
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

      {/* Table */}
      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, overflow: 'hidden' }}>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow sx={{ bgcolor: 'rgba(0,0,0,0.02)' }}>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.companies.companyId}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.companies.name}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.companies.status}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.common.actions}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.common.date}</TableCell>
                <TableCell sx={{ fontWeight: 700 }} align="center">{tObj.common.actions}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {companies.map((comp) => {
                const isActive = comp.status === 'ACTIVE' || !comp.status;
                return (
                  <TableRow key={comp.id} hover>
                    <TableCell sx={{ fontFamily: 'monospace', fontWeight: 700, color: 'primary.main' }}>
                      {comp.id}
                    </TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>{comp.name}</TableCell>
                    <TableCell>
                      <Chip
                        icon={isActive ? <CheckCircleIcon fontSize="small" /> : <BlockIcon fontSize="small" />}
                        label={isActive ? tObj.common.active : tObj.common.inactive}
                        color={isActive ? 'success' : 'default'}
                        size="small"
                        sx={{ fontWeight: 600 }}
                      />
                    </TableCell>
                    <TableCell>
                      <Tooltip title={isActive ? tObj.common.inactive : tObj.common.active}>
                        <Switch
                          checked={isActive}
                          onChange={() => setPending({
                            kind: 'status',
                            company: comp,
                            nextStatus: isActive ? 'INACTIVE' : 'ACTIVE',
                          })}
                          color="success"
                          disabled={!isAdmin}
                        />
                      </Tooltip>
                    </TableCell>
                    <TableCell sx={{ fontSize: '0.85rem', color: 'text.secondary' }}>
                      {comp.createdAt ? new Date(comp.createdAt).toLocaleString() : 'N/A'}
                    </TableCell>
                    <TableCell align="center">
                      {isAdmin && (
                        <Tooltip title={tObj.common.delete}>
                          <IconButton color="error" size="small" onClick={() => setPending({ kind: 'delete', company: comp })}>
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
                  <TableCell colSpan={6} align="center" sx={{ py: 6 }}>
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

      {/* Create Dialog */}
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
          </Stack>
        </DialogContent>
        <DialogActions sx={{ p: 2.5 }}>
          <Button onClick={() => setCreateOpen(false)}>{tObj.common.cancel}</Button>
          <Button variant="contained" onClick={handleCreate}>{tObj.common.create}</Button>
        </DialogActions>
      </Dialog>

      {/* Confirm Dialog: удаление и смена статуса — одно окно на два действия,
          заголовок, вопрос, цвет и надпись кнопки считаются из `pending.kind`. */}
      <ConfirmDialog
        open={pending !== null}
        title={confirmTitle(tObj, pending)}
        question={confirmQuestion(tObj, pending)}
        confirmLabel={pending?.kind === 'delete' ? tObj.common.delete : tObj.common.confirm}
        confirmColor={pending?.kind === 'delete' ? 'error' : 'primary'}
        busy={confirmBusy}
        onConfirm={runPending}
        onCancel={() => setPending(null)}
      >
        {/* Что именно сейчас изменится — прямо в окне: подтверждать «компанию» вслепую
            значит подтверждать не глядя. */}
        {pending && (
          <Box sx={{ mt: 2, p: 2, borderRadius: 1, border: '1px solid', borderColor: 'divider', bgcolor: 'action.hover' }}>
            <Typography variant="body2" sx={{ fontWeight: 700 }}>{pending.company.name}</Typography>
            <Typography variant="body2" sx={{ fontFamily: 'monospace', color: 'text.secondary' }}>
              {pending.company.id}
            </Typography>
          </Box>
        )}
        {pending?.kind === 'delete' && (
          <Alert severity="warning" sx={{ mt: 2 }}>{tObj.companies.deleteIrreversible}</Alert>
        )}
      </ConfirmDialog>
    </Box>
  );
};

// Заголовок и вопрос — три разных случая, и путать их нельзя: снятие пометки «активна» и
// удаление отличаются последствиями настолько, что общий текст был бы вреднее отсутствия окна.
function confirmTitle(tObj: TranslationDictionary, pending: PendingAction | null): string {
  if (!pending) return '';
  if (pending.kind === 'delete') return tObj.companies.deleteTitle;
  return pending.nextStatus === 'ACTIVE' ? tObj.companies.activateTitle : tObj.companies.deactivateTitle;
}

function confirmQuestion(tObj: TranslationDictionary, pending: PendingAction | null): string {
  if (!pending) return '';
  if (pending.kind === 'delete') return tObj.companies.deleteQuestion;
  return pending.nextStatus === 'ACTIVE' ? tObj.companies.activateQuestion : tObj.companies.deactivateQuestion;
}
