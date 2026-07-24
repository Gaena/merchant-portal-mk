import React, { lazy, Suspense } from 'react';
import { createBrowserRouter, Navigate } from 'react-router';
import { Box, CircularProgress } from '@mui/material';
import { MainLayout } from './layouts/MainLayout';
import { useAuth } from './context/AuthContext';

const HomePage = lazy(() => import('./pages/HomePage').then(m => ({ default: m.HomePage })));
const TransactionListPage = lazy(() => import('./pages/TransactionListPage').then(m => ({ default: m.TransactionListPage })));
const EcommerceTransactionListPage = lazy(() => import('./pages/EcommerceTransactionListPage').then(m => ({ default: m.EcommerceTransactionListPage })));
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

const ProtectedRoute = ({ children }: { children: React.ReactNode }) => {
  const { isAuthenticated } = useAuth();
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
};

const PublicOnlyRoute = ({ children }: { children: React.ReactNode }) => {
  const { isAuthenticated } = useAuth();
  if (isAuthenticated) {
    return <Navigate to="/pay-by-link" replace />;
  }
  return <>{children}</>;
};

export const createRouter = (layoutProps: any) => {
  return createBrowserRouter([
    {
      path: '/login',
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
      element: (
        <ProtectedRoute>
          <MainLayout {...layoutProps} />
        </ProtectedRoute>
      ),
      children: [
        {
          index: true,
          element: (
            <Suspense fallback={<PageLoader />}>
              <HomePage transactions={layoutProps.transactions} />
            </Suspense>
          )
        },
        {
          path: 'transactions/ecommerce',
          element: (
            <Suspense fallback={<PageLoader />}>
              <EcommerceTransactionListPage
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
        {
          path: 'companies',
          element: (
            <Suspense fallback={<PageLoader />}>
              <CompaniesPage />
            </Suspense>
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
            <Suspense fallback={<PageLoader />}>
              <UsersPage />
            </Suspense>
          )
        },
        {
          path: 'audit-logs',
          element: (
            <Suspense fallback={<PageLoader />}>
              <AuditLogsPage />
            </Suspense>
          )
        },
        {
          path: 'settings',
          element: (
            <Suspense fallback={<PageLoader />}>
              <SettingsPage />
            </Suspense>
          )
        }
      ]
    }
  ]);
};