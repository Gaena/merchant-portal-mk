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
 * Для логов: статус и текст, **без** самого объекта ошибки. `AxiosError` тащит `config.data`
 * с телом запроса — для `/refresh` и `/logout` это refresh-токен, ему в консоли не место.
 */
export const describeError = (error: unknown): string => {
  if (axios.isAxiosError(error)) {
    return error.response
      ? `HTTP ${error.response.status} ${error.config?.method?.toUpperCase() ?? ''} ${error.config?.url ?? ''}`
      : `network error: ${error.code ?? error.message}`;
  }
  return error instanceof Error ? `${error.name}: ${error.message}` : String(error);
};

// Пустая строка по умолчанию — запросы идут относительно текущего origin: в dev их
// разводит прокси из vite.config.ts, в проде — nginx на том же домене. Если фронтенд
// обслуживается отдельно от API, задайте VITE_API_BASE_URL (см. .env.example).
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  headers: {
    'Content-Type': 'application/json',
  },
});

const LOGIN_PATH = '/api/v1/auth/login';
const REFRESH_PATH = '/api/v1/auth/refresh';
const LOGOUT_PATH = '/api/v1/auth/logout';

/**
 * Эндпоинты, к которым access-токен не прикладывается и по которым 401 не запускает
 * обновление: они публичны по смыслу (`PublicEndpoints.PUBLIC_API`), а 401 от `/login` и
 * `/refresh` — это ответ по существу («неверный пароль», «refresh-токен недействителен»),
 * а не признак протухшего access-токена. Без этого исключения `/refresh`, вернувший 401,
 * запускал бы ещё один `/refresh` — и так по кругу.
 */
const AUTH_PATHS: ReadonlySet<string> = new Set([LOGIN_PATH, REFRESH_PATH, LOGOUT_PATH]);

const isAuthEndpoint = (url: string | undefined): boolean => {
  if (!url) {
    return false;
  }
  try {
    // url может быть относительным ('/api/v1/auth/login') или абсолютным (с baseURL).
    return AUTH_PATHS.has(new URL(url, window.location.origin).pathname);
  } catch {
    return false;
  }
};

/** Флаг «этот запрос уже повторяли после обновления токена» — живёт на конфиге запроса. */
interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  _retried?: boolean;
}

// ─── обновление токена: single-flight ────────────────────────────────────────

let refreshInFlight: Promise<UserProfile> | null = null;

/**
 * Обновляет пару токенов через `POST /api/v1/auth/refresh` и возвращает профиль.
 *
 * **Одиночное (single-flight):** сколько бы запросов ни получили 401 одновременно, обновление
 * идёт одно — остальные ждут тот же промис. Бэкенд гонку пережил бы (окно снисхождения ротации
 * из P1-12), но оно для гонки *вкладок*, а не для штатного режима одной вкладки.
 *
 * Отказ сервера (401 «Invalid refresh token») и нераспознанная роль в ответе сбрасывают сессию —
 * дальше пользователь окажется на странице входа. Сетевая ошибка сессию **не** сбрасывает:
 * refresh-токен остаётся, следующая попытка может пройти.
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
    // Без refresh-токена восстанавливать нечего; access в памяти без него — осиротевший, гасим.
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
    // Пока /refresh летел, сессию сбросили (выход в этой или другой вкладке). Ответ применять
    // нельзя — иначе только что вышедший пользователь оказался бы снова внутри. Сервер уже
    // выпустил новую пару; её refresh-токен гасим вдогонку, чтобы не оставлять живым.
    void revokeRefreshToken(response.data?.refreshToken);
    throw new AuthError('SESSION_CLEARED', 'Session was cleared while the refresh was in flight');
  }
  try {
    return applyLoginResponse(response.data);
  } catch (error) {
    // Роль не распознана / ответ без токенов: applyLoginResponse уже сбросил сессию.
    // Только что выпущенный сервером refresh-токен при этом остался бы живым — гасим.
    void revokeRefreshToken(response.data?.refreshToken);
    throw error;
  }
};

/**
 * `POST /api/v1/auth/logout` — гасит цепочку refresh-токенов на сервере. Отвечает 204 всегда,
 * поэтому единственный интересный исход — сетевая ошибка: её логируем и не пробрасываем,
 * локальный выход состоится в любом случае (см. `AuthProvider.logout`).
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

// ─── интерсепторы ────────────────────────────────────────────────────────────

apiClient.interceptors.request.use((config) => {
  // Токен — только из памяти (auth/session.ts). В localStorage его нет и не должно быть.
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

    // 1. 401 от самих /login, /refresh, /logout — ответ по существу, не повод обновляться.
    if (isAuthEndpoint(config.url)) {
      throw error;
    }

    // 2. Уже повторяли с новым токеном и снова 401 — токен не помогает, выходим.
    if (config._retried) {
      clearSession();
      throw error;
    }

    // 3. Обновляем (single-flight) и повторяем исходный запрос с новым access-токеном.
    //    Заголовок Authorization проставит request-интерсептор из обновлённой памяти.
    config._retried = true;
    try {
      await refreshSession();
    } catch {
      // Обновиться не удалось: сессия уже сброшена (401 / нет токена / плохая роль) либо сеть.
      // Наружу отдаём исходную ошибку — вызывающий код видит тот же 401, что и без интерсептора.
      throw error;
    }
    return apiClient.request(config);
  },
);
