import React from 'react';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
} from '@mui/material';
import { useLanguage } from '../context/LanguageContext';

interface ConfirmDialogProps {
  open: boolean;
  title: React.ReactNode;
  question: React.ReactNode;
  /** Карточка объекта, `Alert`, список меняемых полей — без особых случаев внутри компонента. */
  children?: React.ReactNode;
  confirmLabel: React.ReactNode;
  confirmColor?: 'error' | 'warning' | 'success' | 'primary';
  confirmIcon?: React.ReactNode;
  cancelLabel?: React.ReactNode;
  /** Запрос идёт: обе кнопки погашены, окно не закрыть кликом мимо. */
  busy?: boolean;
  /**
   * Гасит только подтверждение, уйти из окна можно: повторять нельзя, пока исход денежной операции
   * не подтверждён (`utils/moneyOperationError.ts`).
   */
  confirmDisabled?: boolean;
  maxWidth?: 'xs' | 'sm';
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * Единственное окно подтверждения (P3-5b, AGENTS §10): правила безопасного окна живут только здесь.
 * Обе кнопки гаснут на время запроса — двойной клик не даёт двух запросов; подтверждение —
 * `contained` и цветное. Окна-формы (создание, правка, «поделиться ссылкой») сюда не относятся.
 */
export const ConfirmDialog: React.FC<ConfirmDialogProps> = ({
  open,
  title,
  question,
  children,
  confirmLabel,
  confirmColor = 'error',
  confirmIcon,
  cancelLabel,
  busy = false,
  confirmDisabled = false,
  maxWidth = 'sm',
  onConfirm,
  onCancel,
}) => {
  const { tObj } = useLanguage();

  return (
    <Dialog
      open={open}
      // Пока идёт запрос, окно не закрыть кликом мимо: кнопки погашены, а окна бы уже не было.
      onClose={() => { if (!busy) onCancel(); }}
      maxWidth={maxWidth}
      fullWidth
    >
      <DialogTitle sx={{ fontWeight: 700 }}>{title}</DialogTitle>
      <DialogContent>
        <DialogContentText>{question}</DialogContentText>
        {children}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5, gap: 1 }}>
        {/* Фокус — на безопасной кнопке: Enter по инерции ничего не подтверждает. */}
        <Button onClick={onCancel} variant="outlined" disabled={busy} autoFocus>
          {cancelLabel ?? tObj.common.cancel}
        </Button>
        <Button
          onClick={onConfirm}
          variant="contained"
          color={confirmColor}
          startIcon={confirmIcon}
          disabled={busy || confirmDisabled}
        >
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
