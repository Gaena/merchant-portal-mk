export interface CompanyDto {
  id: string;
  name: string;
  status?: 'ACTIVE' | 'INACTIVE' | 'DISABLED';
  createdAt?: string;
}

/** Статусы терминала бэкенда (`TerminalStatus`). Терминалы не удаляются, а блокируются (P2-8). */
export const TERMINAL_STATUSES = ['ACTIVE', 'BLOCKED'] as const;

export type TerminalStatus = (typeof TERMINAL_STATUSES)[number];

export interface TerminalDto {
  id: number;
  name: string;
  login: string;
  password?: string;
  companyId: string;
  /** Бэкенд присылает всегда; поле необязательное только ради ответов, снятых до P2-8. */
  status?: TerminalStatus;
  createdAt?: string;
}

/**
 * Ответ `GET /api/v1/terminals/options` — лёгкий фид для селекторов, фильтров и подписей
 * терминала на экранах платежей. Пароля в нём нет и не будет; `login` есть намеренно —
 * см. `TerminalOptionResponse` на бэкенде.
 */
export interface TerminalOptionDto {
  id: number;
  name: string;
  /**
   * Логин эквайринга — основной параметр терминала: мерчант знает терминал по нему, а не по
   * имени, которое придумывает сам, и не по внутреннему номеру. Подписывает терминал везде,
   * где тот показан, — см. `utils/terminals.ts`.
   */
  login: string;
  /** Бэкенд присылает всегда; поле необязательное только ради ответов, снятых до P2-8. */
  status?: TerminalStatus;
}

/**
 * Терминал доступен для новых платежей. Скрывает только явно заблокированный: отсутствие поля —
 * это ответ старого бэкенда, и по нему нельзя прятать все терминалы разом, иначе форма создания
 * ссылки останется пустой без единой причины на экране.
 */
export const isTerminalActive = (terminal: Pick<TerminalDto, 'status'>): boolean =>
  terminal.status !== 'BLOCKED';

export interface UserDto {
  id: string;
  username: string;
  fullName?: string;
  role: 'SYSTEM_ADMIN' | 'COMPANY_HEAD' | 'COMPANY_MANAGER' | 'COMPANY_EMPLOYEE' | 'AUDITOR';
  companyId?: string;
  status?: string;
  createdAt?: string;
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
 * Сводка главной страницы (`GET /api/v1/dashboard/summary`, P3-7). Считает база; денег без
 * валюты здесь нет ни в одном поле — колонки currency у транзакций не существует, она на ссылке,
 * и общий итог поверх нескольких валют был бы числом, которого не существует.
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
  /** Логин из таблицы терминалов — основная подпись; `null`, если терминала уже нет. */
  terminalLogin: string | null;
  /** Имя из таблицы терминалов; `null`, если терминала уже нет — выдумывать его нельзя. */
  terminalName: string | null;
  netAmount: string;
  transactionCount: number;
}
