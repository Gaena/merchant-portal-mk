import React, { createContext, useCallback, useContext, useEffect, useMemo, useState, useSyncExternalStore } from 'react';
import { Box, CircularProgress } from '@mui/material';
import { apiClient, describeError, refreshSession, revokeRefreshToken } from '../api/client';
import {
  applyLoginResponse,
  clearSession,
  getAccessToken,
  getRefreshToken,
  getUser,
  subscribe,
  type LoginResponse,
  type UserProfile,
} from '../auth/session';

export type { UserProfile } from '../auth/session';

interface AuthContextType {
  user: UserProfile | null;
  /** Есть access-токен в памяти (и профиль). Срок токена здесь не считается: протухший
   *  токен ловит интерсептор по 401 и молча обновляет — см. `api/client.ts`. */
  isAuthenticated: boolean;
  /** Бросает `AuthError('UNKNOWN_ROLE')`, если сервер вернул нераспознанную роль — вход не состоялся. */
  login: (email: string, password: string) => Promise<void>;
  /** Гасит refresh-токен на сервере (ошибку запроса игнорирует, но логирует) и чистит состояние. */
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

const RestoringSession = () => (
  <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
    <CircularProgress size={60} />
  </Box>
);

/**
 * Провайдер авторизации. Состояние живёт в `auth/session.ts` (access-токен — в памяти,
 * refresh — в localStorage); здесь только React-обвязка и три операции: восстановление
 * сессии при загрузке, вход, выход.
 */
export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const user = useSyncExternalStore(subscribe, getUser);

  // Восстановление сессии: refresh-токен есть, access-токена в памяти нет (перезагрузка вкладки).
  // Пока идёт /refresh — показываем загрузку, а не мигаем формой логина.
  const [restoring, setRestoring] = useState<boolean>(() => getUser() === null && getRefreshToken() !== null);

  useEffect(() => {
    if (!restoring) {
      return;
    }
    let active = true;
    refreshSession()
      .catch((error: unknown) => {
        // Отказ (401, плохая роль) уже сбросил сессию; сетевая ошибка оставила refresh-токен —
        // следующая перезагрузка попробует снова. В обоих случаях показываем вход.
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

  const login = useCallback(async (email: string, password: string) => {
    const response = await apiClient.post<LoginResponse>('/api/v1/auth/login', { username: email, password });
    // Если в хранилище остался прежний refresh-токен (восстановление не удалось из-за сети,
    // и пользователь вошёл заново) — новый вход его перезапишет; гасим на сервере вдогонку,
    // чтобы не оставлять живую цепочку без хозяина.
    const previous = getRefreshToken();
    try {
      // Роль — только через parseRole; нераспознанная роль → AuthError, сессия не создаётся.
      applyLoginResponse(response.data, email);
    } catch (error) {
      // Сервер уже выпустил пару токенов для отклонённого входа — refresh-токен гасим.
      void revokeRefreshToken(response.data?.refreshToken);
      throw error;
    }
    if (previous !== null && previous !== response.data.refreshToken) {
      void revokeRefreshToken(previous);
    }
  }, []);

  const logout = useCallback(async () => {
    const refreshToken = getRefreshToken();
    // Локально выходим сразу — UI не должен ждать сеть; отзыв на сервере — вдогонку.
    clearSession();
    if (refreshToken !== null) {
      await revokeRefreshToken(refreshToken);
    }
  }, []);

  const value = useMemo<AuthContextType>(() => ({
    user,
    // Токен и профиль ставятся и сбрасываются вместе (session.ts), но источник истины — токен.
    isAuthenticated: user !== null && getAccessToken() !== null,
    login,
    logout,
  }), [user, login, logout]);

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
