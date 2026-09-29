import React, { lazy, Suspense } from 'react';
import { createBrowserRouter } from 'react-router';
import { Box, CircularProgress } from '@mui/material';
import { MainLayout } from './layouts/MainLayout';
import { ProtectedRoute, PublicOnlyRoute, RoleRoute } from './auth/guards';
import { ROUTE_ACCESS } from './auth/routeAccess';
import { RouteErrorPage } from './pages/RouteErrorPage';
import { NotFoundPage } from './pages/NotFoundPage';

const HomePage = lazy(() => import('./pages/HomePage').then(m => ({ default: m.HomePage })));
const EcommerceTransactionListPage = lazy(() => import('./pages/EcommerceTransactionListPage').then(m => ({ default: m.EcommerceTransactionListPage })));
const EcommerceOrderDetailPage = lazy(() => import('./pages/EcommerceOrderDetailPage').then(m => ({ default: m.EcommerceOrderDetailPage })));
const TransactionDetailPage = lazy(() => import('./pages/TransactionDetailPage').then(m => ({ default: m.TransactionDetailPage })));
const SettingsPage = lazy(() => import('./pages/SettingsPage').then(m => ({ default: m.SettingsPage })));
const CompaniesPage = lazy(() => import('./pages/CompaniesPage').then(m => ({ default: m.CompaniesPage })));
const TerminalsPage = lazy(() => import('./pages/TerminalsPage').then(m => ({ default: m.TerminalsPage })));
const LoginPage = lazy(() => import('./pages/LoginPage').then(m => ({ default: m.LoginPage })));
const PayByLinkPage = lazy(() => import('./pages/PayByLinkPage').then(m => ({ default: m.PayByLinkPage })));
const PayByLinkDetailPage = lazy(() => import('./pages/PayByLinkDetailPage').then(m => ({ default: m.PayByLinkDetailPage })));
const UsersPage = lazy(() => import('./pages/UsersPage').then(m => ({ default: m.UsersPage })));
const AuditLogsPage = lazy(() => import('./pages/AuditLogsPage').then(m => ({ default: m.AuditLogsPage })));

const PageLoader = () => (
  <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '60vh' }}>
    <CircularProgress size={40} />
  </Box>
);

const page = (element: React.ReactNode) => <Suspense fallback={<PageLoader />}>{element}</Suspense>;

/**
 * Роутер создаётся один раз, на уровне модуля: не пересоздавать в `useMemo` от состояния — каждый
 * `createBrowserRouter` оставляет свой `popstate`-listener. Списка операций портала нет (Р-65).
 */
export const router = createBrowserRouter([
  {
    path: '/login',
    errorElement: <RouteErrorPage />,
    element: (
      <PublicOnlyRoute>
        {page(<LoginPage />)}
      </PublicOnlyRoute>
    )
  },
  {
    path: '/',
    // Исключение при рендере вложенной страницы — страница ошибки, а не белый экран.
    errorElement: <RouteErrorPage />,
    element: (
      <ProtectedRoute>
        <MainLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: page(<HomePage />) },
      // Выписка сервиса ecom (Р-65); карточка заказа — по номеру у провайдера.
      { path: 'transactions/ecommerce', element: page(<EcommerceTransactionListPage />) },
      { path: 'transactions/ecommerce/:orderId', element: page(<EcommerceOrderDetailPage />) },
      { path: 'transactions/:id', element: page(<TransactionDetailPage />) },
      { path: 'pay-by-link', element: page(<PayByLinkPage />) },
      { path: 'pay-by-link/:id', element: page(<PayByLinkDetailPage />) },
      // Ролевые guard'ы — UX, не безопасность; роли общие с сайдбаром (auth/routeAccess.ts).
      {
        path: 'companies',
        element: <RoleRoute allow={ROUTE_ACCESS['/companies']}>{page(<CompaniesPage />)}</RoleRoute>
      },
      { path: 'terminals', element: page(<TerminalsPage />) },
      {
        path: 'users',
        element: <RoleRoute allow={ROUTE_ACCESS['/users']}>{page(<UsersPage />)}</RoleRoute>
      },
      {
        path: 'audit-logs',
        element: <RoleRoute allow={ROUTE_ACCESS['/audit-logs']}>{page(<AuditLogsPage />)}</RoleRoute>
      },
      { path: 'settings', element: page(<SettingsPage />) },
      // 404 внутри лейаута; не вошедшего ProtectedRoute уже увёл на /login.
      { path: '*', element: <NotFoundPage /> }
    ]
  }
]);
