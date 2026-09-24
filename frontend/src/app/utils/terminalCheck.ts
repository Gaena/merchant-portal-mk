import { apiClient } from '../api/client';

/**
 * Кнопка «Тест» у заведённого терминала: можно ли создать на нём платёж — пробный заказ у провайдера
 * с логином и паролем компании терминала (Р-93). Своих кредов у терминала нет, поэтому проверки
 * до заведения тоже нет. Пробный заказ остаётся неоплаченным, через десять минут уходит в Expired и
 * в выписку не попадает; на такую нагрузку провайдер дал согласие.
 *
 * Четыре исхода, и различать их обязательно — у каждого свой следующий шаг:
 *   OK                  — креды компании подходят, платёж создать можно;
 *   INVALID_CREDENTIALS — неверный логин или пароль компании;
 *   REJECTED            — ключ подошёл, но провайдер не разрешил заказ (его слова в `message`);
 *   UNREACHABLE         — провайдер не ответил: о терминале это не говорит ничего.
 */
export type TerminalCheckOutcome = 'OK' | 'INVALID_CREDENTIALS' | 'REJECTED' | 'UNREACHABLE';

export interface TerminalCheckResponse {
  outcome: TerminalCheckOutcome;
  providerErrorCode?: string | null;
  message?: string | null;
}

/** Креды компании берутся из базы и наружу не уходят. */
export const checkExistingTerminal = async (terminalId: number): Promise<TerminalCheckResponse> => {
  const res = await apiClient.post<TerminalCheckResponse>(`/api/v1/acquiring/terminal-checks/${terminalId}`);
  return res.data;
};

/** Тон подсказки по исходу: зелёный только у настоящего успеха. */
export const checkSeverity = (outcome: TerminalCheckOutcome): 'success' | 'error' | 'warning' =>
  outcome === 'OK' ? 'success' : outcome === 'UNREACHABLE' ? 'warning' : 'error';
