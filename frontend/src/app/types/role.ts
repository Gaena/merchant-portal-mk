/**
 * Зеркало `common.security.Role`. Новая роль там — сюда и в `auth/routeAccess.ts`, иначе
 * пользователь с ней не войдёт.
 */
export const ROLES = [
  'SYSTEM_ADMIN',
  'COMPANY_HEAD',
  'COMPANY_MANAGER',
  'COMPANY_EMPLOYEE',
  'AUDITOR',
] as const;

export type Role = (typeof ROLES)[number];

/**
 * Как `Role.fromValue`: строго, без приведения регистра и trim — иначе `'system_admin'` стал бы
 * администратором. Не бросает; нераспознанное — `null`, и вызывающий обязан отказать во входе.
 */
export const parseRole = (raw: unknown): Role | null => {
  if (typeof raw !== 'string') {
    return null;
  }
  return (ROLES as readonly string[]).includes(raw) ? (raw as Role) : null;
};
