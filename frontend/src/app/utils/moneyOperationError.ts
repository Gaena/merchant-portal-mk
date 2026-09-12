import axios from 'axios';

/**
 * Как читать отказ денежной операции — возврата и списания DMS-холда.
 *
 * У них два принципиально разных исхода, и путать их нельзя:
 *
 *   `declined` — эквайер прочитал запрос и отказал. Деньги не двигались, повтор безопасен.
 *   `unknown`  — подтверждения нет. Операция могла уже пройти, и повтор списал бы вдвое.
 *
 * Бэкенд это различие уже проводит (`TxpgAcquiringClient.classifyMoneyOperationFailure`,
 * Р-23): отказ уходит как 400, неизвестность — как 502, и в журнал аудита ложится
 * незакрытая запись. Разбор здесь только переносит её на экран.
 *
 * Всё, что 5xx или вовсе без ответа, считается неизвестностью намеренно. Оборванное
 * соединение и истёкший таймаут не говорят ничего о том, выполнил ли эквайер операцию
 * до того, как нас перестали слушать. Ошибиться в эту сторону значит лишний раз попросить
 * проверить статус; ошибиться в другую — предложить повтор поверх ушедших денег.
 */
export type MoneyOperationOutcome = 'declined' | 'unknown';

export interface MoneyOperationFailure {
  outcome: MoneyOperationOutcome;
  /** Текст с бэкенда, если он есть; иначе запасной, переданный вызывающим. */
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
