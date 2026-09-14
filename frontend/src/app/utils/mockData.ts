import type { TransactionStatus, PaymentMethod } from '../types/transaction';

// Здесь стоял `terminalRids` — десять выдуманных кодов вида «TRM-001-AZE». Ими наполнялся
// фильтр по терминалу, и совпасть с настоящей транзакцией они не могли ни разу: выбор любого
// пункта давал пустой список. Фильтр берёт терминалы из `GET /api/v1/terminals/options`.

export function formatCurrency(amount: number, currency: string): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: currency || 'AZN',
  }).format(amount || 0);
}

export function formatDateTime(date: Date): string {
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

/**
 * Английская подпись статуса — для выгрузки в Excel и прочего кода вне React.
 * В интерфейсе подписи берутся из `i18n/translations.ts` (`transactions.statuses`).
 * `raw` показывается, когда бэкенд прислал статус вне словаря (`status === null`).
 */
export function getStatusLabel(status: TransactionStatus | null | undefined, raw?: string): string {
  switch (status) {
    case 'SUCCESS':
      return 'Success';
    case 'PENDING':
      return 'Pending';
    case 'AUTHORIZED':
      return 'Authorized';
    case 'FAILED':
      return 'Failed';
    case 'REFUNDED':
      return 'Refunded';
    case 'PARTIALLY_REFUNDED':
      return 'Partially Refunded';
    default:
      return raw || 'Unknown';
  }
}

export function getPOSPaymentTypeLabel(type?: string): string {
  if (!type) return '-';
  const labels: Record<string, string> = {
    chip: 'Chip & PIN',
    contactless: 'Contactless/NFC',
    swipe: 'Magnetic Swipe',
    manual: 'Manual Entry'
  };
  return labels[type] || type;
}