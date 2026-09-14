/**
 * Роли, которым бэкенд принимает **действие**, — в дополнение к `routeAccess.ts`, где лежат
 * маршруты. Кнопка видна ровно тогда, когда сервер её примет (Р-62): предлагать действие и
 * отвечать на него 403 хуже, чем не предлагать.
 *
 * ⚠ Это UX, а не безопасность: настоящая проверка — на бэкенде. Наборы повторяют константы
 * сервисов и меняются вместе с ними:
 *
 * - `TERMINAL_WRITE_ROLES` — `directory` `TerminalService.TERMINAL_WRITE_ROLES`
 *   (заведение, правка, блокировка терминала);
 * - `LINK_WRITE_ROLES` — `pbl` `PaymentLinkService.LINK_WRITE_ROLES`
 *   (создание и отмена ссылки, списание DMS-холда);
 * - `REFUND_ROLES` — `pbl` `PaymentLinkService.REFUND_ROLES` (возврат: на уровень выше
 *   сотрудника, потому что двигает деньги обратно).
 */
import type { Role } from '../types/role';

export const TERMINAL_WRITE_ROLES: readonly Role[] = ['SYSTEM_ADMIN', 'COMPANY_HEAD', 'COMPANY_MANAGER'];
export const LINK_WRITE_ROLES: readonly Role[] = ['SYSTEM_ADMIN', 'COMPANY_HEAD', 'COMPANY_MANAGER', 'COMPANY_EMPLOYEE'];
export const REFUND_ROLES: readonly Role[] = ['SYSTEM_ADMIN', 'COMPANY_HEAD', 'COMPANY_MANAGER'];

const has = (roles: readonly Role[], role: Role | null | undefined): boolean =>
  role !== null && role !== undefined && roles.includes(role);

export const canWriteTerminals = (role: Role | null | undefined): boolean => has(TERMINAL_WRITE_ROLES, role);
export const canWriteLinks = (role: Role | null | undefined): boolean => has(LINK_WRITE_ROLES, role);
export const canRefund = (role: Role | null | undefined): boolean => has(REFUND_ROLES, role);
