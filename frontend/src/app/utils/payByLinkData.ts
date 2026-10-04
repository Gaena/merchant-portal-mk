/**
 * Ровно имена енумов `pbl` (`PaymentLinkStatus`, `UsageType`, `PaymentType`): новое там — сюда,
 * иначе разбор вернёт `null`; значений «на будущее» не добавлять (P2-13, Р-33). Статуса `paid`
 * нет: оплаченная одноразовая ссылка — `COMPLETED`; `CANCELED` — с одной `l`.
 */
// SUSPENDED — ссылка заблокированного терминала: ставит и снимает только блокировка терминала
// в `directory`, мерчант его не трогает (P2-8).
export const LINK_STATUSES = ['ACTIVE', 'EXPIRED', 'COMPLETED', 'CANCELED', 'SUSPENDED'] as const;

export type LinkStatus = (typeof LINK_STATUSES)[number];

export const LINK_USAGE_TYPES = ['SINGLE', 'MULTIPLE'] as const;

export type LinkUsageType = (typeof LINK_USAGE_TYPES)[number];

export const PAYMENT_TYPES = ['SMS', 'DMS'] as const;

export type PaymentType = (typeof PAYMENT_TYPES)[number];

/**
 * Строгое сравнение со словарём, без приведения регистра и trim; не бросает. Подстановки по
 * умолчанию не заводить: нераспознанный статус стал бы активной ссылкой — «по ней можно платить».
 */
const parseEnumValue = <T extends string>(
  values: readonly T[],
  raw: unknown,
  what: string,
): T | null => {
  if (typeof raw !== 'string') {
    if (raw !== null && raw !== undefined) {
      console.warn(`[pay-by-link] ${what} не строка:`, raw);
    }
    return null;
  }
  if ((values as readonly string[]).includes(raw)) {
    return raw as T;
  }
  console.warn(`[pay-by-link] неизвестный ${what}: "${raw}"`);
  return null;
};

export function parseLinkStatus(raw: unknown): LinkStatus | null {
  return parseEnumValue(LINK_STATUSES, raw, 'статус ссылки');
}

export function parseLinkUsageType(raw: unknown): LinkUsageType | null {
  return parseEnumValue(LINK_USAGE_TYPES, raw, 'тип использования ссылки');
}

export function parsePaymentType(raw: unknown): PaymentType | null {
  return parseEnumValue(PAYMENT_TYPES, raw, 'тип платежа');
}

export interface PaymentLink {
  id: string;
  shortCode: string;
  url: string;
  status: LinkStatus | null;
  /** Исходная строка — показывается серым как есть, когда `status === null`. */
  statusRaw?: string;
  amount: number;
  currency: string;
  description: string;
  customerName: string;
  customerEmail: string;
  customerPhone: string;
  /** `null` — значение вне словаря бэкенда; подставлять `SINGLE` вместо него нельзя. */
  usageType: LinkUsageType | null;
  /** `null` — значение вне словаря бэкенда; подставлять `SMS` вместо него нельзя. */
  paymentType: PaymentType | null;
  maxUses: number;
  /**
   * `currentPaymentsCount` — состоявшиеся платежи (`PAID_STATUSES`, Р-49): возврат число не
   * уменьшает и слот не освобождает. В списочном ответе поля нет — там 0.
   */
  usedCount: number;
  /**
   * `refundedPaymentsCount` — сколько из них возвращено (Р-50), всегда ≤ `usedCount`. В списочном
   * ответе поля нет намеренно (N+1) — там 0.
   */
  refundedCount: number;
  createdAt: Date;
  expiresAt: Date;

  /**
   * `lastPaidAt` (Р-46); `undefined` — не оплачивалась, ничего не подставлять. Возвращённый
   * платёж датой оплаты остаётся (Р-49).
   */
  paidAt?: Date;

  /** `PaymentLinkResponse.terminal`; подпись — `utils/terminals.ts`. */
  terminalId?: number;
}

// Параметры кодируются: иначе описание «Invoice #12 & extras» обрежет тело письма на `#`.
export const mailtoHref = (link: Pick<PaymentLink, 'customerEmail' | 'url'>, subject: string, text: string): string =>
  `mailto:${encodeURIComponent(link.customerEmail)}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(`${text}\n${link.url}`)}`;

export const whatsAppHref = (link: Pick<PaymentLink, 'customerPhone' | 'url'>, text: string): string =>
  `https://wa.me/${link.customerPhone.replace(/\D/g, '')}?text=${encodeURIComponent(`${text} ${link.url}`)}`;

export const formatDateTime = (d: Date) =>
  d.toLocaleString('en-GB', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });

export const formatTimeLeft = (expiresAt: Date): string => {
  const ms = expiresAt.getTime() - Date.now();
  if (ms <= 0) return 'Expired';
  const h = Math.floor(ms / 3600000);
  const m = Math.floor((ms % 3600000) / 60000);
  return h > 0 ? `${h}h ${m}m left` : `${m}m left`;
};

export const expiryPercent = (link: PaymentLink): number =>
  Math.max(0, Math.min(100,
    ((link.expiresAt.getTime() - Date.now()) /
     (link.expiresAt.getTime() - link.createdAt.getTime())) * 100
  ));

interface LinkStatusColors {
  color: 'success' | 'info' | 'default' | 'error' | 'warning';
  bgColor: string;
  textColor: string;
}

const LINK_STATUS_COLORS: Record<LinkStatus, LinkStatusColors> = {
  ACTIVE:    { color: 'success', bgColor: 'rgba(46,125,50,0.1)',  textColor: '#2e7d32' },
  COMPLETED: { color: 'info',    bgColor: 'rgba(21,101,192,0.1)', textColor: '#1565c0' },
  EXPIRED:   { color: 'default', bgColor: 'rgba(0,0,0,0.06)',     textColor: '#546e7a' },
  CANCELED:  { color: 'error',   bgColor: 'rgba(198,40,40,0.1)',  textColor: '#c62828' },
  // Янтарный — не спутать ни с активной, ни с отменённой: платить нельзя, но ссылка вернётся
  // после разблокировки терминала.
  SUSPENDED: { color: 'warning', bgColor: 'rgba(237,108,2,0.12)', textColor: '#ed6c02' },
};

// Статус вне словаря — серый, чтобы не спутать с активной ссылкой.
const UNKNOWN_STATUS_COLORS: LinkStatusColors = {
  color: 'default',
  bgColor: 'rgba(158,158,158,0.16)',
  textColor: '#616161',
};

/** Зеркало `getStatusColorScheme` из `utils/statusColors.ts` (P2-12). */
export const getLinkStatusColors = (status: LinkStatus | null | undefined): LinkStatusColors =>
  (status ? LINK_STATUS_COLORS[status] : UNKNOWN_STATUS_COLORS);
