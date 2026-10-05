/**
 * Роли действий: кнопка видна ровно тогда, когда бэкенд её примет (Р-62). Это UX, а не безопасность.
 * Наборы — зеркало `TerminalService` (`createTerminal`, `TERMINAL_WRITE_ROLES`) и меняются вместе с ним.
 * Возврата и списания здесь нет: их кнопки решает сервер в `actions` операции (Р-123).
 */
import type { Role } from '../types/role';

export const TERMINAL_CREATE_ROLES: readonly Role[] = ['SYSTEM_ADMIN'];
export const TERMINAL_WRITE_ROLES: readonly Role[] = ['SYSTEM_ADMIN', 'COMPANY_HEAD', 'COMPANY_MANAGER'];

const has = (roles: readonly Role[], role: Role | null | undefined): boolean =>
  role !== null && role !== undefined && roles.includes(role);

export const canCreateTerminals = (role: Role | null | undefined): boolean => has(TERMINAL_CREATE_ROLES, role);
export const canWriteTerminals = (role: Role | null | undefined): boolean => has(TERMINAL_WRITE_ROLES, role);
