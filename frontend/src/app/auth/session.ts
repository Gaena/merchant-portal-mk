/**
 * Единственное место, где живут токены (AGENTS §9). Access-токен — только в памяти: `localStorage`
 * читает любой XSS. Refresh — в `localStorage`: без httpOnly-cookie иначе не пережить перезагрузку.
 * React и HTTP модуль не знает — у access-токена нет пути в React-состояние или в хранилище.
 */
import { parseRole, type Role } from '../types/role';

export interface UserProfile {
  /** `sub` из JWT; если не читается — логин из формы входа или пусто. */
  email: string;
  role: Role;
  /** claim `companyId` из JWT; у `SYSTEM_ADMIN` и системного `AUDITOR` отсутствует. */
  companyId?: string;
}

/** Ответ `/login` и `/refresh` (`auth.dto.LoginResponse`). */
export interface LoginResponse {
  token: string;
  expiresIn: number;
  role: string;
  refreshToken: string;
  refreshExpiresIn: number;
  /** Пароль задан не владельцем: токенов нет, сессию даёт только `/change-password` (Р-100). */
  passwordChangeRequired?: boolean;
}

export type AuthErrorCode =
  | 'UNKNOWN_ROLE'
  | 'MALFORMED_RESPONSE'
  | 'NO_REFRESH_TOKEN'
  | 'SESSION_CLEARED'
  | 'PASSWORD_CHANGE_REQUIRED';

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
 * Выход по простою (PCI DSS 8.2.8, Р-99). Отметка действия общая для вкладок: работа в одной не
 * выкидывает из другой. Refresh-токен сервер гасит через 20 минут без обновления, поэтому
 * `auth/idle.ts` обновляет его не реже раза в `KEEP_ALIVE_AFTER_MS`.
 */
export const LAST_ACTIVITY_KEY = 'mp_last_activity';
export const IDLE_TIMEOUT_MS = 15 * 60 * 1000;
export const KEEP_ALIVE_AFTER_MS = 5 * 60 * 1000;

// Ключи access-токена и профиля старых сборок: у пользователей они лежат до очистки браузера,
// поэтому стираются при загрузке.
const LEGACY_KEYS = ['token', 'user'] as const;

let accessToken: string | null = null;
let currentUser: UserProfile | null = null;
let lastTokenAt = 0;
// Копия в памяти: без доступного хранилища вкладка считает простой сама.
let lastActivityInMemory = 0;
// Сессию закрыл простой — форма входа скажет об этом один раз.
let endedByIdle = false;
// Растёт при каждом сбросе: ответ `/refresh`, ушедшего до выхода, не воскресит сессию
// (`client.ts:doRefresh`).
let sessionGeneration = 0;
const listeners = new Set<() => void>();

const notify = () => {
  listeners.forEach((listener) => listener());
};

export const getAccessToken = (): string | null => accessToken;

export const getSessionGeneration = (): number => sessionGeneration;

// Доступ к `localStorage` бывает запрещён (приватный режим, политика браузера) — всё под try/catch.
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
    console.warn('[auth] localStorage is not writable; session will not survive a reload', error);
  }
};

export const getLastActivity = (): number => {
  let stored = 0;
  try {
    const value = Number(localStorage.getItem(LAST_ACTIVITY_KEY));
    stored = Number.isFinite(value) ? value : 0;
  } catch {
    // Хранилище недоступно — считаем по памяти вкладки.
  }
  return Math.max(stored, lastActivityInMemory);
};

export const markActivity = (now: number): void => {
  lastActivityInMemory = now;
  try {
    localStorage.setItem(LAST_ACTIVITY_KEY, String(now));
  } catch {
    // Без записи другие вкладки не узнают о действии — каждая считает свой простой.
  }
};

/** Нет отметки вовсе — тоже истёк: время последнего действия неизвестно. */
export const isIdleExpired = (now: number): boolean => now - getLastActivity() > IDLE_TIMEOUT_MS;

export const getLastTokenAt = (): number => lastTokenAt;

export const markEndedByIdle = (): void => {
  endedByIdle = true;
};

/** Чтение и сброс раздельно: StrictMode зовёт инициализатор `useState` дважды и потерял бы сообщение. */
export const hasIdleNotice = (): boolean => endedByIdle;

export const clearIdleNotice = (): void => {
  endedByIdle = false;
};

export const getUser = (): UserProfile | null => currentUser;

export const subscribe = (listener: () => void): (() => void) => {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
};

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

// Только память: хранилище уже очистила другая вкладка (событие `storage`).
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
 * Fail-closed: нераспознанная роль или нет токенов — старая сессия сбрасывается и бросается
 * `AuthError`. Роли по умолчанию не заводить: пользователь без роли получил бы её права.
 * `emailHint` — логин из формы входа на случай нечитаемого `sub`; у refresh его нет.
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
  lastTokenAt = Date.now();
  currentUser = { email, role, companyId };
  notify();
  return currentUser;
};

// Payload без проверки подписи — только для показа email и companyId. Решений о доступе на этих
// claims не принимать: подпись проверяет бэкенд, роль берётся из поля `role` через `parseRole`.
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

if (typeof window !== 'undefined') {
  try {
    LEGACY_KEYS.forEach((key) => localStorage.removeItem(key));
  } catch {
    // Хранилище недоступно — нечего и чистить.
  }

  // Выход гасит refresh-цепочку на сервере, но access-токены других вкладок живут до истечения.
  // Событие `storage` приходит в остальные вкладки — выходим и там.
  window.addEventListener('storage', (event) => {
    // key === null — это localStorage.clear(): refresh-токена тоже нет.
    const refreshGone = event.key === null
      ? getRefreshToken() === null
      : (event.key === REFRESH_TOKEN_KEY && event.newValue === null);
    if (refreshGone) {
      // Другая вкладка вышла по простою — форма входа скажет об этом и здесь.
      if (isIdleExpired(Date.now())) {
        endedByIdle = true;
      }
      dropInMemorySession();
    }
  });
}
