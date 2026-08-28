import type { TransactionStatus } from '../types/transaction';

const colorSchemes = {
  success: {
    main: '#4caf50',
    light: '#e8f5e9',
    dark: '#2e7d32',
    contrastText: '#1b5e20'
  },
  warning: {
    main: '#ff9800',
    light: '#fff3e0',
    dark: '#e65100',
    contrastText: '#e65100'
  },
  info: {
    main: '#2196f3',
    light: '#e3f2fd',
    dark: '#1565c0',
    contrastText: '#0d47a1'
  },
  purple: {
    main: '#9c27b0',
    light: '#f3e5f5',
    dark: '#7b1fa2',
    contrastText: '#4a148c'
  },
  error: {
    main: '#f44336',
    light: '#ffebee',
    dark: '#c62828',
    contrastText: '#b71c1c'
  },
  /** Статус, которого нет в словаре бэкенда: серый, чтобы его нельзя было спутать с реальным. */
  neutral: {
    main: '#9e9e9e',
    light: '#f5f5f5',
    dark: '#616161',
    contrastText: '#424242'
  }
};

/**
 * Цвет статуса. Аргумент уже разобран `parseTransactionStatus`, поэтому ни `toUpperCase`,
 * ни ветки под чужие словари (`APPROVED`, `DECLINED`, `3D-FAILED`) здесь больше не нужны.
 * `null` — статус вне словаря бэкенда: серый, а не «успешный» по умолчанию.
 */
export const getStatusColorScheme = (status: TransactionStatus | null | undefined) => {
  switch (status) {
    case 'SUCCESS':
      return colorSchemes.success;
    case 'PENDING':
      return colorSchemes.warning;
    case 'AUTHORIZED':
      return colorSchemes.info;
    case 'REFUNDED':
    case 'PARTIALLY_REFUNDED':
      return colorSchemes.purple;
    case 'FAILED':
      return colorSchemes.error;
    default:
      return colorSchemes.neutral;
  }
};
