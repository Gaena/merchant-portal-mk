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
   * Справочник провайдера и кнопку «Тест» видит только системный администратор, и заводит терминалы
   * тоже только он — выбором из справочника (Р-80, Р-93). Пароля у терминала нет: к провайдеру ходят
   * с кредами компании.
   */
  const isAdmin = user?.role === 'SYSTEM_ADMIN';
  const canCreate = canCreateTerminals(user?.role);
  /**
   * Править и блокировать терминалы могут SYSTEM_ADMIN, COMPANY_HEAD и COMPANY_MANAGER
   * (`TerminalService.TERMINAL_WRITE_ROLES`); AUDITOR и COMPANY_EMPLOYEE только смотрят. Кнопки,
   * которым сервер откажет, не показываются (Р-62).
   */
  const canWrite = canWriteTerminals(user?.role);
  /**
   * Компанию выбирает только SYSTEM_ADMIN. Остальные работают в своей, и она известна из токена;
   * список всех компаний им недоступен (`GET /companies` — 403), так что раньше у руководителя
   * и менеджера селект оставался пустым, а форма отказывала «заполните все поля».
   */
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
  const [form, setForm] = useState({ name: '', companyId: '' });
  /** Ошибка внутри открытого окна (заведение, правка). */
  const [error, setError] = useState('');
  /** Ошибка действия из таблицы («Тест», смена статуса): окна нет — полоса на странице. */
  const [pageError, setPageError] = useState('');
  const [snackbar, setSnackbar] = useState('');
  const [creating, setCreating] = useState(false);
  const [editBusy, setEditBusy] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  /**
   * Итог последней проверки по каждому терминалу. Хранится до перезагрузки списка: на новой странице
   * те же строки — уже другие терминалы.
   */
  const [checks, setChecks] = useState<Record<number, TerminalCheckResponse>>({});
  const [checking, setChecking] = useState<number | null>(null);
  // Справочник терминалов провайдера для формы заведения (Р-67, Р-79). Его видит только SYSTEM_ADMIN,
  // он же и заводит терминалы (Р-93).
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
  // форма правки меняет название и **компанию-владельца** — цена промаха разная.
  const [editConfirm, setEditConfirm] = useState<string[] | null>(null);
  const [busy, setBusy] = useState(false);

  // Страница берётся с сервера (P2-1): `/api/v1/terminals` отвечает `PagedResponse`.
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(20);
  const [totalElements, setTotalElements] = useState(0);

  const fetchTerminals = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    // Итоги проверок не переживают перезагрузку списка: на новой странице те же строки — уже другие
    // терминалы, и оставить итог на экране значило бы подписать им чужую проверку.
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

  const fetchCompanies = useCallback(async () => {
    try {
      if (choosesCompany || user?.role === 'AUDITOR') {
        // Компании нужны для выпадающего списка и подписей, поэтому берём их одной страницей
        // по потолку (200 — тот же лимит, что у журнала аудита).
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

  // Компания по умолчанию для форм: администратору — первая из списка, остальным — своя.
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
    setError('');
    setEditingTerminalId(null);
    setForm({ name: '', companyId: defaultCompanyId() });
    setSelectedProvider(null);
    setSyncResult(null);
    loadProviderTerminals();
    setCreateOpen(true);
  };

  const handleOpenEdit = (term: TerminalDto) => {
    setError('');
    setEditingTerminalId(term.id);
    // Компания — та, что у терминала. Подставлять первую из списка нельзя: так правка одного
    // названия молча перевешивала терминал на другую компанию.
    setForm({
      name: term.name || '',
      companyId: term.companyId || ''
    });
    setEditOpen(true);
  };

  // Заводит только администратор выбором из справочника: название и логин сервер берёт оттуда по
  // merchantRid, пароля у терминала нет (TerminalService.createTerminal, Р-93).
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
      });
      // Не дописываем строку в массив: список постраничный и отсортирован сервером по имени —
      // новый терминал может принадлежать другой странице.
      fetchTerminals();
      setCreateOpen(false);
      setForm({ name: '', companyId: defaultCompanyId() });
      setSnackbar(tObj.terminals.created);
    } catch (err: any) {
      setError(err.response?.data?.message || tObj.terminals.createFailed);
    } finally {
      setCreating(false);
    }
  };

  /**
   * «Тест» у терминала: пробный заказ с кредами компании (Р-93). Результат — всегда один из четырёх
   * исходов, даже при неверных кредах или недоступном провайдере; сбоем здесь считается только отказ самого портала (например,
   * нехватка прав), и он идёт в общую строку ошибки.
   */
  const runCheck = async (terminalId: number) => {
    setChecking(terminalId);
    setPageError('');
    try {
      const result = await checkExistingTerminal(terminalId);
      setChecks(prev => ({ ...prev, [terminalId]: result }));
    } catch (err: any) {
      setPageError(err.response?.data?.message || tObj.terminals.checkFailed);
    } finally {
      setChecking(null);
    }
  };

  // Терминал в заголовках окон подписан логином, как везде (Р-59): номер терминала на экране не
  // показывается — его выдаёт база и знать его пользователю незачем (Р-81).
  const editingLogin = terminals.find(t => t.id === editingTerminalId)?.login ?? '';

  const handleUpdate = async () => {
    if (!editingTerminalId || editBusy) return;
    const original = terminals.find(t => t.id === editingTerminalId);
    if (!original) return;
    setError('');
    setEditBusy(true);
    try {
      // Только то, что изменилось. PATCH с прежними значениями бэкенд всё равно записывает в
      // журнал аудита («Name changed from X to X») — лишняя строка там, где окно подтверждения
      // показало один пункт.
      const payload: Record<string, string> = {};
      if (form.name.trim() !== (original.name || '')) payload.name = form.name.trim();
      if (form.companyId !== (original.companyId || '')) payload.companyId = form.companyId;
      const res = await apiClient.patch(`/api/v1/terminals/${editingTerminalId}`, payload);
      setTerminals(prev => prev.map(t => (t.id === editingTerminalId ? res.data : t)));
      setEditOpen(false);
      setEditingTerminalId(null);
      setForm({ name: '', companyId: defaultCompanyId() });
      setSnackbar(tObj.terminals.updated);
    } catch (err: any) {
      setError(err.response?.data?.message || tObj.terminals.updateFailed);
    } finally {
      setEditBusy(false);
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
    if (form.companyId !== (original.companyId || '')) {
      changes.push(`${tObj.terminals.company}: ${getCompanyName(original.companyId || '')} → ${getCompanyName(form.companyId)}`);
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
    setPageError('');
    try {
      const res = await apiClient.patch(`/api/v1/terminals/${terminal.id}`, { status });
      setTerminals(prev => prev.map(t => (t.id === terminal.id ? res.data : t)));
      // Терминал в сообщении — логином, как везде (Р-59); номер пользователю ни к чему (Р-81).
      setSnackbar(`${terminal.login}: ${status === 'BLOCKED' ? tObj.terminals.blockedNotice : tObj.terminals.unblockedNotice}`);
    } catch (err: any) {
      setPageError(err.response?.data?.message || tObj.terminals.statusChangeFailed);
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
      {pageError && (
        <Alert severity="error" sx={{ mb: 3 }} onClose={() => setPageError('')}>
          {pageError}
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
                <TableCell sx={{ fontWeight: 700 }}>{tObj.terminals.name}</TableCell>
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
                  <TableCell sx={{ fontSize: '0.85rem', color: 'text.secondary' }}>
                    {term.createdAt ? new Date(term.createdAt).toLocaleString() : '—'}
                  </TableCell>
                  <TableCell align="center">
                    <Stack direction="row" spacing={0.5} justifyContent="center" alignItems="center">
                      {/* «Тест» — только администратору: проверка отвечает на вопрос, подходят ли
                          креды компании, и перебирать ключи другим ролям незачем. */}
                      {isAdmin && (
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
                      {canWrite && (
                        <Tooltip title={tObj.terminals.editTerminal}>
                          <IconButton color="primary" size="small" onClick={() => handleOpenEdit(term)}>
                            <EditIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      )}
                      {canWrite && (active ? (
                        <Tooltip title={tObj.terminals.blockAction}>
                          <IconButton color="warning" size="small" disabled={busy}
                                      onClick={() => handleAskStatus(term, 'BLOCKED')}>
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
                      ))}
                    </Stack>
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

      {/* Block confirmation: says what it will do, and to how many links */}
      <ConfirmDialog
        open={statusChange !== null}
        maxWidth="xs"
        title={<>
          {statusChange?.nextStatus === 'BLOCKED' ? tObj.terminals.blockAction : tObj.terminals.unblockAction}
          {' '}{statusChange?.terminal.login}
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

            <Box>
              {/* Название и логин — от провайдера (Р-67): выбирается строка справочника, пароля
                  у терминала нет (Р-93). Один терминал провайдера — одна компания: занятый
                  сервер отклонит с объяснением. */}
              <Autocomplete
                options={providerTerminals}
                value={selectedProvider}
                loading={providerState === 'loading'}
                onChange={(_, value) => setSelectedProvider(value)}
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
          </Stack>
        </DialogContent>
        <DialogActions sx={{ p: 2.5 }}>
          <Button onClick={() => setCreateOpen(false)} disabled={creating}>{tObj.common.cancel}</Button>
          <Button variant="contained" onClick={handleCreate} disabled={creating}>
            {creating ? tObj.common.loading : tObj.terminals.registerAction}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Edit Terminal Dialog */}
      <Dialog open={editOpen} onClose={() => { if (!editBusy) setEditOpen(false); }} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>{tObj.terminals.editDialogTitle} {editingLogin}</DialogTitle>
        <DialogContent>
          {error && <Alert severity="error" sx={{ mb: 2, mt: 1 }}>{error}</Alert>}
          <Stack spacing={2.5} sx={{ mt: 1 }}>
            {/* Перевесить терминал на другую компанию может только SYSTEM_ADMIN: остальным сервер
                откажет, поэтому им селект не показывается. */}
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
              fullWidth
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ p: 2.5 }}>
          <Button onClick={() => setEditOpen(false)} disabled={editBusy}>{tObj.common.cancel}</Button>
          <Button variant="contained" onClick={handleAskUpdate} disabled={editBusy}>{tObj.common.save}</Button>
        </DialogActions>
      </Dialog>

      {/* Confirm Edit Dialog */}
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
        {/* Построчно, что именно изменится: подтверждать «правку терминала» вслепую
            значит подтверждать не глядя — в форме рядом лежат название и компания. */}
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

