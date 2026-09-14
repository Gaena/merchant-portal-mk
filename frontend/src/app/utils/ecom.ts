import { apiClient } from '../api/client';
import {
  ECOM_OPERATION_KINDS,
  ECOM_STATUSES,
  parseEcomStatus,
  type EcomCurrencyTotal,
  type EcomOperation,
  type EcomOperationKind,
  type EcomOrder,
  type EcomPage,
  type EcomQuery,
  type EcomStats,
  type EcomStatus,
  type EcomTerminal,
} from '../types/ecom';

/**
 * Запросы к выписке провайдера и разбор ответов. Разбор — только здесь: страницы получают уже
 * типизированные заказы, как операции портала получают их из `utils/mapTransaction.ts`.
 */

/** Потолок периода — `ecom.txpg.max-window` по умолчанию. Сервер проверит сам; здесь — чтобы не слать заведомый отказ. */
export const ECOM_MAX_WINDOW_DAYS = 92;

export type PeriodProblem = 'invalid' | 'tooLong';

export const periodProblem = (from: Date | null, to: Date | null): PeriodProblem | null => {
  if (!from || !to || isNaN(from.getTime()) || isNaN(to.getTime()) || to.getTime() <= from.getTime()) {
    return 'invalid';
  }
  return to.getTime() - from.getTime() > ECOM_MAX_WINDOW_DAYS * 24 * 60 * 60 * 1000 ? 'tooLong' : null;
};

const text = (value: unknown): string | null =>
  typeof value === 'string' && value.length > 0 ? value : null;

const amount = (value: unknown): number | null => {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string' && value.trim() !== '' && Number.isFinite(Number(value))) return Number(value);
  return null;
};

const date = (value: unknown): Date | null => {
  if (typeof value !== 'string') return null;
  const parsed = new Date(value);
  return isNaN(parsed.getTime()) ? null : parsed;
};

const operationKind = (value: unknown): EcomOperationKind =>
  typeof value === 'string' && (ECOM_OPERATION_KINDS as readonly string[]).includes(value)
    ? (value as EcomOperationKind)
    : 'UNKNOWN';

export const mapEcomOperation = (raw: any): EcomOperation => ({
  tranId: text(raw?.tranId),
  at: date(raw?.at),
  kind: operationKind(raw?.kind),
  type: text(raw?.type),
  phase: text(raw?.phase),
  voidKind: text(raw?.voidKind),
  authKind: text(raw?.authKind),
  resultCode: text(raw?.resultCode),
  amount: amount(raw?.amount),
  clearAmount: amount(raw?.clearAmount),
  currency: text(raw?.currency),
  rrn: text(raw?.rrn),
});

export const mapEcomOrder = (raw: any): EcomOrder => ({
  orderId: raw?.orderId === undefined || raw?.orderId === null ? '' : String(raw.orderId),
  merchantRid: text(raw?.merchantRid),
  merchantTitle: text(raw?.merchantTitle),
  ridByMerchant: text(raw?.ridByMerchant),
  status: parseEcomStatus(raw?.status),
  statusRaw: text(raw?.status),
  providerStatus: text(raw?.providerStatus),
  providerPrevStatus: text(raw?.providerPrevStatus),
  amount: amount(raw?.amount),
  capturedAmount: amount(raw?.capturedAmount) ?? 0,
  refundedAmount: amount(raw?.refundedAmount) ?? 0,
  currency: text(raw?.currency) ?? 'AZN',
  description: text(raw?.description),
  createdAt: date(raw?.createdAt),
  lastOperationAt: date(raw?.lastOperationAt),
  cardMask: text(raw?.cardMask),
  rrn: text(raw?.rrn),
  declineCode: text(raw?.declineCode),
  operations: Array.isArray(raw?.operations) ? raw.operations.map(mapEcomOperation) : [],
});

const mapStats = (raw: any): EcomStats => {
  const statusCounts = Object.fromEntries(
    ECOM_STATUSES.map(status => [status, amount(raw?.statusCounts?.[status]) ?? 0])
  ) as Record<EcomStatus, number>;
  const totals: EcomCurrencyTotal[] = Array.isArray(raw?.totals)
    ? raw.totals.map((total: any) => ({
        currency: text(total?.currency),
        capturedAmount: amount(total?.capturedAmount) ?? 0,
        refundedAmount: amount(total?.refundedAmount) ?? 0,
      }))
    : [];
  return { orderCount: amount(raw?.orderCount) ?? 0, statusCounts, totals };
};

/**
 * Параметры запроса. `merchantRids` уходит повторяющимся ключом (`merchantRids=a&merchantRids=b`):
 * axios по умолчанию шлёт `merchantRids[]=a`, такой ключ контроллер не узнает, и фильтр по
 * терминалу молча пропал бы — выписка пришла бы по всем терминалам.
 */
const periodParams = (query: Pick<EcomQuery, 'dateFrom' | 'dateTo' | 'merchantRids'>): URLSearchParams => {
  const params = new URLSearchParams();
  params.set('dateFrom', query.dateFrom.toISOString());
  params.set('dateTo', query.dateTo.toISOString());
  query.merchantRids.forEach(rid => params.append('merchantRids', rid));
  return params;
};

export const fetchEcomPage = async (
  query: EcomQuery,
  cursor: string | null,
  size: number,
  signal?: AbortSignal
): Promise<EcomPage> => {
  const params = periodParams(query);
  if (query.minAmount.trim()) params.set('minAmount', query.minAmount.trim());
  if (query.maxAmount.trim()) params.set('maxAmount', query.maxAmount.trim());
  if (query.query.trim()) params.set('query', query.query.trim());
  if (cursor) params.set('cursor', cursor);
  params.set('size', String(size));
  const res = await apiClient.get('/api/v1/ecom/transactions', { params, signal });
  return {
    content: Array.isArray(res.data?.content) ? res.data.content.map(mapEcomOrder) : [],
    nextCursor: text(res.data?.nextCursor),
  };
};

/** Итоги — по периоду и терминалам; сумма и поиск на них не влияют (`ecom.md` §2.5). */
export const fetchEcomStats = async (
  query: Pick<EcomQuery, 'dateFrom' | 'dateTo' | 'merchantRids'>,
  signal?: AbortSignal
): Promise<EcomStats> => {
  const res = await apiClient.get('/api/v1/ecom/transactions/stats', { params: periodParams(query), signal });
  return mapStats(res.data);
};

export const fetchEcomTerminals = async (signal?: AbortSignal): Promise<EcomTerminal[]> => {
  const res = await apiClient.get('/api/v1/ecom/transactions/terminals', { signal });
  if (!Array.isArray(res.data)) return [];
  return res.data
    .filter((row: any) => typeof row?.merchantRid === 'string')
    .map((row: any) => ({ merchantRid: row.merchantRid, title: text(row.title), login: text(row.login) }));
};

export const fetchEcomOrder = async (orderId: string, signal?: AbortSignal): Promise<EcomOrder> => {
  const res = await apiClient.get(`/api/v1/ecom/transactions/${encodeURIComponent(orderId)}`, { signal });
  return mapEcomOrder(res.data);
};

/**
 * Подпись терминала заказа — тот же порядок, что у операций портала (Р-59): логин, под ним название.
 * Логина в заказе нет, он приходит из списка терминалов скоупа; без него — название мерчанта, затем rid.
 */
export const ecomTerminalLabel = (
  order: Pick<EcomOrder, 'merchantRid' | 'merchantTitle'>,
  terminals: ReadonlyMap<string, EcomTerminal>
): { label: string; subLabel: string } => {
  const terminal = order.merchantRid ? terminals.get(order.merchantRid) : undefined;
  const title = terminal?.title ?? order.merchantTitle;
  const label = terminal?.login ?? title ?? order.merchantRid ?? '—';
  return { label, subLabel: title && title !== label ? title : '' };
};
