/**
 * Единственная раскладка «маршрут → роли»: её читают `RoleRoute` и сайдбар, иначе меню и маршруты
 * разъедутся. Это UX, а не безопасность — права проверяет бэкенд. Повторяет матрицу AGENTS §6;
 * маршрут, которого здесь нет, открыт всем вошедшим.
 */
import type { Role } from '../types/role';

export type RestrictedPath = '/users' | '/companies' | '/audit-logs';

export const ROUTE_ACCESS: Readonly<Record<RestrictedPath, readonly Role[]>> = {
  '/users': ['SYSTEM_ADMIN', 'COMPANY_HEAD'],
  '/companies': ['SYSTEM_ADMIN', 'AUDITOR'],
  '/audit-logs': ['SYSTEM_ADMIN', 'AUDITOR', 'COMPANY_HEAD', 'COMPANY_MANAGER'],
};

const isRestrictedPath = (path: string): path is RestrictedPath =>
  Object.prototype.hasOwnProperty.call(ROUTE_ACCESS, path);

/** `undefined` — маршрут открыт всем вошедшим. Путь — `/users` или относительный `users`. */
export const allowedRolesFor = (path: string): readonly Role[] | undefined => {
  const absolute = path.startsWith('/') ? path : `/${path}`;
  return isRestrictedPath(absolute) ? ROUTE_ACCESS[absolute] : undefined;
};

/** Без роли — нельзя ничего. */
export const canAccessPath = (role: Role | null | undefined, path: string): boolean => {
  if (role === null || role === undefined) {
    return false;
  }
  const allow = allowedRolesFor(path);
  return allow === undefined || allow.includes(role);
};
