import axios from 'axios';

/**
 * Возврат и списание холда (Р-23, Р-61): `declined` — эквайер отказал, повтор безопасен; `unknown` —
 * подтверждения нет, повтор мог бы списать вдвое. 5xx и отсутствие ответа — неизвестность намеренно:
 * ошибка в эту сторону стоит проверки статуса, в другую — повтора поверх ушедших денег.
 */
export type MoneyOperationOutcome = 'declined' | 'unknown';

export interface MoneyOperationFailure {
  outcome: MoneyOperationOutcome;
  message: string;
}

export const readMoneyOperationFailure = (
  error: unknown,
  fallbackMessage: string
): MoneyOperationFailure => {
  const status = axios.isAxiosError(error) ? error.response?.status : undefined;
  const message = (axios.isAxiosError(error) ? error.response?.data?.message : undefined)
    || fallbackMessage;

  // 503 — разомкнут предохранитель к эквайеру: вызов не ушёл и денег не двигал (Р-103), повтор безопасен.
  return {
    outcome: status === undefined || (status >= 500 && status !== 503) ? 'unknown' : 'declined',
    message,
  };
};
