import React from 'react';
import { Navigate, useLocation } from 'react-router';
import { useAuth } from '../context/AuthContext';
import type { Role } from '../types/role';
import { ForbiddenPage } from '../pages/ForbiddenPage';

// Адрес запоминается: открыл ссылку на карточку, вошёл — попал на карточку, а не на главную.
export const ProtectedRoute = ({ children }: { children: React.ReactNode }) => {
  const { isAuthenticated } = useAuth();
  const location = useLocation();
  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  return <>{children}</>;
};

export const PublicOnlyRoute = ({ children }: { children: React.ReactNode }) => {
  const { isAuthenticated } = useAuth();
  const location = useLocation();
  if (isAuthenticated) {
    return <Navigate to={returnPathFrom(location.state)} replace />;
  }
  return <>{children}</>;
};

/** Только свой относительный путь: чужой origin сюда не попадёт. */
export const returnPathFrom = (state: unknown): string => {
  const from = (state as { from?: unknown } | null)?.from;
  return typeof from === 'string' && from.startsWith('/') && !from.startsWith('//') && from !== '/login' ? from : '/';
};

interface RoleRouteProps {
  /** Из `auth/routeAccess.ts`, не литералами на месте. */
  allow: readonly Role[];
  children: React.ReactNode;
}

/**
 * Роль не подходит — страница «нет доступа», а не белый экран и не редирект на вход: пользователь
 * вошёл, просто прав нет. Это UX, а не безопасность — права проверяет бэкенд.
 */
export const RoleRoute = ({ allow, children }: RoleRouteProps) => {
  const { user } = useAuth();
  if (!user || !allow.includes(user.role)) {
    return <ForbiddenPage />;
  }
  return <>{children}</>;
};
