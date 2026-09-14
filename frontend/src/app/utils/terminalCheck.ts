import { apiClient } from '../api/client';

/**
 * Проверка учётных данных терминала пробным заказом у провайдера — кнопка «Тест».
 *
 * У провайдера нет запроса, который проверял бы разом и логин с паролем, и то, что терминалу
 * разрешены оплаты, кроме заведения заказа. Пробный заказ остаётся неоплаченным, через десять
 * минут уходит в Expired и в выписку не попадает; на такую нагрузку провайдер дал согласие.
 *
 * Четыре исхода, и различать их обязательно — у каждого свой следующий шаг:
 *   OK                  — ключ подходит, оплаты разрешены;
 *   INVALID_CREDENTIALS — неверный логин или пароль;
 *   REJECTED            — ключ подошёл, но провайдер не разрешил заказ (его слова в `message`);
 *   UNREACHABLE         — провайдер не ответил: о терминале это не говорит ничего.
 */
export type TerminalCheckOutcome = 'OK' | 'INVALID_CREDENTIALS' | 'REJECTED' | 'UNREACHABLE';

export interface TerminalCheckResponse {
  outcome: TerminalCheckOutcome;
  providerErrorCode?: string | null;
  message?: string | null;
}

/** Уже заведённый терминал: ключ берётся из базы и наружу не уходит. */
export const checkExistingTerminal = async (terminalId: number): Promise<TerminalCheckResponse> => {
  const res = await apiClient.post<TerminalCheckResponse>(`/api/v1/acquiring/terminal-checks/${terminalId}`);
  return res.data;
};

/** Терминал, который ещё заводят: ключ введён в форме и ещё не сохранён. */
export const checkNewTerminal = async (login: string, password: string): Promise<TerminalCheckResponse> => {
  const res = await apiClient.post<TerminalCheckResponse>('/api/v1/acquiring/terminal-checks', { login, password });
  return res.data;
};

/** Тон подсказки по исходу: зелёный только у настоящего успеха. */
export const checkSeverity = (outcome: TerminalCheckOutcome): 'success' | 'error' | 'warning' =>
  outcome === 'OK' ? 'success' : outcome === 'UNREACHABLE' ? 'warning' : 'error';
