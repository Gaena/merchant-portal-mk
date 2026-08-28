/**
 * Хранилище сессии — единственное место, где живут токены.
 *
 * - **access-токен — только в памяти** (модульная переменная). В `localStorage` он не попадает:
 *   всё, что там лежит, доступно любому XSS. Перезагрузка вкладки его теряет — и это нормально,
 *   сессия восстанавливается через refresh (`AuthProvider` + `client.ts`).
 * - **refresh-токен — в `localStorage`** под ключом `mp_refresh_token`. Иного способа пережить
 *   перезагрузку без httpOnly-cookie нет; cookie требуют правок бэкенда (Set-Cookie, CSRF,
 *   SameSite) — отдельная задача.
 * - **профиль** (`UserProfile`) — в памяти, собирается из ответа `/login` или `/refresh`.
 *
 * Модуль не знает про React и про HTTP: React подписывается через `subscribe`/`getUser`
 * (`useSyncExternalStore`), HTTP делает `api/client.ts`. Так у access-токена нет пути обратно
 * в React-состояние или в хранилище.
 */
import { parseRole, type Role } from '../types/role';

export interface UserProfile {
  /** `sub` из JWT (email пользователя). Пусто, если claim не удалось прочитать. */
  email: string;
  role: Role;
  /** claim `companyId` из JWT; у `SYSTEM_ADMIN` и системного `AUDITOR` отсутствует. */
  companyId?: string;
}

/** Тело ответа `POST /api/v1/auth/login` и `POST /api/v1/auth/refresh` (`auth.dto.LoginResponse`). */
export interface LoginResponse {
  token: string;
  expiresIn: number;
  role: string;
  refreshToken: string;
  refreshExpiresIn: number;
}

export type AuthErrorCode = 'UNKNOWN_ROLE' | 'MALFORMED_RESPONSE' | 'NO_REFRESH_TOKEN' | 'SESSION_CLEARED';

/** Ошибка сессии, различимая по `code` (для перевода текста в UI). Не axios-ошибка. */
export class AuthError extends Error {
  readonly code: AuthErrorCode;

  constructor(code: AuthErrorCode, message: string) {
    super(message);
    this.name = 'AuthError';
    this.code = code;
  }
}

export const REFRESH_TOKEN_KEY = 'mp_refresh_token';

/**
 * Ключи, под которыми прежняя версия фронтенда держала access-токен и профиль в `localStorage`.
 * Новый код их не читает, но у уже работавших пользователей они остались бы лежать до очистки
 * браузера — вместе с живым (до 24 ч) access-токеном. Сносим при первой загрузке.
 */
const LEGACY_KEYS = ['token', 'user'] as const;

let accessToken: string | null = null;
let currentUser: UserProfile | null = null;
/**
 * Поколение сессии: растёт при каждом сбросе (`clearSession`, выход в другой вкладке).
 * Нужно, чтобы ответ `/refresh`, ушедшего до выхода, не воскресил сессию после него —
 * см. `client.ts:doRefresh`.
 */
let sessionGeneration = 0;
const listeners = new Set<() => void>();

const notify = () => {
  listeners.forEach((listener) => listener());
};

// ─── access-токен (память) ───────────────────────────────────────────────────

export const getAccessToken = (): string | null => accessToken;

/** Номер поколения сессии; меняется только при сбросе. Снимок «до», сравнение «после». */
export const getSessionGeneration = (): number => sessionGeneration;

// ─── refresh-токен (localStorage) ────────────────────────────────────────────

/**
 * Любое чтение `localStorage` — под `try/catch`: доступ может быть запрещён (приватный режим
 * Safari, политика браузера), а повреждённое значение не должно ронять приложение.
 */
export const getRefreshToken = (): string | null => {
  try {
    const value = localStorage.getItem(REFRESH_TOKEN_KEY);
    return value && value.length > 0 ? value : null;
  } catch (error) {
    console.warn('[auth] localStorage is not readable; treating as no session', error);
    return null;
  }
};

const writeRefreshToken = (value: string | null) => {
  try {
    if (value === null) {
      localStorage.removeItem(REFRESH_TOKEN_KEY);
    } else {
      localStorage.setItem(REFRESH_TOKEN_KEY, value);
    }
  } catch (error) {
    // Без записи сессия проживёт до перезагрузки вкладки — хуже, но не сломано.
    console.warn('[auth] localStorage is not writable; session will not survive a reload', error);
  }
};

// ─── профиль и подписка (для React) ──────────────────────────────────────────

export const getUser = (): UserProfile | null => currentUser;

export const subscribe = (listener: () => void): (() => void) => {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
};

// ─── переходы состояния ──────────────────────────────────────────────────────

/** Сбрасывает всё: access из памяти, refresh из хранилища, профиль. Идемпотентно. */
export const clearSession = (): void => {
  const hadSession = accessToken !== null || currentUser !== null || getRefreshToken() !== null;
  sessionGeneration += 1;
  accessToken = null;
  currentUser = null;
  writeRefreshToken(null);
  if (hadSession) {
    notify();
  }
};

/** Сброс только памяти — когда хранилище уже очистила другая вкладка (событие `storage`). */
const dropInMemorySession = (): void => {
  sessionGeneration += 1;
  const had = accessToken !== null || currentUser !== null;
  accessToken = null;
  currentUser = null;
  if (had) {
    notify();
  }
};

/**
 * Принимает ответ `/login` или `/refresh` и делает его текущей сессией.
 *
 * **Fail-closed:** если роль не распознана (`parseRole` → `null`) или в ответе нет токенов —
 * сессия не создаётся, старая (если была) сбрасывается, и бросается `AuthError`. Никакой роли
 * по умолчанию: раньше пропущенная роль подменялась системным администратором (fail-open) —
 * пользователь без роли молча получал максимальные права.
 *
 * @param emailHint email, введённый в форме входа — запасной вариант, если `sub` в JWT
 *                  не читается. На refresh подсказки нет, тогда email берётся только из JWT.
 */
export const applyLoginResponse = (data: LoginResponse, emailHint?: string): UserProfile => {
  if (!data || typeof data.token !== 'string' || data.token.length === 0
      || typeof data.refreshToken !== 'string' || data.refreshToken.length === 0) {
    clearSession();
    throw new AuthError('MALFORMED_RESPONSE', 'Auth response has no tokens; login refused');
  }

  const role = parseRole(data.role);
  if (role === null) {
    clearSession();
    throw new AuthError(
      'UNKNOWN_ROLE',
      `Server returned an unrecognised role "${String(data.role)}"; login refused`,
    );
  }

  const claims = decodeJwtClaims(data.token);
  const email = typeof claims?.sub === 'string' && claims.sub.length > 0
    ? claims.sub
    : (emailHint ?? '');
  const companyId = claims?.companyId !== undefined && claims.companyId !== null
    ? String(claims.companyId)
    : undefined;

  accessToken = data.token;
  writeRefreshToken(data.refreshToken);
  currentUser = { email, role, companyId };
  notify();
  return currentUser;
};

/**
 * Читает payload JWT **без проверки подписи** — только чтобы показать email и companyId.
 * Подпись проверяет бэкенд на каждом запросе; здесь токен уже получен от него по TLS.
 * Никаких решений о доступе на этих claims не принимается — роль берётся из поля `role`
 * ответа и проверяется `parseRole`.
 */
const decodeJwtClaims = (token: string): Record<string, unknown> | null => {
  try {
    const payload = token.split('.')[1];
    if (!payload) {
      return null;
    }
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=');
    const bytes = Uint8Array.from(atob(padded), (c) => c.charCodeAt(0));
    const parsed: unknown = JSON.parse(new TextDecoder().decode(bytes));
    return parsed !== null && typeof parsed === 'object' ? (parsed as Record<string, unknown>) : null;
  } catch {
    return null;
  }
};

// ─── миграция и синхронизация вкладок ────────────────────────────────────────

if (typeof window !== 'undefined') {
  try {
    LEGACY_KEYS.forEach((key) => localStorage.removeItem(key));
  } catch {
    // Недоступное хранилище — нечего и чистить.
  }

  /**
   * Выход в одной вкладке гасит цепочку refresh-токенов на сервере, но access-токены других
   * вкладок остаются валидными до истечения (stateless-проверка). Событие `storage` приходит
   * во все *остальные* вкладки того же origin — по нему выходим и там, не дожидаясь протухания.
   */
  window.addEventListener('storage', (event) => {
    // key === null — это localStorage.clear(): refresh-токена тоже больше нет.
    const refreshGone = event.key === null
      ? getRefreshToken() === null
      : (event.key === REFRESH_TOKEN_KEY && event.newValue === null);
    if (refreshGone) {
      dropInMemorySession();
    }
  });
}
