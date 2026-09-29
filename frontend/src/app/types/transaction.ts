/**
 * Ровно значения `TransactionStatus` из `pbl`: новое там — сюда, иначе `parseTransactionStatus`
 * вернёт `null`. Значений «на будущее» не добавлять — `tsc` перестанет ловить сравнение
 * с несуществующим статусом (P2-12, Р-30).
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
 * Строгое сравнение, без приведения регистра и trim; не бросает. Подстановки по умолчанию не
 * заводить: нераспознанный статус стал бы «успешной» операцией. `null` показывается как есть.
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

/** Енум `PaymentType` из `pbl` (поле `paymentType` ответа). */
export const PAYMENT_METHODS = ['SMS', 'DMS'] as const;

export type PaymentMethod = (typeof PAYMENT_METHODS)[number];

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

/**
 * Событие из `statusHistory` (`PaymentLinkService.statusHistoryOf`, Р-63) — только записанное:
 * заведение, списание холда, каждый возврат и текущее состояние, если оно ими не объяснено.
 */
export interface StatusHistoryEntry {
  /** `CREATED`, `CAPTURED`, `REFUNDED` или `STATUS`. */
  type: string;
  status: TransactionStatus | null;
  statusRaw?: string;
  timestamp: Date;
  /** Только у списания и возврата. */
  amount?: number;
  /** `ridByPmo` эквайера — весома в споре. */
  acquirerReference?: string;
  note?: string;
}

/**
 * Только то, что отдаёт `TransactionResponse`: комиссии, канала, причины отмены и POS-полей
 * в системе нет, и выдуманных значений на их месте не подставлять (Р-48).
 */
export interface Transaction {
  id: string;
  paymentLinkId?: string;
  /** `createdAt`; не прислан — пусто, «сейчас» не подставлять. */
  timestamp?: Date;
  /** Имя плательщика; пусто — не записано. */
  customer: string;
  customerEmail: string;
  customerPhone?: string;
  amount: number;
  /**
   * Склиренная сумма DMS-холда; `undefined` у SMS и несписанных холдов. Потолок возврата задаёт
   * она, а не `amount` (`PaymentLinkService.refundableBase`, P0-8).
   */
  capturedAmount?: number;
  refundedAmount?: number;
  currency: string;
  status: TransactionStatus | null;
  /** Исходная строка — показывается серым, когда `status === null`. */
  statusRaw?: string;
  paymentMethod: PaymentMethod | null;
  description: string;
  // `merchantOrderId` нет намеренно: форма его не отправляет, у ссылок портала он пуст (Р-60).
  cardLast4?: string;
  cardNumberMasked?: string;
  rrn?: string;
  approvalCode?: string;
  ridByMerchant?: string;
  providerOrderId?: string;
  terminalId?: number;
  /** С операцией не приходит: из `GET /terminals/options` по `terminalId`; подпись — `utils/terminals.ts`. */
  terminalLogin?: string;
  /** Номер терминала у провайдера (Р-96), оттуда же; в подписи идёт раньше логина. */
  terminalRid?: string;
  clientIp?: string;
  userAgent?: string;
  /** Словами эквайера; только у FAILED с известной причиной (Р-24). */
  failureReason?: string;
  statusHistory: StatusHistoryEntry[];
  terminalName?: string;
}
