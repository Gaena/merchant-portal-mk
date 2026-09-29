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
  Edit as EditIcon,
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
import type { Role } from '../types/role';

/**
 * Роли, которые может выдать руководитель компании, — зеркало `UserService.HEAD_MANAGED_ROLES` (Р-85).
 * Правит он только людей с этими ролями в своей компании и себя самого; остальное бэкенд отклонит (Р-62).
 */
const HEAD_MANAGED_ROLES: readonly Role[] = ['COMPANY_MANAGER', 'COMPANY_EMPLOYEE'];
const ALL_ROLES: readonly Role[] = ['SYSTEM_ADMIN', 'AUDITOR', 'COMPANY_HEAD', 'COMPANY_MANAGER', 'COMPANY_EMPLOYEE'];
/** Статусы, которые ставит правка: `DELETED` — только удалением (`DELETE /users/{id}`). */
const EDITABLE_STATUSES = ['ACTIVE', 'BLOCKED'] as const;

type EditForm = { fullName: string; role: string; companyId: string; status: string; password: string };

export const UsersPage: React.FC = () => {
  const { user: currentUser } = useAuth();
  const { tObj } = useLanguage();
  // Компанию нового пользователя выбирает только SYSTEM_ADMIN; руководитель заводит людей в свою
  // компанию из токена — список `GET /companies` ему отвечает 403.
  const isAdmin = currentUser?.role === 'SYSTEM_ADMIN';
  const ownCompanyId = currentUser?.companyId ?? '';
  const grantableRoles: readonly Role[] = isAdmin ? ALL_ROLES : HEAD_MANAGED_ROLES;
  // Себя узнаём по логину: id пользователя в токене фронтенд не хранит, логин — это `sub` (email).
  const isSelf = (u: UserDto) => (u.username || '').toLowerCase() === (currentUser?.email || '').toLowerCase();
  // Кого можно править и удалять — те же правила, что `UserService.validateWriteAccess` (Р-85):
  // администратор — всех, руководитель — себя и людей ниже своей роли; список ему и так отдают по его компании.
  const canWrite = (u: UserDto) => isAdmin || isSelf(u) || HEAD_MANAGED_ROLES.includes(u.role as Role);
  const [usersList, setUsersList] = useState<UserDto[]>([]);
  // Удаление мягкое, но необратимое из портала и сразу гасит все сессии — только через подтверждение.
  const [pendingDelete, setPendingDelete] = useState<UserDto | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [snackbar, setSnackbar] = useState('');
  const [companiesList, setCompaniesList] = useState<CompanyDto[]>([]);
  const [loading, setLoading] = useState(true);

  const [userDialogOpen, setUserDialogOpen] = useState(false);
  // Роль по умолчанию — из выдаваемых: руководитель «Руководителя компании» не выдаёт (Р-85).
  const defaultRole = isAdmin ? 'COMPANY_HEAD' : 'COMPANY_MANAGER';
  // Роли, которым компания обязательна (Р-90, Р-103); администратору и аудитору её не подставляем.
  const isCompanyRole = (role: string) => role === 'COMPANY_HEAD' || role === 'COMPANY_MANAGER' || role === 'COMPANY_EMPLOYEE';
  const [userForm, setUserForm] = useState({
    username: '',
    password: '',
    fullName: '',
    role: defaultRole,
    companyId: ''
  });
  const [notice, setNotice] = useState('');
  // Правка пользователя (Р-90): окно формы, затем подтверждение со списком изменений.
  const [editing, setEditing] = useState<UserDto | null>(null);
  const [editForm, setEditForm] = useState<EditForm>({ fullName: '', role: '', companyId: '', status: '', password: '' });
  const [editError, setEditError] = useState('');
  const [editConfirm, setEditConfirm] = useState<string[] | null>(null);
  const [editBusy, setEditBusy] = useState(false);
  const [userError, setUserError] = useState('');
  const [creating, setCreating] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [roleFilter, setRoleFilter] = useState('all');
  // Поиск и фильтр по роли — серверные: клиентский фильтр видел бы только текущую страницу (P3-1).
  const debouncedSearch = useDebounced(searchQuery, 300);

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
      // Компании нужны для списка и подписей — одной страницей по потолку размера на сервере (200).
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
    if (isAdmin && isCompanyRole(userForm.role) && !userForm.companyId) {
      setUserError(tObj.users.companyRequired);
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
      // Перечитываем, а не дописываем: новая учётная запись может оказаться на другой странице.
      fetchUsers();
      setUserDialogOpen(false);
      setUserForm({
        username: '',
        password: '',
        fullName: '',
        role: defaultRole,
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
      setSnackbar(err.response?.data?.message || tObj.users.deleteFailed);
    } finally {
      setDeleteBusy(false);
      setPendingDelete(null);
    }
  };

  const roleLabel = (role?: string): string => {
    switch (role) {
      case 'SYSTEM_ADMIN': return tObj.users.roles.systemAdmin;
      case 'AUDITOR': return tObj.users.roles.auditor;
      case 'COMPANY_HEAD': return tObj.users.roles.companyHead;
      case 'COMPANY_MANAGER': return tObj.users.roles.companyManager;
      case 'COMPANY_EMPLOYEE': return tObj.users.roles.companyEmployee;
      // Роль вне словаря показывается как есть, а не подменяется знакомой (Р-48).
      default: return role || '—';
    }
  };

  const statusLabel = (status?: string): string =>
    status === 'ACTIVE' || status === 'BLOCKED' ? tObj.users.statuses[status] : (status || '—');

  const handleOpenEdit = (u: UserDto) => {
    setEditError('');
    setEditing(u);
    setEditForm({
      fullName: u.fullName || '',
      role: u.role || '',
      companyId: u.companyId || '',
      status: u.status || 'ACTIVE',
      password: '',
    });
  };

  // Пустой список — PATCH не уходит: он всё равно оставил бы «No fields changed» в журнале аудита.
  const pendingEditChanges = (): string[] => {
    if (!editing) return [];
    const changes: string[] = [];
    if (editForm.fullName.trim() !== (editing.fullName || '')) {
      changes.push(`${tObj.users.name}: ${editing.fullName || '—'} → ${editForm.fullName.trim() || '—'}`);
    }
    if (editForm.role !== (editing.role || '')) {
      changes.push(`${tObj.users.role}: ${roleLabel(editing.role)} → ${roleLabel(editForm.role)}`);
    }
    if (isAdmin && editForm.companyId !== (editing.companyId || '')) {
      changes.push(`${tObj.users.company}: ${getCompanyName(editing.companyId) || tObj.users.noCompany} → `
        + `${getCompanyName(editForm.companyId) || tObj.users.noCompany}`);
    }
    if (editForm.status !== (editing.status || '')) {
      changes.push(`${tObj.users.status}: ${statusLabel(editing.status)} → ${statusLabel(editForm.status)}`);
    }
    if (editForm.password) {
      changes.push(tObj.users.passwordWillChange);
    }
    return changes;
  };

  const handleAskUpdate = () => {
    if (!editing) return;
    if (!editForm.fullName.trim()) {
      setEditError(tObj.users.formIncomplete);
      return;
    }
    setEditError('');
    const changes = pendingEditChanges();
    if (changes.length === 0) {
      setEditing(null);
      setNotice(tObj.users.editNothingChanged);
      return;
    }
    setEditConfirm(changes);
  };

  const handleUpdateUser = async () => {
    if (!editing || editBusy) return;
    setEditBusy(true);
    try {
      // Только изменившиеся поля. Компанию шлёт только администратор; пустая строка — снять компанию.
      const payload: Record<string, string> = {};
      if (editForm.fullName.trim() !== (editing.fullName || '')) payload.fullName = editForm.fullName.trim();
      if (editForm.role !== (editing.role || '')) payload.role = editForm.role;
      if (isAdmin && editForm.companyId !== (editing.companyId || '')) payload.companyId = editForm.companyId;
      if (editForm.status !== (editing.status || '')) payload.status = editForm.status;
      if (editForm.password) payload.password = editForm.password;
      const res = await apiClient.patch<UserDto>(`/api/v1/users/${editing.id}`, payload);
      setUsersList(prev => prev.map(u => (u.id === editing.id ? res.data : u)));
      setEditing(null);
      setNotice(tObj.users.updated);
    } catch (err: any) {
      // Отказ остаётся в окне правки: там форма, которую нужно поправить.
      setEditError(err.response?.data?.message || tObj.users.updateFailed);
    } finally {
      setEditBusy(false);
      setEditConfirm(null);
    }
  };

  const getCompanyName = (companyId?: string) => {
    if (!companyId) return '';
    const comp = companiesList.find(c => String(c.id) === String(companyId));
    return comp ? comp.name : companyId;
  };

  return (
    <Box>
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
      {notice && (
        <Alert severity="info" sx={{ mb: 3 }} onClose={() => setNotice('')}>
          {notice}
        </Alert>
      )}

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
                    <Chip label={roleLabel(u.role)} color="primary" size="small" variant="outlined" />
                  </TableCell>
                  <TableCell>{getCompanyName(u.companyId) || '—'}</TableCell>
                  <TableCell>
                    <Chip
                      label={statusLabel(u.status)}
                      color={u.status === 'ACTIVE' ? 'success' : u.status === 'BLOCKED' ? 'warning' : 'default'}
                      size="small"
                    />
                    {u.passwordChangeRequired === true && (
                      <Chip label={tObj.users.passwordChangePending} size="small" variant="outlined" sx={{ ml: 1 }} />
                    )}
                  </TableCell>
                  <TableCell align="center">
                    {/* Кнопки — только там, где бэкенд примет действие (Р-62, Р-85); удалить себя нельзя,
                        чтобы не лишиться доступа одним кликом. */}
                    {canWrite(u) && (
                      <Tooltip title={tObj.users.editUser}>
                        <IconButton color="primary" size="small" onClick={() => handleOpenEdit(u)}>
                          <EditIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    )}
                    {canWrite(u) && !isSelf(u) && (
                      <Tooltip title={tObj.common.delete}>
                        <IconButton color="error" size="small" onClick={() => setPendingDelete(u)}>
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    )}
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
              helperText={tObj.users.issuedPasswordHint}
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
              onChange={e => {
                const role = e.target.value;
                // Администратор и аудитор — без компании; вернуть её можно выбором ниже.
                setUserForm(f => ({ ...f, role, companyId: isCompanyRole(role) ? f.companyId : '' }));
              }}
              fullWidth
            >
              {/* Только роли, которые бэкенд даст выдать: руководитель — менеджера и сотрудника (Р-85). */}
              {grantableRoles.map(role => (
                <MenuItem key={role} value={role}>{roleLabel(role)}</MenuItem>
              ))}
            </TextField>
            {isAdmin && companiesList.length > 0 && (
              <TextField
                select
                label={tObj.users.company}
                value={userForm.companyId}
                onChange={e => setUserForm(f => ({ ...f, companyId: e.target.value }))}
                fullWidth
              >
                <MenuItem value="">{tObj.users.noCompany}</MenuItem>
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

      <Dialog open={editing !== null} onClose={() => { if (!editBusy) setEditing(null); }} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>
          {tObj.users.editDialogTitle}
          {editing && (
            <Box component="span" sx={{ display: 'block', fontFamily: 'monospace', fontSize: '0.875rem', fontWeight: 400, color: 'text.secondary' }}>
              {editing.username}
            </Box>
          )}
        </DialogTitle>
        <DialogContent>
          {editing && (
            <Stack spacing={2} sx={{ mt: 1 }}>
              {editError && <Alert severity="error">{editError}</Alert>}
              <TextField
                label={tObj.users.name}
                value={editForm.fullName}
                onChange={e => setEditForm(f => ({ ...f, fullName: e.target.value }))}
                fullWidth
                required
              />
              <TextField
                select
                label={tObj.users.role}
                value={editForm.role}
                onChange={e => setEditForm(f => ({ ...f, role: e.target.value }))}
                disabled={isSelf(editing)}
                fullWidth
              >
                {/* Текущая роль остаётся в списке, даже если её нельзя выдать: иначе селект был бы пустым. */}
                {[...new Set([...(editing.role ? [editing.role] : []), ...grantableRoles])].map(role => (
                  <MenuItem key={role} value={role} disabled={!grantableRoles.includes(role as Role)}>
                    {roleLabel(role)}
                  </MenuItem>
                ))}
              </TextField>
              {/* Перевести в другую компанию может только SYSTEM_ADMIN (Р-90). */}
              {isAdmin && (
                <TextField
                  select
                  label={tObj.users.company}
                  value={editForm.companyId}
                  onChange={e => setEditForm(f => ({ ...f, companyId: e.target.value }))}
                  SelectProps={{ displayEmpty: true }}
                  InputLabelProps={{ shrink: true }}
                  fullWidth
                >
                  <MenuItem value="">{tObj.users.noCompany}</MenuItem>
                  {companiesList.map(c => (
                    <MenuItem key={c.id} value={c.id}>
                      {c.name} ({c.id})
                    </MenuItem>
                  ))}
                  {editing.companyId && !companiesList.some(c => c.id === editing.companyId) && (
                    <MenuItem value={editing.companyId}>{editing.companyId}</MenuItem>
                  )}
                </TextField>
              )}
              <TextField
                select
                label={tObj.users.status}
                value={editForm.status}
                onChange={e => setEditForm(f => ({ ...f, status: e.target.value }))}
                disabled={isSelf(editing)}
                fullWidth
              >
                {[...new Set([...(editing.status ? [editing.status] : []), ...EDITABLE_STATUSES])].map(status => (
                  <MenuItem key={status} value={status} disabled={!(EDITABLE_STATUSES as readonly string[]).includes(status)}>
                    {statusLabel(status)}
                  </MenuItem>
                ))}
              </TextField>
              {isSelf(editing) && (
                <Typography variant="caption" color="text.secondary">{tObj.users.selfHint}</Typography>
              )}
              <TextField
                label={tObj.users.newPassword}
                type="password"
                autoComplete="new-password"
                value={editForm.password}
                onChange={e => setEditForm(f => ({ ...f, password: e.target.value }))}
                helperText={isSelf(editing)
                  ? tObj.users.newPasswordHint
                  : `${tObj.users.newPasswordHint} ${tObj.users.issuedPasswordHint}`}
                fullWidth
              />
            </Stack>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditing(null)} disabled={editBusy}>{tObj.common.cancel}</Button>
          <Button variant="contained" onClick={handleAskUpdate} disabled={editBusy}>{tObj.common.save}</Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={editConfirm !== null}
        title={<>{tObj.users.editConfirmTitle} {editing?.username}</>}
        question={tObj.users.editConfirmQuestion}
        confirmLabel={tObj.common.confirm}
        confirmColor="primary"
        busy={editBusy}
        onConfirm={handleUpdateUser}
        onCancel={() => setEditConfirm(null)}
      >
        <Box sx={{ mt: 2, p: 2, borderRadius: 1, border: '1px solid', borderColor: 'divider', bgcolor: 'action.hover' }}>
          <Stack spacing={1}>
            {(editConfirm ?? []).map(change => (
              <Typography key={change} variant="body2">{change}</Typography>
            ))}
          </Stack>
        </Box>
        <Alert severity="info" sx={{ mt: 2 }}>{tObj.users.editSessionsHint}</Alert>
      </ConfirmDialog>

      <ConfirmDialog
        open={pendingDelete !== null}
        title={tObj.users.deleteTitle}
        question={tObj.users.deleteQuestion}
        confirmLabel={tObj.common.delete}
        busy={deleteBusy}
        onConfirm={handleDeleteUser}
        onCancel={() => setPendingDelete(null)}
      >
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
