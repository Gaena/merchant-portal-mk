export type UserRole = 
  | 'SYSTEM_ADMIN'
  | 'COMPANY_HEAD'
  | 'COMPANY_MANAGER'
  | 'COMPANY_EMPLOYEE'
  | 'AUDITOR';

export interface User {
  id: number;
  email: string;
  role: UserRole;
  companyId?: number;
  fullName?: string;
  active: boolean;
  createdAt: string;
}

export interface Company {
  id: number;
  name: string;
  taxNumber?: string;
  active: boolean;
  createdAt: string;
}

export interface Terminal {
  id: number;
  companyId: number;
  terminalId: string;
  name: string;
  active: boolean;
}

export type PaymentType = 'SMS' | 'DMS';
export type UsageType = 'SINGLE' | 'MULTIPLE';
export type PaymentLinkStatus = 'CREATED' | 'ACTIVE' | 'PAID' | 'EXPIRED' | 'CANCELLED';

export interface PaymentLink {
  id: string;
  providerReference?: string;
  merchantOrderId?: string;
  terminalId: number;
  amount: number;
  currency: string;
  description?: string;
  customerName?: string;
  customerEmail?: string;
  customerPhone?: string;
  paymentType: PaymentType;
  usageType: UsageType;
  maxPayments?: number;
  currentPaymentsCount: number;
  status: PaymentLinkStatus;
  metadata?: Record<string, any>;
  expiresAt?: string;
  createdAt: string;
  updatedAt: string;
}

export type TransactionStatus = 'CREATED' | 'AUTHORIZED' | 'COMPLETED' | 'REFUNDED' | 'FAILED' | 'REJECTED';

export interface Transaction {
  id: string;
  linkId: string;
  merchantRid: string;
  providerOrderId: string;
  amount: number;
  refundedAmount: number;
  status: TransactionStatus;
  createdAt: string;
  updatedAt: string;
}

export interface LoginResponse {
  token: string;
  user: User;
}
