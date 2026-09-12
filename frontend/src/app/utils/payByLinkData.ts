/**
 * Словари платёжной ссылки и форматтеры экранов Pay by Link.
 *
 * Значения — ровно те, что присылает бэкенд:
 * `pbl/src/main/java/az/millikart/pbl/domain/PaymentLinkStatus.java`,
 * `pbl/src/main/java/az/millikart/pbl/domain/UsageType.java`,
 * `pbl/src/main/java/az/millikart/pbl/domain/PaymentType.java`.
 * В ответе они сериализуются именами енумов (`PaymentLinkResponse`,
 * `PaymentLinkSummaryResponse`), то есть всегда в верхнем регистре.
 *
 * Новое значение на бэкенде → добавить сюда, иначе разборщик вернёт `null`
 * и значение покажется как неизвестное. Значений «на будущее» здесь быть не должно:
 * пока их нет, `tsc` ловит сравнение с несуществующим статусом (P2-13, Р-33 — то же
 * правило, что у транзакций в `types/transaction.ts`, P2-12, Р-30).
 *
 * Статуса `paid` у бэкенда нет и не было: оплаченная одноразовая ссылка приходит
 * как `COMPLETED`. Написание одно — `CANCELED`, с одной `l`.
 */
// SUSPENDED (P2-8): ссылка заблокированного терминала. Ставится и снимается только блокировкой
// и разблокировкой терминала в `directory` — мерчант этот статус не выставляет и не снимает.
export const LINK_STATUSES = ['ACTIVE', 'EXPIRED', 'COMPLETED', 'CANCELED', 'SUSPENDED'] as const;

export type LinkStatus = (typeof LINK_STATUSES)[number];

export const LINK_USAGE_TYPES = ['SINGLE', 'MULTIPLE'] as const;

export type LinkUsageType = (typeof LINK_USAGE_TYPES)[number];

export const PAYMENT_TYPES = ['SMS', 'DMS'] as const;

export type PaymentType = (typeof PAYMENT_TYPES)[number];

/**
 * Стадия DMS-платежа. Собственного поля под неё в ответе бэкенда нет — значение
 * появляется только локально, после успешного `POST /transactions/{id}/complete`.
 * Это не словарь бэкенда, поэтому разборщика у него нет.
 */
export type DmsStatus = 'authorized' | 'finalized';

/**
 * Общая часть трёх разборщиков ниже: строгое сравнение со словарём, без приведения
 * регистра и trim, никогда не бросает. **Никакой подстановки по умолчанию** — раньше
 * на этом месте стояло `(l.status || 'active').toLowerCase()`, и любое нераспознанное
 * значение молча становилось активной ссылкой, то есть «по ней можно платить».
 *
 * Предупреждение в консоль — единственный побочный эффект: разбор идёт в одном месте,
 * поэтому расхождение со словарём бэкенда видно сразу и с исходным значением.
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

/** Разбор `status` из ответа `/api/v1/payment-links*`. Зеркало `parseTransactionStatus`. */
export function parseLinkStatus(raw: unknown): LinkStatus | null {
  return parseEnumValue(LINK_STATUSES, raw, 'статус ссылки');
}

/** Разбор `usageType` из ответа. Правила те же, что у `parseLinkStatus`. */
export function parseLinkUsageType(raw: unknown): LinkUsageType | null {
  return parseEnumValue(LINK_USAGE_TYPES, raw, 'тип использования ссылки');
}

/** Разбор `paymentType` из ответа. Правила те же, что у `parseLinkStatus`. */
export function parsePaymentType(raw: unknown): PaymentType | null {
  return parseEnumValue(PAYMENT_TYPES, raw, 'тип платежа');
}

export interface PaymentLink {
  id: string;
  shortCode: string;
  url: string;
  /** Разобранный статус; `null` — бэкенд прислал значение вне словаря, см. `statusRaw`. */
  status: LinkStatus | null;
  /** Исходное значение статуса. Показывается серым и как есть, когда `status === null`. */
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
   * Сколько раз ссылкой воспользовались — `currentPaymentsCount` из API. С P2-16 (Р-49)
   * это состоявшиеся платежи: `SUCCESS` + `REFUNDED` + `PARTIALLY_REFUNDED`. Возврат —
   * полный или частичный — использование не отменяет: число не уменьшается и слот
   * не освобождается. В списочном ответе поля нет, там всегда 0.
   */
  usedCount: number;
  /**
   * Сколько из состоявшихся платежей возвращено, полностью или частично, —
   * `refundedPaymentsCount` из API (P2-16, Р-50). Всегда ≤ `usedCount`. Показывается
   * на карточке рядом с «использовано N из M» только когда больше нуля. В списочном
   * ответе поля нет намеренно (счётчик на строку вернул бы N+1) — там всегда 0.
   */
  refundedCount: number;
  createdAt: Date;
  expiresAt: Date;

  /**
   * Время последнего успешного платежа по ссылке — `lastPaidAt` из API (P2-15, Р-46).
   * `undefined` означает «не оплачивалась»; подставлять сюда что-либо нельзя.
   *
   * Возвращённый платёж датой оплаты остаётся: бэкенд ищет по `SUCCESS`, `REFUNDED`
   * и `PARTIALLY_REFUNDED`, потому что возврат переписывает статус самой транзакции.
   */
  paidAt?: Date;

  // ─── Поля, которых в ответе API пока нет ────────────────────────────────────
  // Мок-генератор, который их заполнял, удалён вместе с P2-13. Разметка их читает,
  // поэтому они оставлены — но до появления соответствующих полей в API все они
  // **всегда `undefined`**, и ветки под ними на экран не попадают. Подставлять вместо
  // них значения по умолчанию запрещено (Р-48): построитель, сочинявший карту и номер
  // транзакции из этих полей, удалён в P2-15 — он был безвреден ровно до того дня,
  // когда заработало поле, за которым он прятался.
  redirectUrl?: string;
  note?: string;
  dmsStatus?: DmsStatus;
  finalizedAt?: Date;
  cardNetwork?: string;
  cardLast4?: string;
  transactionId?: string;
  payerIp?: string;
  sentVia?: ('email' | 'whatsapp' | 'copy')[];
  /**
   * Эквайринговый терминал ссылки — `PaymentLinkResponse.terminal`. Подписывается логином
   * на карточке ссылки (`utils/terminals.ts`); пусто, если ответ терминал не назвал.
   */
  terminalId?: number;
  // `PaymentLinkResponse.rid` маппинг из API пока не переносит — поле всегда undefined.
  merchantRid?: string;
}

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
  // Приостановлена не мерчантом и не по сроку: янтарный, чтобы отличалась и от активной,
  // и от отменённой — по ней нельзя платить, но она вернётся, когда терминал разблокируют.
  SUSPENDED: { color: 'warning', bgColor: 'rgba(237,108,2,0.12)', textColor: '#ed6c02' },
};

/** Статус вне словаря бэкенда: серый, чтобы его нельзя было спутать с активной ссылкой. */
const UNKNOWN_STATUS_COLORS: LinkStatusColors = {
  color: 'default',
  bgColor: 'rgba(158,158,158,0.16)',
  textColor: '#616161',
};

/**
 * Цвет статуса. Аргумент уже разобран `parseLinkStatus`, поэтому ни `toLowerCase()`,
 * ни ветки под два написания `canceled`/`cancelled` здесь больше не нужны.
 * `null` — статус вне словаря: серый, а не «активна» по умолчанию.
 * Зеркало `getStatusColorScheme` из `utils/statusColors.ts` (P2-12).
 */
export const getLinkStatusColors = (status: LinkStatus | null | undefined): LinkStatusColors =>
  (status ? LINK_STATUS_COLORS[status] : UNKNOWN_STATUS_COLORS);
