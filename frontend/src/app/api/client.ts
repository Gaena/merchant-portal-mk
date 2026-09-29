import axios, { type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import {
  AuthError,
  applyLoginResponse,
  clearSession,
  getAccessToken,
  getRefreshToken,
  getSessionGeneration,
  type LoginResponse,
  type UserProfile,
} from '../auth/session';

/**
 * Статус и текст ошибки для лога. Сам `AxiosError` в лог не класть: в `config.data` тело запроса,
 * у `/refresh` и `/logout` это refresh-токен.
 */
export const describeError = (error: unknown): string => {
  if (axios.isAxiosError(error)) {
    return error.response
      ? `HTTP ${error.response.status} ${error.config?.method?.toUpperCase() ?? ''} ${error.config?.url ?? ''}`
      : `network error: ${error.code ?? error.message}`;
  }
  return error instanceof Error ? `${error.name}: ${error.message}` : String(error);
};

// Пусто — запросы относительно origin: в dev их разводит прокси Vite, в проде — nginx.
// API на другом адресе — задать VITE_API_BASE_URL (AGENTS §4).
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  headers: {
    'Content-Type': 'application/json',
  },
});

const LOGIN_PATH = '/api/v1/auth/login';
const REFRESH_PATH = '/api/v1/auth/refresh';
const LOGOUT_PATH = '/api/v1/auth/logout';
const CHANGE_PASSWORD_PATH = '/api/v1/auth/change-password';

// Публичные пути (`PublicEndpoints.PUBLIC_API`): токен не прикладывается, а 401 — ответ по
// существу, не повод обновляться. Иначе 401 от `/refresh` запускал бы `/refresh` по кругу.
const AUTH_PATHS: ReadonlySet<string> = new Set([LOGIN_PATH, REFRESH_PATH, LOGOUT_PATH, CHANGE_PASSWORD_PATH]);

const isAuthEndpoint = (url: string | undefined): boolean => {
  if (!url) {
    return false;
  }
  try {
    // url бывает относительным и абсолютным (с baseURL).
    return AUTH_PATHS.has(new URL(url, window.location.origin).pathname);
  } catch {
    return false;
  }
};

interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  _retried?: boolean;
}

let refreshInFlight: Promise<UserProfile> | null = null;

/**
 * Single-flight: сколько бы запросов ни получили 401, обновление одно, остальные ждут тот же промис
 * (окно снисхождения сервера, P1-12, — для гонки вкладок, а не для штатной работы).
 * 401 от сервера и нераспознанная роль сбрасывают сессию; сетевая ошибка — нет: refresh-токен
 * остаётся, следующая попытка может пройти.
 */
export const refreshSession = (): Promise<UserProfile> => {
  if (refreshInFlight === null) {
    refreshInFlight = doRefresh().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
};

const doRefresh = async (): Promise<UserProfile> => {
  const refreshToken = getRefreshToken();
  if (refreshToken === null) {
    // Access-токен без refresh-токена — осиротевший: гасим.
    clearSession();
    throw new AuthError('NO_REFRESH_TOKEN', 'No refresh token stored; nothing to refresh');
  }
  const generation = getSessionGeneration();
  let response: AxiosResponse<LoginResponse>;
  try {
    response = await apiClient.post<LoginResponse>(REFRESH_PATH, { refreshToken });
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      clearSession();
    }
    throw error;
  }
  if (getSessionGeneration() !== generation) {
    // Пока /refresh летел, сессию сбросили: применённый ответ вернул бы вышедшего внутрь.
    // Выпущенный сервером refresh-токен гасим вдогонку.
    void revokeRefreshToken(response.data?.refreshToken);
    throw new AuthError('SESSION_CLEARED', 'Session was cleared while the refresh was in flight');
  }
  try {
    return applyLoginResponse(response.data);
  } catch (error) {
    // Сессию applyLoginResponse уже сбросил, а выпущенный сервером refresh-токен остался бы живым.
    void revokeRefreshToken(response.data?.refreshToken);
    throw error;
  }
};

/**
 * Гасит цепочку refresh-токенов на сервере; тот отвечает 204 всегда. Сетевую ошибку только
 * логируем: локальный выход состоится в любом случае (`AuthProvider.logout`).
 */
export const revokeRefreshToken = async (refreshToken: string | null | undefined): Promise<void> => {
  if (typeof refreshToken !== 'string' || refreshToken.length === 0) {
    return;
  }
  try {
    await apiClient.post(LOGOUT_PATH, { refreshToken });
  } catch (error) {
    console.warn('[auth] logout request failed; local session is cleared anyway:', describeError(error));
  }
};

apiClient.interceptors.request.use((config) => {
  // Токен — только из памяти (`auth/session.ts`), в localStorage его не класть.
  const token = getAccessToken();
  if (token && !isAuthEndpoint(config.url)) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response: AxiosResponse) => response,
  async (error: unknown) => {
    if (!axios.isAxiosError(error) || error.response?.status !== 401 || !error.config) {
      throw error;
    }
    const config = error.config as RetriableRequestConfig;

    if (isAuthEndpoint(config.url)) {
      throw error;
    }

    // Повтор с новым токеном снова получил 401 — обновление не помогает, выходим.
    if (config._retried) {
      clearSession();
      throw error;
    }

    // Заголовок Authorization повтору проставит request-интерсептор из обновлённой памяти.
    config._retried = true;
    try {
      await refreshSession();
    } catch {
      // Наружу — исходная ошибка: вызывающий видит тот же 401, что и без интерсептора.
      throw error;
    }
    return apiClient.request(config);
  },
);
