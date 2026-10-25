import React, { createContext, useCallback, useContext, useEffect, useMemo, useState, useSyncExternalStore } from 'react';
import { Box, CircularProgress } from '@mui/material';
import {
  MFA_SETUP_PATH,
  MFA_VERIFY_PATH,
  apiClient,
  describeError,
  refreshSession,
  revokeRefreshToken,
} from '../api/client';
import {
  AuthError,
  applyLoginResponse,
  clearSession,
  getAccessToken,
  getRefreshToken,
  getUser,
  isIdleExpired,
  markActivity,
  markEndedByIdle,
  subscribe,
  type LoginResponse,
  type UserProfile,
} from '../auth/session';
import { useIdleLogout } from '../auth/idle';

export type { UserProfile } from '../auth/session';

/** Пароль верен, сессии ещё нет: нужен код второго фактора по этому шагу (Р-141). */
export interface MfaStep {
  mfaToken: string;
  /** Фактор не подключён: сначала `setupMfa`, первый принятый код его подтверждает. */
  setupRequired: boolean;
}

/** Секрет для приложения-аутентификатора — только на время подключения, никуда не сохраняется. */
export interface MfaSetup {
  secret: string;
  otpauthUri: string;
}

interface AuthContextType {
  user: UserProfile | null;
  /** Срок токена здесь не считается: протухший ловит интерсептор по 401 (`api/client.ts`). */
  isAuthenticated: boolean;
  /**
   * Сессия началась — `null`; нужен второй фактор — его шаг (Р-141). Вход не состоялся — `AuthError` с
   * `UNKNOWN_ROLE` или `PASSWORD_CHANGE_REQUIRED` (Р-100).
   */
  login: (email: string, password: string) => Promise<MfaStep | null>;
  /** Смена пароля по текущему и вход с новым (Р-100) — так же, через второй фактор. */
  changePassword: (email: string, currentPassword: string, newPassword: string) => Promise<MfaStep | null>;
  setupMfa: (mfaToken: string) => Promise<MfaSetup>;
  /** Принятый код начинает сессию. */
  verifyMfa: (mfaToken: string, code: string, email: string) => Promise<void>;
  logout: () => Promise<void>;
}

// Шаг без токена — ответ, из которого вход не собрать (fail-closed, как у роли).
const mfaStepOf = (data: LoginResponse | undefined): MfaStep | null => {
  if (data?.mfaRequired !== true) {
    return null;
  }
  if (typeof data.mfaToken !== 'string' || data.mfaToken.length === 0) {
    throw new AuthError('MALFORMED_RESPONSE', 'Second-factor step without a token; login refused');
  }
  return { mfaToken: data.mfaToken, setupRequired: data.mfaSetupRequired === true };
};

const AuthContext = createContext<AuthContextType | undefined>(undefined);

const RestoringSession = () => (
  <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
    <CircularProgress size={60} />
  </Box>
);

// Состояние живёт в `auth/session.ts`; здесь только React-обвязка.
export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const user = useSyncExternalStore(subscribe, getUser);

  // Перезагрузка вкладки: refresh-токен есть, access-токена нет. Пока идёт /refresh — загрузка,
  // а не мигание формы входа.
  const [restoring, setRestoring] = useState<boolean>(() => getUser() === null && getRefreshToken() !== null);

  useEffect(() => {
    if (!restoring) {
      return;
    }
    let active = true;
    // Вкладку открыли после простоя (Р-99): сессию не восстанавливаем, а гасим и на сервере.
    if (isIdleExpired(Date.now())) {
      const staleToken = getRefreshToken();
      markEndedByIdle();
      clearSession();
      void revokeRefreshToken(staleToken);
      setRestoring(false);
      return;
    }
    refreshSession()
      .catch((error: unknown) => {
        // Отказ уже сбросил сессию, сетевая ошибка оставила refresh-токен до следующей перезагрузки.
        console.warn('[auth] session restore failed:', describeError(error));
      })
      .finally(() => {
        if (active) {
          setRestoring(false);
        }
      });
    return () => {
      active = false;
    };
  }, [restoring]);

  const startSession = useCallback((response: { data: LoginResponse }, email: string) => {
    // Прежний refresh-токен (восстановление упало из-за сети) новый вход перезапишет — гасим его
    // на сервере, чтобы не оставлять живую цепочку без хозяина.
    const previous = getRefreshToken();
    try {
      applyLoginResponse(response.data, email);
    } catch (error) {
      // Сервер уже выпустил пару токенов для отклонённого входа — refresh-токен гасим.
      void revokeRefreshToken(response.data?.refreshToken);
      throw error;
    }
    if (previous !== null && previous !== response.data.refreshToken) {
      void revokeRefreshToken(previous);
    }
    // Вход — действие пользователя: отсчёт простоя начинается отсюда.
    markActivity(Date.now());
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const response = await apiClient.post<LoginResponse>('/api/v1/auth/login', { username: email, password });
    // Пароль задал не владелец: токенов нет, сессии не будет до смены (Р-100).
    if (response.data?.passwordChangeRequired === true) {
      throw new AuthError('PASSWORD_CHANGE_REQUIRED', 'The password must be changed before a session starts');
    }
    const step = mfaStepOf(response.data);
    if (step === null) {
      startSession(response, email);
    }
    return step;
  }, [startSession]);

  const changePassword = useCallback(async (email: string, currentPassword: string, newPassword: string) => {
    const response = await apiClient.post<LoginResponse>('/api/v1/auth/change-password',
      { username: email, currentPassword, newPassword });
    const step = mfaStepOf(response.data);
    if (step === null) {
      startSession(response, email);
    }
    return step;
  }, [startSession]);

  const setupMfa = useCallback(async (mfaToken: string) => {
    const response = await apiClient.post<MfaSetup>(MFA_SETUP_PATH, { mfaToken });
    const { secret, otpauthUri } = response.data ?? {};
    if (typeof secret !== 'string' || secret.length === 0 || typeof otpauthUri !== 'string' || otpauthUri.length === 0) {
      throw new AuthError('MALFORMED_RESPONSE', 'Second-factor set-up without a secret');
    }
    return { secret, otpauthUri };
  }, []);

  const verifyMfa = useCallback(async (mfaToken: string, code: string, email: string) => {
    const response = await apiClient.post<LoginResponse>(MFA_VERIFY_PATH, { mfaToken, code });
    startSession(response, email);
  }, [startSession]);

  const logout = useCallback(async () => {
    const refreshToken = getRefreshToken();
    // Локально выходим сразу — UI не должен ждать сеть; отзыв на сервере — вдогонку.
    clearSession();
    if (refreshToken !== null) {
      await revokeRefreshToken(refreshToken);
    }
  }, []);

  const isAuthenticated = user !== null && getAccessToken() !== null;
  const logoutAfterIdle = useCallback(() => {
    markEndedByIdle();
    void logout();
  }, [logout]);
  useIdleLogout(isAuthenticated, logoutAfterIdle);

  const value = useMemo<AuthContextType>(() => ({
    user,
    isAuthenticated,
    login,
    changePassword,
    setupMfa,
    verifyMfa,
    logout,
  }), [user, isAuthenticated, login, changePassword, setupMfa, verifyMfa, logout]);

  if (restoring) {
    return <RestoringSession />;
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
