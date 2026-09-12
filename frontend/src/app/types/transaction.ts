/**
 * Статусы транзакции. Источник — backend-enum
 * `pbl/src/main/java/az/millikart/pbl/domain/TransactionStatus.java`: ровно эти шесть значений
 * и никаких других. В ответе они приходят как `tx.getStatus().name()`
 * (`PaymentLinkService.mapToTransactionResponse`), то есть всегда в верхнем регистре.
 *
 * Новое значение на бэкенде → добавить сюда, иначе `parseTransactionStatus` вернёт `null`
 * и статус покажется как неизвестный. Значений «на будущее» здесь быть не должно: пока их
 * не было, `tsc` не мог поймать сравнение с несуществующим статусом (P2-12, Р-30).
 */
export const TRANSACTION_STATUSES = [
  'PENDING',
  'AUTHORIZED',
  'SUCCESS',
  'FAILED',
  'PARTIALLY_REFUNDED',
  'REFUNDED',
] as const;

export type TransactionStatus = (typeof TRANSACTION_STATUSES)[number];

/**
 * Разбор статуса на границе системы (ответы `/api/v1/transactions*`).
 *
 * Зеркало `parseRole` из `types/role.ts`: строгое сравнение, без приведения регистра и trim,
 * никогда не бросает. Ключевое отличие от прежнего кода — **никакой подстановки по умолчанию**:
 * раньше здесь стояло `String(t.status || 'APPROVED')`, и любое нераспознанное значение молча
 * становилось «успешной» транзакцией. Незнакомое значение даёт `null`; вызывающий обязан
 * показать его как есть, а не угадывать.
 *
 * Предупреждение в консоль — единственный побочный эффект: разбор идёт в одном месте, поэтому
 * расхождение со словарём бэкенда видно сразу, с исходным значением.
 */
export const parseTransactionStatus = (raw: unknown): TransactionStatus | null => {
  if (typeof raw !== 'string') {
    if (raw !== null && raw !== undefined) {
      console.warn('[transactions] статус не строка:', raw);
    }
    return null;
  }
  if ((TRANSACTION_STATUSES as readonly string[]).includes(raw)) {
    return raw as TransactionStatus;
  }
  console.warn(`[transactions] неизвестный статус транзакции: "${raw}"`);
  return null;
};

/**
 * Тип обработки платежа. Источник — backend-enum
 * `pbl/src/main/java/az/millikart/pbl/domain/PaymentType.java`: только `SMS` и `DMS`.
 * `mit`/`cit` во фронтовом словаре не существовали на бэкенде никогда.
 */
export const PAYMENT_METHODS = ['SMS', 'DMS'] as const;

export type PaymentMethod = (typeof PAYMENT_METHODS)[number];

/** Разбор `paymentType` из ответа. Правила те же, что у `parseTransactionStatus`. */
export const parsePaymentMethod = (raw: unknown): PaymentMethod | null => {
  if (typeof raw !== 'string') {
    return null;
  }
  if ((PAYMENT_METHODS as readonly string[]).includes(raw)) {
    return raw as PaymentMethod;
  }
  console.warn(`[transactions] неизвестный тип платежа: "${raw}"`);
  return null;
};

export type TransactionChannel = 'ecommerce' | 'pos';
export type POSPaymentType = 'chip' | 'contactless' | 'swipe' | 'manual';

/**
 * Записанное событие жизни операции — из поля `statusHistory` ответа по операции.
 *
 * Событий ровно столько, сколько их записано: заведение, списание холда, каждый возврат и,
 * если состояние ничем из перечисленного не объяснено, само состояние со временем последней
 * записи. Генератор, рисовавший «создано» и «оплачено» одним временем, удалён вместе
 * с подписями вроде «Payment successfully completed» — см. `PaymentLinkService.statusHistoryOf`.
 */
export interface StatusHistoryEntry {
  /** Что произошло: `CREATED`, `CAPTURED`, `REFUNDED` или `STATUS`. */
  type: string;
  /** Состояние операции после события; `null` — значение вне словаря, см. `statusRaw`. */
  status: TransactionStatus | null;
  statusRaw?: string;
  timestamp: Date;
  /** Сумма события. Есть только у денежных — списания и возврата. */
  amount?: number;
  /** Ссылка эквайера на операцию (`ridByPmo`) — та, что весома в споре. */
  acquirerReference?: string;
  note?: string;
}

export interface Transaction {
  id: string;
  paymentLinkId?: string;
  timestamp: Date;
  customer: string;
  customerEmail: string;
  customerPhone?: string;
  amount: number;
  /**
   * Сколько эквайер реально склирил при списании DMS-холда. `undefined` у SMS-платежей и
   * у холдов, которые не списывали. Именно эта сумма, а не `amount`, задаёт потолок возвратов
   * (`PaymentLinkService.refundableBase`), поэтому без неё остаток к возврату посчитать нельзя.
   */
  capturedAmount?: number;
  refundedAmount?: number;
  currency: string;
  /** Разобранный статус; `null` — бэкенд прислал значение вне словаря, см. `statusRaw`. */
  status: TransactionStatus | null;
  /** Исходное значение статуса. Показывается серым, когда `status === null`. */
  statusRaw?: string;
  paymentMethod: PaymentMethod | null;
  description: string;
  // Номера заказа мерчанта (`merchantOrderId`) здесь нет намеренно — см. `utils/exportExcel.ts`.
  cardLast4?: string;
  cardNumberMasked?: string;
  rrn?: string;
  approvalCode?: string;
  merchantRid?: string;
  providerOrderId?: string;
  terminalId?: number;
  /**
   * Логин терминала — основной параметр, по которому мерчант его опознаёт. Приходит не с
   * транзакцией, а из `GET /api/v1/terminals/options` и подставляется по `terminalId`.
   * Подписывает терминал во всех списках и на карточке операции — см. `utils/terminals.ts`.
   */
  terminalLogin?: string;
  clientIp?: string;
  userAgent?: string;
  fee: number;
  statusHistory: StatusHistoryEntry[];
  /** Ключ фильтра по терминалу: тот же логин, что и в `terminalLogin`, — по нему фильтрует `FilterPanel`. */
  terminalRid: string;
  terminalName?: string;
  canceledBy?: 'customer' | 'api';
  canceledByCustomerName?: string;
  canceledByCustomerEmail?: string;
  channel: TransactionChannel;
  // POS-specific fields
  posPaymentType?: POSPaymentType;
  cashierName?: string;
  cashierId?: string;
  receiptNumber?: string;
  batchId?: string;
  locationName?: string;
  shiftId?: string;
  isOffline?: boolean;
}

export interface TransactionFilters {
  dateFrom: Date | null;
  dateTo: Date | null;
  status: TransactionStatus | 'all';
  paymentMethod: PaymentMethod | 'all';
  minAmount: string;
  maxAmount: string;
  searchQuery: string;
  /** Выбранные логины терминалов; сверяется с `Transaction.terminalRid`. */
  terminalRid: string[];
  // POS-specific filters
  posPaymentType?: POSPaymentType | 'all';
  cashierId?: string | 'all';
  locationName?: string | 'all';
  batchId?: string | 'all';
}
