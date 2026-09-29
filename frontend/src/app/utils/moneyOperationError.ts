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

  return {
    outcome: status === undefined || status >= 500 ? 'unknown' : 'declined',
    message,
  };
};
