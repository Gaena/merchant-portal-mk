/**
 * Выписка провайдера — ответы сервиса `ecom` (`/api/v1/ecom/transactions`, `project_docs/ecom.md` §2).
 *
 * Статусов восемь, а не шесть, как у операций портала: к словарю платёжных ссылок добавлены
 * `PARTIALLY_PAID` — частичная оплата (Р-78) — и `CANCELED` — отмена (Р-75, Р-77); как статус выводится
 * из статуса провайдера и сумм — `project_docs/ecom.md` §2.3 (Р-92). Источник — `EcomStatusResolver.EcomStatus`
 * на бэкенде; новое значение там — сюда, иначе `parseEcomStatus` вернёт `null` и статус покажется исходной строкой.
 */
export const ECOM_STATUSES = [
  'PENDING',
  'AUTHORIZED',
  'SUCCESS',
  'PARTIALLY_PAID',
  'FAILED',
  'PARTIALLY_REFUNDED',
  'REFUNDED',
  'CANCELED',
] as const;

export type EcomStatus = (typeof ECOM_STATUSES)[number];

/** Разбор статуса заказа. Правила те же, что у `parseTransactionStatus`: строго и без подстановок. */
export const parseEcomStatus = (raw: unknown): EcomStatus | null => {
  if (typeof raw !== 'string') {
    return null;
  }
  if ((ECOM_STATUSES as readonly string[]).includes(raw)) {
    return raw as EcomStatus;
  }
  console.warn(`[ecom] неизвестный статус заказа: "${raw}"`);
  return null;
};

/** Вид операции — разбор бэкенда (`EcomOperationKind`); сырые коды провайдера приходят рядом. */
export const ECOM_OPERATION_KINDS = ['AUTHORIZATION', 'CAPTURE', 'PURCHASE', 'REVERSAL', 'REFUND', 'UNKNOWN'] as const;

export type EcomOperationKind = (typeof ECOM_OPERATION_KINDS)[number];

export interface EcomOperation {
  /** `tran.ridbyacq`: 18 цифр, только строкой — числом последние знаки теряются. */
  tranId: string | null;
  at: Date | null;
  kind: EcomOperationKind;
  type: string | null;
  phase: string | null;
  voidKind: string | null;
  authKind: string | null;
  resultCode: string | null;
  amount: number | null;
  /** Сколько операция списала: у авторизации 0, у возврата и реверсала покупки — с минусом. */
  clearAmount: number | null;
  currency: string | null;
  rrn: string | null;
}

/** Строка выписки — заказ со всей историей (Р-74). */
export interface EcomOrder {
  orderId: string;
  merchantRid: string | null;
  merchantTitle: string | null;
  /** Бывает пустым у заказов, заведённых мерчантом у провайдера напрямую; ничем не подменять (Р-69). */
  ridByMerchant: string | null;
  status: EcomStatus | null;
  statusRaw: string | null;
  providerStatus: string | null;
  providerPrevStatus: string | null;
  amount: number | null;
  capturedAmount: number;
  refundedAmount: number;
  currency: string | null;
  description: string | null;
  createdAt: Date | null;
  lastOperationAt: Date | null;
  cardMask: string | null;
  rrn: string | null;
  /** Код ответа последней операции — только когда одобренных не было. */
  declineCode: string | null;
  operations: EcomOperation[];
}

export interface EcomPage {
  content: EcomOrder[];
  /** `null` — страница последняя. */
  nextCursor: string | null;
}

/** Суммы одной валюты: разные валюты не складываются. */
export interface EcomCurrencyTotal {
  currency: string | null;
  capturedAmount: number;
  refundedAmount: number;
}

export interface EcomStats {
  orderCount: number;
  statusCounts: Record<EcomStatus, number>;
  totals: EcomCurrencyTotal[];
}

/**
 * Мерчант скоупа для фильтра выписки (Р-97) — у провайдера терминал и мерчант одно. Номер терминала,
 * логин и название — из слепка провайдера; у мерчанта без терминала в слепке есть только название.
 */
export interface EcomTerminal {
  merchantRid: string;
  title: string | null;
  login: string | null;
  terminalRid: string | null;
}

/**
 * Сводка главной (Р-91) — `GET /api/v1/ecom/dashboard/summary`: оплаты картой по всем мерчантам скоупа по
 * выписке провайдера. Заказы периода и деньги — те же, что во вкладке E-commerce; суммы — по валютам.
 */
export interface EcomDashboardTotals {
  currency: string | null;
  orderCount: number;
  paidCount: number;
  capturedAmount: number;
  refundedAmount: number;
  netAmount: number;
  averagePaidAmount: number;
}

export interface EcomDashboard {
  window: { from: string; to: string; zone: string };
  totals: EcomDashboardTotals[];
  statusCounts: Record<EcomStatus, number>;
  /** Сутки в поясе отчёта, `YYYY-MM-DD`, пустые — с нулями. */
  dailyTotals: { date: string; currency: string | null; netAmount: number; orderCount: number }[];
  topTerminals: {
    currency: string | null;
    merchantRid: string | null;
    login: string | null;
    terminalRid: string | null;
    title: string | null;
    netAmount: number;
    orderCount: number;
  }[];
}

/**
 * Тип оплаты заказа — `EcomPaymentType` на бэкенде (Р-87): определяется по операциям заказа.
 * SMS — оплата одним сообщением, DMS — холд и списание.
 */
export const ECOM_PAYMENT_TYPES = ['SMS', 'DMS'] as const;

export type EcomPaymentType = (typeof ECOM_PAYMENT_TYPES)[number];

/**
 * Фильтр выписки. Период — по дате создания заказа и обязателен. Статус и тип уходят на сервер,
 * как и всё остальное: статус он отбирает после сборки заказа, с потолком просмотра (Р-87).
 */
export interface EcomQuery {
  dateFrom: Date;
  dateTo: Date;
  merchantRids: string[];
  minAmount: string;
  maxAmount: string;
  query: string;
  status: EcomStatus | null;
  paymentType: EcomPaymentType | null;
}
