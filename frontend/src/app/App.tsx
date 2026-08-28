import React, { useState, useEffect, useMemo } from 'react';
import {
  ThemeProvider,
  createTheme,
  CssBaseline
} from '@mui/material';
import { RouterProvider } from 'react-router';
import type { Transaction, TransactionFilters } from './types/transaction';
import { parsePaymentMethod, parseTransactionStatus } from './types/transaction';
import { createRouter } from './routes';
import { AuthProvider, useAuth } from './context/AuthContext';
import { LanguageProvider } from './context/LanguageContext';
import { apiClient } from './api/client';

// Create Material Design theme
const theme = createTheme({
  palette: {
    primary: {
      main: '#1976d2',
      light: '#42a5f5',
      dark: '#1565c0',
    },
    secondary: {
      main: '#dc004e',
      light: '#e33371',
      dark: '#9a0036',
    },
    success: {
      main: '#4caf50',
    },
    warning: {
      main: '#ff9800',
    },
    error: {
      main: '#f44336',
    },
    info: {
      main: '#03a9f4',
    },
    background: {
      default: '#f5f5f5',
      paper: '#ffffff',
    },
  },
  typography: {
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
    h4: {
      fontWeight: 600,
    },
    h6: {
      fontWeight: 500,
    },
  },
  shape: {
    borderRadius: 8,
  },
  components: {
    MuiButton: {
      styleOverrides: {
        root: {
          textTransform: 'none',
          fontWeight: 500,
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: {
          fontWeight: 500,
        },
      },
    },
  },
});

// Suppress MUI ThemeProvider prop validation — Figma's inspection layer injects
// data-fg-* attributes onto every component including ThemeProvider, which MUI rejects.
// Use defineProperty so the null can't be overwritten by module re-evaluation.
try {
  Object.defineProperty(ThemeProvider, 'propTypes', { value: null, writable: true, configurable: true });
} catch {
  (ThemeProvider as any).propTypes = null;
}

// Wrapper component to filter out Figma inspection props
const FilteredThemeProvider = ({ children }: { children?: React.ReactNode; [key: string]: any }) => {
  return <ThemeProvider theme={theme}>{children}</ThemeProvider>;
};

/**
 * Состояние транзакций и роутер. Живёт **под** `AuthProvider`: список запрашивается, когда
 * есть сессия (после входа или восстановления), а не при первом рендере до логина — раньше
 * запрос уходил без токена, получал 401 и список оставался пустым до ручного обновления.
 */
function AppShell() {
  const { isAuthenticated } = useAuth();
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [filters, setFilters] = useState<TransactionFilters>({
    dateFrom: new Date(Date.now() - 10 * 60 * 1000), // Last 10 minutes
    dateTo: new Date(),
    status: 'all',
    paymentMethod: 'all',
    minAmount: '',
    maxAmount: '',
    searchQuery: '',
    terminalRid: [],
    posPaymentType: 'all',
    cashierId: 'all',
    locationName: 'all',
    batchId: 'all'
  });
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [newTransactionCount, setNewTransactionCount] = useState(0);

  const fetchTransactions = async () => {
    try {
      // Parallel fetch transactions and terminals to get terminal names.
      // Лёгкий список терминалов (Р-45): здесь нужны только имена, а полная карточка
      // постранична с P2-1 — по ней имя терминала находилось бы только у первых двадцати.
      // Заблокированные терминалы в ответе есть, и это важно: платёж, прошедший через
      // терминал, который потом сняли с обслуживания, должен сохранить его имя.
      const [txRes, termRes] = await Promise.allSettled([
        apiClient.get('/api/v1/transactions', { params: { page: 0, size: 100 } }),
        apiClient.get('/api/v1/terminals/options')
      ]);

      let terminalMap: Record<number, string> = {};
      if (termRes.status === 'fulfilled' && Array.isArray(termRes.value.data)) {
        termRes.value.data.forEach((term: any) => {
          if (term.id) {
            terminalMap[term.id] = term.name || `Terminal #${term.id}`;
          }
        });
      }

      if (txRes.status === 'fulfilled') {
        const rawContent = Array.isArray(txRes.value.data) ? txRes.value.data : (txRes.value.data?.content || []);
        if (Array.isArray(rawContent)) {
          const mapped = rawContent.map((t: any): Transaction => {
            const name = t.terminalId ? terminalMap[t.terminalId] : undefined;
            const displayName = name || (t.terminalId ? `Terminal #${t.terminalId}` : '—');
            return {
              id: t.id,
              paymentLinkId: t.paymentLinkId,
              timestamp: t.createdAt ? new Date(t.createdAt) : new Date(),
              customer: t.customerName || t.customerEmail || 'Customer',
              customerEmail: t.customerEmail || 'N/A',
              customerPhone: t.customerPhone,
              amount: t.amount,
              refundedAmount: t.refundedAmount,
              currency: t.currency || 'AZN',
              // Разбор — только через parse*: нераспознанное значение даёт null и показывается
              // как есть. Прежнее `String(t.status || 'APPROVED') as any` превращало любой
              // неизвестный статус в «успешный» и прятало расхождение от компилятора (P2-12).
              status: parseTransactionStatus(t.status),
              statusRaw: t.status === null || t.status === undefined ? undefined : String(t.status),
              paymentMethod: parsePaymentMethod(t.paymentType),
              description: t.description || t.merchantOrderId || 'Transaction',
              cardNumberMasked: t.cardNumberMasked,
              cardLast4: t.cardNumberMasked ? String(t.cardNumberMasked).slice(-4) : undefined,
              rrn: t.rrn,
              approvalCode: t.approvalCode,
              merchantRid: t.merchantRid,
              providerOrderId: t.providerOrderId || t.provider_order_id,
              terminalId: t.terminalId,
              terminalName: name || displayName,
              clientIp: t.clientIp,
              userAgent: t.userAgent,
              fee: 0,
              statusHistory: [],
              terminalRid: displayName,
              channel: 'ecommerce'
            };
          });
          setTransactions(mapped);
        }
      }
    } catch {
      setTransactions([]);
    }
  };

  useEffect(() => {
    if (isAuthenticated) {
      fetchTransactions();
    } else {
      // Выход: чужие данные не должны пережить сессию до следующего входа.
      setTransactions([]);
    }
  }, [isAuthenticated]);

  const handleRefresh = () => {
    setNewTransactionCount(0);
    setFilters(prev => ({
      ...prev,
      dateFrom: new Date(Date.now() - 10 * 60 * 1000),
      dateTo: new Date()
    }));
    fetchTransactions();
  };

  const handleToggleAutoRefresh = () => {
    setAutoRefresh(prev => !prev);
  };

  const router = useMemo(() => {
    return createRouter({
      transactions,
      filters,
      onFilterChange: setFilters,
      autoRefresh,
      onToggleAutoRefresh: handleToggleAutoRefresh,
      newTransactionCount,
      onRefresh: handleRefresh
    });
  }, [transactions, filters, autoRefresh, newTransactionCount]);

  return <RouterProvider router={router} />;
}

function App() {
  return (
    <LanguageProvider>
      <FilteredThemeProvider>
        <CssBaseline />
        {/* AuthProvider сам показывает загрузку, пока восстанавливает сессию через /refresh. */}
        <AuthProvider>
          <AppShell />
        </AuthProvider>
      </FilteredThemeProvider>
    </LanguageProvider>
  );
}

export default App;