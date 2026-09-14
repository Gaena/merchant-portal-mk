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
} from '@mui/material';
import {
  PointOfSale as POSIcon,
  Add as AddIcon,
  Edit as EditIcon,
  Block as BlockIcon,
  PlayArrow as UnblockIcon,
  Refresh as RefreshIcon,
  Business as BusinessIcon,
  Search as SearchIcon,
  Visibility as VisibilityIcon,
  VisibilityOff as VisibilityOffIcon,
  Sync as SyncIcon,
} from '@mui/icons-material';
import { useLanguage } from '../context/LanguageContext';
import { useAuth } from '../context/AuthContext';
import {
  checkExistingTerminal,
  checkNewTerminal,
  checkSeverity,
  type TerminalCheckOutcome,
  type TerminalCheckResponse,
} from '../utils/terminalCheck';
import { isTerminalActive } from '../types/dto';
import type {
  TerminalDto,
  CompanyDto,
  TerminalStatus,
  ProviderTerminalDto,
  ProviderTerminalSyncOutcome,
} from '../types/dto';

export const TerminalsPage: React.FC = () => {
  const { tObj } = useLanguage();
  const { user } = useAuth();
  /**
   * Пароль терминала — ключ от эквайринга, и видеть его может только системный администратор
   * (`TerminalService.revealPassword`). Здесь та же роль решает, показывать ли кнопку раскрытия
   * и поле смены пароля: прятать кнопку, которой сервер всё равно откажет, честнее, чем
   * предлагать действие и отвечать на него отказом.
   */
  const canSeePassword = user?.role === 'SYSTEM_ADMIN';

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
  const [form, setForm] = useState({ id: '', name: '', login: '', password: '', companyId: '' });
  const [error, setError] = useState('');
  const [snackbar, setSnackbar] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  /**
   * Раскрытые пароли — по одному запросу на терминал, и только пока открыта страница. Ответ
   * намеренно не кладётся в `terminals`: там он пережил бы перерисовку списка и разъехался бы
   * с тем, что отдаёт сервер, где пароль по-прежнему замаскирован.
   */
  const [revealed, setRevealed] = useState<Record<number, string>>({});
  const [revealing, setRevealing] = useState<number | null>(null);
  /**
   * Итог последней проверки по каждому терминалу. Хранится до перезагрузки списка, как и
   * раскрытые пароли, — и по той же причине: на новой странице те же строки уже другие терминалы.
   */
  const [checks, setChecks] = useState<Record<number, TerminalCheckResponse>>({});
  const [checking, setChecking] = useState<number | null>(null);
  // Проверка в форме заведения: ключ ещё не сохранён.
  const [formCheck, setFormCheck] = useState<TerminalCheckResponse | null>(null);
  const [formChecking, setFormChecking] = useState(false);
  // Справочник терминалов провайдера для формы заведения (Р-67, Р-79). Его видит только
  // SYSTEM_ADMIN; у остальных ролей форма остаётся с ручным вводом названия и логина.
  const [providerTerminals, setProviderTerminals] = useState<ProviderTerminalDto[]>([]);
  const [providerState, setProviderState] = useState<'idle' | 'loading' | 'ready' | 'failed'>('idle');
  const [selectedProvider, setSelectedProvider] = useState<ProviderTerminalDto | null>(null);
  const [syncing, setSyncing] = useState(false);
  const [syncResult, setSyncResult] = useState<ProviderTerminalSyncOutcome | null>(null);
  // Поиск — серверный (P3-1): клиентский фильтр видел только текущую страницу. 300 мс задержки,
  // чтобы не слать запрос на каждую букву.
  const debouncedSearch = useDebounced(searchQuery, 300);
  // Терминал, который собираются заблокировать, и число ссылок, которые при этом приостановятся.
  // `affectedLinks === null` — счёт ещё идёт или не удался; в диалоге это так и написано.
  // Разблокировка спрашивает наравне с блокировкой: она тоже трогает чужие ссылки, просто
  // в другую сторону, и «случайно нажал» здесь стоит столько же.
  const [statusChange, setStatusChange] = useState<
    { terminal: TerminalDto; nextStatus: TerminalStatus; affectedLinks: number | null } | null>(null);
  // Правка уходит на сервер только после отдельного подтверждения со списком изменений:
  // форма правки меняет логин, пароль и **компанию-владельца** — цена промаха разная.
  const [editConfirm, setEditConfirm] = useState<string[] | null>(null);
  const [busy, setBusy] = useState(false);

  // Страница берётся с сервера (P2-1): `/api/v1/terminals` отвечает `PagedResponse`.
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(20);
  const [totalElements, setTotalElements] = useState(0);

  const fetchTerminals = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    // Раскрытые ключи не переживают перезагрузку списка: на новой странице те же строки — уже
    // другие терминалы, и оставить значение на экране значило бы подписать им чужой пароль.
    setRevealed({});
    setChecks({});
    try {
      const params: Record<string, unknown> = { page, size: rowsPerPage };
      if (debouncedSearch.trim()) params.search = debouncedSearch.trim();
      const res = await apiClient.get('/api/v1/terminals', { params, signal });
      const list = Array.isArray(res.data) ? res.data : (res.data?.content || []);
      setTerminals(list);
      setTotalElements(res.data?.totalElements ?? list.length);
    } catch (err) {
      // Гонка ответов: устаревший запрос отменён эффектом ниже, его исход не трогает экран.
      if (axios.isCancel(err)) return;
      setTerminals([]);
      setTotalElements(0);
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, [page, rowsPerPage, debouncedSearch]);

  const fetchCompanies = async () => {
    try {
      // Компании нужны только для выпадающего списка в диалогах, поэтому берём их одной
      // страницей по потолку (200 — тот же лимит, что у журнала аудита).
      const res = await apiClient.get('/api/v1/companies', { params: { page: 0, size: 200 } });
      const list = Array.isArray(res.data) ? res.data : (res.data?.content || []);
      setCompanies(list);
      if (list.length > 0 && !form.companyId) {
        setForm(f => ({ ...f, companyId: list[0].id }));
      }
    } catch {
      setCompanies([]);
    }
  };

  // Отмена предыдущего запроса при каждом изменении параметров: без неё ответ на «ив» может
  // прийти позже ответа на «ива» и перезаписать более точный результат.
  useEffect(() => {
    const controller = new AbortController();
    fetchTerminals(controller.signal);
    return () => controller.abort();
  }, [fetchTerminals]);

  useEffect(() => {
    fetchCompanies();
  }, []);

  const loadProviderTerminals = async () => {
    setProviderState('loading');
    try {
      const res = await apiClient.get('/api/v1/ecom/provider-terminals');
      setProviderTerminals(Array.isArray(res.data) ? res.data : []);
      setProviderState('ready');
    } catch {
      setProviderTerminals([]);
      setProviderState('failed');
    }
  };

  // Обновить справочник прямо сейчас, не дожидаясь расписания: нужен, когда терминал только что
  // завели у провайдера или плановое обновление ещё не проходило.
  const handleSyncDirectory = async () => {
    setSyncing(true);
    setSyncResult(null);
    try {
      const res = await apiClient.post<ProviderTerminalSyncOutcome>('/api/v1/ecom/provider-terminals/sync');
      setSyncResult(res.data);
      await loadProviderTerminals();
    } catch (err: any) {
      setError(err.response?.data?.message || tObj.terminals.providerTerminalLoadFailed);
    } finally {
      setSyncing(false);
    }
  };

  const handleOpenCreate = () => {
    fetchCompanies();
    setError('');
    setEditingTerminalId(null);
    setForm({ id: '', name: '', login: '', password: '', companyId: companies[0]?.id || '' });
    setSelectedProvider(null);
    setSyncResult(null);
    setFormCheck(null);
    if (canSeePassword) {
      loadProviderTerminals();
    }
    setCreateOpen(true);
  };

  const handleOpenEdit = (term: any) => {
    fetchCompanies();
    setError('');
    setEditingTerminalId(term.id);
    setForm({
      id: String(term.id),
      name: term.name || '',
      login: term.login || '',
      password: '', // blank password unless changing
      companyId: term.companyId || (companies[0]?.id ?? '')
    });
    setEditOpen(true);
  };

  const handleCreate = async () => {
    // Администратор заводит терминал выбором из справочника: название и логин сервер берёт оттуда
    // по merchantRid и введённые руками игнорирует (TerminalService.createTerminal).
    const fromDirectory = canSeePassword;
    const identityMissing = fromDirectory ? !selectedProvider : !form.name.trim() || !form.login.trim();
    if (!form.id || identityMissing || !form.password.trim() || !form.companyId) {
      setError('All fields including Company selection are required');
      return;
    }
    setError('');
    try {
      const payload = fromDirectory
        ? {
            id: Number(form.id),
            password: form.password.trim(),
            companyId: form.companyId,
            merchantRid: selectedProvider?.rid,
          }
        : {
            id: Number(form.id),
            name: form.name.trim(),
            login: form.login.trim(),
            password: form.password.trim(),
            companyId: form.companyId,
          };
      await apiClient.post('/api/v1/terminals', payload);
      // Не дописываем строку в массив: список постраничный и отсортирован сервером по имени —
      // новый терминал может принадлежать другой странице.
      fetchTerminals();
      setCreateOpen(false);
      setForm({ id: '', name: '', login: '', password: '', companyId: companies[0]?.id || '' });
      setSnackbar('Acquiring terminal registered successfully');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to create terminal');
    }
  };

  /**
   * Показать или спрятать пароль терминала. Каждое раскрытие — отдельный запрос, и каждый
   * пишется в журнал аудита на сервере; повторное нажатие просто убирает значение с экрана,
   * ничего не спрашивая.
   */
  /**
   * «Тест» у терминала. Результат — всегда один из четырёх исходов, даже при неверном пароле
   * или недоступном провайдере; сбоем здесь считается только отказ самого портала (например,
   * нехватка прав), и он идёт в общую строку ошибки.
   */
  const runCheck = async (terminalId: number) => {
    setChecking(terminalId);
    setError('');
    try {
      const result = await checkExistingTerminal(terminalId);
      setChecks(prev => ({ ...prev, [terminalId]: result }));
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to check the terminal');
    } finally {
      setChecking(null);
    }
  };

  // Логин, который уйдёт в проверку и в терминал: у администратора — из выбранной строки справочника.
  const formLogin = (canSeePassword ? selectedProvider?.login ?? '' : form.login).trim();

  const runFormCheck = async () => {
    setFormCheck(null);
    setFormChecking(true);
    setError('');
    try {
      setFormCheck(await checkNewTerminal(formLogin, form.password));
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to check the terminal');
    } finally {
      setFormChecking(false);
    }
  };

  const togglePassword = async (terminalId: number) => {
    if (revealed[terminalId] !== undefined) {
      setRevealed(prev => {
        const next = { ...prev };
        delete next[terminalId];
        return next;
      });
      return;
    }
    setRevealing(terminalId);
    setError('');
    try {
      const res = await apiClient.get(`/api/v1/terminals/${terminalId}/password`);
      setRevealed(prev => ({ ...prev, [terminalId]: String(res.data?.password ?? '') }));
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to reveal the terminal password');
    } finally {
      setRevealing(null);
    }
  };

  const handleUpdate = async () => {
    if (!editingTerminalId) return;
    if (!form.name.trim() || !form.login.trim() || !form.companyId) {
      setError('Name, Login and Company selection are required');
      return;
    }
    setError('');
    try {
      const payload: any = {
        name: form.name.trim(),
        login: form.login.trim(),
        companyId: form.companyId,
      };
      if (form.password.trim()) {
        payload.password = form.password.trim();
      }
      const res = await apiClient.patch(`/api/v1/terminals/${editingTerminalId}`, payload);
      setTerminals(prev => prev.map(t => (t.id === editingTerminalId ? res.data : t)));
      setEditOpen(false);
      setEditingTerminalId(null);
      setForm({ id: '', name: '', login: '', password: '', companyId: companies[0]?.id || '' });
      setSnackbar('Acquiring terminal updated successfully');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to update terminal');
    } finally {
      setEditConfirm(null);
    }
  };

  /**
   * Смена статуса терминала трогает чужие платёжные ссылки, поэтому спрашиваем — и вместе с
   * вопросом показываем, скольких ссылок это коснётся. Число берём у `pbl` (`totalElements`
   * пагинированного ответа), без отдельной ручки в бэкенде: перед блокировкой считаем активные
   * ссылки (их приостановят), перед разблокировкой — приостановленные (их вернут в работу).
   */
  const handleAskStatus = async (terminal: TerminalDto, nextStatus: TerminalStatus) => {
    setStatusChange({ terminal, nextStatus, affectedLinks: null });
    try {
      const res = await apiClient.get('/api/v1/payment-links', {
        params: { terminal: terminal.id, status: nextStatus === 'BLOCKED' ? 'ACTIVE' : 'SUSPENDED', size: 1 },
      });
      const total = res.data?.totalElements;
      setStatusChange({ terminal, nextStatus, affectedLinks: typeof total === 'number' ? total : null });
    } catch {
      // Счёт — справка, а не условие: не смогли посчитать, диалог всё равно показывает, что делает.
      setStatusChange({ terminal, nextStatus, affectedLinks: null });
    }
  };

  /**
   * Что именно изменится, если сохранить форму правки. Пустой список означает, что менять
   * нечего, и тогда запрос не уходит вовсе: PATCH, который ничего не меняет, всё равно оставит
   * запись в журнале аудита.
   */
  const pendingEditChanges = (): string[] => {
    const original = terminals.find(t => t.id === editingTerminalId);
    if (!original) return [];
    const changes: string[] = [];
    if (form.name.trim() !== (original.name || '')) {
      changes.push(`${tObj.terminals.name}: ${original.name || '—'} → ${form.name.trim()}`);
    }
    if (form.login.trim() !== (original.login || '')) {
      changes.push(`${tObj.terminals.login}: ${original.login || '—'} → ${form.login.trim()}`);
    }
    if (form.companyId !== (original.companyId || '')) {
      changes.push(`${tObj.terminals.company}: ${getCompanyName(original.companyId || '')} → ${getCompanyName(form.companyId)}`);
    }
    if (form.password.trim()) {
      changes.push(tObj.terminals.editPasswordReplaced);
    }
    return changes;
  };

  const handleAskUpdate = () => {
    if (!form.name.trim() || !form.login.trim() || !form.companyId) {
      setError('Name, Login and Company selection are required');
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
    setBusy(true);
    try {
      const res = await apiClient.patch(`/api/v1/terminals/${terminal.id}`, { status });
      setTerminals(prev => prev.map(t => (t.id === terminal.id ? res.data : t)));
      setSnackbar(status === 'BLOCKED'
        ? `Терминал #${terminal.id} заблокирован: новые платежи по нему не принимаются, активные ссылки приостановлены`
        : `Терминал #${terminal.id} разблокирован: приостановленные ссылки вернулись в работу (кроме тех, у которых истёк срок)`);
    } catch (err: any) {
      setSnackbar(err.response?.data?.message || 'Не удалось изменить статус терминала');
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
      {/* Header */}
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
          <Button variant="contained" startIcon={<AddIcon />} onClick={handleOpenCreate}>
            {tObj.terminals.addTerminal}
          </Button>
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

      {/* Table */}
      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, overflow: 'hidden' }}>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow sx={{ bgcolor: 'rgba(0,0,0,0.02)' }}>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.terminals.login}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.terminals.password}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.terminals.name}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.terminals.terminalId}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.terminals.company}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.common.status}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{tObj.common.date}</TableCell>
                <TableCell sx={{ fontWeight: 700 }} align="center">{tObj.common.actions}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {terminals.map((term) => {
                const active = isTerminalActive(term);
                return (
                <TableRow key={term.id} hover sx={active ? undefined : { opacity: 0.6 }}>
                  {/* Логин впереди: по нему мерчант терминал и опознаёт, имя он придумывает
                      сам, а номер — внутренний. */}
                  <TableCell sx={{ fontFamily: 'monospace', fontWeight: 700, color: active ? 'primary.main' : 'text.disabled' }}>
                    {term.login}
                  </TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      <Typography
                        variant="body2"
                        sx={{ fontFamily: 'monospace', letterSpacing: revealed[term.id] === undefined ? 2 : 0 }}
                      >
                        {revealed[term.id] ?? '••••••••'}
                      </Typography>
                      {canSeePassword && (
                        <Tooltip title={revealed[term.id] === undefined
                          ? tObj.terminals.revealPassword
                          : tObj.terminals.hidePassword}>
                          <span>
                            <IconButton
                              size="small"
                              disabled={revealing === term.id}
                              onClick={() => togglePassword(term.id)}
                            >
                              {revealed[term.id] === undefined
                                ? <VisibilityIcon fontSize="small" />
                                : <VisibilityOffIcon fontSize="small" />}
                            </IconButton>
                          </span>
                        </Tooltip>
                      )}
                    </Box>
                  </TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{term.name}</TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', color: 'text.secondary' }}>#{term.id}</TableCell>
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
                  <TableCell sx={{ fontSize: '0.85rem', color: 'text.secondary' }}>
                    {term.createdAt ? new Date(term.createdAt).toLocaleString() : 'N/A'}
                  </TableCell>
                  <TableCell align="center">
                    <Stack direction="row" spacing={0.5} justifyContent="center" alignItems="center">
                      {/* «Тест» — только администратору: проверка отвечает на вопрос, подходит ли
                          ключ, и перебирать ключи другим ролям незачем. */}
                      {canSeePassword && (
                        <Tooltip title={checks[term.id]
                          ? `${checkLabel(checks[term.id].outcome)}${checks[term.id].message ? ` — ${checks[term.id].message}` : ''}`
                          : tObj.terminals.testAction}>
                          <span>
                            <Button
                              size="small"
                              variant="outlined"
                              color={checks[term.id] ? checkSeverity(checks[term.id].outcome) : 'primary'}
                              disabled={checking === term.id}
                              onClick={() => runCheck(term.id)}
                              sx={{ minWidth: 0, px: 1 }}
                            >
                              {checking === term.id ? '…' : tObj.terminals.testAction}
                            </Button>
                          </span>
                        </Tooltip>
                      )}
                      <Tooltip title={tObj.terminals.editTerminal}>
                        <IconButton color="primary" size="small" onClick={() => handleOpenEdit(term)}>
                          <EditIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      {active ? (
                        <Tooltip title={tObj.terminals.blockAction}>
                          <IconButton color="warning" size="small" onClick={() => handleAskStatus(term, 'BLOCKED')}>
                            <BlockIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      ) : (
                        <Tooltip title={tObj.terminals.unblockAction}>
                          <IconButton color="success" size="small" disabled={busy}
                                      onClick={() => handleAskStatus(term, 'ACTIVE')}>
                            <UnblockIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      )}
                    </Stack>
                  </TableCell>
                </TableRow>
                );
              })}
              {terminals.length === 0 && !loading && (
                <TableRow>
                  <TableCell colSpan={7} align="center" sx={{ py: 6 }}>
                    <POSIcon sx={{ fontSize: 48, color: 'text.disabled', mb: 1 }} />
                    <Typography color="text.secondary">No acquiring terminals registered yet.</Typography>
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

      {/* Block confirmation: says what it will do, and to how many links */}
      <ConfirmDialog
        open={statusChange !== null}
        maxWidth="xs"
        title={<>
          {statusChange?.nextStatus === 'BLOCKED' ? tObj.terminals.blockAction : tObj.terminals.unblockAction}
          {' #'}{statusChange?.terminal.id}
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

      {/* Create Terminal Dialog */}
      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>{tObj.terminals.createDialogTitle}</DialogTitle>
        <DialogContent>
          {error && <Alert severity="error" sx={{ mb: 2, mt: 1 }}>{error}</Alert>}
          <Stack spacing={2.5} sx={{ mt: 1 }}>
            <TextField
              select
              fullWidth
              label={`${tObj.terminals.company} *`}
              value={form.companyId || (companies[0]?.id ?? '')}
              onChange={e => setForm(f => ({ ...f, companyId: e.target.value }))}
            >
              {companies.map((comp) => (
                <MenuItem key={comp.id} value={comp.id}>
                  {comp.name} (ID: {comp.id})
                </MenuItem>
              ))}
            </TextField>

            <TextField
              label={`${tObj.terminals.terminalId} *`}
              type="number"
              value={form.id}
              onChange={e => setForm(f => ({ ...f, id: e.target.value }))}
              placeholder="e.g. 1"
              fullWidth
              helperText={tObj.terminals.terminalIdHint}
            />
            {canSeePassword ? (
              <Box>
                {/* Название и логин — от провайдера (Р-67): выбирается строка справочника, руками
                    вводится только пароль. Один терминал провайдера — одна компания: занятый
                    сервер отклонит с объяснением. */}
                <Autocomplete
                  options={providerTerminals}
                  value={selectedProvider}
                  loading={providerState === 'loading'}
                  onChange={(_, value) => { setSelectedProvider(value); setFormCheck(null); }}
                  getOptionLabel={option => [option.login, option.title].filter(Boolean).join(' — ') || option.rid}
                  isOptionEqualToValue={(option, value) => option.rid === value.rid}
                  renderOption={({ key, ...optionProps }, option) => (
                    <li key={key} {...optionProps}>
                      <Box>
                        <Typography variant="body2" sx={{ fontFamily: 'monospace', fontWeight: 700 }}>
                          {option.login || '—'}
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
                      {tObj.terminals.login}: <b style={{ fontFamily: 'monospace' }}>{selectedProvider.login || '—'}</b>
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
            ) : (
              <>
                <TextField
                  label={`${tObj.terminals.name} *`}
                  value={form.name}
                  onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
                  placeholder="e.g. Main Online E-commerce Terminal"
                  fullWidth
                />
                <TextField
                  label={`${tObj.terminals.login} *`}
                  value={form.login}
                  onChange={e => { setForm(f => ({ ...f, login: e.target.value })); setFormCheck(null); }}
                  placeholder="e.g. term_login_001"
                  fullWidth
                />
              </>
            )}
            <TextField
              label={`${tObj.terminals.password} *`}
              type="password"
              value={form.password}
              onChange={e => { setForm(f => ({ ...f, password: e.target.value })); setFormCheck(null); }}
              placeholder="••••••••"
              fullWidth
            />
            {/* Проверить ключ до сохранения: неверный пароль иначе выяснится на первом платеже.
                Итог сбрасывается, как только логин или пароль поменяли, — он относится только
                к тому, что проверяли. */}
            {canSeePassword && (
              <Box>
                <Button
                  variant="outlined"
                  disabled={formChecking || !formLogin || !form.password}
                  onClick={runFormCheck}
                >
                  {formChecking ? tObj.common.loading : tObj.terminals.testAction}
                </Button>
                {formCheck && (
                  <Alert severity={checkSeverity(formCheck.outcome)} sx={{ mt: 1.5 }}>
                    {checkLabel(formCheck.outcome)}
                    {formCheck.message ? ` — ${formCheck.message}` : ''}
                  </Alert>
                )}
              </Box>
            )}
          </Stack>
        </DialogContent>
        <DialogActions sx={{ p: 2.5 }}>
          <Button onClick={() => setCreateOpen(false)}>{tObj.common.cancel}</Button>
          <Button variant="contained" onClick={handleCreate}>{tObj.terminals.registerAction}</Button>
        </DialogActions>
      </Dialog>

      {/* Edit Terminal Dialog */}
      <Dialog open={editOpen} onClose={() => setEditOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>{tObj.terminals.editDialogTitle} #{editingTerminalId}</DialogTitle>
        <DialogContent>
          {error && <Alert severity="error" sx={{ mb: 2, mt: 1 }}>{error}</Alert>}
          <Stack spacing={2.5} sx={{ mt: 1 }}>
            <TextField
              select
              fullWidth
              label="Assigned Company *"
              value={form.companyId}
              onChange={e => setForm(f => ({ ...f, companyId: e.target.value }))}
              helperText="Выберите компанию, к которой относится терминал"
            >
              {companies.map((comp) => (
                <MenuItem key={comp.id} value={comp.id}>
                  {comp.name} (ID: {comp.id})
                </MenuItem>
              ))}
            </TextField>

            <TextField
              label="Terminal Name *"
              value={form.name}
              onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
              placeholder="e.g. Main Online E-commerce Terminal"
              fullWidth
            />
            <TextField
              label="Terminal Login *"
              value={form.login}
              onChange={e => setForm(f => ({ ...f, login: e.target.value }))}
              placeholder="e.g. term_login_001"
              fullWidth
            />
            {/* Пароль эквайринга меняет только системный администратор — те же ворота, что
                и на его чтение. Остальным поле не показывается вовсе: сервер откажет. */}
            {canSeePassword && (
              <TextField
                label={tObj.terminals.newPassword}
                type="password"
                value={form.password}
                onChange={e => setForm(f => ({ ...f, password: e.target.value }))}
                placeholder="Leave blank to keep existing password"
                fullWidth
                helperText={tObj.terminals.newPasswordHint}
              />
            )}
          </Stack>
        </DialogContent>
        <DialogActions sx={{ p: 2.5 }}>
          <Button onClick={() => setEditOpen(false)}>{tObj.common.cancel}</Button>
          <Button variant="contained" onClick={handleAskUpdate}>{tObj.common.save}</Button>
        </DialogActions>
      </Dialog>

      {/* Confirm Edit Dialog */}
      <ConfirmDialog
        open={editConfirm !== null}
        title={<>{tObj.terminals.editConfirmTitle} #{editingTerminalId}</>}
        question={tObj.terminals.editConfirmQuestion}
        confirmLabel={tObj.common.confirm}
        confirmColor="primary"
        onConfirm={handleUpdate}
        onCancel={() => setEditConfirm(null)}
      >
        {/* Построчно, что именно изменится: подтверждать «правку терминала» вслепую
            значит подтверждать не глядя — в форме рядом лежат логин, пароль и компания. */}
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

