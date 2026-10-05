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
 * Почему кнопка возврата или списания выключена — ровно `MoneyActionReason` из `pbl` (Р-123). Новое значение
 * там — сюда и во все три языка (`transactions.detail.moneyReasons`); незнакомое разбирается в `null`.
 */
export const MONEY_ACTION_REASONS = [
  'NO_RIGHTS',
  'TERMINAL_NOT_IN_PORTAL',
  'NO_PROVIDER_CREDENTIALS',
  'FULLY_REFUNDED',
  'CAPTURE_FIRST',
  'ALREADY_CAPTURED',
  'OUTCOME_UNKNOWN',
  'IN_PROGRESS',
] as const;

export type MoneyActionReason = (typeof MONEY_ACTION_REASONS)[number];

const parseFrom = <T extends string>(values: readonly T[], raw: unknown, what: string): T | null => {
  if (typeof raw !== 'string') {
    return null;
  }
  if ((values as readonly string[]).includes(raw)) {
    return raw as T;
  }
  console.warn(`[transactions] неизвестный ${what}: "${raw}"`);
  return null;
};

export const parseMoneyActionReason = (raw: unknown): MoneyActionReason | null =>
  parseFrom(MONEY_ACTION_REASONS, raw, 'код причины');

export const MONEY_OPERATION_KINDS = ['CAPTURE', 'REFUND'] as const;
export type MoneyOperationKind = (typeof MONEY_OPERATION_KINDS)[number];
export const parseMoneyOperationKind = (raw: unknown): MoneyOperationKind | null =>
  parseFrom(MONEY_OPERATION_KINDS, raw, 'вид денежной операции');

export const MONEY_OPERATION_STATES = ['IN_PROGRESS', 'UNKNOWN'] as const;
export type MoneyOperationState = (typeof MONEY_OPERATION_STATES)[number];
export const parseMoneyOperationState = (raw: unknown): MoneyOperationState | null =>
  parseFrom(MONEY_OPERATION_STATES, raw, 'состояние денежной операции');

/**
 * Кнопка денежного действия (Р-123): сервер решает, активна ли она, и называет причину, если нет. Экран
 * правил не повторяет. `reason === null` у выключенной — код незнакомый, подсказка общая.
 */
export interface MoneyAction {
  enabled: boolean;
  reason: MoneyActionReason | null;
  /** Потолок суммы; только у активной. */
  maxAmount?: number;
}

/** Возврат или списание, исход которого не записан: пока он есть, обе кнопки выключены. */
export interface UnresolvedMoneyOperation {
  kind: MoneyOperationKind | null;
  amount: number;
  state: MoneyOperationState | null;
  startedAt?: Date;
  startedBy: string;
  /** Может ли смотрящий отметить итог — только `SYSTEM_ADMIN` и только при неизвестном исходе. */
  resolvable: boolean;
}

/** `null` у кнопки — её нет по смыслу (у SMS нет списания, у отклонённой операции нет ничего). */
export interface TransactionActions {
  refund: MoneyAction | null;
  capture: MoneyAction | null;
  unresolved: UnresolvedMoneyOperation | null;
}

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
  /** Только у одной операции (`GET /transactions/{id}`, `/status`); в списках не приходит. */
  actions?: TransactionActions;
}
