export type TransactionStatus =
  | 'APPROVED'
  | 'PENDING'
  | 'DECLINED'
  | 'REFUNDED'
  | 'PARTIALLY_REFUNDED'
  | 'CANCELED'
  | 'FAILED'
  | 'success'
  | 'pending'
  | 'canceled'
  | '3d-failed';
export type PaymentMethod = 'SMS' | 'DMS' | 'sms' | 'dms' | 'mit' | 'cit';
export type TransactionChannel = 'ecommerce' | 'pos';
export type POSPaymentType = 'chip' | 'contactless' | 'swipe' | 'manual';

export interface StatusHistoryEntry {
  status: TransactionStatus;
  timestamp: Date;
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
  refundedAmount?: number;
  currency: string;
  status: TransactionStatus;
  paymentMethod: PaymentMethod;
  description: string;
  merchantReference?: string;
  cardLast4?: string;
  cardNumberMasked?: String;
  rrn?: string;
  approvalCode?: string;
  merchantRid?: string;
  providerOrderId?: string;
  terminalId?: number;
  clientIp?: string;
  userAgent?: string;
  fee: number;
  statusHistory: StatusHistoryEntry[];
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
  terminalRid: string[];
  // POS-specific filters
  posPaymentType?: POSPaymentType | 'all';
  cashierId?: string | 'all';
  locationName?: string | 'all';
  batchId?: string | 'all';
}