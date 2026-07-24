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
  }
};

export const getStatusColorScheme = (status: TransactionStatus) => {
  if (!status) return colorSchemes.success;
  const upper = String(status).toUpperCase();
  switch (upper) {
    case 'APPROVED':
    case 'SUCCESS':
      return colorSchemes.success;
    case 'PENDING':
      return colorSchemes.warning;
    case 'REFUNDED':
    case 'PARTIALLY_REFUNDED':
      return colorSchemes.purple;
    case 'CANCELED':
      return colorSchemes.info;
    case 'DECLINED':
    case 'FAILED':
    case '3D-FAILED':
      return colorSchemes.error;
    default:
      return colorSchemes.success;
  }
};
