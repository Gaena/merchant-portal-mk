/**
 * Роли действий: кнопка видна ровно тогда, когда бэкенд её примет (Р-62). Это UX, а не безопасность.
 * Наборы — зеркало `TerminalService` (`createTerminal`, `TERMINAL_WRITE_ROLES`) и `PaymentLinkService`
 * (`LINK_WRITE_ROLES`, `REFUND_ROLES`) и меняются вместе с ними.
 */
import type { Role } from '../types/role';

export const TERMINAL_CREATE_ROLES: readonly Role[] = ['SYSTEM_ADMIN'];
export const TERMINAL_WRITE_ROLES: readonly Role[] = ['SYSTEM_ADMIN', 'COMPANY_HEAD', 'COMPANY_MANAGER'];
export const LINK_WRITE_ROLES: readonly Role[] = ['SYSTEM_ADMIN', 'COMPANY_HEAD', 'COMPANY_MANAGER', 'COMPANY_EMPLOYEE'];
export const REFUND_ROLES: readonly Role[] = ['SYSTEM_ADMIN', 'COMPANY_HEAD', 'COMPANY_MANAGER'];

const has = (roles: readonly Role[], role: Role | null | undefined): boolean =>
  role !== null && role !== undefined && roles.includes(role);

export const canCreateTerminals = (role: Role | null | undefined): boolean => has(TERMINAL_CREATE_ROLES, role);
export const canWriteTerminals = (role: Role | null | undefined): boolean => has(TERMINAL_WRITE_ROLES, role);
export const canWriteLinks = (role: Role | null | undefined): boolean => has(LINK_WRITE_ROLES, role);
export const canRefund = (role: Role | null | undefined): boolean => has(REFUND_ROLES, role);
