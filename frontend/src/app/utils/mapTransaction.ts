import type { StatusHistoryEntry, Transaction } from '../types/transaction';
import { parsePaymentMethod, parseTransactionStatus } from '../types/transaction';
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
  };
};
