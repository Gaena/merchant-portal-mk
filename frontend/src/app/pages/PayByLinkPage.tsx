import React, { useState, useEffect, useCallback } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router';
import { apiClient } from '../api/client';
import { useLanguage } from '../context/LanguageContext';
import { useAuth } from '../context/AuthContext';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { LinkPaymentsStats } from '../components/LinkPaymentsStats';
import { LinkQrCode } from '../components/LinkQrCode';
import type { PeriodKey } from '../components/DashboardParts';
import {
  Box,
  Paper,
  Typography,
  Button,
  TextField,
  MenuItem,
  Chip,
  Stack,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  InputAdornment,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  Tooltip,
  Divider,
  Alert,
  Snackbar,
  ToggleButton,
  ToggleButtonGroup,
  LinearProgress,
  Tabs,
  Tab,
} from '@mui/material';
import {
  Link as LinkIcon,
  Add as AddIcon,
  ContentCopy as CopyIcon,
  Share as ShareIcon,
  Cancel as CancelIcon,
  CheckCircle as CheckCircleIcon,
  ErrorOutline as ExpiredIcon,
  HelpOutline as UnknownStatusIcon,
  WhatsApp as WhatsAppIcon,
  Email as EmailIcon,
  FilterList as FilterIcon,
  Person as PersonIcon,
  AccessTime as TimeIcon,
  Insights as StatsIcon,
} from '@mui/icons-material';

import {
  formatDateTime,
  formatTimeLeft,
  getLinkStatusColors,
  mailtoHref,
  parseLinkStatus,
  parseLinkUsageType,
  parsePaymentType,
  whatsAppHref,
} from '../utils/payByLinkData';
import type {
  PaymentLink,
  LinkStatus,
  LinkUsageType,
  PaymentType,
} from '../utils/payByLinkData';
import { isTerminalActive } from '../types/dto';
import type { TerminalOptionDto } from '../types/dto';
import { terminalOptionLabel } from '../utils/terminals';
import { formatCurrency } from '../utils/format';
import { linkStatusLabel } from '../i18n/translations';
import type { TranslationDictionary } from '../i18n/translations';

// null — статус вне словаря бэкенда: вопросительный знак, чтобы неизвестное не приняли за знакомое.
const statusIcon = (status: LinkStatus | null) => {
  switch (status) {
    case 'ACTIVE':
    case 'COMPLETED':
      return <CheckCircleIcon sx={{ fontSize: 14 }} />;
    case 'EXPIRED':
      return <ExpiredIcon sx={{ fontSize: 14 }} />;
    case 'CANCELED':
      return <CancelIcon sx={{ fontSize: 14 }} />;
    default:
      return <UnknownStatusIcon sx={{ fontSize: 14 }} />;
  }
};

const getStatusConfig = (link: PaymentLink, tObj: TranslationDictionary) => ({
  label: linkStatusLabel(tObj, link.status, link.statusRaw),
  color: getLinkStatusColors(link.status).color,
  icon: statusIcon(link.status),
});

const messageFrom = (err: any, fallback: string): string =>
  err?.response?.data?.message || err?.response?.data?.error || fallback;

// Потолок срока на бэкенде — `pbl.link.max-ttl` (90 дней), 30 дней в него укладываются.
type ExpiryOption = 'h1' | 'h24' | 'h72' | 'd7' | 'd30';
const EXPIRY_MS: Record<ExpiryOption, number> = {
  h1: 3_600_000,
  h24: 86_400_000,
  h72: 3 * 86_400_000,
  d7: 7 * 86_400_000,
  d30: 30 * 86_400_000,
};
const EXPIRY_OPTIONS: ExpiryOption[] = ['h1', 'h24', 'h72', 'd7', 'd30'];

// Азербайджанский номер: +994, 994 или 0 и 9 цифр; пробелы, дефисы и скобки допустимы (Р-96). Зеркало
// CustomerPhone в pbl — меняется вместе с ним.
const isAzerbaijaniPhone = (value: string): boolean =>
  /^(?:\+994|994|0)\d{9}$/.test(value.trim().replace(/[\s\-()]/g, ''));

const emptyForm = () => ({
  terminalId: '', amount: '', currency: 'AZN', description: '', customerName: '',
  customerEmail: '', customerPhone: '', usageType: 'SINGLE' as LinkUsageType, maxUses: '2',
  expiry: 'h24' as ExpiryOption, paymentType: 'SMS' as PaymentType,
});

/**
 * Форма для «Создать новую ссылку с теми же данными». Срок — по умолчанию: прежний истёк; терминал
 * подставляется, когда известен список активных. Тип вне словаря (`null`) — значение по умолчанию:
 * это выбор в форме, мерчант видит его до создания.
 */
const formFromLink = (link: PaymentLink) => ({
  ...emptyForm(),
  amount: String(link.amount),
  currency: link.currency || 'AZN',
  description: link.description,
  // У многоразовой ссылки клиента нет (Р-96): у старых он мог остаться в базе, но в новую не переносится.
  customerName: link.usageType === 'MULTIPLE' ? '' : link.customerName,
  customerEmail: link.usageType === 'MULTIPLE' ? '' : link.customerEmail,
  customerPhone: link.usageType === 'MULTIPLE' ? '' : link.customerPhone,
  usageType: link.usageType ?? 'SINGLE',
  maxUses: link.usageType === 'MULTIPLE' ? String(link.maxUses) : '2',
  paymentType: link.paymentType ?? 'SMS',
});

const mapLink = (l: any, fallbackTerminal?: number): PaymentLink => ({
  id: l.id,
  shortCode: String(l.id).slice(0, 8).toUpperCase(),
  // `PaymentLinkResponse.link` собран сервером из `pbl.base-url`; в списочном ответе поля нет,
  // там адрес строится от origin портала.
  url: typeof l.link === 'string' && l.link ? l.link : `${window.location.origin}/api/v1/payment-links/${l.id}/open`,
  status: parseLinkStatus(l.status),
  statusRaw: l.status === null || l.status === undefined ? undefined : String(l.status),
  amount: Number(l.amount),
  currency: l.currency || 'AZN',
  description: l.description || '',
  // Пусто — не указано (Р-48): заглушка вроде «N/A» зажгла бы кнопки письма и WhatsApp.
  customerName: l.customer?.fullName || l.customerName || '',
  customerEmail: l.customer?.email || l.customerEmail || '',
  customerPhone: l.customer?.phone || l.customerPhone || '',
  usageType: parseLinkUsageType(l.usageType),
  maxUses: l.maxPayments || 1,
  // Списочный ответ счётчиков не несёт (P2-16) — в списке оба поля всегда 0 и не показываются.
  usedCount: l.currentPaymentsCount || 0,
  refundedCount: l.refundedPaymentsCount || 0,
  createdAt: new Date(l.createdAt),
  expiresAt: l.expiresAt ? new Date(l.expiresAt) : new Date(Date.now() + 86400000),
  paymentType: parsePaymentType(l.paymentType),
  // Последний успешный платёж (P2-15, Р-46); пусто — платежей не было, ничего не подставлять.
  paidAt: l.lastPaidAt ? new Date(l.lastPaidAt) : undefined,
  // Без терминала карточка, открытая из списка, ждала бы своего запроса, показывая прочерк.
  terminalId: typeof l.terminal === 'number' ? l.terminal : fallbackTerminal,
});

type PayByLinkTab = 'links' | 'stats';

export const PayByLinkPage: React.FC = () => {
  const navigate = useNavigate();
  const { tObj } = useLanguage();
  // Вкладка — в адресе (`?tab=stats`), незнакомое значение — список. Переключение заменяет запись
  // истории: «назад» уводит со страницы, а не листает вкладки.
  const [searchParams, setSearchParams] = useSearchParams();
  const tab: PayByLinkTab = searchParams.get('tab') === 'stats' ? 'stats' : 'links';
  const handleTabChange = (_: React.SyntheticEvent, value: PayByLinkTab) => {
    setSearchParams(prev => {
      const next = new URLSearchParams(prev);
      if (value === 'stats') next.set('tab', 'stats');
      else next.delete('tab');
      return next;
    }, { replace: true });
  };
  // Период статистики хранится здесь: вкладка размонтируется при переключении, и выбор
  // сбрасывался бы на 7 дней.
  const [statsPeriod, setStatsPeriod] = useState<PeriodKey>('days7');
  const [links, setLinks] = useState<PaymentLink[]>([]);
  const [createOpen, setCreateOpen] = useState(false);
  const [shareOpen, setShareOpen] = useState(false);
  const [selectedLink, setSelectedLink] = useState<PaymentLink | null>(null);
  // Саму ссылку, а не id: окну нужны `shortCode` и сумма, чтобы отличить её от похожих строк (P3-5a).
  const [cancelTarget, setCancelTarget] = useState<PaymentLink | null>(null);
  const [cancelBusy, setCancelBusy] = useState(false);
  // Фильтр по статусу — серверный: клиентский видел бы только текущую страницу. Поиска нет —
  // у `GET /payment-links` нет параметра.
  const [statusFilter, setStatusFilter] = useState<LinkStatus | 'all'>('all');
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; error?: boolean }>({ open: false, message: '' });

  const [form, setForm] = useState(emptyForm);
  const [formError, setFormError] = useState('');
  const [generating, setGenerating] = useState(false);
  const [newlyCreatedLink, setNewlyCreatedLink] = useState<PaymentLink | null>(null);

  // Сводку по ссылкам считает бэкенд (вкладка «Статистика»), по странице списка её не считать.

  // «Скопировано» — только после успеха `writeText`: в небезопасном контексте он отказывает.
  const handleCopy = (url: string) => {
    navigator.clipboard.writeText(url)
      .then(() => setSnackbar({ open: true, message: tObj.common.copied }))
      .catch(() => setSnackbar({ open: true, message: tObj.common.error, error: true }));
  };

  const handleShare = (link: PaymentLink) => {
    setSelectedLink(link);
    setShareOpen(true);
  };

  const [terminals, setTerminals] = useState<TerminalOptionDto[]>([]);

  // Р-132: DMS — если разрешён и пользователю (у администратора не проверяется), и выбранному терминалу.
  // Решает сервер, здесь — подсказка: недоступный DMS гаснет, а выбранный переключается на SMS.
  const { user } = useAuth();
  const formTerminal = terminals.find(t => t.id === (form.terminalId ? Number(form.terminalId) : terminals[0]?.id));
  const dmsForbiddenToUser = user?.role !== 'SYSTEM_ADMIN' && user?.dmsLinksAllowed !== true;
  const dmsForbiddenOnTerminal = formTerminal?.dmsAllowed === false;
  const dmsAvailable = !dmsForbiddenToUser && !dmsForbiddenOnTerminal;
  useEffect(() => {
    if (!dmsAvailable && form.paymentType === 'DMS') {
      setForm(f => ({ ...f, paymentType: 'SMS' }));
    }
  }, [dmsAvailable, form.paymentType]);

  const [totalElements, setTotalElements] = useState(0);

  // Страница уже нарезана сервером: резать её на клиенте нельзя — вторая и дальше окажутся пустыми.
  const fetchPaymentLinks = useCallback(() => {
    const params: Record<string, unknown> = { page, size: rowsPerPage };
    if (statusFilter !== 'all') params.status = statusFilter;
    apiClient.get('/api/v1/payment-links', { params })
      .then(res => {
        const rawContent = Array.isArray(res.data) ? res.data : (res.data?.content || []);
        setTotalElements(res.data?.totalElements ?? rawContent.length);
        if (Array.isArray(rawContent)) {
          setLinks(rawContent.map((l: any) => mapLink(l)));
        }
      })
      .catch(err => {
        // Список не очищается: перечитывание идёт и после отмены (Р-34), и сбой не должен
        // стирать строки, о судьбе которых ничего не известно.
        console.warn('[pay-by-link] не удалось получить список ссылок:', err);
      });
  }, [page, rowsPerPage, statusFilter]);

  // Строку не правим локально (Р-34): и после успеха, и после отказа она перечитывается с сервера,
  // иначе неудавшаяся отмена нарисовала бы ссылку отменённой, пока по ней платят.
  const handleCancel = async () => {
    if (!cancelTarget) return;
    setCancelBusy(true);
    try {
      await apiClient.patch(`/api/v1/payment-links/${cancelTarget.id}`, { status: 'CANCELED' });
      setSnackbar({ open: true, message: tObj.payByLink.linkCancelledSuccess, error: false });
    } catch (err) {
      setSnackbar({ open: true, message: messageFrom(err, tObj.payByLink.linkCancelFailed), error: true });
    } finally {
      // Окно закрывается и после отказа — причина видна в snackbar; так же ведёт себя карточка.
      setCancelBusy(false);
      setCancelTarget(null);
      fetchPaymentLinks();
    }
  };

  useEffect(() => {
    fetchPaymentLinks();

    // Лёгкий список (Р-45), фильтрует потребитель: только активные — на заблокированном
    // бэкенд откажет в создании ссылки (P2-8).
    apiClient.get('/api/v1/terminals/options')
      .then(res => {
        const rawContent = Array.isArray(res.data) ? res.data : (res.data?.content || []);
        setTerminals(Array.isArray(rawContent) ? rawContent.filter(isTerminalActive) : []);
      })
      .catch(() => {});
  }, [fetchPaymentLinks]);

  // `state.prefill` с карточки ссылки сразу стирается из истории, иначе обновление страницы
  // открыло бы форму снова; `?tab=stats` уходит тоже — форма над списком ссылок.
  const location = useLocation();
  const [prefillTerminalId, setPrefillTerminalId] = useState<number | null>(null);
  useEffect(() => {
    const prefill = (location.state as { prefill?: PaymentLink } | null)?.prefill;
    if (!prefill) return;
    setForm(formFromLink(prefill));
    setPrefillTerminalId(prefill.terminalId ?? null);
    setNewlyCreatedLink(null);
    setFormError('');
    setCreateOpen(true);
    const params = new URLSearchParams(location.search);
    params.delete('tab');
    const search = params.toString();
    navigate(`${location.pathname}${search ? `?${search}` : ''}`, { replace: true, state: null });
  }, [location, navigate]);

  // Терминал ссылки встаёт в форму, только если он среди активных: на заблокированном бэкенд
  // откажет в создании (P2-8). Иначе поле пустое, и форма предлагает первый активный. Пока
  // список не пришёл, терминал ждёт: пустой список до ответа и «активных нет» не различить.
  useEffect(() => {
    if (prefillTerminalId === null || terminals.length === 0) return;
    const active = terminals.some(t => t.id === prefillTerminalId);
    setForm(f => ({ ...f, terminalId: active ? String(prefillTerminalId) : '' }));
    setPrefillTerminalId(null);
  }, [terminals, prefillTerminalId]);

  const handleGenerate = async () => {
    if (generating) return;
    const selectedTerminal = form.terminalId ? Number(form.terminalId) : terminals[0]?.id;
    if (!selectedTerminal) {
      setFormError(tObj.payByLink.noActiveTerminals);
      return;
    }
    if (!form.amount || isNaN(Number(form.amount)) || Number(form.amount) <= 0) {
      setFormError(tObj.payByLink.invalidAmount);
      return;
    }
    if (!form.description.trim()) {
      setFormError(tObj.payByLink.descriptionRequired);
      return;
    }
    // Лимит платежей — целое число не меньше единицы, как на бэкенде (`maxPayments > 0`).
    const maxPayments = Number(form.maxUses);
    if (form.usageType === 'MULTIPLE' && (!Number.isInteger(maxPayments) || maxPayments < 1)) {
      setFormError(tObj.payByLink.invalidMaxUses);
      return;
    }
    // Телефон — только азербайджанский (Р-96), то же правило, что CustomerPhone на бэкенде.
    if (form.usageType === 'SINGLE' && form.customerPhone.trim() && !isAzerbaijaniPhone(form.customerPhone)) {
      setFormError(tObj.payByLink.customerPhoneInvalid);
      return;
    }
    setFormError('');
    setGenerating(true);

    try {
      const payload: Record<string, unknown> = {
        terminal: selectedTerminal,
        amount: parseFloat(form.amount),
        currency: form.currency || 'AZN',
        description: form.description.trim(),
        paymentType: form.paymentType,
        usageType: form.usageType,
        expiresAt: new Date(Date.now() + EXPIRY_MS[form.expiry]).toISOString(),
      };

      if (form.usageType === 'MULTIPLE') {
        payload.maxPayments = maxPayments;
      }

      // Только введённое: пустое поле клиента — null, без заглушек (Р-48).
      const customer = {
        fullName: form.customerName.trim() || null,
        email: form.customerEmail.trim() || null,
        phone: form.customerPhone.trim() || null,
      };
      // Клиент — только у одноразовой ссылки: у многоразовой бэкенд его отвергает (Р-96).
      if (form.usageType === 'SINGLE' && (customer.fullName || customer.email || customer.phone)) {
        payload.customer = customer;
      }

      const res = await apiClient.post('/api/v1/payment-links', payload);
      const newLink = mapLink(res.data, selectedTerminal);

      setNewlyCreatedLink(newLink);
      setForm(emptyForm());
      // Список перечитывается с сервера, а не дописывается: он постраничный и отсортирован там.
      fetchPaymentLinks();
    } catch (err: any) {
      setFormError(messageFrom(err, tObj.payByLink.createFailed));
    } finally {
      setGenerating(false);
    }
  };

  // Пока запрос идёт, окно не закрыть: иначе ответ придёт в закрытое окно, и следующее открытие
  // покажет «ссылка создана» про прошлую ссылку.
  const handleCloseCreate = () => {
    if (generating) return;
    setCreateOpen(false);
    setNewlyCreatedLink(null);
    setFormError('');
  };

  return (
    <Box>
      <Box sx={{ mb: 2, display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 600, mb: 0.5 }}>
            {tObj.payByLink.title}
          </Typography>
          <Typography variant="body1" color="text.secondary">
            {tObj.payByLink.subtitle}
          </Typography>
        </Box>
        <Button
          variant="contained"
          size="large"
          startIcon={<AddIcon />}
          onClick={() => setCreateOpen(true)}
          sx={{ fontWeight: 600 }}
        >
          {tObj.payByLink.createButton}
        </Button>
      </Box>

      <Tabs value={tab} onChange={handleTabChange} sx={{ mb: 3, borderBottom: 1, borderColor: 'divider' }}>
        <Tab value="links" id="pay-by-link-tab-links" aria-controls="pay-by-link-panel-links"
             icon={<LinkIcon />} iconPosition="start" label={tObj.payByLink.tabs.links} sx={{ minHeight: 48 }} />
        <Tab value="stats" id="pay-by-link-tab-stats" aria-controls="pay-by-link-panel-stats"
             icon={<StatsIcon />} iconPosition="start" label={tObj.payByLink.tabs.stats} sx={{ minHeight: 48 }} />
      </Tabs>

      {/* Статистика оплат по ссылкам (Р-91) монтируется только на своей вкладке: сводку pbl не
          запрашивают, пока её не открыли, и при каждом открытии она свежая. */}
      {tab === 'stats' && (
        <Box role="tabpanel" id="pay-by-link-panel-stats" aria-labelledby="pay-by-link-tab-stats">
          <LinkPaymentsStats period={statsPeriod} onPeriodChange={setStatsPeriod} />
        </Box>
      )}

      {tab === 'links' && (
        <Paper elevation={0} role="tabpanel" id="pay-by-link-panel-links" aria-labelledby="pay-by-link-tab-links"
               sx={{ border: '1px solid', borderColor: 'divider' }}>
          <Box sx={{ p: 2.5, display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap', borderBottom: '1px solid', borderColor: 'divider' }}>
            <FilterIcon color="action" />
            {/* Счётчиков в кнопках нет: страница — не вся выборка. */}
            <ToggleButtonGroup
              size="small"
              exclusive
              value={statusFilter}
              onChange={(_, v) => { if (v !== null) { setStatusFilter(v); setPage(0); } }}
            >
              <ToggleButton value="all">{tObj.common.all}</ToggleButton>
              <ToggleButton value="ACTIVE">{tObj.payByLink.statuses.ACTIVE}</ToggleButton>
              <ToggleButton value="COMPLETED">{tObj.payByLink.statuses.COMPLETED}</ToggleButton>
              <ToggleButton value="EXPIRED">{tObj.payByLink.statuses.EXPIRED}</ToggleButton>
              <ToggleButton value="CANCELED">{tObj.payByLink.statuses.CANCELED}</ToggleButton>
              <ToggleButton value="SUSPENDED">{tObj.payByLink.statuses.SUSPENDED}</ToggleButton>
            </ToggleButtonGroup>
            <Box sx={{ ml: 'auto' }}>
              <Typography variant="body2" color="text.secondary">
                {totalElements}
              </Typography>
            </Box>
          </Box>

          <TableContainer>
            <Table>
              <TableHead>
                <TableRow sx={{ bgcolor: 'rgba(0,0,0,0.02)' }}>
                  <TableCell sx={{ fontWeight: 600 }}>{tObj.payByLink.table.linkId}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{tObj.payByLink.table.customer}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{tObj.payByLink.descriptionLabel.replace(' *', '')}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }} align="right">{tObj.payByLink.table.amount}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{tObj.payByLink.table.type}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{tObj.payByLink.table.status}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{tObj.payByLink.table.usage}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{tObj.payByLink.table.expires}</TableCell>
                  <TableCell sx={{ fontWeight: 600 }} align="center">{tObj.payByLink.table.actions}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {links.map(link => {
                  const cfg = getStatusConfig(link, tObj);
                  const expiryProgress = link.status === 'ACTIVE'
                    ? Math.max(0, Math.min(100, ((link.expiresAt.getTime() - Date.now()) / (link.expiresAt.getTime() - link.createdAt.getTime())) * 100))
                    : null;

                  return (
                    <TableRow
                      key={link.id}
                      hover
                      onClick={() => navigate(`/pay-by-link/${link.id}`, { state: { link } })}
                      sx={{ cursor: 'pointer', '&:hover': { bgcolor: 'rgba(0,0,0,0.02)' } }}
                    >
                      <TableCell>
                        <Box>
                          <Typography variant="body2" sx={{ fontFamily: 'monospace', fontWeight: 700, color: 'primary.main', letterSpacing: 0.5 }}>
                            {link.shortCode}
                          </Typography>
                          <Typography variant="caption" color="text.disabled" sx={{ fontSize: '0.7rem' }}>
                            {link.url}
                          </Typography>
                        </Box>
                      </TableCell>

                      <TableCell>
                        {link.customerName ? (
                          <Box>
                            <Typography variant="body2" sx={{ fontWeight: 600 }}>
                              {link.customerName}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {link.customerEmail}
                            </Typography>
                          </Box>
                        ) : (
                          <Typography variant="body2" color="text.disabled" sx={{ fontStyle: 'italic' }}>
                            {tObj.payByLink.customerNotSpecified}
                          </Typography>
                        )}
                      </TableCell>

                      <TableCell>
                        <Typography variant="body2" sx={{ maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {link.description}
                        </Typography>
                      </TableCell>

                      <TableCell align="right">
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>
                          {formatCurrency(link.amount, link.currency)}
                        </Typography>
                      </TableCell>

                      <TableCell>
                        <Chip
                          label={link.paymentType ?? '—'}
                          size="small"
                          variant="outlined"
                          color={link.paymentType === 'DMS' ? 'warning' : link.paymentType === 'SMS' ? 'primary' : 'default'}
                          sx={{ fontWeight: 700, fontFamily: 'monospace', fontSize: '0.7rem' }}
                        />
                      </TableCell>

                      <TableCell>
                        <Chip
                          icon={cfg.icon as React.ReactElement}
                          label={cfg.label}
                          color={cfg.color}
                          size="small"
                          variant={link.status === 'ACTIVE' ? 'filled' : 'outlined'}
                          sx={{ fontWeight: 600 }}
                        />
                      </TableCell>

                      <TableCell>
                        {/* Счётчика платежей в списочном ответе нет (P2-16): только лимит. */}
                        {link.usageType === 'MULTIPLE' ? (
                          <Typography variant="caption" sx={{ fontWeight: 600 }}>
                            {tObj.payByLink.multipleUse} · {link.maxUses}
                          </Typography>
                        ) : (
                          <Typography variant="caption" color="text.secondary">
                            {link.usageType === 'SINGLE' ? tObj.payByLink.singleUse : '—'}
                          </Typography>
                        )}
                      </TableCell>

                      <TableCell>
                        {/* Дата оплаты вместо срока — только у завершённой ссылки (Р-47): у активной
                            важнее, сколько ей осталось. */}
                        {link.status === 'COMPLETED' && link.paidAt ? (
                          <Box>
                            <Typography variant="caption" color="success.main" sx={{ fontWeight: 600 }}>
                              {tObj.payByLink.paid}
                            </Typography>
                            <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                              {formatDateTime(link.paidAt)}
                            </Typography>
                          </Box>
                        ) : (
                          <Box>
                            <Typography variant="caption" sx={{ fontWeight: 600, color: link.status === 'ACTIVE' && expiryProgress !== null && expiryProgress < 20 ? 'error.main' : 'text.primary' }}>
                              {link.status === 'ACTIVE' ? formatTimeLeft(link.expiresAt) : formatDateTime(link.expiresAt)}
                            </Typography>
                            {link.status === 'ACTIVE' && expiryProgress !== null && (
                              <LinearProgress
                                variant="determinate"
                                value={expiryProgress}
                                color={expiryProgress < 20 ? 'error' : expiryProgress < 50 ? 'warning' : 'primary'}
                                sx={{ mt: 0.5, height: 3, borderRadius: 2 }}
                              />
                            )}
                          </Box>
                        )}
                      </TableCell>

                      <TableCell align="center" onClick={e => e.stopPropagation()}>
                        <Stack direction="row" spacing={0.5} justifyContent="center">
                          <Tooltip title={tObj.payByLink.copyLink}>
                            <span>
                              <IconButton size="small" onClick={() => handleCopy(link.url)} disabled={link.status !== 'ACTIVE'}>
                                <CopyIcon fontSize="small" />
                              </IconButton>
                            </span>
                          </Tooltip>
                          <Tooltip title={tObj.payByLink.share}>
                            <span>
                              <IconButton size="small" onClick={() => handleShare(link)} disabled={link.status !== 'ACTIVE'}>
                                <ShareIcon fontSize="small" />
                              </IconButton>
                            </span>
                          </Tooltip>
                          <Tooltip title={tObj.payByLink.cancelLinkAction}>
                            <span>
                              <IconButton
                                size="small"
                                color="error"
                                onClick={() => setCancelTarget(link)}
                                disabled={link.status !== 'ACTIVE'}
                              >
                                <CancelIcon fontSize="small" />
                              </IconButton>
                            </span>
                          </Tooltip>
                        </Stack>
                      </TableCell>
                    </TableRow>
                  );
                })}
                {links.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={9} align="center" sx={{ py: 6 }}>
                      <LinkIcon sx={{ fontSize: 48, color: 'text.disabled', mb: 1 }} />
                      <Typography color="text.secondary">{tObj.payByLink.empty}</Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>

          <TablePagination
            rowsPerPageOptions={[10, 25, 50]}
            component="div"
            count={totalElements}
            rowsPerPage={rowsPerPage}
            page={page}
            onPageChange={(_, p) => setPage(p)}
            onRowsPerPageChange={e => { setRowsPerPage(parseInt(e.target.value)); setPage(0); }}
          />
        </Paper>
      )}

      <Dialog open={createOpen} onClose={handleCloseCreate} maxWidth="sm" fullWidth scroll="paper">
        <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1.5, pb: 1 }}>
          <Box sx={{ p: 1, borderRadius: 1.5, bgcolor: 'primary.light', color: 'white', display: 'flex' }}>
            <LinkIcon />
          </Box>
          <Box>
            <Box sx={{ fontWeight: 700, fontSize: '1.25rem' }}>{tObj.payByLink.createTitle}</Box>
            <Typography variant="caption" color="text.secondary">
              {tObj.payByLink.createSubtitle}
            </Typography>
          </Box>
        </DialogTitle>

        <Divider />

        <DialogContent sx={{ pt: 3 }}>
          {newlyCreatedLink ? (
            <Box>
              <Alert severity="success" sx={{ mb: 3 }}>
                {tObj.payByLink.createdTitle}
              </Alert>
              <Paper variant="outlined" sx={{ p: 2.5, mb: 3, borderRadius: 2, bgcolor: 'action.hover' }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, textTransform: 'uppercase', letterSpacing: 0.5 }}>
                  {tObj.payByLink.linkLabel}
                </Typography>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 0.75 }}>
                  <Typography
                    variant="body1"
                    sx={{ flex: 1, fontFamily: 'monospace', fontWeight: 700, color: 'primary.main', wordBreak: 'break-all' }}
                  >
                    {newlyCreatedLink.url}
                  </Typography>
                  <Tooltip title={tObj.common.copy}>
                    <IconButton size="small" onClick={() => handleCopy(newlyCreatedLink.url)}>
                      <CopyIcon />
                    </IconButton>
                  </Tooltip>
                </Box>
              </Paper>
              <Stack spacing={1.5}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" color="text.secondary">{tObj.payByLink.table.amount}</Typography>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>
                    {formatCurrency(newlyCreatedLink.amount, newlyCreatedLink.currency)}
                  </Typography>
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" color="text.secondary">{tObj.payByLink.table.expires}</Typography>
                  <Typography variant="body2">{formatDateTime(newlyCreatedLink.expiresAt)}</Typography>
                </Box>
                {newlyCreatedLink.customerEmail && (
                  <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                    <Typography variant="body2" color="text.secondary">{tObj.payByLink.table.customer}</Typography>
                    <Typography variant="body2">{newlyCreatedLink.customerEmail}</Typography>
                  </Box>
                )}
              </Stack>
              <Stack direction="row" spacing={1.5} sx={{ mt: 3 }}>
                <Button
                  variant="outlined"
                  startIcon={<EmailIcon />}
                  fullWidth
                  href={mailtoHref(newlyCreatedLink, tObj.payByLink.emailSubject, tObj.payByLink.messageText)}
                  disabled={!newlyCreatedLink.customerEmail}
                >
                  {tObj.payByLink.sendEmail}
                </Button>
                <Button
                  variant="outlined"
                  color="success"
                  startIcon={<WhatsAppIcon />}
                  fullWidth
                  href={whatsAppHref(newlyCreatedLink, tObj.payByLink.messageText)}
                  target="_blank"
                  rel="noopener"
                  disabled={!newlyCreatedLink.customerPhone}
                >
                  {tObj.payByLink.sendWhatsApp}
                </Button>
              </Stack>
            </Box>
          ) : (
            <Stack spacing={3}>
              {terminals.length === 0 ? (
                <Alert severity="warning">{tObj.payByLink.noActiveTerminals}</Alert>
              ) : (
                <TextField
                  select
                  fullWidth
                  label={tObj.payByLink.terminalSelect}
                  value={form.terminalId || (terminals[0]?.id ?? '')}
                  onChange={e => setForm(f => ({ ...f, terminalId: e.target.value }))}
                  helperText={tObj.payByLink.terminalHelper}
                >
                  {terminals.map((t) => (
                    <MenuItem key={t.id} value={t.id}>
                      <Box component="span" sx={{ fontFamily: 'monospace', fontWeight: 700 }}>
                        {terminalOptionLabel(t)}
                      </Box>
                      {t.name && t.name !== terminalOptionLabel(t) && (
                        <Box component="span" sx={{ ml: 1, color: 'text.secondary' }}>
                          {t.name}
                        </Box>
                      )}
                    </MenuItem>
                  ))}
                </TextField>
              )}

              {formError && <Alert severity="error">{formError}</Alert>}

              <Box>
                <Typography variant="subtitle2" sx={{ mb: 1.5, fontWeight: 700, color: 'text.primary' }}>
                  {tObj.payByLink.amountLabel}
                </Typography>
                <Box sx={{ display: 'flex', gap: 1.5 }}>
                  <TextField
                    fullWidth
                    label={tObj.payByLink.table.amount}
                    type="number"
                    placeholder="0.00"
                    value={form.amount}
                    onChange={e => setForm(f => ({ ...f, amount: e.target.value }))}
                    InputProps={{
                      startAdornment: <InputAdornment position="start">{form.currency}</InputAdornment>,
                    }}
                    inputProps={{ min: 0, step: '0.01' }}
                  />
                  <TextField
                    select
                    label={tObj.payByLink.currencyLabel}
                    value={form.currency}
                    onChange={e => setForm(f => ({ ...f, currency: e.target.value }))}
                    sx={{ minWidth: 110 }}
                  >
                    <MenuItem value="AZN">AZN</MenuItem>
                    <MenuItem value="USD">USD</MenuItem>
                    <MenuItem value="EUR">EUR</MenuItem>
                  </TextField>
                </Box>
              </Box>

              <TextField
                fullWidth
                label={tObj.payByLink.descriptionLabel}
                value={form.description}
                onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
                helperText={tObj.payByLink.descriptionHint}
              />

              {/* Customer — только у одноразовой ссылки (Р-96): многоразовой платят разные люди. */}
              {form.usageType === 'SINGLE' && (
              <Box>
                <Typography variant="subtitle2" sx={{ mb: 1.5, fontWeight: 700, color: 'text.primary' }}>
                  {tObj.payByLink.customerSection}
                </Typography>
                <Stack spacing={2}>
                  <TextField
                    fullWidth
                    label={tObj.payByLink.customerNameLabel}
                    value={form.customerName}
                    onChange={e => setForm(f => ({ ...f, customerName: e.target.value }))}
                    InputProps={{ startAdornment: <InputAdornment position="start"><PersonIcon color="action" /></InputAdornment> }}
                  />
                  <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
                    <TextField
                      fullWidth
                      label={tObj.payByLink.customerEmailLabel}
                      type="email"
                      value={form.customerEmail}
                      onChange={e => setForm(f => ({ ...f, customerEmail: e.target.value }))}
                    />
                    <TextField
                      fullWidth
                      label={tObj.payByLink.customerPhoneLabel}
                      value={form.customerPhone}
                      onChange={e => setForm(f => ({ ...f, customerPhone: e.target.value }))}
                      placeholder="+994 70 330 10 25"
                      helperText={tObj.payByLink.customerPhoneHint}
                    />
                  </Box>
                </Stack>
              </Box>
              )}

              {/* Полей «redirect URL», «заметка» и «отправить письмо» нет: бэкенд их не принимает. */}
              <Box>
                <Typography variant="subtitle2" sx={{ mb: 1.5, fontWeight: 700, color: 'text.primary' }}>
                  {tObj.payByLink.linkSettings}
                </Typography>
                <Stack spacing={2}>
                  <Box>
                    <Typography variant="body2" sx={{ mb: 0.75, fontWeight: 600, color: 'text.primary' }}>
                      {tObj.payByLink.paymentTypeLabel}
                    </Typography>
                    <ToggleButtonGroup
                      exclusive
                      fullWidth
                      size="small"
                      value={form.paymentType}
                      onChange={(_, v) => { if (v) setForm(f => ({ ...f, paymentType: v })); }}
                    >
                      <ToggleButton value="SMS">SMS</ToggleButton>
                      <ToggleButton value="DMS" disabled={!dmsAvailable}>DMS</ToggleButton>
                    </ToggleButtonGroup>
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.75 }}>
                      {form.paymentType === 'SMS' ? tObj.payByLink.smsHint : tObj.payByLink.dmsHint}
                    </Typography>
                    {!dmsAvailable && (
                      <Typography variant="caption" color="warning.main" sx={{ display: 'block', mt: 0.5 }}>
                        {dmsForbiddenToUser ? tObj.payByLink.dmsForbiddenUser : tObj.payByLink.dmsForbiddenTerminal}
                      </Typography>
                    )}
                  </Box>

                  <TextField
                    select
                    fullWidth
                    label={tObj.payByLink.expirationLabel}
                    value={form.expiry}
                    onChange={e => setForm(f => ({ ...f, expiry: e.target.value as ExpiryOption }))}
                    InputProps={{ startAdornment: <InputAdornment position="start"><TimeIcon color="action" /></InputAdornment> }}
                  >
                    {EXPIRY_OPTIONS.map(option => (
                      <MenuItem key={option} value={option}>{tObj.payByLink.expiry[option]}</MenuItem>
                    ))}
                  </TextField>

                  <Box>
                    <Typography variant="body2" sx={{ mb: 1, color: 'text.secondary' }}>{tObj.payByLink.usageTypeLabel}</Typography>
                    <ToggleButtonGroup
                      exclusive
                      fullWidth
                      size="small"
                      value={form.usageType}
                      onChange={(_, v) => { if (v) setForm(f => ({ ...f, usageType: v })); }}
                    >
                      <ToggleButton value="SINGLE">{tObj.payByLink.singleUse}</ToggleButton>
                      <ToggleButton value="MULTIPLE">{tObj.payByLink.multipleUse}</ToggleButton>
                    </ToggleButtonGroup>
                    {form.usageType === 'MULTIPLE' && (
                      <TextField
                        fullWidth
                        label={tObj.payByLink.maxUsesLabel}
                        type="number"
                        value={form.maxUses}
                        onChange={e => setForm(f => ({ ...f, maxUses: e.target.value }))}
                        sx={{ mt: 1.5 }}
                        inputProps={{ min: 1, max: 100, step: 1 }}
                        helperText={tObj.payByLink.maxUsesHint}
                      />
                    )}
                  </Box>
                </Stack>
              </Box>
            </Stack>
          )}
        </DialogContent>

        <Divider />
        <DialogActions sx={{ p: 2.5, gap: 1 }}>
          {newlyCreatedLink ? (
            <Button onClick={handleCloseCreate} variant="contained" fullWidth>
              {tObj.payByLink.done}
            </Button>
          ) : (
            <>
              <Button onClick={handleCloseCreate} variant="outlined" sx={{ minWidth: 100 }} disabled={generating}>
                {tObj.common.cancel}
              </Button>
              <Button
                onClick={handleGenerate}
                variant="contained"
                startIcon={generating ? undefined : <LinkIcon />}
                disabled={generating}
                sx={{ minWidth: 180 }}
              >
                {generating ? tObj.payByLink.generating : tObj.payByLink.createLinkAction}
              </Button>
            </>
          )}
        </DialogActions>
      </Dialog>

      <Dialog open={shareOpen && !!selectedLink} onClose={() => setShareOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>{tObj.payByLink.shareDialogTitle}</DialogTitle>
        <DialogContent>
          {selectedLink && (
            <Stack spacing={2.5}>
              <Paper variant="outlined" sx={{ p: 2, borderRadius: 2, bgcolor: 'action.hover' }}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, textTransform: 'uppercase', letterSpacing: 0.5 }}>
                  {selectedLink.shortCode}
                </Typography>
                <Typography variant="body2" sx={{ fontFamily: 'monospace', color: 'primary.main', mt: 0.5, wordBreak: 'break-all' }}>
                  {selectedLink.url}
                </Typography>
                <Stack direction="row" spacing={1} sx={{ mt: 1.5 }}>
                  <Typography variant="caption" color="text.secondary">
                    {formatCurrency(selectedLink.amount, selectedLink.currency)} · {formatTimeLeft(selectedLink.expiresAt)}
                  </Typography>
                </Stack>
              </Paper>

              <LinkQrCode url={selectedLink.url} shortCode={selectedLink.shortCode} size={180} />

              <Button
                fullWidth
                variant="outlined"
                startIcon={<CopyIcon />}
                onClick={() => { handleCopy(selectedLink.url); setShareOpen(false); }}
              >
                {tObj.payByLink.copyLink}
              </Button>

              <Button
                fullWidth
                variant="outlined"
                startIcon={<EmailIcon />}
                href={mailtoHref(selectedLink, tObj.payByLink.emailSubject, tObj.payByLink.messageText)}
                disabled={!selectedLink.customerEmail}
              >
                {tObj.payByLink.sendEmail}
              </Button>

              <Button
                fullWidth
                variant="outlined"
                color="success"
                startIcon={<WhatsAppIcon />}
                href={whatsAppHref(selectedLink, tObj.payByLink.messageText)}
                target="_blank"
                rel="noopener"
                disabled={!selectedLink.customerPhone}
              >
                {tObj.payByLink.sendWhatsApp}
              </Button>
            </Stack>
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={() => setShareOpen(false)} variant="contained" fullWidth>
            {tObj.common.close}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Те же вопрос и кнопки, что на карточке ссылки (P3-5a): это одно действие. */}
      <ConfirmDialog
        open={cancelTarget !== null}
        maxWidth="xs"
        title={tObj.payByLink.cancelConfirmTitle}
        question={tObj.payByLink.cancelConfirmText}
        cancelLabel={tObj.payByLink.keepLink}
        confirmLabel={tObj.payByLinkDetail.cancelLink}
        confirmIcon={<CancelIcon />}
        busy={cancelBusy}
        onConfirm={handleCancel}
        onCancel={() => setCancelTarget(null)}
      >
        {cancelTarget && (
          <Box sx={{ mt: 2, p: 2, borderRadius: 1, border: '1px solid', borderColor: 'divider', bgcolor: 'action.hover' }}>
            <Typography variant="body2" color="text.secondary">
              {tObj.payByLinkDetail.summary.shortCode}
            </Typography>
            <Typography variant="body2" sx={{ fontWeight: 700, fontFamily: 'monospace' }}>
              {cancelTarget.shortCode}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
              {tObj.payByLink.table.amount}
            </Typography>
            <Typography variant="body2" sx={{ fontWeight: 700 }}>
              {formatCurrency(cancelTarget.amount, cancelTarget.currency)}
            </Typography>
          </Box>
        )}
      </ConfirmDialog>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={snackbar.error ? 10000 : 3000}
        onClose={() => setSnackbar(s => ({ ...s, open: false }))}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity={snackbar.error ? 'error' : 'success'} onClose={() => setSnackbar(s => ({ ...s, open: false }))} sx={{ width: '100%' }}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
};
