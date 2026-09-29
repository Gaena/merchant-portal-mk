import type { TerminalOptionDto } from '../types/dto';

/**
 * Порядок подписи терминала живёт только здесь (Р-59, Р-96): номер у провайдера, иначе логин, иначе
 * имя; имя — пояснением. Операция несёт только `terminalId`, подпись собирается по `/terminals/options`.
 */

// Строка вида UUID в поле терминала — `ridByMerchant` платежа, а не терминал: подписи её отбрасывают.
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Чужой формат ответа — пустой индекс. */
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
 * Прочерк — подписать нечем. Внутренний id не показывать и подпись по нему не выдумывать
 * (`TRM-…`, «Default Terminal»; Р-81).
 */
export const terminalLabel = (terminal: {
  terminalRid?: string | null;
  terminalLogin?: string | null;
  terminalName?: string | null;
  terminalId?: number;
}): string => {
  const usable = (value?: string | null) => Boolean(value && !UUID.test(value));

  if (usable(terminal.terminalRid)) return terminal.terminalRid as string;
  if (usable(terminal.terminalLogin)) return terminal.terminalLogin as string;
  if (usable(terminal.terminalName)) return terminal.terminalName as string;
  return '—';
};

export const terminalSubLabel = (terminal: {
  terminalRid?: string | null;
  terminalLogin?: string | null;
  terminalName?: string | null;
}): string => {
  if (!terminal.terminalName || UUID.test(terminal.terminalName)) return '';
  return terminal.terminalName === terminalLabel(terminal) ? '' : terminal.terminalName;
};

export const terminalOptionLabel = (terminal: Pick<TerminalOptionDto, 'id' | 'name' | 'login' | 'terminalRid'>): string =>
  terminalLabel({ terminalRid: terminal.terminalRid, terminalLogin: terminal.login, terminalName: terminal.name, terminalId: terminal.id });
