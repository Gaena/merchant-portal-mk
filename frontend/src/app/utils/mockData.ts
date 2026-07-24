import type { TransactionStatus, PaymentMethod } from '../types/transaction';

export const terminalRids = [
  'TRM-001-AZE',
  'TRM-002-BAK',
  'TRM-003-GNJ',
  'TRM-004-SMX',
  'TRM-005-MNG',
  'TRM-006-AZE',
  'TRM-007-BAK',
  'TRM-008-GNJ',
  'TRM-009-SMX',
  'TRM-010-MNG'
];

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

export function getPaymentMethodLabel(method: PaymentMethod): string {
  if (!method) return 'SMS';
  const upper = String(method).toUpperCase();
  if (upper === 'SMS') return 'SMS (Single)';
  if (upper === 'DMS') return 'DMS (Two-Stage)';
  return upper;
}

export function getStatusLabel(status: TransactionStatus): string {
  if (!status) return 'Pending';
  const upper = String(status).toUpperCase();
  switch (upper) {
    case 'APPROVED':
    case 'SUCCESS':
      return 'Approved';
    case 'PENDING':
      return 'Pending';
    case 'DECLINED':
      return 'Declined';
    case 'FAILED':
    case '3D-FAILED':
      return 'Failed';
    case 'REFUNDED':
      return 'Refunded';
    case 'PARTIALLY_REFUNDED':
      return 'Partially Refunded';
    case 'CANCELED':
      return 'Canceled';
    default:
      return String(status).charAt(0).toUpperCase() + String(status).slice(1);
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