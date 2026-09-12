import type { TerminalOptionDto } from '../types/dto';

/**
 * Подписи терминала на экранах платежей.
 *
 * Основной параметр терминала — его **логин**: по нему мерчант терминал и опознаёт, тогда как
 * имя он придумывает сам, а числовой id и вовсе внутренний. Поэтому логин идёт подписью везде,
 * где терминал показан, а имя — пояснением под ним.
 *
 * Логин приходит одним лёгким фидом `GET /api/v1/terminals/options` (id, name, login, status);
 * транзакция несёт только `terminalId`, и подпись к ней собирается здесь.
 */

/**
 * В поля терминала когда-то попадал `merchantRid` платежа, и такую строку нельзя показывать как
 * терминал: подписи ниже её отбрасывают.
 */
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Индекс терминалов по id из ответа `/api/v1/terminals/options`. Чужой формат даёт пустой индекс. */
export const buildTerminalIndex = (raw: unknown): Record<number, TerminalOptionDto> => {
  const rows = Array.isArray(raw) ? raw : (raw as { content?: unknown })?.content;
  if (!Array.isArray(rows)) {
    return {};
  }
  const index: Record<number, TerminalOptionDto> = {};
  rows.forEach((row: any) => {
    if (row && typeof row.id === 'number') {
      index[row.id] = row;
    }
  });
  return index;
};

/**
 * Подпись терминала: логин, иначе имя, иначе номер, иначе прочерк.
 *
 * Значение, похожее на UUID, отбрасывается: в поля терминала раньше попадал `merchantRid`
 * платежа, и такую строку нельзя показывать как терминал. Прочерк означает «терминала уже нет» —
 * выдумывать подпись по id (`TRM-…`, «Default Terminal») нельзя.
 */
export const terminalLabel = (terminal: {
  terminalLogin?: string;
  terminalName?: string;
  terminalId?: number;
}): string => {
  const usable = (value?: string) => Boolean(value && !UUID.test(value));

  if (usable(terminal.terminalLogin)) return terminal.terminalLogin as string;
  if (usable(terminal.terminalName)) return terminal.terminalName as string;
  if (terminal.terminalId) return `#${terminal.terminalId}`;
  return '—';
};

/** Имя терминала как пояснение под логином: пусто, когда оно совпадает с подписью или отсутствует. */
export const terminalSubLabel = (terminal: {
  terminalLogin?: string;
  terminalName?: string;
}): string => {
  if (!terminal.terminalName || UUID.test(terminal.terminalName)) return '';
  return terminal.terminalName === terminal.terminalLogin ? '' : terminal.terminalName;
};

/** Подпись терминала в селекторах и фильтрах — того же порядка, что и `terminalLabel`. */
export const terminalOptionLabel = (terminal: Pick<TerminalOptionDto, 'id' | 'name' | 'login'>): string =>
  terminalLabel({ terminalLogin: terminal.login, terminalName: terminal.name, terminalId: terminal.id });
