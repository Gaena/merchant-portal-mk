import type { TransactionStatus } from '../types/transaction';
import type { LinkStatus } from '../utils/payByLinkData';
import type { TerminalStatus } from '../types/dto';
import type { EcomOperationKind, EcomStatus, EcomPaymentType } from '../types/ecom';
import type { Role } from '../types/role';
import type { AuditActionCode, AuditEntityCode, IntegrityProblemKind } from '../types/audit';

export type Language = 'en' | 'az' | 'ru';

export interface TranslationDictionary {
  common: {
    save: string;
    cancel: string;
    delete: string;
    edit: string;
    search: string;
    filter: string;
    actions: string;
    export: string;
    copy: string;
    close: string;
    refresh: string;
    loading: string;
    yes: string;
    no: string;
    all: string;
    status: string;
    date: string;
    back: string;
    copied: string;
    success: string;
    error: string;
    warning: string;
    info: string;
    confirm: string;
    viewAll: string;
    details: string;
    active: string;
    inactive: string;
    overview: string;
    create: string;
    update: string;
    loadFailed: string;
    /** Периоды панелей: главной и статистики по ссылкам (Р-89, Р-91). */
    periods: { today: string; days7: string; days30: string; days90: string };
  };
  nav: {
    home: string;
    payByLink: string;
    transactions: string;
    ecommerce: string;
    pos: string;
    terminals: string;
    companies: string;
    users: string;
    auditLogs: string;
    settings: string;
  };
  header: {
    title: string;
    notifications: string;
    markAllRead: string;
    noNotifications: string;
    profile: string;
    logout: string;
    logoutQuestion: string;
    language: string;
    adminBadge: string;
  };
  home: {
    title: string;
    /** Главная — оплаты картой по мерчантам логина компании из выписки провайдера (Р-91, Р-97). */
    subtitle: string;
    /** То же для SYSTEM_ADMIN и AUDITOR — по мерчантам всех компаний (Р-97). */
    subtitleAll: string;
    openStatement: string;
    period: string;
    loadFailed: string;
    empty: string;
    metrics: {
      netRevenue: string;
      netRevenueHint: string;
      paidCount: string;
      paidCountHint: string;
      refunded: string;
      refundedHint: string;
      averagePayment: string;
    };
    charts: {
      daily: string;
      statuses: string;
      statusesHint: string;
      terminals: string;
    };
    recentOrders: {
      title: string;
      empty: string;
    };
  };
  /** Р-91: статистика во вкладке Pay by Link — только оплаты по платёжным ссылкам (сводка `pbl`, Р-89). */
  linkStats: {
    subtitle: string;
    loadFailed: string;
    empty: string;
    metrics: {
      netRevenue: string;
      netRevenueHint: string;
      paidCount: string;
      paidCountHint: string;
      refunded: string;
      /** Возвраты считаются по дате возврата, а не платежа (Р-89). */
      refundedHint: string;
      averagePayment: string;
    };
    charts: {
      daily: string;
      /** Каждое открытие ссылки — попытка; брошенная становится «Неуспешной». Это не исходы платежей. */
      attempts: string;
      attemptsHint: string;
      terminals: string;
      links: string;
      linksHint: string;
      /** Воронка — по ссылкам, созданным в периоде (Р-128): оплаты после периода тоже засчитываются. */
      funnel: string;
      funnelHint: string;
      funnelSteps: { created: string; opened: string; paymentStarted: string; paid: string };
      timeToPay: string;
      timeToPayHint: string;
      median: string;
      paidLinks: string;
      noPaidLinks: string;
      timeToPayRanges: { UP_TO_1_HOUR: string; UP_TO_1_DAY: string; UP_TO_7_DAYS: string; OVER_7_DAYS: string };
      /** Единицы для «2 ч 15 мин»: число и единица ставятся рядом, фраз с подстановкой нет. */
      units: { lessThanMinute: string; minute: string; hour: string; day: string };
    };
  };
  settings: {
    title: string;
    subtitle: string;
    saveSuccess: string;
    saveChanges: string;
    account: {
      title: string;
      merchantName: string;
      merchantEmail: string;
      emailReadOnly: string;
      nameReadOnly: string;
      noCompany: string;
      loadFailed: string;
      saveFailed: string;
    };
    display: {
      title: string;
      language: string;
    };
  };
  payByLink: {
    title: string;
    subtitle: string;
    tabs: {
      links: string;
      stats: string;
    };
    createButton: string;
    createTitle: string;
    createSubtitle: string;
    linkDetails: string;
    terminalSelect: string;
    terminalHelper: string;
    amountLabel: string;
    currencyLabel: string;
    descriptionLabel: string;
    customerNameLabel: string;
    customerEmailLabel: string;
    customerPhoneLabel: string;
    /** Телефон клиента — только азербайджанский номер (Р-96): провайдер ждёт код страны и номер раздельно. */
    customerPhoneHint: string;
    customerPhoneInvalid: string;
    usageTypeLabel: string;
    singleUse: string;
    multipleUse: string;
    maxUsesLabel: string;
    expirationLabel: string;
    paymentTypeLabel: string;
    createLinkAction: string;
    cancelLinkAction: string;
    /** Заголовок окна «поделиться ссылкой» — это форма, а не подтверждение (P3-5b). */
    shareDialogTitle: string;
    cancelConfirmTitle: string;
    cancelConfirmText: string;
    /** Безопасная кнопка обоих окон отмены ссылки — списка и карточки (P3-5a). */
    keepLink: string;
    linkCancelledSuccess: string;
    linkCancelFailed: string;
    noActiveTerminals: string;
    customerNotSpecified: string;
    empty: string;
    share: string;
    copyLink: string;
    sendEmail: string;
    sendWhatsApp: string;
    qrCode: string;
    qrHint: string;
    downloadQr: string;
    createdTitle: string;
    linkLabel: string;
    done: string;
    generating: string;
    invalidAmount: string;
    descriptionRequired: string;
    invalidMaxUses: string;
    createFailed: string;
    smsHint: string;
    dmsHint: string;
    dmsForbiddenUser: string;
    dmsForbiddenTerminal: string;
    maxUsesHint: string;
    descriptionHint: string;
    customerSection: string;
    linkSettings: string;
    /** Текст, который уходит клиенту в письме и в WhatsApp перед самой ссылкой. */
    messageText: string;
    emailSubject: string;
    /** Варианты срока жизни ссылки; значение уходит на сервер как `expiresAt`. */
    expiry: { h1: string; h24: string; h72: string; d7: string; d30: string };
    /** Подпись в списке вместо срока — только у завершённой ссылки (P2-15, Р-47). */
    paid: string;
    /** `Record<LinkStatus, string>`: новый статус — и `tsc` потребует подпись на трёх языках (P2-13). */
    statuses: Record<LinkStatus, string>;
    table: {
      linkId: string;
      customer: string;
      amount: string;
      type: string;
      status: string;
      usage: string;
      created: string;
      expires: string;
      actions: string;
    };
  };
  payByLinkDetail: {
    backToLinks: string;
    title: string;
    subtitle: string;
    copyUrl: string;
    cancelLink: string;
    /**
     * Панель у истёкшей и отменённой ссылки: кнопка открывает форму создания на странице
     * Pay by Link, заполненную полями этой ссылки (терминал — только если он активен).
     */
    quickActions: string;
    createSameLink: string;
    tabs: {
      overview: string;
      transactions: string;
      settings: string;
    };
    summary: {
      linkInfo: string;
      shortCode: string;
      originalUrl: string;
      redirectUrl: string;
      dmsStatus: string;
      payerIp: string;
      sentVia: string;
      terminal: string;
      /**
       * `refundedPaymentsCount` рядом с «использовано N из M» (Р-50), только когда возвраты были:
       * возврат использование не отменяет (Р-49) — отдельная цифра, а не минус из счётчика.
       */
      refundedOfUsed: string;
    };
    timeline: {
      title: string;
      created: string;
      paid: string;
      finalized: string;
    };
  };
  transactions: {
    title: string;
    subtitle: string;
    filters: {
      dateRange: string;
      search: string;
      paymentMethod: string;
      terminal: string;
      clearFilters: string;
    };
    /** `Record<TransactionStatus, string>`: новый статус — и `tsc` потребует подпись на трёх языках. */
    statuses: Record<TransactionStatus, string>;
    columns: {
      /** Номер заказа у провайдера и RID платежа идут первыми, внутренний `id` — последним (Р-58). */
      providerOrderId: string;
      ridByMerchant: string;
      id: string;
      date: string;
      amount: string;
      customer: string;
      status: string;
      rrn: string;
      method: string;
      terminal: string;
      /** Подпись поля терминала на карточке операции; сама подпись — `utils/terminals.ts`. */
      terminalLogin: string;
      actions: string;
    };
    detail: {
      title: string;
      backToTransactions: string;
      refundAction: string;
      refundTitle: string;
      refundAmount: string;
      confirmRefund: string;
      /** Списание холда (P3-5a); общие с окном «Finalize» карточки ссылки — там тот же `/complete`. */
      completeAction: string;
      completeTitle: string;
      captureExplains: string;
      captureAmount: string;
      confirmCapture: string;
      /** Возврат (P3-5a): окно говорит «возврат», как бэкенд (`/refund`, `REFUND` в журнале). */
      refundQuestion: string;
      /** Необязательная причина возврата — в журнал аудита (Р-126). */
      refundReason: string;
      refundReasonHint: string;
      keepTransaction: string;
      customerInfo: string;
      paymentInfo: string;
      technicalInfo: string;
      approvalCode: string;
      /**
       * Неподтверждённый исход (502) — отдельно от отказа: после отказа повтор безопасен, здесь
       * операция могла уже пройти (`utils/moneyOperationError.ts`).
       */
      unresolvedTitle: string;
      unresolvedHint: string;
      /** Спросить эквайера о судьбе операции — единственный верный следующий шаг. */
      checkStatusAction: string;
      statusChecked: string;
      /** Сбой самой проверки — не исход денежной операции, запрет не трогает. */
      checkStatusFailed: string;
      /** Вернуть можно только остаток. */
      refundableLeft: string;
      /** Сумма возврата и списания (Р-133): в окне сразу потолок, её можно уменьшить. */
      amountLabel: string;
      amountUpTo: string;
      amountWholeRefund: string;
      amountWholeCapture: string;
      amountInvalid: string;
      amountAboveMax: string;
      refundRemains: string;
      captureReleased: string;
      capturePartialHint: string;
      /** Заголовок блока возврата и списания — общий у карточки операции и панели заказа выписки. */
      actionsTitle: string;
      /** Причина выключенной кнопки (Р-123) — ровно `MoneyActionReason` бэкенда; `other` — код незнакомый. */
      moneyReasons: {
        NO_RIGHTS: string;
        TERMINAL_NOT_IN_PORTAL: string;
        NO_PROVIDER_CREDENTIALS: string;
        FULLY_REFUNDED: string;
        CAPTURE_FIRST: string;
        ALREADY_CAPTURED: string;
        OUTCOME_UNKNOWN: string;
        IN_PROGRESS: string;
        other: string;
      };
      /** Неподтверждённая операция, которую помнит сервер (Р-123); итог отмечает только администратор. */
      unresolvedInProgressTitle: string;
      unresolvedInProgressHint: string;
      unresolvedKindRefund: string;
      unresolvedKindCapture: string;
      unresolvedStartedAt: string;
      unresolvedStartedBy: string;
      resolveExecuted: string;
      resolveNotExecuted: string;
      resolveExecutedTitle: string;
      resolveExecutedQuestion: string;
      resolveNotExecutedTitle: string;
      resolveNotExecutedQuestion: string;
      resolveFailed: string;
      /** Денежные события называют действие, а не состояние: рядом с суммой «возврат» понятнее. */
      eventCreated: string;
      eventCaptured: string;
      eventRefunded: string;
      /** Пустая шкала без слов читается как поломка. */
      historyEmpty: string;
      /** Блок `providerOrderId` и `ridByMerchant` — первый на карточке операции (Р-58). */
      identifiers: string;
      providerOrderId: string;
      ridByMerchant: string;
      clientIp: string;
    };
  };
  /**
   * Выписка сервиса `ecom` (`project_docs/modules/ecom.md` §2). Свой словарь, а не `transactions`:
   * статусов восемь (Р-75, Р-78), период обязателен, страница курсорная, операции — внутри заказа.
   */
  ecommerce: {
    title: string;
    subtitle: string;
    periodFrom: string;
    periodTo: string;
    periodHint: string;
    periodInvalid: string;
    periodTooLong: string;
    terminals: string;
    allTerminals: string;
    search: string;
    minAmount: string;
    maxAmount: string;
    loadMore: string;
    loaded: string;
    loadFailed: string;
    empty: string;
    exportLoaded: string;
    /** Фильтры статуса и типа оплаты (Р-87). Статус сервер отбирает после сборки заказа, с потолком просмотра. */
    statusFilter: string;
    allStatuses: string;
    paymentTypeFilter: string;
    allPaymentTypes: string;
    paymentTypes: Record<EcomPaymentType, string>;
    /** Потолок просмотра дошёл, а совпадений в просмотренной части нет: дальше — «показать ещё». */
    noMatchesYet: string;
    /** Фильтры уходят в запрос только по кнопке (Р-88): каждый запрос выписки идёт в боевую базу провайдера. */
    applyFilters: string;
    resetFilters: string;
    filtersChanged: string;
    stats: {
      orders: string;
      captured: string;
      refunded: string;
    };
    /** Все восемь статусов `types/ecom.ts`: новый статус — и `tsc` потребует подпись на трёх языках. */
    statuses: Record<EcomStatus, string>;
    operationKinds: Record<EcomOperationKind, string>;
    columns: {
      createdAt: string;
      orderId: string;
      ridByMerchant: string;
      card: string;
      amount: string;
      captured: string;
      status: string;
      terminal: string;
    };
    detail: {
      back: string;
      openInNewTab: string;
      title: string;
      notFound: string;
      loadFailed: string;
      identifiers: string;
      money: string;
      orderAmount: string;
      captured: string;
      refunded: string;
      payment: string;
      card: string;
      terminal: string;
      providerStatus: string;
      createdAt: string;
      lastOperationAt: string;
      declineCode: string;
      description: string;
      operations: string;
      operationAt: string;
      kind: string;
      codes: string;
      result: string;
      amount: string;
      clearAmount: string;
      rrn: string;
      tranId: string;
      noOperations: string;
    };
  };
  terminals: {
    title: string;
    subtitle: string;
    addTerminal: string;
    /** Кнопка, заводящая терминал, в окне создания (P3-5b). */
    registerAction: string;
    editTerminal: string;
    createDialogTitle: string;
    editDialogTitle: string;
    name: string;
    /**
     * Выбор терминала провайдера при заведении (Р-67, Р-79): название и логин — из справочника, пароля
     * у терминала нет (Р-93). Видит и заводит только SYSTEM_ADMIN (`project_docs/modules/ecom.md` §3).
     */
    providerTerminal: string;
    providerTerminalHint: string;
    providerTerminalEmpty: string;
    providerTerminalLoadFailed: string;
    syncDirectory: string;
    syncApplied: string;
    syncSkipped: string;
    login: string;
    /** Колонка и подпись терминала — номер терминала у провайдера (Р-96), у старых — логин. */
    terminal: string;
    /**
     * Кнопка «Тест» и её исходы. Проверка — пробный заказ у провайдера с кредами компании терминала
     * (Р-93); различать нужно все четыре исхода: следующий шаг у каждого свой (`utils/terminalCheck.ts`).
     */
    testAction: string;
    checkOk: string;
    checkInvalid: string;
    checkRejected: string;
    checkUnreachable: string;
    company: string;
    status: string;
    /** Полный словарь статусов терминала: новое значение потребует перевода на все три языка. */
    statuses: Record<TerminalStatus, string>;
    blockAction: string;
    unblockAction: string;
    blockExplains: string;
    blockLinksAffected: string;
    blockLinksUnknown: string;
    unblockExplains: string;
    unblockLinksAffected: string;
    editConfirmTitle: string;
    editConfirmQuestion: string;
    editNothingChanged: string;
    searchPlaceholder: string;
    /** Пояснение к выбору компании в формах; виден только тем, кто выбирает (SYSTEM_ADMIN). */
    companyHint: string;
    nameFromProvider: string;
    dmsColumn: string;
    dmsAllowed: string;
    dmsForbidden: string;
    dmsSwitch: string;
    dmsSwitchHint: string;
    formIncomplete: string;
    created: string;
    createFailed: string;
    updated: string;
    updateFailed: string;
    checkFailed: string;
    blockedNotice: string;
    unblockedNotice: string;
    statusChangeFailed: string;
    empty: string;
  };
  companies: {
    title: string;
    subtitle: string;
    addCompany: string;
    createDialogTitle: string;
    companyId: string;
    name: string;
    status: string;
    searchPlaceholder: string;
    deleteTitle: string;
    deleteQuestion: string;
    deleteIrreversible: string;
    /**
     * Креды компании к провайдеру (Р-93): все запросы к шлюзу идут от её имени. Задаёт и меняет только
     * SYSTEM_ADMIN; пароль хранится зашифрованным и не показывается никому — только заменяется.
     */
    providerLogin: string;
    providerLoginHint: string;
    /** Свободных логинов мультимерчантов в справочнике нет (Р-95). */
    providerLoginEmpty: string;
    /** Итог обновления справочника логинов мультимерчантов (Р-94) — по кнопке в форме компании. */
    loginsSyncApplied: string;
    loginsSyncSkipped: string;
    providerPassword: string;
    providerPasswordHint: string;
    newProviderPassword: string;
    newProviderPasswordHint: string;
    /**
     * Окно правки компании: название, логин и пароль к провайдеру, статус. Подтверждение перечисляет
     * изменения; предупреждения — только к тому, что меняется.
     */
    editCompany: string;
    editConfirmTitle: string;
    editConfirmQuestion: string;
    credentialsWarning: string;
    statusWarning: string;
    providerPasswordReplaced: string;
    /** VÖEN компании (Р-129): 10 цифр, печатается на чеке плательщика; стереть нельзя, только заменить. */
    taxId: string;
    taxIdHint: string;
    taxIdInvalid: string;
    editNothingChanged: string;
    formIncomplete: string;
    created: string;
    createFailed: string;
    updated: string;
    updateFailed: string;
  };
  users: {
    title: string;
    subtitle: string;
    addUser: string;
    createDialogTitle: string;
    username: string;
    password: string;
    name: string;
    role: string;
    company: string;
    status: string;
    searchPlaceholder: string;
    deleteTitle: string;
    deleteQuestion: string;
    deleteIrreversible: string;
    roles: {
      systemAdmin: string;
      companyHead: string;
      companyManager: string;
      companyEmployee: string;
      auditor: string;
    };
    formIncomplete: string;
    createFailed: string;
    deleteFailed: string;
    /** Правка пользователя (Р-90): имя, роль, компания, статус, новый пароль — через подтверждение. */
    editUser: string;
    editDialogTitle: string;
    editConfirmTitle: string;
    editConfirmQuestion: string;
    editNothingChanged: string;
    /** Роль и компания вступают в силу при следующем обновлении сессии пользователя. */
    editSessionsHint: string;
    noCompany: string;
    /** Роль компании без компании бэкенд отклоняет (Р-103). */
    companyRequired: string;
    newPassword: string;
    newPasswordHint: string;
    /** Пароль, заданный администратором, пользователь сменит при первом входе (Р-100). */
    issuedPasswordHint: string;
    passwordChangePending: string;
    /** Р-131: сотрудник видит только назначенные терминалы; руководитель и менеджер — все терминалы компании. */
    terminals: string;
    terminalsHint: string;
    terminalsRequired: string;
    noCompanyTerminals: string;
    allCompanyTerminals: string;
    noTerminals: string;
    noTerminalsHint: string;
    passwordWillChange: string;
    /** Свой пароль: сервер гасит все сессии, и эту тоже — после сохранения вход заново. */
    ownPasswordSignsOut: string;
    /** Свою роль и статус в этом окне не поменять: так себя легко лишить доступа. */
    selfHint: string;
    dmsLinks: string;
    dmsLinksAllowed: string;
    dmsLinksForbidden: string;
    dmsLinksSwitch: string;
    dmsLinksHint: string;
    noDmsLinks: string;
    updated: string;
    updateFailed: string;
    statuses: {
      ACTIVE: string;
      BLOCKED: string;
    };
  };
  auditLogs: {
    title: string;
    subtitle: string;
    user: string;
    action: string;
    resource: string;
    timestamp: string;
    ip: string;
    filterEntity: string;
    searchPlaceholder: string;
    outcome: string;
    outcomeSuccess: string;
    outcomeDenied: string;
    outcomeUnresolved: string;
    /** Возврат или списание отклонил эквайер (Р-134); не отказ портала в доступе. */
    outcomeDeclined: string;
    filterOutcome: string;
    dateFrom: string;
    dateTo: string;
    /** Действие и объект записи словами (`types/audit.ts`); незнакомый код показывается как есть. */
    actions: Record<AuditActionCode, string>;
    entities: Record<AuditEntityCode, string>;
    filterAction: string;
    filterUser: string;
    /** Только у SYSTEM_ADMIN и AUDITOR: остальные видят свою компанию. */
    filterCompany: string;
    empty: string;
    /** Фильтр «Требует внимания» и выгрузка CSV (Р-137). */
    attention: string;
    attentionHint: string;
    exportAction: string;
    exportHint: string;
    exportFailed: string;
    /** Проверка цепочки журнала (Р-138): SYSTEM_ADMIN и AUDITOR. */
    integrityAction: string;
    integrityTitle: string;
    integrityIntact: string;
    integrityBroken: string;
    integrityChecked: string;
    integrityHead: string;
    integrityStartedAt: string;
    integrityNotCovered: string;
    integrityUnsealed: string;
    integrityMore: string;
    integrityFailed: string;
    integrityProblems: Record<IntegrityProblemKind, string>;
    /** Карточка записи журнала: открывается кликом по строке. */
    detailsTitle: string;
    entityId: string;
    company: string;
    recordId: string;
    traceId: string;
    openTransaction: string;
    openPaymentLink: string;
    openEcomOrder: string;
  };
  auth: {
    unknownRole: string;
    subtitle: string;
    emailLabel: string;
    passwordLabel: string;
    signIn: string;
    fillBoth: string;
    invalidEmail: string;
    /** Запрос до сервера не дошёл: это не «неверный пароль», перепечатывать его незачем. */
    networkError: string;
    authFailed: string;
    /** 200 без токенов (например, прокси отдал HTML): вход отклонён на клиенте. */
    malformedResponse: string;
    /** Выход после 15 минут без действий (PCI DSS 8.2.8, Р-99). */
    idleSignedOut: string;
    /** Смена пароля, заданного не владельцем, при входе (PCI DSS 8.3.5, Р-100). */
    passwordChangeRequired: string;
    newPasswordLabel: string;
    confirmPasswordLabel: string;
    passwordRules: string;
    passwordsDoNotMatch: string;
    samePassword: string;
    changePasswordAndSignIn: string;
  };
  errors: {
    forbiddenTitle: string;
    forbiddenText: string;
    notFoundTitle: string;
    notFoundText: string;
    unexpectedTitle: string;
    unexpectedText: string;
    goHome: string;
  };
}

/** Статус вне словаря — исходным значением из ответа, ничего знакомого не подставлять. */
export const statusLabel = (
  dict: TranslationDictionary,
  status: TransactionStatus | null | undefined,
  raw?: string
): string => (status ? dict.transactions.statuses[status] : raw || '—');

/** Роль словами; правило то же, что у `statusLabel`: роль вне словаря — как есть, не знакомой (Р-48). */
export const roleLabel = (
  dict: TranslationDictionary,
  role: Role | null | undefined,
  raw?: string
): string => {
  switch (role) {
    case 'SYSTEM_ADMIN': return dict.users.roles.systemAdmin;
    case 'AUDITOR': return dict.users.roles.auditor;
    case 'COMPANY_HEAD': return dict.users.roles.companyHead;
    case 'COMPANY_MANAGER': return dict.users.roles.companyManager;
    case 'COMPANY_EMPLOYEE': return dict.users.roles.companyEmployee;
    default: return raw || '—';
  }
};

/** Правило то же, что у `statusLabel`. */
export const linkStatusLabel = (
  dict: TranslationDictionary,
  status: LinkStatus | null | undefined,
  raw?: string
): string => (status ? dict.payByLink.statuses[status] : raw || '—');

export const translations: Record<Language, TranslationDictionary> = {
  en: {
    common: {
      save: 'Save',
      cancel: 'Cancel',
      delete: 'Delete',
      edit: 'Edit',
      search: 'Search...',
      filter: 'Filter',
      actions: 'Actions',
      export: 'Export',
      copy: 'Copy',
      close: 'Close',
      refresh: 'Refresh',
      loading: 'Loading...',
      yes: 'Yes',
      no: 'No',
      all: 'All',
      status: 'Status',
      date: 'Date',
      back: 'Back',
      copied: 'Copied to clipboard',
      success: 'Success',
      error: 'Error',
      warning: 'Warning',
      info: 'Information',
      confirm: 'Confirm',
      viewAll: 'View All',
      details: 'Details',
      active: 'Active',
      inactive: 'Inactive',
      overview: 'Overview',
      create: 'Create',
      update: 'Update',
      loadFailed: 'Could not load the data.',
      periods: { today: 'Today', days7: '7 days', days30: '30 days', days90: '90 days' },
    },
    nav: {
      home: 'Home Page',
      payByLink: 'Pay by Link',
      transactions: 'Transaction List',
      ecommerce: 'E-commerce',
      pos: 'POS Terminals',
      terminals: 'Terminals',
      companies: 'Companies',
      users: 'Users',
      auditLogs: 'Audit Logs',
      settings: 'Settings',
    },
    header: {
      title: 'Merchant Portal',
      notifications: 'Notifications',
      markAllRead: 'Mark all as read',
      noNotifications: 'No unread notifications',
      profile: 'My Profile',
      logout: 'Log Out',
      logoutQuestion: 'Sign out of the portal?',
      language: 'Language',
      adminBadge: 'SYSTEM ADMIN',
    },
    home: {
      title: 'Merchant Dashboard',
      subtitle: 'Card payments across all of the company\'s terminals, from the provider\'s statement, for orders created in the period.',
      subtitleAll: 'Card payments of all companies of the portal, from the provider\'s statement, for orders created in the period.',
      openStatement: 'Open E-commerce statement',
      period: 'Period',
      loadFailed: 'Could not load the dashboard summary.',
      empty: 'No orders in this period.',
      metrics: {
        netRevenue: 'Net revenue',
        netRevenueHint: 'Captured minus refunds, for orders of the period',
        paidCount: 'Paid orders',
        paidCountHint: 'Including orders refunded later',
        refunded: 'Refunded',
        refundedHint: 'Refunds for orders of the period',
        averagePayment: 'Average order',
      },
      charts: {
        daily: 'Revenue by day',
        statuses: 'Orders by status',
        statusesHint: 'Statuses as in the E-commerce statement',
        terminals: 'Terminals by revenue',
      },
      recentOrders: {
        title: 'Latest orders',
        empty: 'No orders in this period.',
      },
    },
    linkStats: {
      subtitle: 'Payments made through the portal\'s payment links only. Refunds count on the day they were made.',
      loadFailed: 'Could not load the payment link statistics.',
      empty: 'No payment link payments in this period.',
      metrics: {
        netRevenue: 'Net revenue',
        netRevenueHint: 'Received minus refunds made in the period',
        paidCount: 'Payments received',
        paidCountHint: 'Including payments refunded later',
        refunded: 'Refunded',
        refundedHint: 'Refunds made in the period, whenever the payment was',
        averagePayment: 'Average payment',
      },
      charts: {
        daily: 'Revenue by day',
        attempts: 'Payment attempts',
        attemptsHint: 'Every opening of a payment link is an attempt; an abandoned one ends up failed.',
        terminals: 'Terminals by revenue',
        links: 'Payment links created',
        linksHint: 'Current status of the links created in the period',
        funnel: 'Link funnel',
        funnelHint: 'Links created in the period and how far they got. Payments made after the period count too, so a recent period can still grow. Paid means charged or held.',
        funnelSteps: { created: 'Created', opened: 'Opened', paymentStarted: 'Card submitted', paid: 'Paid' },
        timeToPay: 'Time to payment',
        timeToPayHint: 'Single-use links of the period: from creating the link to the start of the paid attempt.',
        median: 'Median',
        paidLinks: 'Paid links',
        noPaidLinks: 'No paid single-use links in the period',
        timeToPayRanges: { UP_TO_1_HOUR: 'Under 1 hour', UP_TO_1_DAY: '1–24 hours', UP_TO_7_DAYS: '1–7 days', OVER_7_DAYS: 'Over 7 days' },
        units: { lessThanMinute: '< 1 min', minute: 'min', hour: 'h', day: 'd' },
      },
    },
    settings: {
      title: 'Settings',
      subtitle: 'Your company name and the interface language. Everything else lives on its own page.',
      saveSuccess: 'Settings saved successfully!',
      saveChanges: 'Save Changes',
      account: {
        title: 'Business Information',
        merchantName: 'Merchant / Business Name',
        merchantEmail: 'Account Email',
        emailReadOnly: 'Taken from the account you signed in with; it cannot be changed here.',
        nameReadOnly: 'Only a system administrator can change the company name.',
        noCompany: 'Your account is not linked to a company. Companies are managed on the Companies page.',
        loadFailed: 'Could not load the company details.',
        saveFailed: 'Could not save the changes.',
      },
      display: {
        title: 'Interface',
        language: 'System Language',
      },
    },
    payByLink: {
      title: 'Pay by Link',
      subtitle: 'Create, share and manage instant payment links for your customers via SMS, Email or Messaging apps.',
      tabs: {
        links: 'Links',
        stats: 'Statistics',
      },
      createButton: 'Create Payment Link',
      createTitle: 'Create New Payment Link',
      createSubtitle: 'Generate a secure payment link to request money from a customer.',
      linkDetails: 'Link Details',
      terminalSelect: 'Select Terminal *',
      terminalHelper: 'Acquiring terminal used to process the payment',
      amountLabel: 'Payment Amount *',
      currencyLabel: 'Currency',
      descriptionLabel: 'Payment Description / Order Ref *',
      customerNameLabel: 'Customer Name',
      customerEmailLabel: 'Customer Email',
      customerPhoneLabel: 'Customer Phone',
      customerPhoneHint: 'Azerbaijani number: +994 and 9 digits',
      customerPhoneInvalid: 'The customer phone must be an Azerbaijani number: +994 and 9 digits, e.g. +994 70 330 10 25',
      usageTypeLabel: 'Usage Type',
      singleUse: 'Single Use (One Payment)',
      multipleUse: 'Multiple Uses (Reusable Link)',
      maxUsesLabel: 'Max Payments Allowed',
      expirationLabel: 'Link Expiration Time',
      paymentTypeLabel: 'Payment Capture Type',
      createLinkAction: 'Generate Payment Link',
      cancelLinkAction: 'Cancel Payment Link',
      shareDialogTitle: 'Share Payment Link',
      cancelConfirmTitle: 'Cancel Payment Link?',
      cancelConfirmText: 'Are you sure you want to cancel this payment link? Customers will no longer be able to pay using it.',
      keepLink: 'Keep Link',
      linkCancelledSuccess: 'Payment link cancelled successfully',
      linkCancelFailed: 'Could not cancel the payment link',
      paid: 'Paid',
      noActiveTerminals: 'No active terminals: register one or unblock it on the Terminals page. A blocked terminal issues no new links.',
      customerNotSpecified: 'Not specified',
      empty: 'No payment links',
      share: 'Share',
      copyLink: 'Copy link',
      sendEmail: 'Send by email',
      sendWhatsApp: 'Send via WhatsApp',
      qrCode: 'QR code',
      qrHint: 'Point a phone camera at the code to open the payment page',
      downloadQr: 'Download PNG',
      createdTitle: 'Payment link created. Share it with the customer.',
      linkLabel: 'Payment link',
      done: 'Done',
      generating: 'Generating…',
      invalidAmount: 'Enter a valid amount',
      descriptionRequired: 'Add a description or order reference',
      invalidMaxUses: 'Max payments must be a whole number of at least 1',
      createFailed: 'Could not create the payment link',
      smsHint: 'SMS: funds are charged as soon as the customer pays.',
      dmsHint: 'DMS: funds are reserved on the card; capture them from the transaction card.',
      dmsForbiddenUser: 'You are not allowed to create DMS links. Ask the company head or the administrator.',
      dmsForbiddenTerminal: 'DMS links are not allowed on this terminal. Ask the administrator.',
      maxUsesHint: 'The link closes after this many successful payments',
      descriptionHint: 'Shown to the customer on the payment page',
      customerSection: 'Customer',
      linkSettings: 'Link settings',
      messageText: 'Please complete your payment using this link:',
      emailSubject: 'Payment request',
      expiry: { h1: '1 hour', h24: '24 hours', h72: '3 days', d7: '7 days', d30: '30 days' },
      statuses: {
        ACTIVE: 'Active',
        EXPIRED: 'Expired',
        COMPLETED: 'Completed',
        CANCELED: 'Canceled',
        SUSPENDED: 'Suspended (terminal blocked)',
      },
      table: {
        linkId: 'Link ID / Ref',
        customer: 'Customer',
        amount: 'Amount',
        type: 'Type',
        status: 'Status',
        usage: 'Usage',
        created: 'Created At',
        expires: 'Expires At',
        actions: 'Actions',
      },
    },
    payByLinkDetail: {
      backToLinks: 'Back to Pay by Link',
      title: 'Payment Link Details',
      subtitle: 'Inspect payment link specifications, execution history and DMS status.',
      copyUrl: 'Copy Link URL',
      cancelLink: 'Cancel Link',
      quickActions: 'Quick Actions',
      createSameLink: 'Create New Link (Same Details)',
      tabs: {
        overview: 'Overview',
        transactions: 'Transactions',
        settings: 'Link Settings',
      },
      summary: {
        linkInfo: 'Payment Link Specs',
        shortCode: 'Short Code',
        originalUrl: 'Pay URL',
        redirectUrl: 'Redirect URL',
        dmsStatus: 'DMS Status',
        payerIp: 'Payer IP',
        sentVia: 'Sent Via',
        terminal: 'Assigned Terminal',
        refundedOfUsed: 'of them refunded',
      },
      timeline: {
        title: 'Execution Lifecycle',
        created: 'Link Generated',
        paid: 'Payment Authorized',
        finalized: 'DMS Payment Completed',
      },
    },
    transactions: {
      title: 'Transactions',
      subtitle: 'View and audit all transaction logs processed across your terminals.',
      filters: {
        dateRange: 'Date Range',
        search: 'Search by Provider Order ID, RID by merchant, customer, email or transaction ID...',
        paymentMethod: 'Payment Method',
        terminal: 'Terminal',
        clearFilters: 'Clear Filters',
      },
      statuses: {
        PENDING: 'Pending',
        AUTHORIZED: 'Authorized',
        SUCCESS: 'Success',
        FAILED: 'Failed',
        PARTIALLY_REFUNDED: 'Partially Refunded',
        REFUNDED: 'Refunded',
      },
      columns: {
        providerOrderId: 'Provider Order ID',
        ridByMerchant: 'RID by merchant',
        id: 'Transaction ID',
        date: 'Date & Time',
        amount: 'Amount',
        customer: 'Customer',
        status: 'Status',
        rrn: 'RRN',
        method: 'Method',
        terminal: 'Terminal',
        terminalLogin: 'Terminal Login',
        actions: 'Details',
      },
      detail: {
        title: 'Transaction Details',
        backToTransactions: 'Back to Transactions',
        refundAction: 'Process Refund',
        refundTitle: 'Issue Transaction Refund',
        refundAmount: 'Refund amount',
        confirmRefund: 'Confirm Refund',
        completeAction: 'Complete',
        completeTitle: 'Complete DMS Transaction',
        captureExplains: 'The funds currently held on the customer\u2019s card will be captured and transferred to your account. This cannot be undone.',
        captureAmount: 'Amount being captured',
        confirmCapture: 'Capture Funds',
        refundQuestion: 'Do you want to refund this transaction? The funds will be returned to the customer\u2019s card and this cannot be undone.',
        refundReason: 'Reason (optional)',
        refundReasonHint: 'Goes to the audit log; the customer and the acquirer do not see it.',
        keepTransaction: 'Keep Transaction',
        customerInfo: 'Customer Information',
        paymentInfo: 'Payment Breakdown',
        technicalInfo: 'Technical Gateway Info',
        approvalCode: 'Approval Code',
        unresolvedTitle: 'Outcome not confirmed by the acquirer',
        unresolvedHint: 'The operation may already have gone through. It stays locked until a system administrator reconciles it with the provider and records the outcome.',
        checkStatusAction: 'Check status',
        statusChecked: 'Transaction status refreshed.',
        checkStatusFailed: 'Could not check the status. Try again.',
        refundableLeft: 'Left to refund',
        amountLabel: 'Amount',
        amountUpTo: 'Up to',
        amountWholeRefund: 'Whole remainder',
        amountWholeCapture: 'Whole amount',
        amountInvalid: 'Enter an amount above zero with at most two decimal places',
        amountAboveMax: 'The amount is above what is available',
        refundRemains: 'Left to refund after this',
        captureReleased: 'Not captured',
        capturePartialHint: 'A hold is captured once: the portal will not capture the rest later, the bank releases it on its own schedule.',
        actionsTitle: 'Actions',
        moneyReasons: {
          NO_RIGHTS: 'Your role does not allow this action',
          TERMINAL_NOT_IN_PORTAL: 'The terminal of this operation is not registered in the portal',
          NO_PROVIDER_CREDENTIALS: 'The terminal\'s company has no provider login',
          FULLY_REFUNDED: 'Fully refunded',
          CAPTURE_FIRST: 'Capture the hold first',
          ALREADY_CAPTURED: 'The hold is already captured',
          OUTCOME_UNKNOWN: 'The outcome of the previous operation is unknown',
          IN_PROGRESS: 'Another operation on this payment is in progress',
          other: 'This action is not available',
        },
        unresolvedInProgressTitle: 'An operation is in progress',
        unresolvedInProgressHint: 'The acquirer\'s answer has not been recorded yet. Wait a moment and reload the page.',
        unresolvedKindRefund: 'Refund',
        unresolvedKindCapture: 'Capture',
        unresolvedStartedAt: 'Sent',
        unresolvedStartedBy: 'By',
        resolveExecuted: 'It went through',
        resolveNotExecuted: 'It did not go through',
        resolveExecutedTitle: 'Record the operation as executed?',
        resolveExecutedQuestion: 'It will be recorded as confirmed, without the acquirer\'s references. Do this only after reconciling it with the provider.',
        resolveNotExecutedTitle: 'Record the operation as not executed?',
        resolveNotExecutedQuestion: 'The lock is lifted and nothing changes on the payment. Do this only after reconciling it with the provider.',
        resolveFailed: 'Could not record the outcome',
        eventCreated: 'Transaction opened',
        eventCaptured: 'Hold captured',
        eventRefunded: 'Refund confirmed',
        historyEmpty: 'No recorded events for this transaction yet.',
        identifiers: 'Payment Identifiers',
        providerOrderId: 'Provider Order ID',
        ridByMerchant: 'RID by merchant',
        clientIp: 'Client IP Address',
      },
    },
    ecommerce: {
      title: 'E-commerce Statement',
      subtitle: 'Payments of your terminals from the provider\u2019s gateway: one row per order, with its full history.',
      periodFrom: 'Created from',
      periodTo: 'Created to',
      periodHint: 'Orders are selected by creation date. The period is required and can be at most 92 days.',
      periodInvalid: 'The end of the period must be later than its start.',
      periodTooLong: 'The period cannot be longer than 92 days.',
      terminals: 'Terminals',
      allTerminals: 'All terminals',
      search: 'Order ID, RID by merchant or RRN (exact match)',
      minAmount: 'Minimum amount',
      maxAmount: 'Maximum amount',
      loadMore: 'Load more',
      loaded: 'Orders loaded',
      loadFailed: 'Could not load the statement.',
      empty: 'No orders for the selected period and filters.',
      exportLoaded: 'Export loaded rows',
      statusFilter: 'Status',
      allStatuses: 'All statuses',
      paymentTypeFilter: 'Payment type',
      allPaymentTypes: 'All types',
      paymentTypes: { SMS: 'SMS (single message)', DMS: 'DMS (hold and capture)' },
      noMatchesYet: 'No orders with this status among the orders checked so far. Load more to keep looking.',
      applyFilters: 'Apply',
      resetFilters: 'Reset',
      filtersChanged: 'Filters changed — press Apply to update the statement.',
      stats: {
        orders: 'Orders',
        captured: 'Captured',
        refunded: 'Refunded',
      },
      statuses: {
        PENDING: 'Pending',
        AUTHORIZED: 'Authorized',
        SUCCESS: 'Success',
        PARTIALLY_PAID: 'Partially Paid',
        FAILED: 'Failed',
        PARTIALLY_REFUNDED: 'Partially Refunded',
        REFUNDED: 'Refunded',
        CANCELED: 'Canceled',
      },
      operationKinds: {
        AUTHORIZATION: 'Authorization (hold)',
        CAPTURE: 'Capture',
        PURCHASE: 'Purchase',
        REVERSAL: 'Reversal',
        REFUND: 'Refund',
        UNKNOWN: 'Unrecognised operation',
      },
      columns: {
        createdAt: 'Created',
        orderId: 'Provider Order ID',
        ridByMerchant: 'RID by merchant',
        card: 'Card',
        amount: 'Order amount',
        captured: 'Captured',
        status: 'Status',
        terminal: 'Terminal',
      },
      detail: {
        back: 'Back to statement',
        openInNewTab: 'Open in a new tab',
        title: 'Order',
        notFound: 'Order not found: it does not exist, belongs to another merchant or is not finished yet.',
        loadFailed: 'Could not load the order.',
        identifiers: 'Identifiers',
        money: 'Money',
        orderAmount: 'Order amount',
        captured: 'Captured',
        refunded: 'Refunded',
        payment: 'Payment',
        card: 'Card',
        terminal: 'Terminal',
        providerStatus: 'Provider status',
        createdAt: 'Created',
        lastOperationAt: 'Last operation',
        declineCode: 'Decline code',
        description: 'Description',
        operations: 'Operations',
        operationAt: 'Time',
        kind: 'Operation',
        codes: 'Provider codes',
        result: 'Result',
        amount: 'Amount',
        clearAmount: 'Cleared',
        rrn: 'RRN',
        tranId: 'Transaction ID',
        noOperations: 'No operations for this order.',
      },
    },
    terminals: {
      title: 'Terminals',
      subtitle: 'Manage payment processing POS and E-commerce acquiring terminals.',
      addTerminal: 'Add Terminal',
      registerAction: 'Register Terminal',
      editTerminal: 'Edit Terminal',
      createDialogTitle: 'Create New Terminal',
      editDialogTitle: 'Edit Terminal Details',
      name: 'Terminal Name',
      providerTerminal: 'Provider terminal',
      providerTerminalHint: 'Only terminals of merchants linked to the company login that are not added yet. Name, login and terminal number come from the provider directory; a terminal has no password.',
      providerTerminalEmpty: 'No free terminals of this company’s merchants in the provider directory. Refresh it; if the terminal still does not appear, it is inactive at the provider, already added, or its merchant is not linked to the company login.',
      providerTerminalLoadFailed: 'Could not load the provider directory.',
      syncDirectory: 'Refresh directory',
      syncApplied: 'Directory refreshed. Terminals received',
      syncSkipped: 'Directory was not refreshed',
      login: 'Merchant Login ID',
      terminal: 'Terminal',
      testAction: 'Test',
      checkOk: 'Company credentials accepted, a payment can be created',
      checkInvalid: 'Invalid company login or password',
      checkRejected: 'Credentials accepted, but the acquirer refused a payment',
      checkUnreachable: 'The acquirer did not answer — nothing is known about the terminal',
      company: 'Assigned Company',
      status: 'Status',
      statuses: {
        ACTIVE: 'Active',
        BLOCKED: 'Blocked',
      },
      blockAction: 'Block terminal',
      unblockAction: 'Unblock terminal',
      blockExplains: 'A blocked terminal takes no new payments: its active links are suspended and no new ones can be created. Refunds, DMS captures and status checks on existing payments keep working. Unblocking brings the links back.',
      blockLinksAffected: 'Active links that will be suspended',
      blockLinksUnknown: 'Could not count the affected links — blocking still suspends every active link of this terminal.',
      unblockExplains: 'Unblocking lets the terminal take payments again: suspended links go back to active, except the ones whose lifetime ran out while it was blocked.',
      unblockLinksAffected: 'Suspended links that will go back to active',
      editConfirmTitle: 'Save changes to terminal',
      editConfirmQuestion: 'The following will change. The name and the owning company live in this one form — check the list before confirming.',
      editNothingChanged: 'Nothing changed — no request was sent.',
      searchPlaceholder: 'Search terminals by name, ID or login...',
      companyHint: 'The company that owns the terminal',
      nameFromProvider: 'The name comes from the provider directory: rename the terminal at the provider',
      dmsColumn: 'DMS',
      dmsAllowed: 'Allowed',
      dmsForbidden: 'Forbidden',
      dmsSwitch: 'DMS links allowed',
      dmsSwitchHint: 'Without DMS only SMS links can be created on the terminal. Existing DMS links keep working.',
      formIncomplete: 'Fill in every required field',
      created: 'Terminal registered',
      createFailed: 'Could not register the terminal',
      updated: 'Terminal updated',
      updateFailed: 'Could not update the terminal',
      checkFailed: 'Could not check the terminal',
      blockedNotice: 'Terminal blocked: it takes no new payments and its active links are suspended',
      unblockedNotice: 'Terminal unblocked: suspended links are active again, except those whose lifetime ran out',
      statusChangeFailed: 'Could not change the terminal status',
      empty: 'No terminals yet',
    },
    companies: {
      title: 'Companies',
      subtitle: 'Manage merchant companies and legal entities.',
      addCompany: 'Add Company',
      createDialogTitle: 'Add New Merchant Company',
      companyId: 'Company Code / ID',
      name: 'Company Legal Name',
      status: 'Status',
      searchPlaceholder: 'Search companies by ID or name...',
      deleteTitle: 'Delete company?',
      deleteQuestion: 'The company disappears from every list in the portal. Its terminals, payment links and transactions stay in the database.',
      deleteIrreversible: 'This cannot be undone from the portal: a deleted company cannot be restored here.',
      providerLogin: 'Acquirer login',
      providerLoginHint: 'A multimerchant login from the provider directory: only active logins with active merchants that no other company uses. Created just now — refresh the directory.',
      providerLoginEmpty: 'No free multimerchant logins in the directory. Refresh it; if the login still does not appear, it is not created at the provider, is inactive, has no active merchants or belongs to another company.',
      loginsSyncApplied: 'Directory refreshed. Multimerchant logins received',
      loginsSyncSkipped: 'Login directory was not refreshed',
      providerPassword: 'Acquirer password',
      providerPasswordHint: 'Stored encrypted. Nobody can view it later — it can only be replaced.',
      newProviderPassword: 'New acquirer password (optional)',
      newProviderPasswordHint: 'Leave blank to keep the current password',
      providerPasswordReplaced: 'The acquirer password will be replaced',
      taxId: 'Tax ID (VÖEN)',
      taxIdHint: '10 digits, printed on the payer\'s receipt. It can be replaced but not cleared.',
      taxIdInvalid: 'VÖEN must be exactly 10 digits',
      editNothingChanged: 'Nothing changed — no request was sent.',
      formIncomplete: 'Fill in every required field',
      created: 'Company created',
      createFailed: 'Could not create the company',
      editCompany: 'Edit company',
      editConfirmTitle: 'Save changes to the company?',
      editConfirmQuestion: 'The following will change for this company. Check the list before confirming.',
      credentialsWarning: 'Every request of this company to the acquirer — new payments, captures, refunds and status checks — will go with the new credentials. Wrong ones stop the company payments.',
      statusWarning: 'Status is a label in the directory and an entry in the audit log. It does not stop sign-ins, link creation or payments — to stop payments, block the terminals.',
      updated: 'Company updated',
      updateFailed: 'Could not update the company',
    },
    users: {
      title: 'Users',
      subtitle: 'Manage merchant portal staff users, roles and access permissions.',
      addUser: 'Add User',
      createDialogTitle: 'Create Portal User',
      username: 'Username (Login ID)',
      password: 'Password',
      name: 'Full Name',
      role: 'System Role',
      company: 'Assigned Company',
      status: 'Status',
      searchPlaceholder: 'Search users by name, login or email...',
      deleteTitle: 'Delete user?',
      deleteQuestion: 'The account stops working: sign-in is refused at once, and the user\'s open sessions end within 15 minutes. The record stays in the database, hidden from the lists.',
      deleteIrreversible: 'This cannot be undone from the portal: a deleted user cannot be restored or edited here.',
      roles: {
        systemAdmin: 'System Administrator',
        companyHead: 'Company Head',
        companyManager: 'Company Manager',
        companyEmployee: 'Company Employee',
        auditor: 'Auditor',
      },
      formIncomplete: 'Fill in every required field',
      createFailed: 'Could not create the user',
      deleteFailed: 'Could not delete the user',
      editUser: 'Edit user',
      editDialogTitle: 'Edit user',
      editConfirmTitle: 'Save changes to user',
      editConfirmQuestion: 'The following will change. Role, company and status decide what this person can see and do — check the list before confirming.',
      editNothingChanged: 'Nothing changed — no request was sent.',
      editSessionsHint: 'A new role or company takes effect when the user\'s session next refreshes, within 15 minutes. Blocking refuses sign-in at once; open sessions end within 15 minutes.',
      noCompany: 'No company',
      companyRequired: 'A company role needs a company: choose one.',
      newPassword: 'New password (optional)',
      newPasswordHint: 'Leave empty to keep the current password. At least 12 characters with upper and lower case, a digit and a symbol.',
      issuedPasswordHint: 'The user will be asked to change this password at the first sign-in.',
      passwordChangePending: 'Password change pending',
      terminals: 'Terminals',
      terminalsHint: 'The employee sees only these terminals, their links and payments',
      terminalsRequired: 'Choose at least one terminal for the employee.',
      noCompanyTerminals: 'The company has no terminals',
      allCompanyTerminals: 'All company terminals',
      noTerminals: 'No terminals',
      noTerminalsHint: 'Sees nothing in the portal until terminals are assigned',
      passwordWillChange: 'The password will be replaced',
      ownPasswordSignsOut: 'All your sessions will end, this one too: sign in again with the new password',
      selfHint: 'You cannot change your own role or status here.',
      dmsLinks: 'DMS links',
      dmsLinksAllowed: 'allowed',
      dmsLinksForbidden: 'forbidden',
      dmsLinksSwitch: 'May create DMS links',
      dmsLinksHint: 'Without it the user creates SMS links only. A change takes effect within 15 minutes.',
      noDmsLinks: 'No DMS',
      updated: 'User updated',
      updateFailed: 'Could not update the user',
      statuses: { ACTIVE: 'Active', BLOCKED: 'Blocked' },
    },
    auditLogs: {
      title: 'Audit Logs',
      subtitle: 'Security audit trial of system actions and API calls.',
      user: 'User',
      action: 'Action',
      resource: 'Resource',
      timestamp: 'Timestamp',
      ip: 'IP Address',
      filterEntity: 'Filter by Resource Entity',
      searchPlaceholder: 'Search actor, action, entity ID, details or trace ID...',
      outcome: 'Outcome',
      outcomeSuccess: 'Success',
      outcomeDenied: 'Denied',
      outcomeUnresolved: 'Unresolved',
      outcomeDeclined: 'Declined by acquirer',
      filterOutcome: 'Filter by Outcome',
      dateFrom: 'From',
      dateTo: 'To',
      actions: {
        CREATE: 'Creation', READ: 'Read', UPDATE: 'Change', DELETE: 'Deletion', LIST: 'List', BLOCK: 'Block',
        UNBLOCK: 'Unblock', LOGIN: 'Sign-in', LOGOUT: 'Sign-out', LOCKOUT: 'Account lockout',
        RATE_LIMIT: 'Sign-in attempt limit', TOKEN_REUSE: 'Refresh token reuse', PASSWORD_CHANGE: 'Password change',
        CAPTURE: 'Capture', REFUND: 'Refund', CANCEL: 'Cancellation', RESOLVE: 'Outcome resolution',
        STATUS_CHANGE: 'Status change', START: 'Service start', STOP: 'Service stop',
        EXPORT: 'Export', VERIFY: 'Integrity check',
      },
      entities: {
        COMPANY: 'Company', TERMINAL: 'Terminal', USER: 'User', AUTH: 'Authentication', PAYMENT_LINK: 'Payment link',
        TRANSACTION: 'Transaction', PROVIDER_ORDER: 'Statement order', AUDIT_LOG: 'Audit journal', SERVICE: 'Service',
      },
      filterAction: 'Action',
      filterUser: 'User',
      filterCompany: 'Company',
      empty: 'No records match the filters.',
      attention: 'Requires attention',
      attentionHint: 'Unconfirmed money operations and journal gaps, refresh token reuse, account lockouts and sign-in attempt limits',
      exportAction: 'Export CSV',
      exportHint: 'All records matching the filters, oldest first, up to 100 000. The export is recorded in the journal.',
      exportFailed: 'Could not export the journal',
      integrityAction: 'Verify integrity',
      integrityTitle: 'Audit journal integrity',
      integrityIntact: 'The journal chain is intact: no record was changed, deleted or added around the portal.',
      integrityBroken: 'The journal chain is broken: records were changed in the database around the portal.',
      integrityChecked: 'Records checked',
      integrityHead: 'Last link',
      integrityStartedAt: 'Chain kept since',
      integrityNotCovered: 'Records before the chain (not covered)',
      integrityUnsealed: 'Records written around the portal',
      integrityMore: 'And more — see the journal record of this check.',
      integrityFailed: 'Could not verify the journal',
      integrityProblems: {
        RECORD_CHANGED: 'Record changed', RECORD_DELETED: 'Record deleted', LINKS_MISSING: 'Links missing',
        TIME_CHANGED: 'Record time changed', HEAD_MISMATCH: 'Chain end does not match', RECORDS_OUTSIDE_CHAIN: 'Records outside the chain',
      },
      detailsTitle: 'Audit record',
      entityId: 'Entity ID',
      company: 'Company',
      recordId: 'Record ID',
      traceId: 'Trace ID (service logs)',
      openTransaction: 'Open transaction',
      openPaymentLink: 'Open payment link',
      openEcomOrder: 'Open statement order',
    },
    auth: {
      unknownRole: 'The server returned a role this application does not recognise. Sign-in was refused — contact your administrator.',
      subtitle: 'Sign in to the merchant portal',
      emailLabel: 'Email',
      passwordLabel: 'Password',
      signIn: 'Sign in',
      fillBoth: 'Enter your email and password',
      invalidEmail: 'Enter a valid email address',
      networkError: 'The server is unreachable. Check the connection and try again.',
      authFailed: 'Sign-in failed',
      malformedResponse: 'The server answered without a session. Sign-in was refused — try again or contact your administrator.',
      idleSignedOut: 'You were signed out after 15 minutes of inactivity. Sign in again.',
      passwordChangeRequired: 'Your password was set by an administrator. Choose your own password to sign in.',
      newPasswordLabel: 'New password',
      confirmPasswordLabel: 'Repeat the new password',
      passwordRules: 'At least 12 characters: upper- and lower-case letters, a digit and a special character. Not one of your last 4 passwords.',
      passwordsDoNotMatch: 'The passwords do not match.',
      samePassword: 'The new password must differ from the current one.',
      changePasswordAndSignIn: 'Change password and sign in',
    },
    errors: {
      forbiddenTitle: 'Access denied',
      forbiddenText: 'Your role does not allow you to open this page.',
      notFoundTitle: 'Page not found',
      notFoundText: 'The address you requested does not exist.',
      unexpectedTitle: 'Something went wrong',
      unexpectedText: 'The page could not be displayed. Try again or go back to the home page.',
      goHome: 'Go to home page',
    },
  },
  az: {
    common: {
      save: 'Yadda saxla',
      cancel: 'Ləğv et',
      delete: 'Sil',
      edit: 'Düzəliş et',
      search: 'Axtarış...',
      filter: 'Filtr',
      actions: 'Əməliyyatlar',
      export: 'İxrac et',
      copy: 'Kopyala',
      close: 'Bağla',
      refresh: 'Yenilə',
      loading: 'Yüklənir...',
      yes: 'Bəli',
      no: 'Xeyr',
      all: 'Hamısı',
      status: 'Status',
      date: 'Tarix',
      back: 'Geri',
      copied: 'Panoya kopyalandı',
      success: 'Uğurlu',
      error: 'Xəta',
      warning: 'Xəbərdarlıq',
      info: 'Məlumat',
      confirm: 'Təsdiqlə',
      viewAll: 'Hamısına bax',
      details: 'Ətraflı',
      active: 'Aktiv',
      inactive: 'Deaktiv',
      overview: 'Xülasə',
      create: 'Yarat',
      update: 'Yenilə',
      loadFailed: 'Məlumatı yükləmək mümkün olmadı.',
      periods: { today: 'Bu gün', days7: '7 gün', days30: '30 gün', days90: '90 gün' },
    },
    nav: {
      home: 'Ana Səhifə',
      payByLink: 'Linklə Ödəniş',
      transactions: 'Əməliyyatlar Siyahısı',
      ecommerce: 'E-ticarət',
      pos: 'POS Terminallar',
      terminals: 'Terminallar',
      companies: 'Şirkətlər',
      users: 'İstifadəçilər',
      auditLogs: 'Audit Jurnalı',
      settings: 'Tənzimləmələr',
    },
    header: {
      title: 'Mərfəti Portalı (Merchant Portal)',
      notifications: 'Bildirişlər',
      markAllRead: 'Hamısını oxunmuş qeyd et',
      noNotifications: 'Oxunmamış bildiriş yoxdur',
      profile: 'Profilim',
      logout: 'Çıxış',
      logoutQuestion: 'Portaldan çıxmaq istəyirsiniz?',
      language: 'Dil',
      adminBadge: 'SİSTEM ADMİNİ',
    },
    home: {
      title: 'Merchant Paneli',
      subtitle: 'Şirkətin bütün terminalları üzrə kartla ödənişlər — provayderin çıxarışına görə, dövrdə yaradılmış sifarişlər üzrə.',
      subtitleAll: 'Portalın bütün şirkətləri üzrə kartla ödənişlər — provayderin çıxarışına görə, dövrdə yaradılmış sifarişlər üzrə.',
      openStatement: 'E-commerce çıxarışını aç',
      period: 'Dövr',
      loadFailed: 'İcmalı yükləmək mümkün olmadı.',
      empty: 'Bu dövrdə sifariş yoxdur.',
      metrics: {
        netRevenue: 'Xalis gəlir',
        netRevenueHint: 'Dövrün sifarişləri üzrə silinən məbləğ, geri qaytarmalar çıxılmaqla',
        paidCount: 'Ödənilmiş sifarişlər',
        paidCountHint: 'Sonradan geri qaytarılanlar daxil',
        refunded: 'Geri qaytarılıb',
        refundedHint: 'Dövrün sifarişləri üzrə geri qaytarmalar',
        averagePayment: 'Orta sifariş',
      },
      charts: {
        daily: 'Günlər üzrə gəlir',
        statuses: 'Statuslar üzrə sifarişlər',
        statusesHint: 'Statuslar E-commerce çıxarışında olduğu kimi',
        terminals: 'Gəlirə görə terminallar',
      },
      recentOrders: {
        title: 'Son sifarişlər',
        empty: 'Bu dövrdə sifariş yoxdur.',
      },
    },
    linkStats: {
      subtitle: 'Yalnız portalın ödəniş linkləri ilə edilən ödənişlər. Geri qaytarmalar edildiyi gün hesablanır.',
      loadFailed: 'Ödəniş linkləri üzrə statistikanı yükləmək mümkün olmadı.',
      empty: 'Bu dövrdə ödəniş linkləri ilə ödəniş yoxdur.',
      metrics: {
        netRevenue: 'Xalis gəlir',
        netRevenueHint: 'Alınan məbləğ, dövrdə edilən geri qaytarmalar çıxılmaqla',
        paidCount: 'Alınan ödənişlər',
        paidCountHint: 'Sonradan geri qaytarılanlar daxil',
        refunded: 'Geri qaytarılıb',
        refundedHint: 'Dövrdə edilən geri qaytarmalar, ödənişin tarixindən asılı olmayaraq',
        averagePayment: 'Orta ödəniş',
      },
      charts: {
        daily: 'Günlər üzrə gəlir',
        attempts: 'Ödəniş cəhdləri',
        attemptsHint: 'Ödəniş linkinin hər açılışı bir cəhddir; yarımçıq qalan cəhd uğursuz olur.',
        terminals: 'Gəlirə görə terminallar',
        links: 'Yaradılmış ödəniş linkləri',
        linksHint: 'Dövrdə yaradılmış linklərin cari statusu',
        funnel: 'Linklərin ödəniş hunisi',
        funnelHint: 'Dövrdə yaradılmış linklər və onların hansı mərhələyə çatdığı. Dövrdən sonra edilən ödənişlər də sayılır, ona görə son dövrün rəqəmləri hələ arta bilər. Ödənilib — məbləğ kartdan tutulub və ya bloklanıb.',
        funnelSteps: { created: 'Yaradılıb', opened: 'Açılıb', paymentStarted: 'Kart göndərilib', paid: 'Ödənilib' },
        timeToPay: 'Ödənişə qədər vaxt',
        timeToPayHint: 'Dövrün birdəfəlik linkləri: linkin yaradılmasından ödənilmiş cəhdin başlanmasına qədər.',
        median: 'Median',
        paidLinks: 'Ödənilmiş linklər',
        noPaidLinks: 'Dövrdə ödənilmiş birdəfəlik link yoxdur',
        timeToPayRanges: { UP_TO_1_HOUR: '1 saatdan az', UP_TO_1_DAY: '1–24 saat', UP_TO_7_DAYS: '1–7 gün', OVER_7_DAYS: '7 gündən çox' },
        units: { lessThanMinute: '< 1 dəq', minute: 'dəq', hour: 'saat', day: 'gün' },
      },
    },
    settings: {
      title: 'Tənzimləmələr',
      subtitle: 'Şirkətinizin adı və interfeys dili. Qalan bölmələr öz səhifələrində idarə olunur.',
      saveSuccess: 'Tənzimləmələr uğurla yadda saxlanıldı!',
      saveChanges: 'Dəyişiklikləri Yadda Saxla',
      account: {
        title: 'Biznes Məlumatları',
        merchantName: 'Təşkilat / Şirkət Adı',
        merchantEmail: 'Hesabın E-poçtu',
        emailReadOnly: 'Daxil olduğunuz hesabdan götürülür, burada dəyişdirilmir.',
        nameReadOnly: 'Şirkətin adını yalnız sistem administratoru dəyişə bilər.',
        noCompany: 'Hesabınız hər hansı şirkətə bağlı deyil. Şirkətlər «Şirkətlər» səhifəsində idarə olunur.',
        loadFailed: 'Şirkət məlumatlarını yükləmək mümkün olmadı.',
        saveFailed: 'Dəyişiklikləri yadda saxlamaq mümkün olmadı.',
      },
      display: {
        title: 'İnterfeys',
        language: 'Sistem Dili',
      },
    },
    payByLink: {
      title: 'Linklə Ödəniş',
      subtitle: 'Müştəriləriniz üçün instant ödəniş linkləri yaradın, SMS, E-poçt və ya messencerlər vasitəsilə paylaşın.',
      tabs: {
        links: 'Linklər',
        stats: 'Statistika',
      },
      createButton: 'Ödəniş Linki Yarat',
      createTitle: 'Yeni Ödəniş Linki Yarat',
      createSubtitle: 'Müştəridən ödəniş qəbul etmək üçün təhlükəsiz link hazırlayın.',
      linkDetails: 'Link Təfərrüatları',
      terminalSelect: 'Terminal Seçin *',
      terminalHelper: 'Ödənişin keçəcəyi ekvayrinq terminalı',
      amountLabel: 'Ödəniş Məbləği *',
      currencyLabel: 'Valyuta',
      descriptionLabel: 'Ödəniş Təsviri / Sifariş Nömrəsi *',
      customerNameLabel: 'Müştərinin Adı',
      customerEmailLabel: 'Müştərinin E-poçtu',
      customerPhoneLabel: 'Müştərinin Telefonu',
      customerPhoneHint: 'Azərbaycan nömrəsi: +994 və 9 rəqəm',
      customerPhoneInvalid: 'Müştərinin telefonu Azərbaycan nömrəsi olmalıdır: +994 və 9 rəqəm, məsələn +994 70 330 10 25',
      usageTypeLabel: 'İstifadə Növü',
      singleUse: 'Bir dəfəlik (Tək ödəniş)',
      multipleUse: 'Çox dəfəlik (Təkrar istifadə)',
      maxUsesLabel: 'Maksimum İcazə Verilən Ödəniş Sayı',
      expirationLabel: 'Linkin Bitmə Vaxtı',
      paymentTypeLabel: 'Ödəniş Tutulma Növü',
      createLinkAction: 'Ödəniş Linkini Genersiya Et',
      cancelLinkAction: 'Ödəniş Linkini Ləğv Et',
      shareDialogTitle: 'Ödəniş Linkini Paylaş',
      cancelConfirmTitle: 'Ödəniş linki ləğv edilsin?',
      cancelConfirmText: 'Bu ödəniş linkini ləğv etmək istədiyinizdən əminsiniz? Müştərilər bundan sonra bu linklə ödəniş edə bilməyəcəklər.',
      keepLink: 'Linki Saxla',
      linkCancelledSuccess: 'Ödəniş linki uğurla ləğv edildi',
      linkCancelFailed: 'Ödəniş linkini ləğv etmək mümkün olmadı',
      paid: 'Ödənilib',
      noActiveTerminals: 'Aktiv terminal yoxdur: Terminallar səhifəsində terminal qeydiyyatdan keçirin və ya blokunu açın. Bloklanmış terminal yeni link vermir.',
      customerNotSpecified: 'Göstərilməyib',
      empty: 'Ödəniş linki yoxdur',
      share: 'Paylaş',
      copyLink: 'Linki kopyala',
      sendEmail: 'E-poçtla göndər',
      sendWhatsApp: 'WhatsApp ilə göndər',
      qrCode: 'QR kod',
      qrHint: 'Ödəniş səhifəsini açmaq üçün telefonun kamerasını koda yönəldin',
      downloadQr: 'PNG yüklə',
      createdTitle: 'Ödəniş linki yaradıldı. Onu müştəri ilə paylaşın.',
      linkLabel: 'Ödəniş linki',
      done: 'Hazırdır',
      generating: 'Yaradılır…',
      invalidAmount: 'Düzgün məbləğ daxil edin',
      descriptionRequired: 'Təsvir və ya sifariş nömrəsi əlavə edin',
      invalidMaxUses: 'Maksimum ödəniş sayı ən azı 1 olan tam ədəd olmalıdır',
      createFailed: 'Ödəniş linkini yaratmaq mümkün olmadı',
      smsHint: 'SMS: vəsait müştəri ödəyən kimi tutulur.',
      dmsHint: 'DMS: vəsait kartda bloklanır; silinməsi əməliyyat kartından edilir.',
      dmsForbiddenUser: 'DMS linkləri yaratmağa icazəniz yoxdur. Şirkət rəhbərinə və ya administratora müraciət edin.',
      dmsForbiddenTerminal: 'Bu terminalda DMS linkləri qadağandır. Administratora müraciət edin.',
      maxUsesHint: 'Bu qədər uğurlu ödənişdən sonra link bağlanır',
      descriptionHint: 'Ödəniş səhifəsində müştəriyə göstərilir',
      customerSection: 'Müştəri',
      linkSettings: 'Link parametrləri',
      messageText: 'Zəhmət olmasa ödənişi bu link vasitəsilə tamamlayın:',
      emailSubject: 'Ödəniş sorğusu',
      expiry: { h1: '1 saat', h24: '24 saat', h72: '3 gün', d7: '7 gün', d30: '30 gün' },
      statuses: {
        ACTIVE: 'Aktiv',
        EXPIRED: 'Müddəti bitib',
        COMPLETED: 'Tamamlanıb',
        CANCELED: 'Ləğv edilib',
        SUSPENDED: 'Dayandırılıb (terminal bloklanıb)',
      },
      table: {
        linkId: 'Link ID / Kod',
        customer: 'Müştəri',
        amount: 'Məbləğ',
        type: 'Növ',
        status: 'Status',
        usage: 'İstifadə',
        created: 'Yaradıldı',
        expires: 'Bitmə vaxtı',
        actions: 'Əməliyyatlar',
      },
    },
    payByLinkDetail: {
      backToLinks: 'Linklə Ödənişə Geri Dön',
      title: 'Ödəniş Linkinin Ətraflı Məlumatları',
      subtitle: 'Ödəniş linkinin parametrlərinə, icra tarixçəsinə və DMS statusuna baxın.',
      copyUrl: 'Link URL-ni Kopyala',
      cancelLink: 'Linki Ləğv Et',
      quickActions: 'Sürətli Əməliyyatlar',
      createSameLink: 'Eyni Məlumatlarla Yeni Link Yarat',
      tabs: {
        overview: 'Xülasə',
        transactions: 'Əməliyyatlar',
        settings: 'Link Tənzimləmələri',
      },
      summary: {
        linkInfo: 'Link Parametrləri',
        shortCode: 'Qısa Kod',
        originalUrl: 'Ödəniş URL-i',
        redirectUrl: 'Yönləndirmə URL-i',
        dmsStatus: 'DMS Statusu',
        payerIp: 'Ödəyicinin IP-si',
        sentVia: 'Göndərildi',
        terminal: 'Təyin Edilmiş Terminal',
        refundedOfUsed: 'onlardan geri qaytarılıb',
      },
      timeline: {
        title: 'İcra Tarixçəsi',
        created: 'Link Yaradıldı',
        paid: 'Ödəniş Təsdiqləndi',
        finalized: 'DMS Ödənişi Tamamlandı',
      },
    },
    transactions: {
      title: 'Əməliyyatlar',
      subtitle: 'Terminallarınızdan keçən bütün ödəniş jurnalını nəzərdən keçirin.',
      filters: {
        dateRange: 'Tarix Aralığı',
        search: 'Provayder Sifariş ID, RID by merchant, müştəri, e-poçt və ya əməliyyat ID üzrə axtarış...',
        paymentMethod: 'Ödəniş Üsulu',
        terminal: 'Terminal',
        clearFilters: 'Filtrləri Sıfırla',
      },
      statuses: {
        PENDING: 'Gözləmədə',
        AUTHORIZED: 'Avtorizasiya edilib',
        SUCCESS: 'Uğurlu',
        FAILED: 'Uğursuz',
        PARTIALLY_REFUNDED: 'Qismən qaytarılıb',
        REFUNDED: 'Qaytarılıb',
      },
      columns: {
        providerOrderId: 'Provayder Sifariş ID',
        ridByMerchant: 'RID by merchant',
        id: 'Əməliyyat ID',
        date: 'Tarix və Vaxt',
        amount: 'Məbləğ',
        customer: 'Müştəri',
        status: 'Status',
        rrn: 'RRN',
        method: 'Üsul',
        terminal: 'Terminal',
        terminalLogin: 'Terminal Logini',
        actions: 'Ətraflı',
      },
      detail: {
        title: 'Əməliyyat Təfərrüatları',
        backToTransactions: 'Əməliyyatlara Geri Dön',
        refundAction: 'Ödənişi Qaytar (Refund)',
        refundTitle: 'Məbləğin Qaytarılması',
        refundAmount: 'Qaytarılan məbləğ',
        confirmRefund: 'Qaytarılmanı Təsdiqlə',
        completeAction: 'Tamamla',
        completeTitle: 'DMS Əməliyyatını Tamamla',
        captureExplains: 'Müştərinin kartında bloklanmış məbləğ silinərək hesabınıza köçürüləcək. Bu əməliyyatı geri qaytarmaq mümkün deyil.',
        captureAmount: 'Silinəcək məbləğ',
        confirmCapture: 'Məbləği Sil',
        refundQuestion: 'Bu əməliyyat üzrə məbləği qaytarmaq istəyirsiniz? Vəsait müştərinin kartına qaytarılacaq və bunu geri almaq mümkün olmayacaq.',
        refundReason: 'Səbəb (istəyə görə)',
        refundReasonHint: 'Audit jurnalına düşür; müştəri və ekvayer onu görmür.',
        keepTransaction: 'Əməliyyatı Saxla',
        customerInfo: 'Müştəri Məlumatları',
        paymentInfo: 'Ödəniş Bölgüsü',
        technicalInfo: 'Texniki Əlaqə Məlumatı',
        approvalCode: 'Təsdiq Kodu (Approval Code)',
        unresolvedTitle: 'Nəticə ekvayer tərəfindən təsdiqlənmədi',
        unresolvedHint: 'Əməliyyat artıq keçmiş ola bilər. Sistem administratoru onu provayderlə tutuşdurub nəticəni qeyd edənə qədər əməliyyat bağlı qalır.',
        checkStatusAction: 'Statusu yoxla',
        statusChecked: 'Əməliyyatın statusu yeniləndi.',
        checkStatusFailed: 'Statusu yoxlamaq mümkün olmadı. Yenidən cəhd edin.',
        refundableLeft: 'Qaytarıla bilən qalıq',
        amountLabel: 'Məbləğ',
        amountUpTo: 'Maksimum',
        amountWholeRefund: 'Bütün qalıq',
        amountWholeCapture: 'Bütün məbləğ',
        amountInvalid: 'Sıfırdan böyük, ən çox iki onluq rəqəmli məbləğ daxil edin',
        amountAboveMax: 'Məbləğ mövcud olandan çoxdur',
        refundRemains: 'Bundan sonra qaytarıla bilən qalıq',
        captureReleased: 'Silinməyəcək',
        capturePartialHint: 'Blok bir dəfə silinir: portal qalığı sonra silməyəcək, bank onu öz müddətində azad edəcək.',
        actionsTitle: 'Əməliyyatlar',
        moneyReasons: {
          NO_RIGHTS: 'Rolunuz bu əməliyyata icazə vermir',
          TERMINAL_NOT_IN_PORTAL: 'Bu əməliyyatın terminalı portalda qeydiyyatda deyil',
          NO_PROVIDER_CREDENTIALS: 'Terminalın şirkətinin provayder loqini yoxdur',
          FULLY_REFUNDED: 'Tam qaytarılıb',
          CAPTURE_FIRST: 'Əvvəlcə holdu silin',
          ALREADY_CAPTURED: 'Hold artıq silinib',
          OUTCOME_UNKNOWN: 'Əvvəlki əməliyyatın nəticəsi məlum deyil',
          IN_PROGRESS: 'Bu ödəniş üzrə başqa əməliyyat gedir',
          other: 'Bu əməliyyat əlçatan deyil',
        },
        unresolvedInProgressTitle: 'Əməliyyat gedir',
        unresolvedInProgressHint: 'Ekvayerin cavabı hələ qeyd olunmayıb. Bir az gözləyin və səhifəni yeniləyin.',
        unresolvedKindRefund: 'Qaytarma',
        unresolvedKindCapture: 'Silinmə',
        unresolvedStartedAt: 'Göndərilib',
        unresolvedStartedBy: 'Kim',
        resolveExecuted: 'Keçib',
        resolveNotExecuted: 'Keçməyib',
        resolveExecutedTitle: 'Əməliyyat keçmiş kimi qeyd edilsin?',
        resolveExecutedQuestion: 'Əməliyyat ekvayerin identifikatorları olmadan təsdiqlənmiş kimi yazılacaq. Bunu yalnız provayderlə tutuşdurduqdan sonra edin.',
        resolveNotExecutedTitle: 'Əməliyyat keçməmiş kimi qeyd edilsin?',
        resolveNotExecutedQuestion: 'Təkrar qadağası götürülür, ödənişdə heç nə dəyişmir. Bunu yalnız provayderlə tutuşdurduqdan sonra edin.',
        resolveFailed: 'Nəticəni qeyd etmək alınmadı',
        eventCreated: 'Əməliyyat açıldı',
        eventCaptured: 'Blok məbləği silindi',
        eventRefunded: 'Qaytarma təsdiqləndi',
        historyEmpty: 'Bu əməliyyat üzrə qeydə alınmış hadisə yoxdur.',
        identifiers: 'Ödəniş identifikatorları',
        providerOrderId: 'Provayder Sifariş ID',
        ridByMerchant: 'RID by merchant',
        clientIp: 'Müştərinin IP Ünvanı',
      },
    },
    ecommerce: {
      title: 'E-ticarət çıxarışı',
      subtitle: 'Terminallarınızın provayder şlüzündən ödənişləri: hər sətir bütün tarixçəsi ilə bir sifarişdir.',
      periodFrom: 'Yaradılma tarixindən',
      periodTo: 'Yaradılma tarixinədək',
      periodHint: 'Sifarişlər yaradılma tarixinə görə seçilir. Dövr mütləqdir və 92 gündən uzun ola bilməz.',
      periodInvalid: 'Dövrün sonu başlanğıcından gec olmalıdır.',
      periodTooLong: 'Dövr 92 gündən uzun ola bilməz.',
      terminals: 'Terminallar',
      allTerminals: 'Bütün terminallar',
      search: 'Sifariş nömrəsi, RID by merchant və ya RRN (dəqiq uyğunluq)',
      minAmount: 'Minimum məbləğ',
      maxAmount: 'Maksimum məbləğ',
      loadMore: 'Daha çox göstər',
      loaded: 'Yüklənmiş sifarişlər',
      loadFailed: 'Çıxarışı yükləmək mümkün olmadı.',
      empty: 'Seçilmiş dövr və filtrlər üzrə sifariş yoxdur.',
      exportLoaded: 'Yüklənənləri ixrac et',
      statusFilter: 'Status',
      allStatuses: 'Bütün statuslar',
      paymentTypeFilter: 'Ödəniş növü',
      allPaymentTypes: 'Bütün növlər',
      paymentTypes: { SMS: 'SMS (tək mesaj)', DMS: 'DMS (bloklama və silinmə)' },
      noMatchesYet: 'Yoxlanılmış sifarişlər arasında bu statusda sifariş yoxdur. Axtarışı davam etdirmək üçün daha çox yükləyin.',
      applyFilters: 'Tətbiq et',
      resetFilters: 'Sıfırla',
      filtersChanged: 'Filtrlər dəyişib — çıxarışı yeniləmək üçün «Tətbiq et» düyməsini basın.',
      stats: {
        orders: 'Sifarişlər',
        captured: 'Silinib',
        refunded: 'Qaytarılıb',
      },
      statuses: {
        PENDING: 'Gözləmədə',
        AUTHORIZED: 'Avtorizasiya edilib',
        SUCCESS: 'Uğurlu',
        PARTIALLY_PAID: 'Qismən ödənilib',
        FAILED: 'Uğursuz',
        PARTIALLY_REFUNDED: 'Qismən qaytarılıb',
        REFUNDED: 'Qaytarılıb',
        CANCELED: 'Ləğv edilib',
      },
      operationKinds: {
        AUTHORIZATION: 'Avtorizasiya (hold)',
        CAPTURE: 'Silinmə',
        PURCHASE: 'Alış',
        REVERSAL: 'Reversal',
        REFUND: 'Geri qaytarma',
        UNKNOWN: 'Tanınmayan əməliyyat',
      },
      columns: {
        createdAt: 'Yaradılıb',
        orderId: 'Provayder Sifariş ID',
        ridByMerchant: 'RID by merchant',
        card: 'Kart',
        amount: 'Sifariş məbləği',
        captured: 'Silinib',
        status: 'Status',
        terminal: 'Terminal',
      },
      detail: {
        back: 'Çıxarışa qayıt',
        openInNewTab: 'Yeni tabda aç',
        title: 'Sifariş',
        notFound: 'Sifariş tapılmadı: mövcud deyil, başqa merçanta aiddir və ya hələ tamamlanmayıb.',
        loadFailed: 'Sifarişi yükləmək mümkün olmadı.',
        identifiers: 'İdentifikatorlar',
        money: 'Məbləğlər',
        orderAmount: 'Sifariş məbləği',
        captured: 'Silinib',
        refunded: 'Qaytarılıb',
        payment: 'Ödəniş',
        card: 'Kart',
        terminal: 'Terminal',
        providerStatus: 'Provayderdə status',
        createdAt: 'Yaradılıb',
        lastOperationAt: 'Son əməliyyat',
        declineCode: 'İmtina kodu',
        description: 'Təsvir',
        operations: 'Əməliyyatlar',
        operationAt: 'Vaxt',
        kind: 'Əməliyyat',
        codes: 'Provayder kodları',
        result: 'Nəticə',
        amount: 'Məbləğ',
        clearAmount: 'Silinmiş məbləğ',
        rrn: 'RRN',
        tranId: 'Tranzaksiya ID',
        noOperations: 'Sifariş üzrə əməliyyat yoxdur.',
      },
    },
    terminals: {
      title: 'Terminallar',
      subtitle: 'POS və E-ticarət ekvayrinq terminallarını idarə edin.',
      addTerminal: 'Terminal Əlavə Et',
      registerAction: 'Terminalı Qeydiyyatdan Keçir',
      editTerminal: 'Terminalı Redaktə Et',
      createDialogTitle: 'Yeni Terminal Yarat',
      editDialogTitle: 'Terminal Parametrlərini Redaktə Et',
      name: 'Terminalın Adı',
      providerTerminal: 'Provayder terminalı',
      providerTerminalHint: 'Yalnız şirkət logininə bağlı merçantların hələ əlavə edilməmiş terminalları. Ad, login və terminal nömrəsi provayder kataloqundan gəlir; terminalın şifrəsi yoxdur.',
      providerTerminalEmpty: 'Kataloqda bu şirkətin merçantlarının boş terminalı yoxdur. Kataloqu yeniləyin; terminal yenə görünmürsə, o provayderdə aktiv deyil, artıq əlavə edilib və ya merçantı şirkət logininə bağlı deyil.',
      providerTerminalLoadFailed: 'Provayder kataloqunu yükləmək mümkün olmadı.',
      syncDirectory: 'Kataloqu yenilə',
      syncApplied: 'Kataloq yeniləndi. Alınan terminallar',
      syncSkipped: 'Kataloq yenilənmədi',
      login: 'Mərfəti Terminal Logini',
      terminal: 'Terminal',
      testAction: 'Test',
      checkOk: 'Şirkətin məlumatları qəbul edildi, ödəniş yaratmaq olar',
      checkInvalid: 'Şirkətin logini və ya şifrəsi yanlışdır',
      checkRejected: 'Məlumatlar qəbul edildi, lakin ekvayer ödənişə icazə vermədi',
      checkUnreachable: 'Ekvayer cavab vermədi — terminal barədə məlumat yoxdur',
      company: 'Təyin Olunmuş Şirkət',
      status: 'Status',
      statuses: {
        ACTIVE: 'Aktiv',
        BLOCKED: 'Bloklanıb',
      },
      blockAction: 'Terminalı blokla',
      unblockAction: 'Bloku aç',
      blockExplains: 'Bloklanmış terminal yeni ödənişləri qəbul etmir: aktiv linkləri dayandırılır, yenisi yaradıla bilmir. Mövcud ödənişlər üzrə geri qaytarma, DMS bağlanışı və status yoxlaması işləməyə davam edir. Blok açılanda linklər geri qayıdır.',
      blockLinksAffected: 'Dayandırılacaq aktiv linklər',
      blockLinksUnknown: 'Linklərin sayını hesablamaq mümkün olmadı — bloklama bu terminalın bütün aktiv linklərini dayandırır.',
      unblockExplains: 'Blokdan çıxarma terminala ödəniş qəbulunu qaytarır: dayandırılmış linklər yenidən aktiv olur, blok müddətində vaxtı bitmiş olanlar istisna.',
      unblockLinksAffected: 'Yenidən aktiv olacaq dayandırılmış linklər',
      editConfirmTitle: 'Terminalın dəyişiklikləri yadda saxlanılsın',
      editConfirmQuestion: 'Aşağıdakılar dəyişəcək. Bu formada ad və sahib şirkət yan-yana durur — təsdiqləməzdən əvvəl siyahını yoxlayın.',
      editNothingChanged: 'Dəyişiklik yoxdur — sorğu göndərilmədi.',
      searchPlaceholder: 'Ad, ID və ya login üzrə axtarış...',
      companyHint: 'Terminalın aid olduğu şirkət',
      nameFromProvider: 'Ad provayderin kataloqundan gəlir: terminalın adını provayderdə dəyişin',
      dmsColumn: 'DMS',
      dmsAllowed: 'İcazəlidir',
      dmsForbidden: 'Qadağandır',
      dmsSwitch: 'DMS linklərinə icazə verilir',
      dmsSwitchHint: 'DMS olmadan terminalda yalnız SMS linkləri yaradılır. Mövcud DMS linkləri işləməyə davam edir.',
      formIncomplete: 'Bütün məcburi sahələri doldurun',
      created: 'Terminal qeydiyyatdan keçdi',
      createFailed: 'Terminalı qeydiyyatdan keçirmək mümkün olmadı',
      updated: 'Terminal yeniləndi',
      updateFailed: 'Terminalı yeniləmək mümkün olmadı',
      checkFailed: 'Terminalı yoxlamaq mümkün olmadı',
      blockedNotice: 'Terminal bloklandı: yeni ödənişlər qəbul edilmir, aktiv linklər dayandırıldı',
      unblockedNotice: 'Terminalın bloku açıldı: dayandırılmış linklər yenidən aktivdir (müddəti bitənlər istisna olmaqla)',
      statusChangeFailed: 'Terminalın statusunu dəyişmək mümkün olmadı',
      empty: 'Hələ terminal yoxdur',
    },
    companies: {
      title: 'Şirkətlər',
      subtitle: 'Ticarət şirkətlərini və hüquqi şəxsləri idarə edin.',
      addCompany: 'Şirkət Əlavə Et',
      createDialogTitle: 'Yeni Ticarət Şirkəti',
      companyId: 'Şirkət Kodu / ID',
      name: 'Şirkətin Hüquqi Adı',
      status: 'Status',
      searchPlaceholder: 'Şirkəti ID və ya ada görə axtarın...',
      deleteTitle: 'Şirkət silinsin?',
      deleteQuestion: 'Şirkət portalın bütün siyahılarından yox olacaq. Onun terminalları, ödəniş linkləri və əməliyyatları bazada qalır.',
      deleteIrreversible: 'Bunu portaldan geri qaytarmaq mümkün deyil: silinmiş şirkəti burada bərpa etmək olmur.',
      providerLogin: 'Provayder logini',
      providerLoginHint: 'Provayder kataloqundan multimerçant logini: yalnız aktiv merçantları olan, başqa şirkətin istifadə etmədiyi aktiv loginlər. İndicə yaradılıbsa — kataloqu yeniləyin.',
      providerLoginEmpty: 'Kataloqda boş multimerçant logini yoxdur. Kataloqu yeniləyin; login yenə görünmürsə, o provayderdə yaradılmayıb, aktiv deyil, aktiv merçantı yoxdur və ya başqa şirkətə aiddir.',
      loginsSyncApplied: 'Kataloq yeniləndi. Alınan multimerçant loginləri',
      loginsSyncSkipped: 'Login kataloqu yenilənmədi',
      providerPassword: 'Provayder şifrəsi',
      providerPasswordHint: 'Şifrələnmiş saxlanılır. Sonra onu görmək olmaz — yalnız əvəz etmək.',
      newProviderPassword: 'Yeni provayder şifrəsi (istəyə görə)',
      newProviderPasswordHint: 'Cari şifrəni saxlamaq üçün boş buraxın',
      providerPasswordReplaced: 'Provayder şifrəsi əvəz olunacaq',
      taxId: 'VÖEN',
      taxIdHint: '10 rəqəm, ödəyicinin çekində çap olunur. Dəyişmək olar, silmək olmaz.',
      taxIdInvalid: 'VÖEN düz 10 rəqəmdən ibarət olmalıdır',
      editNothingChanged: 'Heç nə dəyişməyib — sorğu göndərilmədi.',
      formIncomplete: 'Bütün məcburi sahələri doldurun',
      created: 'Şirkət yaradıldı',
      createFailed: 'Şirkəti yaratmaq alınmadı',
      editCompany: 'Şirkəti redaktə et',
      editConfirmTitle: 'Şirkətdəki dəyişikliklər saxlanılsın?',
      editConfirmQuestion: 'Bu şirkətdə aşağıdakılar dəyişəcək. Təsdiqləməzdən əvvəl siyahını yoxlayın.',
      credentialsWarning: 'Şirkətin provayderə bütün sorğuları — yeni ödənişlər, silinmələr, geri qaytarmalar və status yoxlamaları — yeni məlumatlarla gedəcək. Yanlış məlumatlar şirkətin ödənişlərini dayandırar.',
      statusWarning: 'Status sorğu kitabçasında qeyd və audit jurnalında yazıdır. İşçilərin girişini, link yaradılmasını və ödənişlərin qəbulunu dayandırmır — ödənişləri dayandırmaq üçün terminalları bloklayın.',
      updated: 'Şirkət yeniləndi',
      updateFailed: 'Şirkəti yeniləmək alınmadı',
    },
    users: {
      title: 'İstifadəçilər',
      subtitle: 'Portal istifadəçilərini, rolları və icazələri idarə edin.',
      addUser: 'İstifadəçi Əlavə Et',
      createDialogTitle: 'Portal İstifadəçisi Yarat',
      username: 'İstifadəçi Adı (Login)',
      password: 'Şifrə',
      name: 'Ad və Soyad',
      role: 'Sistem Rolu',
      company: 'Təyin Olunmuş Şirkət',
      status: 'Status',
      searchPlaceholder: 'Ad, login və ya e-poçt üzrə axtarış...',
      deleteTitle: 'İstifadəçi silinsin?',
      deleteQuestion: 'Hesab işləməyi dayandırır: giriş dərhal rədd edilir, istifadəçinin açıq sessiyaları 15 dəqiqə ərzində bağlanır. Qeyd bazada qalır, siyahılardan gizlədilir.',
      deleteIrreversible: 'Bunu portaldan geri qaytarmaq mümkün deyil: silinmiş istifadəçini burada nə bərpa etmək, nə də redaktə etmək olar.',
      roles: {
        systemAdmin: 'Sistem Administratoru',
        companyHead: 'Şirkət Rəhbəri',
        companyManager: 'Şirkət Meneceri',
        companyEmployee: 'Şirkət Əməkdaşı',
        auditor: 'Auditor',
      },
      formIncomplete: 'Bütün məcburi sahələri doldurun',
      createFailed: 'İstifadəçini yaratmaq mümkün olmadı',
      deleteFailed: 'İstifadəçini silmək mümkün olmadı',
      editUser: 'İstifadəçini redaktə et',
      editDialogTitle: 'İstifadəçini redaktə et',
      editConfirmTitle: 'İstifadəçidəki dəyişiklikləri saxla',
      editConfirmQuestion: 'Aşağıdakılar dəyişəcək. Rol, şirkət və status bu şəxsin nəyi görə və edə biləcəyini müəyyən edir — təsdiqləməzdən əvvəl siyahını yoxlayın.',
      editNothingChanged: 'Heç nə dəyişməyib — sorğu göndərilmədi.',
      editSessionsHint: 'Yeni rol və ya şirkət istifadəçinin sessiyası növbəti dəfə yeniləndikdə, 15 dəqiqə ərzində qüvvəyə minir. Bloklama girişi dərhal dayandırır, açıq sessiyalar 15 dəqiqə ərzində bağlanır.',
      noCompany: 'Şirkətsiz',
      companyRequired: 'Şirkət rolu üçün şirkət seçin.',
      newPassword: 'Yeni parol (istəyə bağlı)',
      newPasswordHint: 'Cari parolu saxlamaq üçün boş buraxın. Ən azı 12 simvol: böyük və kiçik hərf, rəqəm və xüsusi simvol.',
      issuedPasswordHint: 'İstifadəçi ilk girişdə bu parolu dəyişməli olacaq.',
      passwordChangePending: 'Parol dəyişikliyi gözlənilir',
      terminals: 'Terminallar',
      terminalsHint: 'Əməkdaş yalnız bu terminalları, onların linklərini və ödənişlərini görür',
      terminalsRequired: 'Əməkdaş üçün ən azı bir terminal seçin.',
      noCompanyTerminals: 'Şirkətin terminalı yoxdur',
      allCompanyTerminals: 'Şirkətin bütün terminalları',
      noTerminals: 'Terminal yoxdur',
      noTerminalsHint: 'Terminal təyin olunana qədər portalda heç nə görmür',
      passwordWillChange: 'Parol dəyişdiriləcək',
      ownPasswordSignsOut: 'Bütün sessiyalarınız, bu da daxil olmaqla, bitəcək: yeni parolla yenidən daxil olun',
      selfHint: 'Öz rolunuzu və statusunuzu burada dəyişə bilməzsiniz.',
      dmsLinks: 'DMS linkləri',
      dmsLinksAllowed: 'icazəlidir',
      dmsLinksForbidden: 'qadağandır',
      dmsLinksSwitch: 'DMS linkləri yarada bilər',
      dmsLinksHint: 'İcazə olmadan istifadəçi yalnız SMS linkləri yaradır. Dəyişiklik 15 dəqiqə ərzində qüvvəyə minir.',
      noDmsLinks: 'DMS yoxdur',
      updated: 'İstifadəçi yeniləndi',
      updateFailed: 'İstifadəçini yeniləmək mümkün olmadı',
      statuses: { ACTIVE: 'Aktiv', BLOCKED: 'Bloklanıb' },
    },
    auditLogs: {
      title: 'Audit Jurnalı',
      subtitle: 'Sistem əməliyyatlarının və API çağırışlarının təhlükəsizlik jurnalı.',
      user: 'İstifadəçi',
      action: 'Əməliyyat',
      resource: 'Resurs',
      timestamp: 'Tarix və Vaxt',
      ip: 'IP Ünvanı',
      filterEntity: 'Resurs Əsasında Filtr',
      searchPlaceholder: 'İstifadəçi, əməliyyat, ID, təfərrüat və ya trace ID üzrə axtarış...',
      outcome: 'Nəticə',
      outcomeSuccess: 'Uğurlu',
      outcomeDenied: 'Rədd edilib',
      outcomeUnresolved: 'Təsdiqlənməyib',
      outcomeDeclined: 'Ekvayer rədd etdi',
      filterOutcome: 'Nəticə üzrə filtr',
      dateFrom: 'Tarixdən',
      dateTo: 'Tarixədək',
      actions: {
        CREATE: 'Yaradılma', READ: 'Oxuma', UPDATE: 'Dəyişiklik', DELETE: 'Qeydin silinməsi', LIST: 'Siyahı',
        BLOCK: 'Bloklama', UNBLOCK: 'Blokdan çıxarma', LOGIN: 'Giriş', LOGOUT: 'Çıxış', LOCKOUT: 'Hesabın bloklanması',
        RATE_LIMIT: 'Giriş cəhdləri limiti', TOKEN_REUSE: 'Refresh-tokenin təkrarı', PASSWORD_CHANGE: 'Şifrənin dəyişdirilməsi',
        CAPTURE: 'Vəsaitin silinməsi', REFUND: 'Geri qaytarma', CANCEL: 'Ləğv', RESOLVE: 'Əməliyyatın nəticəsi',
        STATUS_CHANGE: 'Statusun dəyişməsi', START: 'Servisin işə salınması', STOP: 'Servisin dayandırılması',
        EXPORT: 'İxrac', VERIFY: 'Bütövlük yoxlaması',
      },
      entities: {
        COMPANY: 'Şirkət', TERMINAL: 'Terminal', USER: 'İstifadəçi', AUTH: 'Autentifikasiya', PAYMENT_LINK: 'Ödəniş linki',
        TRANSACTION: 'Əməliyyat', PROVIDER_ORDER: 'Çıxarış sifarişi', AUDIT_LOG: 'Audit jurnalı', SERVICE: 'Servis',
      },
      filterAction: 'Əməliyyat növü',
      filterUser: 'İstifadəçi',
      filterCompany: 'Şirkət',
      empty: 'Filtrlərə uyğun qeyd yoxdur.',
      attention: 'Diqqət tələb edir',
      attentionHint: 'Təsdiqlənməmiş pul əməliyyatları və jurnal fasilələri, refresh-tokenin təkrarı, hesabın bloklanması və giriş cəhdləri limiti',
      exportAction: 'CSV ixracı',
      exportHint: 'Filtrlərə uyğun bütün qeydlər, köhnələr əvvəl, 100 000-ə qədər. İxrac jurnala yazılır.',
      exportFailed: 'Jurnalı ixrac etmək mümkün olmadı',
      integrityAction: 'Bütövlüyü yoxla',
      integrityTitle: 'Audit jurnalının bütövlüyü',
      integrityIntact: 'Jurnal zənciri bütövdür: heç bir qeyd portaldan kənar dəyişdirilməyib, silinməyib və əlavə olunmayıb.',
      integrityBroken: 'Jurnal zənciri pozulub: qeydlər verilənlər bazasında portaldan kənar dəyişdirilib.',
      integrityChecked: 'Yoxlanılan qeydlər',
      integrityHead: 'Son halqa',
      integrityStartedAt: 'Zəncir aparılır',
      integrityNotCovered: 'Zəncirdən əvvəlki qeydlər (əhatə olunmur)',
      integrityUnsealed: 'Portaldan kənar yazılmış qeydlər',
      integrityMore: 'Və digərləri — bu yoxlamanın jurnal qeydinə baxın.',
      integrityFailed: 'Jurnalı yoxlamaq mümkün olmadı',
      integrityProblems: {
        RECORD_CHANGED: 'Qeyd dəyişdirilib', RECORD_DELETED: 'Qeyd silinib', LINKS_MISSING: 'Halqalar çatışmır',
        TIME_CHANGED: 'Qeydin vaxtı dəyişdirilib', HEAD_MISMATCH: 'Zəncirin sonu uyğun gəlmir', RECORDS_OUTSIDE_CHAIN: 'Zəncirdən kənar qeydlər',
      },
      detailsTitle: 'Audit jurnalı qeydi',
      entityId: 'Obyektin ID-si',
      company: 'Şirkət',
      recordId: 'Qeydin ID-si',
      traceId: 'Trace ID (servis logları)',
      openTransaction: 'Əməliyyatı aç',
      openPaymentLink: 'Ödəniş linkini aç',
      openEcomOrder: 'Çıxarış sifarişini aç',
    },
    auth: {
      unknownRole: 'Server bu tətbiqin tanımadığı bir rol qaytardı. Giriş rədd edildi — administratorla əlaqə saxlayın.',
      subtitle: 'Merçant portalına daxil olun',
      emailLabel: 'E-poçt',
      passwordLabel: 'Parol',
      signIn: 'Daxil ol',
      fillBoth: 'E-poçt və parolu daxil edin',
      invalidEmail: 'Düzgün e-poçt ünvanı daxil edin',
      networkError: 'Server əlçatan deyil. Bağlantını yoxlayın və yenidən cəhd edin.',
      authFailed: 'Giriş alınmadı',
      malformedResponse: 'Server sessiyasız cavab verdi. Giriş rədd edildi — yenidən cəhd edin və ya administratorla əlaqə saxlayın.',
      idleSignedOut: '15 dəqiqə fəaliyyət olmadığı üçün sistemdən çıxdınız. Yenidən daxil olun.',
      passwordChangeRequired: 'Parolunuzu administrator təyin edib. Daxil olmaq üçün öz parolunuzu seçin.',
      newPasswordLabel: 'Yeni parol',
      confirmPasswordLabel: 'Yeni parolu təkrarlayın',
      passwordRules: 'Ən azı 12 simvol: böyük və kiçik hərflər, rəqəm və xüsusi simvol. Son 4 parolunuzdan biri olmamalıdır.',
      passwordsDoNotMatch: 'Parollar üst-üstə düşmür.',
      samePassword: 'Yeni parol cari paroldan fərqli olmalıdır.',
      changePasswordAndSignIn: 'Parolu dəyişin və daxil olun',
    },
    errors: {
      forbiddenTitle: 'Giriş qadağandır',
      forbiddenText: 'Rolunuz bu səhifəni açmağa imkan vermir.',
      notFoundTitle: 'Səhifə tapılmadı',
      notFoundText: 'Sorğu etdiyiniz ünvan mövcud deyil.',
      unexpectedTitle: 'Xəta baş verdi',
      unexpectedText: 'Səhifəni göstərmək mümkün olmadı. Yenidən cəhd edin və ya ana səhifəyə qayıdın.',
      goHome: 'Ana səhifəyə keç',
    },
  },
  ru: {
    common: {
      save: 'Сохранить',
      cancel: 'Отмена',
      delete: 'Удалить',
      edit: 'Редактировать',
      search: 'Поиск...',
      filter: 'Фильтр',
      actions: 'Действия',
      export: 'Экспорт',
      copy: 'Копировать',
      close: 'Закрыть',
      refresh: 'Обновить',
      loading: 'Загрузка...',
      yes: 'Да',
      no: 'Нет',
      all: 'Все',
      status: 'Статус',
      date: 'Дата',
      back: 'Назад',
      copied: 'Скопировано в буфер обмена',
      success: 'Успешно',
      error: 'Ошибка',
      warning: 'Предупреждение',
      info: 'Информация',
      confirm: 'Подтвердить',
      viewAll: 'Смотреть все',
      details: 'Детали',
      active: 'Активно',
      inactive: 'Неактивно',
      overview: 'Обзор',
      create: 'Создать',
      update: 'Обновить',
      loadFailed: 'Не удалось загрузить данные.',
      periods: { today: 'Сегодня', days7: '7 дней', days30: '30 дней', days90: '90 дней' },
    },
    nav: {
      home: 'Главная',
      payByLink: 'Оплата по ссылке',
      transactions: 'Транзакции',
      ecommerce: 'Электронная коммерция',
      pos: 'POS-терминалы',
      terminals: 'Терминалы',
      companies: 'Компании',
      users: 'Пользователи',
      auditLogs: 'Журнал аудита',
      settings: 'Настройки',
    },
    header: {
      title: 'Мерчант Портал (Merchant Portal)',
      notifications: 'Уведомления',
      markAllRead: 'Отметить все как прочитанные',
      noNotifications: 'Нет непрочитанных уведомлений',
      profile: 'Мой профиль',
      logout: 'Выйти',
      logoutQuestion: 'Выйти из портала?',
      language: 'Язык',
      adminBadge: 'СИСТЕМНЫЙ АДМИН',
    },
    home: {
      title: 'Панель мерчанта',
      subtitle: 'Оплаты картой по всем терминалам компании — по выписке провайдера, по заказам, созданным за период.',
      subtitleAll: 'Оплаты картой всех компаний портала — по выписке провайдера, по заказам, созданным за период.',
      openStatement: 'Открыть выписку E-commerce',
      period: 'Период',
      loadFailed: 'Не удалось загрузить сводку.',
      empty: 'За период заказов нет.',
      metrics: {
        netRevenue: 'Выручка',
        netRevenueHint: 'Списано за вычетом возвратов по заказам периода',
        paidCount: 'Оплаченных заказов',
        paidCountHint: 'Включая позже возвращённые',
        refunded: 'Возвращено',
        refundedHint: 'Возвраты по заказам периода',
        averagePayment: 'Средний чек',
      },
      charts: {
        daily: 'Выручка по дням',
        statuses: 'Заказы по статусам',
        statusesHint: 'Статусы — как в выписке E-commerce',
        terminals: 'Терминалы по выручке',
      },
      recentOrders: {
        title: 'Последние заказы',
        empty: 'За период заказов нет.',
      },
    },
    linkStats: {
      subtitle: 'Только оплаты по платёжным ссылкам портала. Возвраты считаются в день, когда их провели.',
      loadFailed: 'Не удалось загрузить статистику по ссылкам.',
      empty: 'За период оплат по ссылкам нет.',
      metrics: {
        netRevenue: 'Выручка',
        netRevenueHint: 'Получено за вычетом возвратов, проведённых за период',
        paidCount: 'Платежей получено',
        paidCountHint: 'Включая позже возвращённые',
        refunded: 'Возвращено',
        refundedHint: 'Возвраты, проведённые за период, когда бы ни был платёж',
        averagePayment: 'Средний платёж',
      },
      charts: {
        daily: 'Выручка по дням',
        attempts: 'Попытки оплаты',
        attemptsHint: 'Каждое открытие платёжной ссылки — попытка; брошенная становится неуспешной.',
        terminals: 'Терминалы по выручке',
        links: 'Созданные платёжные ссылки',
        linksHint: 'Текущий статус ссылок, созданных за период',
        funnel: 'Воронка ссылок',
        funnelHint: 'Ссылки, созданные за период, и докуда они дошли. Оплаты после периода тоже засчитываются, поэтому цифры недавнего периода ещё растут. Оплачена — списание или холд.',
        funnelSteps: { created: 'Создана', opened: 'Открыта', paymentStarted: 'Карта отправлена', paid: 'Оплачена' },
        timeToPay: 'Время до оплаты',
        timeToPayHint: 'Одноразовые ссылки периода: от создания ссылки до начала оплаченной попытки.',
        median: 'Медиана',
        paidLinks: 'Оплачено ссылок',
        noPaidLinks: 'За период нет оплаченных одноразовых ссылок',
        timeToPayRanges: { UP_TO_1_HOUR: 'До 1 часа', UP_TO_1_DAY: '1–24 часа', UP_TO_7_DAYS: '1–7 дней', OVER_7_DAYS: 'Больше 7 дней' },
        units: { lessThanMinute: '< 1 мин', minute: 'мин', hour: 'ч', day: 'д' },
      },
    },
    settings: {
      title: 'Настройки',
      subtitle: 'Название вашей компании и язык интерфейса. Остальные разделы — на своих страницах.',
      saveSuccess: 'Настройки успешно сохранены!',
      saveChanges: 'Сохранить изменения',
      account: {
        title: 'Информация о бизнесе',
        merchantName: 'Название организации',
        merchantEmail: 'Email учётной записи',
        emailReadOnly: 'Берётся из учётной записи, с которой выполнен вход; здесь не меняется.',
        nameReadOnly: 'Менять название компании может только системный администратор.',
        noCompany: 'Учётная запись не привязана к компании. Компаниями управляют на странице «Компании».',
        loadFailed: 'Не удалось загрузить данные компании.',
        saveFailed: 'Не удалось сохранить изменения.',
      },
      display: {
        title: 'Интерфейс',
        language: 'Язык системы',
      },
    },
    payByLink: {
      title: 'Оплата по ссылке (Pay by Link)',
      subtitle: 'Создавайте, отправляйте и управляйте платежными ссылками для клиентов через SMS, Email и мессенджеры.',
      tabs: {
        links: 'Ссылки',
        stats: 'Статистика',
      },
      createButton: 'Создать ссылку',
      createTitle: 'Создать новую платежную ссылку',
      createSubtitle: 'Сформируйте безопасную ссылку для получения оплаты от клиента.',
      linkDetails: 'Детали ссылки',
      terminalSelect: 'Выберите терминал *',
      terminalHelper: 'Эквайринговый терминал, через который пройдет платеж',
      amountLabel: 'Сумма платежа *',
      currencyLabel: 'Валюта',
      descriptionLabel: 'Описание платежа / Номер заказа *',
      customerNameLabel: 'Имя клиента',
      customerEmailLabel: 'Email клиента',
      customerPhoneLabel: 'Телефон клиента',
      customerPhoneHint: 'Азербайджанский номер: +994 и 9 цифр',
      customerPhoneInvalid: 'Телефон клиента — только азербайджанский номер: +994 и 9 цифр, например +994 70 330 10 25',
      usageTypeLabel: 'Тип использования',
      singleUse: 'Одноразовая (Один платеж)',
      multipleUse: 'Многоразовая (Многократная оплата)',
      maxUsesLabel: 'Макс. кол-во оплат',
      expirationLabel: 'Срок действия ссылки',
      paymentTypeLabel: 'Тип списания',
      createLinkAction: 'Сформировать ссылку',
      cancelLinkAction: 'Отменить ссылку',
      shareDialogTitle: 'Поделиться ссылкой',
      cancelConfirmTitle: 'Отменить платежную ссылку?',
      cancelConfirmText: 'Вы уверены, что хотите отменить эту ссылку? Клиенты больше не смогут провести по ней оплату.',
      keepLink: 'Оставить ссылку',
      linkCancelledSuccess: 'Ссылка на оплату успешно отменена',
      linkCancelFailed: 'Не удалось отменить ссылку на оплату',
      paid: 'Оплачена',
      noActiveTerminals: 'Нет активных терминалов: заведите терминал или снимите блокировку на странице «Терминалы». По заблокированному терминалу новые ссылки не создаются.',
      customerNotSpecified: 'Не указан',
      empty: 'Ссылок на оплату нет',
      share: 'Поделиться',
      copyLink: 'Скопировать ссылку',
      sendEmail: 'Отправить по email',
      sendWhatsApp: 'Отправить в WhatsApp',
      qrCode: 'QR-код',
      qrHint: 'Наведите камеру телефона на код, чтобы открыть страницу оплаты',
      downloadQr: 'Скачать PNG',
      createdTitle: 'Ссылка на оплату создана. Поделитесь ею с клиентом.',
      linkLabel: 'Ссылка на оплату',
      done: 'Готово',
      generating: 'Создаётся…',
      invalidAmount: 'Введите корректную сумму',
      descriptionRequired: 'Добавьте описание или номер заказа',
      invalidMaxUses: 'Максимум платежей — целое число не меньше 1',
      createFailed: 'Не удалось создать ссылку на оплату',
      smsHint: 'SMS: деньги списываются сразу, как только клиент платит.',
      dmsHint: 'DMS: деньги резервируются на карте; списание — с карточки операции.',
      dmsForbiddenUser: 'Вам не разрешено создавать DMS-ссылки. Обратитесь к руководителю компании или администратору.',
      dmsForbiddenTerminal: 'На этом терминале DMS-ссылки запрещены. Обратитесь к администратору.',
      maxUsesHint: 'После этого числа успешных платежей ссылка закрывается',
      descriptionHint: 'Показывается клиенту на странице оплаты',
      customerSection: 'Клиент',
      linkSettings: 'Параметры ссылки',
      messageText: 'Пожалуйста, завершите оплату по этой ссылке:',
      emailSubject: 'Запрос на оплату',
      expiry: { h1: '1 час', h24: '24 часа', h72: '3 дня', d7: '7 дней', d30: '30 дней' },
      statuses: {
        ACTIVE: 'Активна',
        EXPIRED: 'Истекла',
        COMPLETED: 'Завершена',
        CANCELED: 'Отменена',
        SUSPENDED: 'Приостановлена (терминал заблокирован)',
      },
      table: {
        linkId: 'ID ссылки / Код',
        customer: 'Клиент',
        amount: 'Сумма',
        type: 'Тип',
        status: 'Статус',
        usage: 'Использование',
        created: 'Создано',
        expires: 'Истекает',
        actions: 'Действия',
      },
    },
    payByLinkDetail: {
      backToLinks: 'Назад к оплатам по ссылке',
      title: 'Детали платежной ссылки',
      subtitle: 'Просмотр спецификаций ссылки, истории ее выполнения и статуса DMS.',
      copyUrl: 'Скопировать URL',
      cancelLink: 'Отменить ссылку',
      quickActions: 'Быстрые действия',
      createSameLink: 'Создать новую ссылку с теми же данными',
      tabs: {
        overview: 'Обзор',
        transactions: 'Транзакции',
        settings: 'Настройки ссылки',
      },
      summary: {
        linkInfo: 'Параметры платежной ссылки',
        shortCode: 'Короткий код',
        originalUrl: 'URL оплаты',
        redirectUrl: 'URL перенаправления',
        dmsStatus: 'Статус DMS',
        payerIp: 'IP плательщика',
        sentVia: 'Отправлено через',
        terminal: 'Назначенный терминал',
        refundedOfUsed: 'из них возвращено',
      },
      timeline: {
        title: 'Журнал событий',
        created: 'Ссылка создана',
        paid: 'Оплата авторизована',
        finalized: 'DMS платеж завершен',
      },
    },
    transactions: {
      title: 'Транзакции',
      subtitle: 'Просмотр и аудит всех проведённых платежей по вашим терминалам.',
      filters: {
        dateRange: 'Диапазон дат',
        search: 'Поиск по ID заказа провайдера, RID by merchant, клиенту, email или ID транзакции...',
        paymentMethod: 'Метод оплаты',
        terminal: 'Терминал',
        clearFilters: 'Сбросить фильтры',
      },
      statuses: {
        PENDING: 'В обработке',
        AUTHORIZED: 'Авторизована',
        SUCCESS: 'Успешно',
        FAILED: 'Неуспешно',
        PARTIALLY_REFUNDED: 'Частичный возврат',
        REFUNDED: 'Возврат',
      },
      columns: {
        providerOrderId: 'ID заказа провайдера',
        ridByMerchant: 'RID by merchant',
        id: 'ID Транзакции',
        date: 'Дата и Время',
        amount: 'Сумма',
        customer: 'Клиент',
        status: 'Статус',
        rrn: 'RRN',
        method: 'Метод',
        terminal: 'Терминал',
        terminalLogin: 'Логин терминала',
        actions: 'Детали',
      },
      detail: {
        title: 'Детали транзакции',
        backToTransactions: 'Назад к транзакциям',
        refundAction: 'Оформить возврат (Refund)',
        refundTitle: 'Возврат средств по транзакции',
        refundAmount: 'Сумма возврата',
        confirmRefund: 'Подтвердить возврат',
        completeAction: 'Завершить',
        completeTitle: 'Завершить DMS-транзакцию',
        captureExplains: 'Заблокированная на карте клиента сумма будет списана и переведена на ваш счет. Отменить это действие нельзя.',
        captureAmount: 'Сумма к списанию',
        confirmCapture: 'Списать средства',
        refundQuestion: 'Вернуть средства по этой транзакции? Деньги вернутся на карту клиента, отменить это действие нельзя.',
        refundReason: 'Причина (необязательно)',
        refundReasonHint: 'Попадает в журнал аудита; клиент и эквайер её не видят.',
        keepTransaction: 'Оставить транзакцию',
        customerInfo: 'Информация о клиенте',
        paymentInfo: 'Параметры платежа',
        technicalInfo: 'Техническая информация шлюза',
        approvalCode: 'Код одобрения (Approval Code)',
        unresolvedTitle: 'Эквайер не подтвердил исход',
        unresolvedHint: 'Операция могла уже пройти. Повтор закрыт, пока системный администратор не сверит её с провайдером и не отметит итог.',
        checkStatusAction: 'Проверить статус',
        statusChecked: 'Статус операции обновлён.',
        checkStatusFailed: 'Не удалось проверить статус. Попробуйте ещё раз.',
        refundableLeft: 'Остаток к возврату',
        amountLabel: 'Сумма',
        amountUpTo: 'Не больше',
        amountWholeRefund: 'Весь остаток',
        amountWholeCapture: 'Вся сумма',
        amountInvalid: 'Введите сумму больше нуля, не больше двух знаков после запятой',
        amountAboveMax: 'Сумма больше доступной',
        refundRemains: 'Останется к возврату',
        captureReleased: 'Не будет списано',
        capturePartialHint: 'Холд списывается один раз: остаток портал потом не спишет, банк снимет его по своим срокам.',
        actionsTitle: 'Действия',
        moneyReasons: {
          NO_RIGHTS: 'Ваша роль не позволяет это действие',
          TERMINAL_NOT_IN_PORTAL: 'Терминала этой операции нет в системе портала',
          NO_PROVIDER_CREDENTIALS: 'У компании терминала нет логина к провайдеру',
          FULLY_REFUNDED: 'Возвращена полностью',
          CAPTURE_FIRST: 'Сначала спишите холд',
          ALREADY_CAPTURED: 'Холд уже списан',
          OUTCOME_UNKNOWN: 'Исход прошлой операции неизвестен',
          IN_PROGRESS: 'По этому платежу уже идёт операция',
          other: 'Действие недоступно',
        },
        unresolvedInProgressTitle: 'Операция ещё идёт',
        unresolvedInProgressHint: 'Ответ эквайера ещё не записан. Подождите и обновите страницу.',
        unresolvedKindRefund: 'Возврат',
        unresolvedKindCapture: 'Списание',
        unresolvedStartedAt: 'Отправлена',
        unresolvedStartedBy: 'Кто отправил',
        resolveExecuted: 'Прошла',
        resolveNotExecuted: 'Не прошла',
        resolveExecutedTitle: 'Отметить операцию как прошедшую?',
        resolveExecutedQuestion: 'Операция будет записана как подтверждённая — без идентификаторов эквайера. Делайте это только после сверки с провайдером.',
        resolveNotExecutedTitle: 'Отметить операцию как не прошедшую?',
        resolveNotExecutedQuestion: 'Запрет на повтор будет снят, у платежа ничего не изменится. Делайте это только после сверки с провайдером.',
        resolveFailed: 'Не удалось отметить итог',
        eventCreated: 'Операция заведена',
        eventCaptured: 'Холд списан',
        eventRefunded: 'Возврат подтверждён',
        historyEmpty: 'По этой операции не записано ни одного события.',
        identifiers: 'Идентификаторы платежа',
        providerOrderId: 'ID заказа провайдера',
        ridByMerchant: 'RID by merchant',
        clientIp: 'IP адрес клиента',
      },
    },
    ecommerce: {
      title: 'Выписка E-commerce',
      subtitle: 'Платежи ваших терминалов из шлюза провайдера: строка — заказ со всей его историей.',
      periodFrom: 'Создан с',
      periodTo: 'Создан по',
      periodHint: 'Заказы отбираются по дате создания. Период обязателен и не длиннее 92 дней.',
      periodInvalid: 'Конец периода должен быть позже начала.',
      periodTooLong: 'Период не может быть длиннее 92 дней.',
      terminals: 'Терминалы',
      allTerminals: 'Все терминалы',
      search: 'Номер заказа, RID by merchant или RRN (точное совпадение)',
      minAmount: 'Сумма от',
      maxAmount: 'Сумма до',
      loadMore: 'Показать ещё',
      loaded: 'Загружено заказов',
      loadFailed: 'Не удалось загрузить выписку.',
      empty: 'За выбранный период и с этими фильтрами заказов нет.',
      exportLoaded: 'Выгрузить загруженные',
      statusFilter: 'Статус',
      allStatuses: 'Все статусы',
      paymentTypeFilter: 'Тип оплаты',
      allPaymentTypes: 'Все типы',
      paymentTypes: { SMS: 'SMS (одним сообщением)', DMS: 'DMS (холд и списание)' },
      noMatchesYet: 'Среди просмотренных заказов с этим статусом нет. Нажмите «показать ещё», чтобы искать дальше.',
      applyFilters: 'Применить',
      resetFilters: 'Сбросить',
      filtersChanged: 'Фильтры изменены — нажмите «Применить», чтобы обновить выписку.',
      stats: {
        orders: 'Заказов',
        captured: 'Списано',
        refunded: 'Возвращено',
      },
      statuses: {
        PENDING: 'В обработке',
        AUTHORIZED: 'Авторизован',
        SUCCESS: 'Успешно',
        PARTIALLY_PAID: 'Частично оплачен',
        FAILED: 'Неуспешно',
        PARTIALLY_REFUNDED: 'Частичный возврат',
        REFUNDED: 'Возврат',
        CANCELED: 'Отменён',
      },
      operationKinds: {
        AUTHORIZATION: 'Авторизация (холд)',
        CAPTURE: 'Списание',
        PURCHASE: 'Покупка',
        REVERSAL: 'Реверсал',
        REFUND: 'Возврат',
        UNKNOWN: 'Нераспознанная операция',
      },
      columns: {
        createdAt: 'Создан',
        orderId: 'ID заказа провайдера',
        ridByMerchant: 'RID by merchant',
        card: 'Карта',
        amount: 'Сумма заказа',
        captured: 'Списано',
        status: 'Статус',
        terminal: 'Терминал',
      },
      detail: {
        back: 'К выписке',
        openInNewTab: 'Открыть в новой вкладке',
        title: 'Заказ',
        notFound: 'Заказ не найден: его нет, он принадлежит другому мерчанту или ещё не завершён.',
        loadFailed: 'Не удалось загрузить заказ.',
        identifiers: 'Идентификаторы',
        money: 'Деньги',
        orderAmount: 'Сумма заказа',
        captured: 'Списано',
        refunded: 'Возвращено',
        payment: 'Платёж',
        card: 'Карта',
        terminal: 'Терминал',
        providerStatus: 'Статус у провайдера',
        createdAt: 'Создан',
        lastOperationAt: 'Последняя операция',
        declineCode: 'Код отказа',
        description: 'Описание',
        operations: 'Операции',
        operationAt: 'Время',
        kind: 'Операция',
        codes: 'Коды провайдера',
        result: 'Результат',
        amount: 'Сумма',
        clearAmount: 'Списано операцией',
        rrn: 'RRN',
        tranId: 'ID транзакции',
        noOperations: 'По заказу нет операций.',
      },
    },
    terminals: {
      title: 'Терминалы',
      subtitle: 'Управление эквайринговыми POS и E-commerce терминалами.',
      addTerminal: 'Добавить терминал',
      registerAction: 'Зарегистрировать терминал',
      editTerminal: 'Редактировать терминал',
      createDialogTitle: 'Создать новый терминал',
      editDialogTitle: 'Редактировать параметры терминала',
      name: 'Название терминала',
      providerTerminal: 'Терминал провайдера',
      providerTerminalHint: 'Только терминалы мерчантов, привязанных к логину компании, и ещё не заведённые. Название, логин и номер терминала — из справочника провайдера; пароля у терминала нет.',
      providerTerminalEmpty: 'Свободных терминалов мерчантов этой компании в справочнике нет. Обновите справочник; если терминал так и не появился — он выключен у провайдера, уже заведён или его мерчант не привязан к логину компании.',
      providerTerminalLoadFailed: 'Не удалось загрузить справочник провайдера.',
      syncDirectory: 'Обновить справочник',
      syncApplied: 'Справочник обновлён. Получено терминалов',
      syncSkipped: 'Справочник не обновлён',
      login: 'Логин терминала мерчанта',
      terminal: 'Терминал',
      testAction: 'Тест',
      checkOk: 'Данные компании приняты, платёж создать можно',
      checkInvalid: 'Неверный логин или пароль компании',
      checkRejected: 'Данные приняты, но эквайер не разрешил оплату',
      checkUnreachable: 'Эквайер не ответил — о терминале ничего не известно',
      company: 'Назначенная компания',
      status: 'Статус',
      statuses: {
        ACTIVE: 'Активен',
        BLOCKED: 'Заблокирован',
      },
      blockAction: 'Заблокировать терминал',
      unblockAction: 'Разблокировать терминал',
      blockExplains: 'Заблокированный терминал не принимает новые платежи: его активные ссылки приостанавливаются, новые создать нельзя. Возвраты, списание холдов DMS и проверка статуса по уже прошедшим платежам продолжают работать. Разблокировка вернёт ссылки в работу.',
      blockLinksAffected: 'Активных ссылок будет приостановлено',
      blockLinksUnknown: 'Не удалось посчитать ссылки — блокировка всё равно приостановит все активные ссылки этого терминала.',
      unblockExplains: 'Разблокировка возвращает терминалу приём платежей: приостановленные ссылки вернутся в работу, кроме тех, у которых за время блокировки истёк срок.',
      unblockLinksAffected: 'Приостановленных ссылок вернётся в работу',
      editConfirmTitle: 'Сохранить изменения терминала',
      editConfirmQuestion: 'Изменится следующее. В этой форме рядом лежат название и компания-владелец — сверьтесь со списком перед подтверждением.',
      editNothingChanged: 'Изменений нет — запрос не отправлялся.',
      searchPlaceholder: 'Поиск по названию, ID или логину...',
      companyHint: 'Компания, которой принадлежит терминал',
      nameFromProvider: 'Название приходит из справочника провайдера: переименуйте терминал у провайдера',
      dmsColumn: 'DMS',
      dmsAllowed: 'Разрешён',
      dmsForbidden: 'Запрещён',
      dmsSwitch: 'DMS-ссылки разрешены',
      dmsSwitchHint: 'Без DMS на терминале создаются только SMS-ссылки. Уже созданные DMS-ссылки продолжают работать.',
      formIncomplete: 'Заполните все обязательные поля',
      created: 'Терминал заведён',
      createFailed: 'Не удалось завести терминал',
      updated: 'Терминал обновлён',
      updateFailed: 'Не удалось обновить терминал',
      checkFailed: 'Не удалось проверить терминал',
      blockedNotice: 'Терминал заблокирован: новые платежи по нему не принимаются, активные ссылки приостановлены',
      unblockedNotice: 'Терминал разблокирован: приостановленные ссылки вернулись в работу, кроме тех, у которых истёк срок',
      statusChangeFailed: 'Не удалось изменить статус терминала',
      empty: 'Терминалов пока нет',
    },
    companies: {
      title: 'Компании',
      subtitle: 'Управление торговыми компаниями и юридическими лицами.',
      addCompany: 'Добавить компанию',
      createDialogTitle: 'Новая торговая компания',
      companyId: 'Код / ID компании',
      name: 'Юридическое название',
      status: 'Статус',
      searchPlaceholder: 'Поиск компании по ID или названию...',
      deleteTitle: 'Удалить компанию?',
      deleteQuestion: 'Компания пропадёт из всех списков портала. Её терминалы, платёжные ссылки и операции останутся в базе.',
      deleteIrreversible: 'Отменить это из портала нельзя: восстановить удалённую компанию здесь не получится.',
      providerLogin: 'Логин к провайдеру',
      providerLoginHint: 'Логин мультимерчанта из справочника провайдера: в списке только активные логины с активными мерчантами, не занятые другой компанией. Только что заведённый — «Обновить справочник».',
      providerLoginEmpty: 'Свободных логинов мультимерчантов в справочнике нет. Обновите справочник; если логин так и не появился — он не заведён у провайдера, выключен, без активных мерчантов или уже у другой компании.',
      loginsSyncApplied: 'Справочник обновлён. Получено логинов мультимерчантов',
      loginsSyncSkipped: 'Справочник логинов не обновлён',
      providerPassword: 'Пароль к провайдеру',
      providerPasswordHint: 'Хранится зашифрованным. Посмотреть его потом нельзя — только заменить.',
      newProviderPassword: 'Новый пароль к провайдеру (необязательно)',
      newProviderPasswordHint: 'Оставьте пустым, чтобы не менять пароль',
      providerPasswordReplaced: 'Пароль к провайдеру будет заменён',
      taxId: 'VÖEN (ИНН)',
      taxIdHint: '10 цифр, печатается на чеке плательщика. Заменить можно, стереть нельзя.',
      taxIdInvalid: 'VÖEN — ровно 10 цифр',
      editNothingChanged: 'Ничего не изменилось — запрос не отправлен.',
      formIncomplete: 'Заполните все обязательные поля',
      created: 'Компания создана',
      createFailed: 'Не удалось создать компанию',
      editCompany: 'Редактировать компанию',
      editConfirmTitle: 'Сохранить изменения компании?',
      editConfirmQuestion: 'У этой компании изменится следующее. Проверьте список перед подтверждением.',
      credentialsWarning: 'С новыми данными пойдут все запросы компании к провайдеру — новые платежи, списания, возвраты и проверки статуса. Неверные остановят платежи компании.',
      statusWarning: 'Статус — пометка в справочнике и запись в журнале аудита. Вход сотрудников, создание ссылок и приём платежей он не останавливает — чтобы остановить платежи, блокируйте терминалы.',
      updated: 'Компания обновлена',
      updateFailed: 'Не удалось обновить компанию',
    },
    users: {
      title: 'Пользователи',
      subtitle: 'Управление пользователями портала, ролями и правами доступа.',
      addUser: 'Добавить пользователя',
      createDialogTitle: 'Создать пользователя портала',
      username: 'Имя пользователя (Логин)',
      password: 'Пароль',
      name: 'ФИО',
      role: 'Системная роль',
      company: 'Назначенная компания',
      status: 'Статус',
      searchPlaceholder: 'Поиск по имени, логину или email...',
      deleteTitle: 'Удалить пользователя?',
      deleteQuestion: 'Учётная запись перестанет работать: вход будет отклонён сразу, открытые сессии пользователя завершатся в течение 15 минут. Запись останется в базе, скрытая из списков.',
      deleteIrreversible: 'Отменить это из портала нельзя: удалённого пользователя здесь не восстановить и не отредактировать.',
      roles: {
        systemAdmin: 'Системный администратор',
        companyHead: 'Руководитель компании',
        companyManager: 'Менеджер компании',
        companyEmployee: 'Сотрудник компании',
        auditor: 'Аудитор',
      },
      formIncomplete: 'Заполните все обязательные поля',
      createFailed: 'Не удалось создать пользователя',
      deleteFailed: 'Не удалось удалить пользователя',
      editUser: 'Изменить пользователя',
      editDialogTitle: 'Изменить пользователя',
      editConfirmTitle: 'Сохранить изменения пользователя',
      editConfirmQuestion: 'Изменится следующее. Роль, компания и статус решают, что этот человек видит и может делать, — проверьте список перед подтверждением.',
      editNothingChanged: 'Ничего не изменилось — запрос не отправлен.',
      editSessionsHint: 'Новая роль или компания вступит в силу при следующем обновлении сессии пользователя, в течение 15 минут. Блокировка сразу закрывает вход, открытые сессии завершатся в течение 15 минут.',
      noCompany: 'Без компании',
      companyRequired: 'Для роли компании выберите компанию.',
      newPassword: 'Новый пароль (необязательно)',
      newPasswordHint: 'Оставьте пустым, чтобы не менять. Не меньше 12 символов: заглавные и строчные буквы, цифра и спецсимвол.',
      issuedPasswordHint: 'Пользователь сменит этот пароль при первом входе.',
      passwordChangePending: 'Ждёт смены пароля',
      terminals: 'Терминалы',
      terminalsHint: 'Сотрудник видит только эти терминалы, их ссылки и платежи',
      terminalsRequired: 'Выберите сотруднику хотя бы один терминал.',
      noCompanyTerminals: 'У компании нет терминалов',
      allCompanyTerminals: 'Все терминалы компании',
      noTerminals: 'Нет терминалов',
      noTerminalsHint: 'Пока не назначены терминалы, в портале ничего не видит',
      passwordWillChange: 'Пароль будет заменён',
      ownPasswordSignsOut: 'Все ваши сессии, и эта тоже, завершатся: войдите снова с новым паролем',
      selfHint: 'Свою роль и статус здесь поменять нельзя.',
      dmsLinks: 'DMS-ссылки',
      dmsLinksAllowed: 'разрешены',
      dmsLinksForbidden: 'запрещены',
      dmsLinksSwitch: 'Может создавать DMS-ссылки',
      dmsLinksHint: 'Без права пользователь создаёт только SMS-ссылки. Изменение действует в течение 15 минут.',
      noDmsLinks: 'Без DMS',
      updated: 'Пользователь обновлён',
      updateFailed: 'Не удалось обновить пользователя',
      statuses: { ACTIVE: 'Активен', BLOCKED: 'Заблокирован' },
    },
    auditLogs: {
      title: 'Журнал аудита',
      subtitle: 'Журнал безопасности и историй действий в системе.',
      user: 'Пользователь',
      action: 'Действие',
      resource: 'Ресурс',
      timestamp: 'Дата и Время',
      ip: 'IP адрес',
      filterEntity: 'Фильтр по ресурсу',
      searchPlaceholder: 'Поиск по пользователю, действию, ID, деталям или trace ID...',
      outcome: 'Результат',
      outcomeSuccess: 'Успешно',
      outcomeDenied: 'Отказано',
      outcomeUnresolved: 'Не подтверждён',
      outcomeDeclined: 'Отклонён эквайером',
      filterOutcome: 'Фильтр по результату',
      dateFrom: 'С даты',
      dateTo: 'По дату',
      actions: {
        CREATE: 'Создание', READ: 'Чтение', UPDATE: 'Изменение', DELETE: 'Удаление', LIST: 'Список',
        BLOCK: 'Блокировка', UNBLOCK: 'Разблокировка', LOGIN: 'Вход', LOGOUT: 'Выход', LOCKOUT: 'Блокировка входа',
        RATE_LIMIT: 'Лимит попыток входа', TOKEN_REUSE: 'Повтор refresh-токена', PASSWORD_CHANGE: 'Смена пароля',
        CAPTURE: 'Списание', REFUND: 'Возврат', CANCEL: 'Отмена', RESOLVE: 'Итог операции',
        STATUS_CHANGE: 'Смена статуса', START: 'Запуск сервиса', STOP: 'Остановка сервиса',
        EXPORT: 'Выгрузка', VERIFY: 'Проверка целостности',
      },
      entities: {
        COMPANY: 'Компания', TERMINAL: 'Терминал', USER: 'Пользователь', AUTH: 'Вход в систему', PAYMENT_LINK: 'Платёжная ссылка',
        TRANSACTION: 'Операция', PROVIDER_ORDER: 'Заказ выписки', AUDIT_LOG: 'Журнал аудита', SERVICE: 'Сервис',
      },
      filterAction: 'Действие',
      filterUser: 'Пользователь',
      filterCompany: 'Компания',
      empty: 'Записей по фильтрам нет.',
      attention: 'Требует внимания',
      attentionHint: 'Неподтверждённые денежные операции и перерывы журнала, повтор refresh-токена, блокировки учёток и лимит попыток входа',
      exportAction: 'Выгрузить CSV',
      exportHint: 'Все записи по фильтрам, старые раньше, до 100 000. Выгрузка записывается в журнал.',
      exportFailed: 'Не удалось выгрузить журнал',
      integrityAction: 'Проверить целостность',
      integrityTitle: 'Целостность журнала аудита',
      integrityIntact: 'Цепочка журнала цела: ни одна запись не изменена, не удалена и не добавлена мимо портала.',
      integrityBroken: 'Цепочка журнала разорвана: записи меняли в базе мимо портала.',
      integrityChecked: 'Проверено записей',
      integrityHead: 'Последнее звено',
      integrityStartedAt: 'Цепочка ведётся с',
      integrityNotCovered: 'Записей до начала цепочки (не покрыты)',
      integrityUnsealed: 'Записей мимо портала',
      integrityMore: 'И другие — см. запись этой проверки в журнале.',
      integrityFailed: 'Не удалось проверить журнал',
      integrityProblems: {
        RECORD_CHANGED: 'Запись изменена', RECORD_DELETED: 'Запись удалена', LINKS_MISSING: 'Пропали звенья',
        TIME_CHANGED: 'Изменено время записи', HEAD_MISMATCH: 'Конец цепочки не сходится', RECORDS_OUTSIDE_CHAIN: 'Записи вне цепочки',
      },
      detailsTitle: 'Запись журнала аудита',
      entityId: 'ID объекта',
      company: 'Компания',
      recordId: 'ID записи',
      traceId: 'Trace ID (логи сервиса)',
      openTransaction: 'Открыть операцию',
      openPaymentLink: 'Открыть платёжную ссылку',
      openEcomOrder: 'Открыть заказ выписки',
    },
    auth: {
      unknownRole: 'Сервер вернул роль, неизвестную приложению. Вход отклонён — обратитесь к администратору.',
      subtitle: 'Вход в портал мерчанта',
      emailLabel: 'Email',
      passwordLabel: 'Пароль',
      signIn: 'Войти',
      fillBoth: 'Введите email и пароль',
      invalidEmail: 'Введите корректный email',
      networkError: 'Сервер недоступен. Проверьте соединение и попробуйте ещё раз.',
      authFailed: 'Вход не выполнен',
      malformedResponse: 'Сервер ответил без сессии. Вход отклонён — попробуйте ещё раз или обратитесь к администратору.',
      idleSignedOut: 'Сессия завершена: 15 минут без действий. Войдите снова.',
      passwordChangeRequired: 'Пароль вам задал администратор. Чтобы войти, придумайте свой.',
      newPasswordLabel: 'Новый пароль',
      confirmPasswordLabel: 'Повторите новый пароль',
      passwordRules: 'Не меньше 12 символов: заглавные и строчные буквы, цифра и спецсимвол. Не один из 4 последних паролей.',
      passwordsDoNotMatch: 'Пароли не совпадают.',
      samePassword: 'Новый пароль должен отличаться от текущего.',
      changePasswordAndSignIn: 'Сменить пароль и войти',
    },
    errors: {
      forbiddenTitle: 'Нет доступа',
      forbiddenText: 'Ваша роль не позволяет открыть эту страницу.',
      notFoundTitle: 'Страница не найдена',
      notFoundText: 'Запрошенный адрес не существует.',
      unexpectedTitle: 'Что-то пошло не так',
      unexpectedText: 'Не удалось показать страницу. Попробуйте ещё раз или вернитесь на главную.',
      goHome: 'На главную',
    },
  },
};
