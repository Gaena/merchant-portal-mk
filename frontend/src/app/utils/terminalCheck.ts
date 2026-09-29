import { apiClient } from '../api/client';

/**
 * Кнопка «Тест»: пробный заказ у провайдера с кредами компании терминала (Р-70, Р-93); неоплаченный,
 * в выписку не попадает. REJECTED — креды подошли, но заказ не разрешён (слова провайдера в
 * `message`); UNREACHABLE — провайдер не ответил, о терминале это не говорит ничего.
 */
export type TerminalCheckOutcome = 'OK' | 'INVALID_CREDENTIALS' | 'REJECTED' | 'UNREACHABLE';

export interface TerminalCheckResponse {
  outcome: TerminalCheckOutcome;
  providerErrorCode?: string | null;
  message?: string | null;
}

/** Креды компании берутся из базы на сервере и наружу не уходят. */
export const checkExistingTerminal = async (terminalId: number): Promise<TerminalCheckResponse> => {
  const res = await apiClient.post<TerminalCheckResponse>(`/api/v1/acquiring/terminal-checks/${terminalId}`);
  return res.data;
};

export const checkSeverity = (outcome: TerminalCheckOutcome): 'success' | 'error' | 'warning' =>
  outcome === 'OK' ? 'success' : outcome === 'UNREACHABLE' ? 'warning' : 'error';
