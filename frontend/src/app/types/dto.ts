export interface CompanyDto {
  id: string;
  name: string;
  status?: 'ACTIVE' | 'INACTIVE' | 'DISABLED';
  /** Только у SYSTEM_ADMIN, остальным `null`; пароля в ответе нет вовсе (Р-93). */
  providerLogin?: string | null;
  /** VÖEN — реквизит продавца на чеке плательщика (Р-129); `null`, пока не задан. */
  taxId?: string | null;
  createdAt?: string;
}

/** `TerminalStatus` бэкенда; терминалы не удаляются, а блокируются (P2-8). */
export const TERMINAL_STATUSES = ['ACTIVE', 'BLOCKED'] as const;

export type TerminalStatus = (typeof TERMINAL_STATUSES)[number];

export interface TerminalDto {
  id: number;
  name: string;
  login: string;
  /** Номер у провайдера (`terminal.rid`, Р-96) — основная подпись; у заведённых до Р-96 пуст. */
  terminalRid?: string | null;
  /** Связан со справочником провайдера: название — провайдера, правкой его не сменить (Р-67). */
  providerLinked?: boolean;
  companyId: string;
  /** Бэкенд присылает всегда; необязательное намеренно — без поля терминал не прячется (`isTerminalActive`). */
  status?: TerminalStatus;
  createdAt?: string;
}

/** Лёгкий `GET /terminals/options` для селекторов и подписей; `login` в нём намеренно (`TerminalOptionResponse`). */
export interface TerminalOptionDto {
  id: number;
  name: string;
  /** Подпись терминала, когда нет `terminalRid` (`utils/terminals.ts`). */
  login: string;
  /** Номер у провайдера (Р-96) — в подписи раньше логина. */
  terminalRid?: string | null;
  /** Бэкенд присылает всегда; необязательное намеренно — без поля терминал не прячется (`isTerminalActive`). */
  status?: TerminalStatus;
  /** Компания терминала: по ней администратор выбирает терминалы сотрудника (Р-131). */
  companyId?: string | null;
}

// Только явно заблокированный: без поля `status` (старый бэкенд) форма ссылки осталась бы пустой
// без единой причины на экране.
export const isTerminalActive = (terminal: Pick<TerminalDto, 'status'>): boolean =>
  terminal.status !== 'BLOCKED';

/**
 * `GET /terminals/provider-terminals` (Р-96): мерчанты логина компании, ещё не заведённые у нас.
 * `rid` — код мерчанта (`merchantRid`), `terminalRid` — номер терминала у провайдера.
 */
export interface ProviderTerminalOption {
  rid: string;
  title: string | null;
  login: string | null;
  terminalRid: string;
}

/** `GET /companies/provider-logins` (Р-95): `login` — с префиксом; `merchants` — его активные мерчанты. */
export interface ProviderLoginOption {
  login: string;
  merchants: string[];
}

/** Итог обновления слепка логинов мультимерчантов (Р-94). */
export interface ProviderLoginSyncOutcome {
  applied: boolean;
  logins: number;
  links: number;
  skippedBecause: string | null;
}

/** `POST /ecom/provider-terminals/sync`: верхние поля — терминалы, `logins` — логины (Р-94). */
export interface ProviderTerminalSyncOutcome {
  applied: boolean;
  seen: number;
  ambiguous: number;
  disabled: number;
  skippedBecause: string | null;
  logins?: ProviderLoginSyncOutcome | null;
}

export interface UserDto {
  id: string;
  username: string;
  fullName?: string;
  role: 'SYSTEM_ADMIN' | 'COMPANY_HEAD' | 'COMPANY_MANAGER' | 'COMPANY_EMPLOYEE' | 'AUDITOR';
  companyId?: string;
  status?: string;
  createdAt?: string;
  /** Пароль задал не владелец, и он ещё не сменил его при входе (Р-100). */
  passwordChangeRequired?: boolean;
  /** Терминалы сотрудника (Р-131): он видит только их; у остальных ролей — пусто. */
  terminalIds?: number[];
}

export interface AuditLogDto {
  id?: string | number;
  action: string;
  performedBy?: string;
  companyId?: string;
  entityType?: 'COMPANY' | 'TERMINAL' | 'USER' | 'AUTH' | 'PAYMENT_LINK' | 'TRANSACTION' | 'AUDIT_LOG' | string;
  entityId?: string;
  details?: string;
  clientIp?: string | null;
  outcome?: 'SUCCESS' | 'DENIED' | 'UNRESOLVED';
  createdAt?: string;
}

/**
 * Статистика оплат по ссылкам (`GET /dashboard/summary`, Р-91). Денег без валюты нет ни в одном
 * поле: валюта — на ссылке, общий итог поверх валют был бы несуществующим числом.
 */
export interface DashboardSummary {
  window: { from: string; to: string; zone: string };
  totals: DashboardCurrencyTotals[];
  statusBreakdown: { status: string; count: number }[];
  dailyTotals: { date: string; currency: string; netAmount: string; transactionCount: number }[];
  hourlyTotals: { hour: number; transactionCount: number }[];
  topTerminals: DashboardTerminalTotal[];
  paymentLinks: {
    total: number;
    byPaymentType: { paymentType: string; count: number }[];
    byUsageType: { usageType: string; count: number }[];
    byStatus: { status: string; count: number }[];
  };
  /** Ссылки, созданные в периоде, и докуда они дошли (Р-128): когорта, недавний период ещё дорастает. */
  linkFunnel: { created: number; opened: number; paymentStarted: number; paid: number };
  /** Одноразовые ссылки периода: от создания до начала оплаченной попытки. Медианы нет — null, не ноль. */
  timeToPay: {
    paidLinks: number;
    medianSeconds: number | null;
    buckets: { range: string; count: number }[];
  };
}

/** Интервалы времени до оплаты в порядке ответа сервера (`DashboardService.TimeToPayRange`). */
export type TimeToPayRange = 'UP_TO_1_HOUR' | 'UP_TO_1_DAY' | 'UP_TO_7_DAYS' | 'OVER_7_DAYS';

const TIME_TO_PAY_RANGES: readonly TimeToPayRange[] = ['UP_TO_1_HOUR', 'UP_TO_1_DAY', 'UP_TO_7_DAYS', 'OVER_7_DAYS'];

export function parseTimeToPayRange(value: string): TimeToPayRange | null {
  return (TIME_TO_PAY_RANGES as readonly string[]).includes(value) ? (value as TimeToPayRange) : null;
}

export interface DashboardCurrencyTotals {
  currency: string;
  transactionCount: number;
  paidCount: number;
  failedCount: number;
  pendingCount: number;
  refundedCount: number;
  paidAmount: string;
  refundedAmount: string;
  netAmount: string;
  averagePaidAmount: string;
}

export interface DashboardTerminalTotal {
  currency: string;
  terminalId: number;
  /** Номер у провайдера (Р-96) — основная подпись. */
  terminalRid?: string | null;
  /** Подпись, когда номера нет; `null`, если терминала уже нет. */
  terminalLogin: string | null;
  /** `null`, если терминала уже нет, — не выдумывать. */
  terminalName: string | null;
  netAmount: string;
  transactionCount: number;
}
