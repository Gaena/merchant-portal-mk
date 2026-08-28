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
  /** Заголовок: какое именно действие подтверждается. */
  title: React.ReactNode;
  /** Вопрос под заголовком. */
  question: React.ReactNode;
  /**
   * Всё, чем девять окон различаются по содержимому: карточка объекта, `Alert`,
   * список меняемых полей. Через `children` — чтобы ни одно из них не потребовало
   * особого случая внутри компонента, а компонент остался маленьким.
   */
  children?: React.ReactNode;
  confirmLabel: React.ReactNode;
  confirmColor?: 'error' | 'warning' | 'success' | 'primary';
  confirmIcon?: React.ReactNode;
  /** По умолчанию `common.cancel`; у отмены ссылки — «Оставить ссылку». */
  cancelLabel?: React.ReactNode;
  /** Запрос идёт: обе кнопки погашены, окно не закрыть кликом мимо. */
  busy?: boolean;
  maxWidth?: 'xs' | 'sm';
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * Единственное окно подтверждения в проекте (P3-5b). Заведено вместо девяти собственных —
 * не потому, что те работали неправильно, а потому, что правила P3-5 были записаны в девяти
 * местах: десятый диалог написали бы, забыв одно из них, и никто бы не заметил.
 *
 * Четыре правила, которые здесь невозможно забыть:
 *   1. `onClose` игнорируется, пока идёт запрос, — окно не закрыть кликом мимо;
 *   2. кнопка отказа первая и в фокусе — Enter не подтверждает опасное действие;
 *   3. обе кнопки гаснут на время запроса — двойной клик не даёт двух запросов;
 *   4. подтверждение — `contained` и цветное, заголовок жирный.
 *
 * Окна-формы (создание пользователя, компании, терминала, ссылки; правка терминала;
 * «поделиться ссылкой») сюда не относятся: у них другая задача и другая структура.
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
  maxWidth = 'sm',
  onConfirm,
  onCancel,
}) => {
  const { tObj } = useLanguage();

  return (
    <Dialog
      open={open}
      // Пока запрос идёт, окно не закрыть кликом мимо: иначе кнопки погашены, а окна нет.
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
        {/* Фокус при открытии — на безопасной кнопке: Enter по инерции ничего не подтверждает. */}
        <Button onClick={onCancel} variant="outlined" disabled={busy} autoFocus>
          {cancelLabel ?? tObj.common.cancel}
        </Button>
        <Button
          onClick={onConfirm}
          variant="contained"
          color={confirmColor}
          startIcon={confirmIcon}
          disabled={busy}
        >
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
