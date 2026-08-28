import React from 'react';
import { Navigate } from 'react-router';
import { useAuth } from '../context/AuthContext';
import type { Role } from '../types/role';
import { ForbiddenPage } from '../pages/ForbiddenPage';

/** Только для вошедших: без сессии — на `/login`. */
export const ProtectedRoute = ({ children }: { children: React.ReactNode }) => {
  const { isAuthenticated } = useAuth();
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
};

/** Только для не вошедших (`/login`): с сессией — на главную, туда же, куда ведёт вход. */
export const PublicOnlyRoute = ({ children }: { children: React.ReactNode }) => {
  const { isAuthenticated } = useAuth();
  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
};

interface RoleRouteProps {
  /** Роли, которым маршрут открыт. Берётся из `auth/routeAccess.ts`, не из литералов на месте. */
  allow: readonly Role[];
  children: React.ReactNode;
}

/**
 * Ролевой guard поверх `ProtectedRoute`. Роль не подходит → страница «нет доступа», а не белый
 * экран и не редирект на логин: пользователь вошёл, просто у него нет прав.
 *
 * ⚠ Это UX, а не безопасность: настоящая проверка — на бэкенде (`UserService.validateAccess`,
 * `PaymentLinkService.validateAccess`, …), клиентский guard лишь прячет то, что всё равно
 * вернёт 403. См. `auth/routeAccess.ts`.
 */
export const RoleRoute = ({ allow, children }: RoleRouteProps) => {
  const { user } = useAuth();
  if (!user || !allow.includes(user.role)) {
    return <ForbiddenPage />;
  }
  return <>{children}</>;
};
