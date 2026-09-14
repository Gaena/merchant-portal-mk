import type { StatusHistoryEntry, Transaction } from '../types/transaction';
import { parsePaymentMethod, parseTransactionStatus } from '../types/transaction';
import type { TerminalOptionDto } from '../types/dto';

/**
 * Разбор одной операции из ответа `/api/v1/transactions*` в её экранный вид.
 *
 * Копий этого разбора было две — в `App.tsx` и на карточке операции, — и они успели разойтись:
 * карточка знала о `ridByMerchant`, список нет. Теперь место одно, и проверка статуса у эквайера
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

const optionalText = (value: unknown): string | undefined =>
  typeof value === 'string' && value.length > 0 ? value : undefined;

/**
 * Ничего не подставляется (Р-48): нет `createdAt` — нет даты (а не «сейчас»), нет имени —
 * пусто (а не «Customer»), нет валюты — пусто (а не AZN). Комиссии в ответе нет — нет и поля.
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
    // Разбор — только через parse*: нераспознанное значение даёт null и показывается как есть.
    // Прежнее `String(t.status || 'APPROVED') as any` превращало любой неизвестный статус
    // в «успешный» и прятало расхождение от компилятора (P2-12).
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
    terminalLogin: terminal?.login,
    terminalName: terminal?.name,
    clientIp: optionalText(raw.clientIp),
    userAgent: optionalText(raw.userAgent),
    failureReason: optionalText(raw.failureReason),
    statusHistory: mapStatusHistory(raw.statusHistory),
  };
};
