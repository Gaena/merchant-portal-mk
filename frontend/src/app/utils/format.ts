import type { PaymentMethod } from '../types/transaction';

/**
 * Форматтеры сумм и дат для экранов. Файл назывался `mockData.ts` — в нём когда-то жили
 * выдуманные терминалы и подписи; от моков осталось одно имя, и оно снято.
 */

/**
 * Сумма с валютой. Без валюты — просто число с двумя знаками: подставлять AZN нельзя (Р-48),
 * а `Intl.NumberFormat` с пустой валютой бросает `RangeError` и роняет страницу.
 */
export function formatCurrency(amount: number | null | undefined, currency?: string | null): string {
  const value = typeof amount === 'number' && Number.isFinite(amount) ? amount : 0;
  if (!currency) {
    return new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);
  }
  try {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(value);
  } catch {
    // Код вне ISO 4217 (провайдер прислал что-то своё): число и код рядом, как есть.
    return `${value.toFixed(2)} ${currency}`;
  }
}

export function formatDateTime(date: Date | null | undefined): string {
  if (!date || isNaN(new Date(date).getTime())) return '—';
  const d = new Date(date);
  const day = String(d.getDate()).padStart(2, '0');
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const year = String(d.getFullYear()).slice(-2);
  const hours = String(d.getHours()).padStart(2, '0');
  const minutes = String(d.getMinutes()).padStart(2, '0');

  return `${day}.${month}.${year} ${hours}:${minutes}`;
}

/** `null` — `paymentType` вне словаря бэкенда; подставлять «SMS» по умолчанию нельзя. */
export function getPaymentMethodLabel(method: PaymentMethod | null | undefined): string {
  switch (method) {
    case 'SMS':
      return 'SMS (Single)';
    case 'DMS':
      return 'DMS (Two-Stage)';
    default:
      return '—';
  }
}
