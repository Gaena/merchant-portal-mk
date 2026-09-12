import type { StatusHistoryEntry, Transaction } from '../types/transaction';
import { parsePaymentMethod, parseTransactionStatus } from '../types/transaction';
import type { TerminalOptionDto } from '../types/dto';
import { terminalLabel } from './terminals';

/**
 * Разбор одной операции из ответа `/api/v1/transactions*` в её экранный вид.
 *
 * Копий этого разбора было две — в `App.tsx` и на карточке операции, — и они успели разойтись:
 * карточка знала о `merchantRid`, список нет. Теперь место одно, и проверка статуса у эквайера
 * читает ответ тем же разбором, что и список.
 *
 * `terminalIndex` — ответ `GET /api/v1/terminals/options`: операция несёт только `terminalId`,
 * а подписывается терминал логином (см. `terminals.ts`). Пустой индекс не ошибка — подпись
 * тогда опустится до номера терминала.
 */
/**
 * История операции из ответа. Событие без времени пропускается: показать его на шкале не
 * получится, а придумать ему момент нельзя. Порядок приходит с бэкенда и не пересортировывается.
 */
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

export const mapTransaction = (
  raw: any,
  terminalIndex: Record<number, TerminalOptionDto>
): Transaction => {
  const terminal = raw.terminalId ? terminalIndex[raw.terminalId] : undefined;
  const label = terminalLabel({
    terminalLogin: terminal?.login,
    terminalName: terminal?.name,
    terminalId: raw.terminalId
  });

  return {
    id: raw.id,
    paymentLinkId: raw.paymentLinkId,
    timestamp: raw.createdAt ? new Date(raw.createdAt) : new Date(),
    customer: raw.customerName || raw.customerEmail || 'Customer',
    customerEmail: raw.customerEmail || 'N/A',
    customerPhone: raw.customerPhone,
    amount: raw.amount,
    capturedAmount: raw.capturedAmount,
    refundedAmount: raw.refundedAmount,
    currency: raw.currency || 'AZN',
    // Разбор — только через parse*: нераспознанное значение даёт null и показывается как есть.
    // Прежнее `String(t.status || 'APPROVED') as any` превращало любой неизвестный статус
    // в «успешный» и прятало расхождение от компилятора (P2-12).
    status: parseTransactionStatus(raw.status),
    statusRaw: raw.status === null || raw.status === undefined ? undefined : String(raw.status),
    paymentMethod: parsePaymentMethod(raw.paymentType),
    description: raw.description || raw.merchantOrderId || 'Transaction',
    cardNumberMasked: raw.cardNumberMasked,
    cardLast4: raw.cardNumberMasked ? String(raw.cardNumberMasked).slice(-4) : undefined,
    rrn: raw.rrn,
    approvalCode: raw.approvalCode,
    merchantRid: raw.merchantRid,
    providerOrderId: raw.providerOrderId || raw.provider_order_id,
    terminalId: raw.terminalId,
    terminalLogin: terminal?.login,
    terminalName: terminal?.name,
    clientIp: raw.clientIp,
    userAgent: raw.userAgent,
    fee: 0,
    statusHistory: mapStatusHistory(raw.statusHistory),
    terminalRid: label,
    channel: 'ecommerce'
  };
};
