import type { MoneyAction, StatusHistoryEntry, Transaction, TransactionActions } from '../types/transaction';
import {
  parseMoneyActionReason,
  parseMoneyOperationKind,
  parseMoneyOperationState,
  parsePaymentMethod,
  parseTransactionStatus,
} from '../types/transaction';
import type { TerminalOptionDto } from '../types/dto';

// Единственный разбор операции из ответа `/transactions*` (AGENTS §9): второй копии не заводить.

// Событие без времени пропускается — момент ему не выдумывать. Порядок — бэкенда, не пересортировывать.
const mapStatusHistory = (raw: unknown): StatusHistoryEntry[] => {
  if (!Array.isArray(raw)) {
    return [];
  }
  return raw
    .filter((event: any) => event && event.at)
    .map((event: any): StatusHistoryEntry => ({
      type: String(event.type ?? 'STATUS'),
      status: parseTransactionStatus(event.status),
      statusRaw: event.status === null || event.status === undefined ? undefined : String(event.status),
      timestamp: new Date(event.at),
      amount: event.amount === null || event.amount === undefined ? undefined : Number(event.amount),
      acquirerReference: event.acquirerReference ?? undefined,
    }));
};

// Активна только при явном `enabled: true`: кривой ответ выключает кнопку, а не включает.
const mapMoneyAction = (raw: any): MoneyAction | null => {
  if (!raw || typeof raw !== 'object') {
    return null;
  }
  return {
    enabled: raw.enabled === true,
    reason: parseMoneyActionReason(raw.reason),
    maxAmount: raw.maxAmount === null || raw.maxAmount === undefined ? undefined : Number(raw.maxAmount),
  };
};

const mapActions = (raw: any): TransactionActions | undefined => {
  if (!raw || typeof raw !== 'object') {
    return undefined;
  }
  const unresolved = raw.unresolved && typeof raw.unresolved === 'object' ? raw.unresolved : null;
  return {
    refund: mapMoneyAction(raw.refund),
    capture: mapMoneyAction(raw.capture),
    unresolved: unresolved && {
      kind: parseMoneyOperationKind(unresolved.kind),
      amount: Number(unresolved.amount),
      state: parseMoneyOperationState(unresolved.state),
      startedAt: unresolved.startedAt ? new Date(unresolved.startedAt) : undefined,
      startedBy: typeof unresolved.startedBy === 'string' ? unresolved.startedBy : '',
      resolvable: unresolved.resolvable === true,
    },
  };
};

const optionalText = (value: unknown): string | undefined =>
  typeof value === 'string' && value.length > 0 ? value : undefined;

/**
 * Ничего не подставлять (Р-48): нет `createdAt` — нет даты, нет имени или валюты — пусто.
 * `terminalIndex` — `/terminals/options`; пустой индекс не ошибка, подпись станет прочерком.
 */
export const mapTransaction = (
  raw: any,
  terminalIndex: Record<number, TerminalOptionDto>
): Transaction => {
  const terminal = raw.terminalId ? terminalIndex[raw.terminalId] : undefined;

  return {
    id: raw.id,
    paymentLinkId: raw.paymentLinkId,
    timestamp: raw.createdAt ? new Date(raw.createdAt) : undefined,
    customer: optionalText(raw.customerName) ?? '',
    customerEmail: optionalText(raw.customerEmail) ?? '',
    customerPhone: optionalText(raw.customerPhone),
    amount: Number(raw.amount),
    capturedAmount: raw.capturedAmount === null || raw.capturedAmount === undefined ? undefined : Number(raw.capturedAmount),
    refundedAmount: raw.refundedAmount === null || raw.refundedAmount === undefined ? undefined : Number(raw.refundedAmount),
    currency: optionalText(raw.currency) ?? '',
    // Только через parse*: нераспознанное значение — null и показывается как есть (P2-12).
    status: parseTransactionStatus(raw.status),
    statusRaw: raw.status === null || raw.status === undefined ? undefined : String(raw.status),
    paymentMethod: parsePaymentMethod(raw.paymentType),
    description: optionalText(raw.description) ?? '',
    cardNumberMasked: optionalText(raw.cardNumberMasked),
    cardLast4: raw.cardNumberMasked ? String(raw.cardNumberMasked).slice(-4) : undefined,
    rrn: optionalText(raw.rrn),
    approvalCode: optionalText(raw.approvalCode),
    ridByMerchant: optionalText(raw.ridByMerchant),
    providerOrderId: optionalText(raw.providerOrderId),
    terminalId: raw.terminalId,
    terminalRid: terminal?.terminalRid ?? undefined,
    terminalLogin: terminal?.login,
    terminalName: terminal?.name,
    clientIp: optionalText(raw.clientIp),
    userAgent: optionalText(raw.userAgent),
    failureReason: optionalText(raw.failureReason),
    statusHistory: mapStatusHistory(raw.statusHistory),
    actions: mapActions(raw.actions),
  };
};
