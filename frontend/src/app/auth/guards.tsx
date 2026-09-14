import React from 'react';
import { Navigate, useLocation } from 'react-router';
import { useAuth } from '../context/AuthContext';
import type { Role } from '../types/role';
import { ForbiddenPage } from '../pages/ForbiddenPage';

/**
 * Только для вошедших: без сессии — на `/login`, с запомненным адресом: открыл ссылку на карточку,
 * вошёл — попал на карточку, а не на главную.
 */
export const ProtectedRoute = ({ children }: { children: React.ReactNode }) => {
  const { isAuthenticated } = useAuth();
  const location = useLocation();
  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  return <>{children}</>;
};

/** Только для не вошедших (`/login`): с сессией — туда, откуда увели на вход, иначе на главную. */
export const PublicOnlyRoute = ({ children }: { children: React.ReactNode }) => {
  const { isAuthenticated } = useAuth();
  const location = useLocation();
  if (isAuthenticated) {
    return <Navigate to={returnPathFrom(location.state)} replace />;
  }
  return <>{children}</>;
};

/** Адрес, куда вернуть после входа. Только свой относительный путь: чужой origin сюда не попадёт. */
export const returnPathFrom = (state: unknown): string => {
  const from = (state as { from?: unknown } | null)?.from;
  return typeof from === 'string' && from.startsWith('/') && !from.startsWith('//') && from !== '/login' ? from : '/';
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
