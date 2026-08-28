/**
 * Роли портала. Зеркалит backend-enum `common.security.Role` — пять значений, тот же порядок.
 * Новая роль на бэкенде → добавить сюда и в `auth/routeAccess.ts`, иначе пользователь
 * с этой ролью не сможет войти (см. `parseRole`).
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
 * Разбор роли на границе системы (ответ `/login` и `/refresh`).
 *
 * Та же логика, что в `common.security.Role.fromValue` на бэкенде: **строгое сравнение,
 * без приведения регистра и без trim**. `'system_admin'` не становится `SYSTEM_ADMIN` —
 * регистронезависимый разбор был бы повышением привилегий, а не удобством. Никогда не бросает:
 * всё нераспознанное (не строка, пустая строка, опечатка) даёт `null`, и вызывающий обязан
 * отказать во входе, а не подставлять роль по умолчанию.
 */
export const parseRole = (raw: unknown): Role | null => {
  if (typeof raw !== 'string') {
    return null;
  }
  return (ROLES as readonly string[]).includes(raw) ? (raw as Role) : null;
};
