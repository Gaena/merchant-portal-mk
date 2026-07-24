export interface CompanyDto {
  id: string;
  name: string;
  status?: 'ACTIVE' | 'INACTIVE' | 'DISABLED';
  createdAt?: string;
}

export interface TerminalDto {
  id: number;
  name: string;
  login: string;
  password?: string;
  companyId: string;
  createdAt?: string;
}

export interface UserDto {
  id: string;
  username: string;
  fullName?: string;
  role: 'SYSTEM_ADMIN' | 'COMPANY_HEAD' | 'COMPANY_MANAGER' | 'COMPANY_EMPLOYEE' | 'AUDITOR';
  companyId?: string;
  status?: string;
  createdAt?: string;
}

export interface AuditLogDto {
  id?: string | number;
  action: string;
  performedBy?: string;
  companyId?: string;
  entityType?: 'COMPANY' | 'TERMINAL' | 'USER' | 'PAYMENT_LINK' | 'TRANSACTION' | string;
  entityId?: string;
  details?: string;
  createdAt?: string;
}
