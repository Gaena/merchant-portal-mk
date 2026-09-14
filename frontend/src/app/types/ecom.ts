/**
 * Выписка провайдера — ответы сервиса `ecom` (`/api/v1/ecom/transactions`, `project_docs/ecom.md` §2).
 *
 * Статусов восемь, а не шесть, как у операций портала: к словарю платёжных ссылок добавлены
 * `PARTIALLY_PAID` — списано меньше суммы заказа (Р-78) — и `CANCELED` — одобрено, но в итоге ничего
 * не списано (Р-75, Р-77). Источник — `EcomStatusResolver.EcomStatus` на бэкенде; новое значение там —
 * сюда, иначе `parseEcomStatus` вернёт `null` и статус покажется исходной строкой.
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
  currency: string;
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

/** Терминал для фильтра выписки: наш терминал в скоупе и провайдерский за ним. */
export interface EcomTerminal {
  merchantRid: string;
  title: string | null;
  login: string | null;
}

/** Фильтр выписки. Период — по дате создания заказа и обязателен. */
export interface EcomQuery {
  dateFrom: Date;
  dateTo: Date;
  merchantRids: string[];
  minAmount: string;
  maxAmount: string;
  query: string;
}
