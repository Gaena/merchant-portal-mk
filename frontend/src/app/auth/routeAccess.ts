/**
 * Раскладка «маршрут → кому виден». Единственное место: отсюда читают и guard'ы в `routes.tsx`
 * (`RoleRoute`), и сайдбар (`Sidebar.tsx`) — иначе меню и маршруты разъедутся.
 *
 * ⚠ **Это UX, а не безопасность.** Настоящая проверка прав — на бэкенде
 * (`UserService.validateAccess`, `PaymentLinkService.validateAccess`, `TerminalService`,
 * `AuditLogService` и т.д.); клиентские guard'ы лишь прячут то, что сервер всё равно вернёт
 * с 403. Любой пользователь может обойти их в DevTools — и ничего не получит.
 *
 * Раскладка повторяет матрицу из AGENTS.md §6 (по коду, не по `project_docs/technical_handover.md`):
 * `/users` — `POST/GET/PATCH/DELETE /users`; `/companies` — `GET /companies` (список);
 * `/audit-logs` — `GET /audit-logs`. Маршрут, которого здесь нет, открыт всем вошедшим
 * (`/terminals`, `/settings`, транзакции, pay-by-link).
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

/**
 * Список ролей для маршрута или `undefined`, если маршрут открыт всем вошедшим.
 * Принимает и абсолютный путь (`/users`), и относительный из вложенных маршрутов (`users`).
 */
export const allowedRolesFor = (path: string): readonly Role[] | undefined => {
  const absolute = path.startsWith('/') ? path : `/${path}`;
  return isRestrictedPath(absolute) ? ROUTE_ACCESS[absolute] : undefined;
};

/** `true`, если пользователь с ролью `role` может открыть `path`. Без роли — нельзя ничего. */
export const canAccessPath = (role: Role | null | undefined, path: string): boolean => {
  if (role === null || role === undefined) {
    return false;
  }
  const allow = allowedRolesFor(path);
  return allow === undefined || allow.includes(role);
};
