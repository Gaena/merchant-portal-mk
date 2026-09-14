import React, { lazy, Suspense } from 'react';
import { createBrowserRouter } from 'react-router';
import { Box, CircularProgress } from '@mui/material';
import { MainLayout } from './layouts/MainLayout';
import { ProtectedRoute, PublicOnlyRoute, RoleRoute } from './auth/guards';
import { ROUTE_ACCESS } from './auth/routeAccess';
import { RouteErrorPage } from './pages/RouteErrorPage';
import { NotFoundPage } from './pages/NotFoundPage';
import type { Transaction, TransactionFilters } from './types/transaction';

const HomePage = lazy(() => import('./pages/HomePage').then(m => ({ default: m.HomePage })));
const TransactionListPage = lazy(() => import('./pages/TransactionListPage').then(m => ({ default: m.TransactionListPage })));
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

/**
 * Пропсы, которые `App` прокидывает в лейаут и страницы транзакций. Состояние транзакций живёт
 * в `App` (один список на все страницы), поэтому роутер собирается из него.
 */
export interface AppRouterProps {
  transactions: Transaction[];
  filters: TransactionFilters;
  onFilterChange: (filters: TransactionFilters) => void;
  autoRefresh: boolean;
  onToggleAutoRefresh: () => void;
  newTransactionCount: number;
  onRefresh: () => void;
}

export const createRouter = (layoutProps: AppRouterProps) => {
  return createBrowserRouter([
    {
      path: '/login',
      errorElement: <RouteErrorPage />,
      element: (
        <PublicOnlyRoute>
          <Suspense fallback={<PageLoader />}>
            <LoginPage />
          </Suspense>
        </PublicOnlyRoute>
      )
    },
    {
      path: '/',
      // Исключение при рендере любой вложенной страницы показывает страницу ошибки, а не белый экран.
      errorElement: <RouteErrorPage />,
      element: (
        <ProtectedRoute>
          <MainLayout newTransactionCount={layoutProps.newTransactionCount} />
        </ProtectedRoute>
      ),
      children: [
        {
          index: true,
          element: (
            <Suspense fallback={<PageLoader />}>
              <HomePage />
            </Suspense>
          )
        },
        // Выписка провайдера из сервиса ecom (Р-65): свои запросы и своя карточка заказа по номеру у
        // провайдера. Общий список операций портала из App сюда не передаётся — это другой источник.
        {
          path: 'transactions/ecommerce',
          element: (
            <Suspense fallback={<PageLoader />}>
              <EcommerceTransactionListPage />
            </Suspense>
          )
        },
        {
          path: 'transactions/ecommerce/:orderId',
          element: (
            <Suspense fallback={<PageLoader />}>
              <EcommerceOrderDetailPage />
            </Suspense>
          )
        },
        {
          path: 'transactions',
          element: (
            <Suspense fallback={<PageLoader />}>
              <TransactionListPage
                transactions={layoutProps.transactions}
                filters={layoutProps.filters}
                onFilterChange={layoutProps.onFilterChange}
                autoRefresh={layoutProps.autoRefresh}
                onToggleAutoRefresh={layoutProps.onToggleAutoRefresh}
                newTransactionCount={layoutProps.newTransactionCount}
                onRefresh={layoutProps.onRefresh}
              />
            </Suspense>
          )
        },
        {
          path: 'transactions/:id',
          element: (
            <Suspense fallback={<PageLoader />}>
              <TransactionDetailPage
                transactions={layoutProps.transactions}
              />
            </Suspense>
          )
        },
        {
          path: 'pay-by-link',
          element: (
            <Suspense fallback={<PageLoader />}>
              <PayByLinkPage />
            </Suspense>
          )
        },
        {
          path: 'pay-by-link/:id',
          element: (
            <Suspense fallback={<PageLoader />}>
              <PayByLinkDetailPage />
            </Suspense>
          )
        },
        // Ролевые guard'ы — UX, не безопасность: список ролей общий с сайдбаром (auth/routeAccess.ts),
        // настоящая проверка прав на бэкенде.
        {
          path: 'companies',
          element: (
            <RoleRoute allow={ROUTE_ACCESS['/companies']}>
              <Suspense fallback={<PageLoader />}>
                <CompaniesPage />
              </Suspense>
            </RoleRoute>
          )
        },
        {
          path: 'terminals',
          element: (
            <Suspense fallback={<PageLoader />}>
              <TerminalsPage />
            </Suspense>
          )
        },
        {
          path: 'users',
          element: (
            <RoleRoute allow={ROUTE_ACCESS['/users']}>
              <Suspense fallback={<PageLoader />}>
                <UsersPage />
              </Suspense>
            </RoleRoute>
          )
        },
        {
          path: 'audit-logs',
          element: (
            <RoleRoute allow={ROUTE_ACCESS['/audit-logs']}>
              <Suspense fallback={<PageLoader />}>
                <AuditLogsPage />
              </Suspense>
            </RoleRoute>
          )
        },
        {
          path: 'settings',
          element: (
            <Suspense fallback={<PageLoader />}>
              <SettingsPage />
            </Suspense>
          )
        },
        // Несуществующий путь для вошедшего — 404 внутри лейаута; для не вошедшего
        // ProtectedRoute выше уже увёл на /login.
        {
          path: '*',
          element: <NotFoundPage />
        }
      ]
    }
  ]);
};
