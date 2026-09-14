import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
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
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  MenuItem,
  Stack,
  Alert,
  InputAdornment,
  CircularProgress,
  TablePagination,
  Tooltip
} from '@mui/material';
import {
  Add as AddIcon,
  Delete as DeleteIcon,
  Search as SearchIcon,
  Refresh as RefreshIcon,
  Group as GroupIcon
} from '@mui/icons-material';
import { apiClient } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { useLanguage } from '../context/LanguageContext';
import { useDebounced } from '../hooks/useDebounced';
import { ConfirmDialog } from '../components/ConfirmDialog';

import type { UserDto, CompanyDto } from '../types/dto';

export const UsersPage: React.FC = () => {
  const { user: currentUser } = useAuth();
  const { tObj } = useLanguage();
  /**
   * Компанию нового пользователя выбирает только SYSTEM_ADMIN. COMPANY_HEAD заводит людей в свою
   * (`UserService.validateCreatePermission`), и она известна из токена; список всех компаний ему
   * недоступен (`GET /companies` — 403), так что раньше селект оставался пустым, `companyId`
   * уходил пустым, и сервер отвечал «Cannot create user for another company».
   */
  const isAdmin = currentUser?.role === 'SYSTEM_ADMIN';
  const ownCompanyId = currentUser?.companyId ?? '';
  const [usersList, setUsersList] = useState<UserDto[]>([]);
  // Удаление уходит на сервер только после подтверждения: оно мягкое, но необратимое из
  // портала (updateUser на удалённом отвечает «User not found») и гасит все сессии сразу.
  const [pendingDelete, setPendingDelete] = useState<UserDto | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [snackbar, setSnackbar] = useState('');
  const [companiesList, setCompaniesList] = useState<CompanyDto[]>([]);
  const [loading, setLoading] = useState(true);

  // Dialog & Form
  const [userDialogOpen, setUserDialogOpen] = useState(false);
  const [userForm, setUserForm] = useState({
    username: '',
    password: '',
    fullName: '',
    role: 'COMPANY_HEAD',
    companyId: ''
  });
  const [userError, setUserError] = useState('');
  const [creating, setCreating] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [roleFilter, setRoleFilter] = useState('all');
  // Поиск и фильтр по роли — серверные (P3-1): клиентский фильтр видел только текущую страницу.
  // 300 мс задержки, чтобы не слать запрос на каждую букву.
  const debouncedSearch = useDebounced(searchQuery, 300);

  // Страница берётся с сервера (P2-1): `/api/v1/users` отвечает `PagedResponse`, а не массивом.
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(20);
  const [totalElements, setTotalElements] = useState(0);

  const fetchUsers = useCallback((signal?: AbortSignal) => {
    setLoading(true);
    const params: Record<string, unknown> = { page, size: rowsPerPage };
    if (debouncedSearch.trim()) params.search = debouncedSearch.trim();
    if (roleFilter !== 'all') params.role = roleFilter;
    apiClient.get('/api/v1/users', { params, signal })
      .then(res => {
        const content = Array.isArray(res.data) ? res.data : (res.data?.content || []);
        setUsersList(Array.isArray(content) ? content : []);
        setTotalElements(res.data?.totalElements ?? content.length);
      })
      .catch(err => {
        // Гонка ответов: устаревший запрос отменён эффектом ниже, его исход не трогает экран.
        if (axios.isCancel(err)) return;
        setUsersList([]);
        setTotalElements(0);
      })
      .finally(() => {
        if (!signal?.aborted) setLoading(false);
      });
  }, [page, rowsPerPage, debouncedSearch, roleFilter]);

  // Отмена предыдущего запроса при каждом изменении параметров: без неё ответ на «ив» может
  // прийти позже ответа на «ива» и перезаписать более точный результат.
  useEffect(() => {
    const controller = new AbortController();
    fetchUsers(controller.signal);
    return () => controller.abort();
  }, [fetchUsers]);

  useEffect(() => {
    if (isAdmin) {
      // Компании нужны для выпадающего списка и подписей, поэтому берём их одной страницей
      // по потолку (200 — тот же лимит, что у журнала аудита).
      apiClient.get('/api/v1/companies', { params: { page: 0, size: 200 } })
        .then(res => {
          const content = Array.isArray(res.data) ? res.data : (res.data?.content || []);
          if (Array.isArray(content)) {
            setCompaniesList(content);
            if (content.length > 0) {
              setUserForm(f => ({ ...f, companyId: content[0].id }));
            }
          }
        })
        .catch(() => {});
      return;
    }
    // Своя компания — одиночным GET, он открыт всем ролям компании (AGENTS.md §6): нужна для
    // подписи в таблице. В форму она подставляется при отправке.
    if (ownCompanyId) {
      apiClient.get(`/api/v1/companies/${ownCompanyId}`)
        .then(res => { if (res.data) setCompaniesList([res.data]); })
        .catch(() => {});
    }
  }, [isAdmin, ownCompanyId]);

  const handleOpenCreate = () => {
    setUserError('');
    setUserDialogOpen(true);
  };

  const handleCreateUser = async () => {
    if (creating) return;
    setUserError('');
    if (!userForm.username || !userForm.password || !userForm.fullName) {
      setUserError(tObj.users.formIncomplete);
      return;
    }
    setCreating(true);
    try {
      const payload = {
        username: userForm.username,
        password: userForm.password,
        fullName: userForm.fullName,
        role: userForm.role,
        companyId: (isAdmin ? userForm.companyId : ownCompanyId) || undefined,
      };
      await apiClient.post('/api/v1/users', payload);
      // Не дописываем строку в массив: список постраничный и отсортирован сервером — новая
      // учётная запись может принадлежать другой странице, а на этой строк станет больше `size`.
      fetchUsers();
      setUserDialogOpen(false);
      setUserForm({
        username: '',
        password: '',
        fullName: '',
        role: 'COMPANY_HEAD',
        companyId: isAdmin ? (companiesList[0]?.id || '') : ''
      });
    } catch (err: any) {
      setUserError(err.response?.data?.message || tObj.users.createFailed);
    } finally {
      setCreating(false);
    }
  };

  const handleDeleteUser = async () => {
    if (!pendingDelete || deleteBusy) return;
    setDeleteBusy(true);
    try {
      await apiClient.delete(`/api/v1/users/${pendingDelete.id}`);
      // Перечитываем страницу: после удаления на неё поднимается строка со следующей. Если
      // удалили единственную строку не первой страницы, страницы больше нет — шаг назад.
      if (usersList.length === 1 && page > 0) {
        setPage(page - 1);
      } else {
        fetchUsers();
      }
    } catch (err: any) {
      // Полосой на странице, а не системным alert'ом: остальные экраны отвечают так же.
      setSnackbar(err.response?.data?.message || tObj.users.deleteFailed);
    } finally {
      setDeleteBusy(false);
      setPendingDelete(null);
    }
  };

  const getCompanyName = (companyId?: string) => {
    if (!companyId) return '';
    const comp = companiesList.find(c => String(c.id) === String(companyId));
    return comp ? comp.name : companyId;
  };

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 4, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 600, mb: 0.5, display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <GroupIcon color="primary" fontSize="large" />
            {tObj.users.title}
          </Typography>
          <Typography variant="body1" color="text.secondary">
            {tObj.users.subtitle}
          </Typography>
        </Box>
        <Stack direction="row" spacing={2}>
          <Button variant="outlined" startIcon={<RefreshIcon />} onClick={() => fetchUsers()}>
            {tObj.common.refresh}
          </Button>
          <Button variant="contained" startIcon={<AddIcon />} onClick={handleOpenCreate}>
            {tObj.users.addUser}
          </Button>
        </Stack>
      </Box>

      {snackbar && (
        <Alert severity="error" sx={{ mb: 3 }} onClose={() => setSnackbar('')}>
          {snackbar}
        </Alert>
      )}

      {/* Filters Bar */}
      <Paper elevation={0} sx={{ p: 2, mb: 3, border: '1px solid', borderColor: 'divider', display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <TextField
          size="small"
          placeholder={tObj.users.searchPlaceholder}
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
          label={tObj.users.role}
          value={roleFilter}
          onChange={e => { setRoleFilter(e.target.value); setPage(0); }}
          sx={{ minWidth: 200 }}
        >
          <MenuItem value="all">{tObj.common.all}</MenuItem>
          <MenuItem value="COMPANY_HEAD">{tObj.users.roles.companyHead}</MenuItem>
          <MenuItem value="COMPANY_MANAGER">{tObj.users.roles.companyManager}</MenuItem>
          <MenuItem value="COMPANY_EMPLOYEE">{tObj.users.roles.companyEmployee}</MenuItem>
          <MenuItem value="AUDITOR">{tObj.users.roles.auditor}</MenuItem>
          <MenuItem value="SYSTEM_ADMIN">{tObj.users.roles.systemAdmin}</MenuItem>
        </TextField>
      </Paper>

      {/* Users Table */}
      <TableContainer component={Paper} variant="outlined">
        {loading ? (
          <Box sx={{ p: 6, textAlign: 'center' }}>
            <CircularProgress />
          </Box>
        ) : (
          <Table>
            <TableHead>
              <TableRow sx={{ bgcolor: 'action.hover' }}>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.users.username}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.users.name}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.users.role}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.users.company}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.users.status}</TableCell>
                <TableCell sx={{ fontWeight: 700 }} align="center">{tObj.common.actions}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {usersList.map((u) => (
                <TableRow key={u.id} hover>
                  <TableCell sx={{ fontWeight: 600 }}>{u.username}</TableCell>
                  <TableCell>{u.fullName || '—'}</TableCell>
                  <TableCell>
                    {/* Роль — как прислал сервер; подставлять несуществующую «USER» нельзя (Р-48). */}
                    <Chip label={u.role || '—'} color="primary" size="small" variant="outlined" />
                  </TableCell>
                  <TableCell>{getCompanyName(u.companyId) || '—'}</TableCell>
                  <TableCell>
                    {/* Статус — из ответа: чип «Active» на всех строках подряд скрывал заблокированных. */}
                    <Chip
                      label={u.status === 'ACTIVE' ? tObj.common.active : (u.status || '—')}
                      color={u.status === 'ACTIVE' ? 'success' : 'default'}
                      size="small"
                    />
                  </TableCell>
                  <TableCell align="center">
                    <Tooltip title={tObj.common.delete}>
                      <IconButton color="error" size="small" onClick={() => setPendingDelete(u)}>
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
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

      {/* Create User Dialog */}
      <Dialog open={userDialogOpen} onClose={() => { if (!creating) setUserDialogOpen(false); }} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>{tObj.users.createDialogTitle}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {userError && <Alert severity="error">{userError}</Alert>}
            <TextField
              label={tObj.users.username}
              type="email"
              value={userForm.username}
              onChange={e => setUserForm(f => ({ ...f, username: e.target.value }))}
              placeholder="user@company.az"
              fullWidth
              required
            />
            <TextField
              label={tObj.users.password}
              type="password"
              value={userForm.password}
              onChange={e => setUserForm(f => ({ ...f, password: e.target.value }))}
              placeholder="••••••••"
              fullWidth
              required
            />
            <TextField
              label={tObj.users.name}
              value={userForm.fullName}
              onChange={e => setUserForm(f => ({ ...f, fullName: e.target.value }))}
              placeholder="John Doe"
              fullWidth
              required
            />
            <TextField
              select
              label={tObj.users.role}
              value={userForm.role}
              onChange={e => setUserForm(f => ({ ...f, role: e.target.value }))}
              fullWidth
            >
              <MenuItem value="COMPANY_HEAD">{tObj.users.roles.companyHead}</MenuItem>
              <MenuItem value="COMPANY_MANAGER">{tObj.users.roles.companyManager}</MenuItem>
              <MenuItem value="COMPANY_EMPLOYEE">{tObj.users.roles.companyEmployee}</MenuItem>
              <MenuItem value="AUDITOR">{tObj.users.roles.auditor}</MenuItem>
              {/* Системного администратора назначает только системный администратор
                  (`UserService.validateCreatePermission`): остальным пункт не предлагается. */}
              {isAdmin && <MenuItem value="SYSTEM_ADMIN">{tObj.users.roles.systemAdmin}</MenuItem>}
            </TextField>
            {isAdmin && companiesList.length > 0 && (
              <TextField
                select
                label={tObj.users.company}
                value={userForm.companyId}
                onChange={e => setUserForm(f => ({ ...f, companyId: e.target.value }))}
                fullWidth
              >
                {companiesList.map((c) => (
                  <MenuItem key={c.id} value={c.id}>
                    {c.name} ({c.id})
                  </MenuItem>
                ))}
              </TextField>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setUserDialogOpen(false)} disabled={creating}>{tObj.common.cancel}</Button>
          <Button variant="contained" onClick={handleCreateUser} disabled={creating}>
            {creating ? tObj.common.loading : tObj.common.create}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Confirm Delete Dialog */}
      <ConfirmDialog
        open={pendingDelete !== null}
        title={tObj.users.deleteTitle}
        question={tObj.users.deleteQuestion}
        confirmLabel={tObj.common.delete}
        busy={deleteBusy}
        onConfirm={handleDeleteUser}
        onCancel={() => setPendingDelete(null)}
      >
        {/* Кого именно удаляем — в самом окне: у списка бывает по двадцать похожих строк. */}
        {pendingDelete && (
          <Box sx={{ mt: 2, p: 2, borderRadius: 1, border: '1px solid', borderColor: 'divider', bgcolor: 'action.hover' }}>
            <Typography variant="body2" sx={{ fontWeight: 700 }}>
              {pendingDelete.fullName || pendingDelete.username}
            </Typography>
            <Typography variant="body2" sx={{ fontFamily: 'monospace', color: 'text.secondary' }}>
              {pendingDelete.username} · {pendingDelete.role}
            </Typography>
          </Box>
        )}
        <Alert severity="warning" sx={{ mt: 2 }}>{tObj.users.deleteIrreversible}</Alert>
      </ConfirmDialog>
    </Box>
  );
};
