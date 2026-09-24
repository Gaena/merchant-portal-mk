import type { TransactionStatus } from '../types/transaction';
import type { LinkStatus } from '../utils/payByLinkData';
import type { TerminalStatus } from '../types/dto';
import type { EcomOperationKind, EcomStatus, EcomPaymentType } from '../types/ecom';

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
    /** Общий отказ загрузки экрана или его части. */
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
    /** Вопрос окна выхода: подтверждение — общее `ConfirmDialog`. */
    logoutQuestion: string;
    language: string;
    adminBadge: string;
  };
  home: {
    title: string;
    /** Р-91: главная — оплаты картой по всем терминалам компании по выписке провайдера. */
    subtitle: string;
    /** То же для SYSTEM_ADMIN и AUDITOR: по всем терминалам портала. */
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
    /** Вкладки страницы: список ссылок и статистика оплат по ним. */
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
    /** Форма и окно создания: раньше тексты были зашиты по-английски и по-русски вперемешку. */
    noActiveTerminals: string;
    customerNotSpecified: string;
    empty: string;
    share: string;
    copyLink: string;
    sendEmail: string;
    sendWhatsApp: string;
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
    /**
     * Подписи четырёх статусов из `utils/payByLinkData.ts`. `Record<LinkStatus, string>`
     * держит их в связке со словарём бэкенда: новый статус — и `tsc` потребует подпись
     * во всех трёх языках, лишний ключ он не пропустит (P2-13, зеркало `transactions.statuses`).
     */
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
    finalizeDMS: string;
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
       * Подпись к `refundedPaymentsCount` рядом с «использовано N из M» (P2-16, Р-50):
       * «…, из них возвращено: 1». Показывается только когда возвраты были — возврат
       * не отменяет использование (Р-49), поэтому это отдельная цифра, а не минус из счётчика.
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
    /**
     * Подписи шести статусов из `types/transaction.ts`. `Record<TransactionStatus, string>`
     * держит их в связке со словарём бэкенда: новый статус — и `tsc` потребует подпись
     * во всех трёх языках, лишний ключ он не пропустит.
     */
    statuses: Record<TransactionStatus, string>;
    columns: {
      /**
       * Идентификаторы, которые мерчант знает по своей стороне: номер заказа у провайдера
       * и RID платежа. Идут первыми во всех таблицах операций — внутренний `id` для мерчанта
       * ничего не значит и стоит последним, служебной колонкой.
       */
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
      /**
       * Подпись колонки терминала. Основной его параметр — логин: имя мерчант придумывает сам,
       * а числовой id внутренний. Короткая форма `terminals.login`, годная для шапки таблицы.
       */
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
      /**
       * Списание холда (P3-5a). Общие с окном «Finalize» на карточке ссылки: там тот же
       * `POST /transactions/{id}/complete`, поэтому текст один, а не две копии в словаре.
       */
      completeAction: string;
      completeTitle: string;
      captureExplains: string;
      captureAmount: string;
      confirmCapture: string;
      /**
       * Возврат (P3-5a). Кнопка, диалог и журнал аудита теперь говорят «возврат»: бэкенд
       * зовёт `POST /transactions/{id}/refund` и пишет `REFUND`, а окно раньше спрашивало
       * про отмену транзакции и возврат не упоминало.
       */
      refundQuestion: string;
      keepTransaction: string;
      customerInfo: string;
      paymentInfo: string;
      technicalInfo: string;
      approvalCode: string;
      /**
       * Тексты неподтверждённого исхода денежной операции (502 от бэкенда). Отдельные от
       * обычного отказа намеренно: при отказе деньги не двигались и повтор безопасен, здесь
       * операция могла уже пройти — см. `utils/moneyOperationError.ts`.
       */
      unresolvedTitle: string;
      unresolvedHint: string;
      /** Кнопка, спрашивающая эквайера о судьбе операции. Единственный верный следующий шаг. */
      checkStatusAction: string;
      statusChecked: string;
      /**
       * Перечитали, а операция не изменилась: исход по-прежнему не подтверждён, повтор закрыт.
       * Снять запрет может только перечитывание, которое показало движение денег.
       */
      statusStillUnresolved: string;
      /** Сама проверка статуса не удалась. Это не исход денежной операции — запрет не трогает. */
      checkStatusFailed: string;
      /** Остаток к возврату у частично возвращённой операции: вернуть можно только его. */
      refundableLeft: string;
      /**
       * Подписи событий на шкале истории. Денежные события называют действие, а не состояние
       * после него: «возврат» понятнее, чем «частично возвращена», когда рядом стоит сумма.
       */
      eventCreated: string;
      eventCaptured: string;
      eventRefunded: string;
      /** Ответ не принёс ни одного события. Пустая шкала без слов читается как поломка. */
      historyEmpty: string;
      /** Заголовок блока с ProviderOrderId и MerchantRid — он открывает карточку операции. */
      identifiers: string;
      providerOrderId: string;
      ridByMerchant: string;
      clientIp: string;
    };
  };
  /**
   * Вкладка E-commerce — выписка провайдера из сервиса `ecom` (`project_docs/ecom.md` §2). Свой
   * словарь, а не `transactions`: статусов восемь (Р-75, Р-78), период обязателен и ограничен,
   * страница курсорная, а операции заказа приходят вместе с ним.
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
     * Выбор терминала провайдера при заведении (Р-67, Р-79): название и логин приходят из справочника,
     * пароля у терминала нет (Р-93). Справочник видит и терминалы заводит только SYSTEM_ADMIN (`ecom.md` §3).
     */
    providerTerminal: string;
    providerTerminalHint: string;
    providerTerminalEmpty: string;
    providerTerminalLoadFailed: string;
    syncDirectory: string;
    syncApplied: string;
    syncSkipped: string;
    login: string;
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
    /** Итоги и отказы действий на странице — раньше были зашиты по-английски и по-русски вперемешку. */
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
    deactivateTitle: string;
    deactivateQuestion: string;
    activateTitle: string;
    activateQuestion: string;
    /**
     * Креды компании к провайдеру (Р-93): все запросы к шлюзу идут от её имени. Задаёт и меняет только
     * SYSTEM_ADMIN; пароль хранится зашифрованным и не показывается никому — только заменяется.
     */
    providerLogin: string;
    providerLoginHint: string;
    /** Итог обновления справочника логинов мультимерчантов (Р-94) — по кнопке в форме компании. */
    loginsSyncApplied: string;
    loginsSyncSkipped: string;
    providerPassword: string;
    providerPasswordHint: string;
    newProviderPassword: string;
    newProviderPasswordHint: string;
    editCredentials: string;
    credentialsConfirmTitle: string;
    credentialsConfirmQuestion: string;
    providerPasswordReplaced: string;
    editNothingChanged: string;
    formIncomplete: string;
    created: string;
    createFailed: string;
    credentialsUpdated: string;
    credentialsUpdateFailed: string;
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
    newPassword: string;
    newPasswordHint: string;
    passwordWillChange: string;
    /** Свою роль и статус в этом окне не поменять: так себя легко лишить доступа. */
    selfHint: string;
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
    filterOutcome: string;
    dateFrom: string;
    dateTo: string;
    entityAuth: string;
    entityAuditLog: string;
    /** Карточка записи журнала: открывается кликом по строке. */
    detailsTitle: string;
    entityId: string;
    company: string;
    recordId: string;
    openTransaction: string;
    openPaymentLink: string;
  };
  auth: {
    unknownRole: string;
    /** Форма входа. Раньше была зашита по-английски целиком, а под ней стояла ложная надпись о 2FA. */
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

/**
 * Подпись статуса для интерфейса. Неизвестный статус (`parseTransactionStatus` вернул `null`)
 * показывается как есть — исходным значением из ответа, без подстановки чего-либо знакомого.
 */
export const statusLabel = (
  dict: TranslationDictionary,
  status: TransactionStatus | null | undefined,
  raw?: string
): string => (status ? dict.transactions.statuses[status] : raw || '—');

/**
 * Подпись статуса платёжной ссылки. Правило то же, что у `statusLabel`: статус вне словаря
 * бэкенда (`parseLinkStatus` вернул `null`) показывается исходным значением из ответа,
 * без подстановки чего-либо знакомого.
 */
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
      subtitleAll: 'Card payments across all terminals of the portal, from the provider\'s statement, for orders created in the period.',
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
      finalizeDMS: 'Complete DMS Payment',
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
        keepTransaction: 'Keep Transaction',
        customerInfo: 'Customer Information',
        paymentInfo: 'Payment Breakdown',
        technicalInfo: 'Technical Gateway Info',
        approvalCode: 'Approval Code',
        unresolvedTitle: 'Outcome not confirmed by the acquirer',
        unresolvedHint: 'The operation may already have gone through. Do not send it again — check the transaction status first.',
        checkStatusAction: 'Check status',
        statusChecked: 'Transaction status refreshed.',
        statusStillUnresolved: 'The re-read shows no change: the outcome is still unconfirmed and the operation stays locked. Review it in the audit log before retrying.',
        checkStatusFailed: 'Could not check the status. Try again.',
        refundableLeft: 'Left to refund',
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
      providerTerminalHint: 'Name and login come from the provider directory. A terminal has no password: the acquirer is reached with the company credentials.',
      providerTerminalEmpty: 'The provider directory is empty. Refresh it — the scheduled update may not have run yet.',
      providerTerminalLoadFailed: 'Could not load the provider directory.',
      syncDirectory: 'Refresh directory',
      syncApplied: 'Directory refreshed. Terminals received',
      syncSkipped: 'Directory was not refreshed',
      login: 'Merchant Login ID',
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
      deactivateTitle: 'Mark company inactive?',
      deactivateQuestion: 'This is a label in the directory and an entry in the audit log. It does not stop sign-ins, link creation or payments — to stop payments, block the terminals.',
      activateTitle: 'Mark company active?',
      activateQuestion: 'The company is marked active again. Nothing else changes.',
      providerLogin: 'Acquirer login',
      providerLoginHint: 'The full multimerchant login: MultiMerchantSys/<login>. It must be active in the provider directory with at least one active merchant — refresh the directory if the login was created just now.',
      loginsSyncApplied: 'Directory refreshed. Multimerchant logins received',
      loginsSyncSkipped: 'Login directory was not refreshed',
      providerPassword: 'Acquirer password',
      providerPasswordHint: 'Stored encrypted. Nobody can view it later — it can only be replaced.',
      newProviderPassword: 'New acquirer password (optional)',
      newProviderPasswordHint: 'Leave blank to keep the current password',
      editCredentials: 'Acquirer credentials',
      credentialsConfirmTitle: 'Change the acquirer credentials?',
      credentialsConfirmQuestion: 'Every request of this company to the acquirer — new payments, captures, refunds and status checks — will go with these credentials. Wrong ones stop the company payments.',
      providerPasswordReplaced: 'The acquirer password will be replaced',
      editNothingChanged: 'Nothing changed — no request was sent.',
      formIncomplete: 'Fill in every required field',
      created: 'Company created',
      createFailed: 'Could not create the company',
      credentialsUpdated: 'Acquirer credentials updated',
      credentialsUpdateFailed: 'Could not update the acquirer credentials',
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
      deleteQuestion: 'The account stops working immediately: every session of this user is ended and sign-in is refused. The record stays in the database, hidden from the lists.',
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
      editSessionsHint: 'A new role or company takes effect when the user\'s session next refreshes, within 15 minutes. Blocking ends the sessions at once.',
      noCompany: 'No company',
      newPassword: 'New password (optional)',
      newPasswordHint: 'Leave empty to keep the current password. At least 12 characters with upper and lower case, a digit and a symbol.',
      passwordWillChange: 'The password will be replaced',
      selfHint: 'You cannot change your own role or status here.',
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
      searchPlaceholder: 'Search actor, action, entity ID or details...',
      outcome: 'Outcome',
      outcomeSuccess: 'Success',
      outcomeDenied: 'Denied',
      outcomeUnresolved: 'Unresolved',
      filterOutcome: 'Filter by Outcome',
      dateFrom: 'From',
      dateTo: 'To',
      entityAuth: 'Authentication',
      entityAuditLog: 'Audit Journal',
      detailsTitle: 'Audit record',
      entityId: 'Entity ID',
      company: 'Company',
      recordId: 'Record ID',
      openTransaction: 'Open transaction',
      openPaymentLink: 'Open payment link',
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
      subtitleAll: 'Portalın bütün terminalları üzrə kartla ödənişlər — provayderin çıxarışına görə, dövrdə yaradılmış sifarişlər üzrə.',
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
      finalizeDMS: 'DMS Ödənişini Tamamla',
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
        keepTransaction: 'Əməliyyatı Saxla',
        customerInfo: 'Müştəri Məlumatları',
        paymentInfo: 'Ödəniş Bölgüsü',
        technicalInfo: 'Texniki Əlaqə Məlumatı',
        approvalCode: 'Təsdiq Kodu (Approval Code)',
        unresolvedTitle: 'Nəticə ekvayer tərəfindən təsdiqlənmədi',
        unresolvedHint: 'Əməliyyat artıq keçmiş ola bilər. Təkrar göndərməyin — əvvəlcə əməliyyatın statusunu yoxlayın.',
        checkStatusAction: 'Statusu yoxla',
        statusChecked: 'Əməliyyatın statusu yeniləndi.',
        statusStillUnresolved: 'Yenidən oxuma dəyişiklik göstərmir: nəticə hələ də təsdiqlənməyib, əməliyyat bağlı qalır. Təkrarlamazdan əvvəl audit jurnalında yoxlayın.',
        checkStatusFailed: 'Statusu yoxlamaq mümkün olmadı. Yenidən cəhd edin.',
        refundableLeft: 'Qaytarıla bilən qalıq',
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
      providerTerminalHint: 'Ad və login provayder kataloqundan gəlir. Terminalın şifrəsi yoxdur: provayderə şirkətin məlumatları ilə müraciət olunur.',
      providerTerminalEmpty: 'Provayder kataloqu boşdur. Onu yeniləyin — planlı yenilənmə hələ işləməmiş ola bilər.',
      providerTerminalLoadFailed: 'Provayder kataloqunu yükləmək mümkün olmadı.',
      syncDirectory: 'Kataloqu yenilə',
      syncApplied: 'Kataloq yeniləndi. Alınan terminallar',
      syncSkipped: 'Kataloq yenilənmədi',
      login: 'Mərfəti Terminal Logini',
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
      deactivateTitle: 'Şirkət qeyri-aktiv işarələnsin?',
      deactivateQuestion: 'Bu, sorğu kitabçasında qeyd və audit jurnalında yazıdır. İşçilərin girişini, link yaradılmasını və ödənişlərin qəbulunu dayandırmır — ödənişləri dayandırmaq üçün terminalları bloklayın.',
      activateTitle: 'Şirkət aktiv işarələnsin?',
      activateQuestion: 'Şirkət yenidən aktiv işarələnəcək. Başqa heç nə dəyişmir.',
      providerLogin: 'Provayder logini',
      providerLoginHint: 'Multimerçant logini tam şəkildə: MultiMerchantSys/<login>. O, provayder kataloqunda aktiv olmalı və ən azı bir aktiv merçantı olmalıdır — login indicə yaradılıbsa, kataloqu yeniləyin.',
      loginsSyncApplied: 'Kataloq yeniləndi. Alınan multimerçant loginləri',
      loginsSyncSkipped: 'Login kataloqu yenilənmədi',
      providerPassword: 'Provayder şifrəsi',
      providerPasswordHint: 'Şifrələnmiş saxlanılır. Sonra onu görmək olmaz — yalnız əvəz etmək.',
      newProviderPassword: 'Yeni provayder şifrəsi (istəyə görə)',
      newProviderPasswordHint: 'Cari şifrəni saxlamaq üçün boş buraxın',
      editCredentials: 'Provayderə giriş',
      credentialsConfirmTitle: 'Provayderə giriş dəyişdirilsin?',
      credentialsConfirmQuestion: 'Şirkətin provayderə bütün sorğuları — yeni ödənişlər, silinmələr, geri qaytarmalar və status yoxlamaları — bu məlumatlarla gedəcək. Yanlış məlumatlar şirkətin ödənişlərini dayandırar.',
      providerPasswordReplaced: 'Provayder şifrəsi əvəz olunacaq',
      editNothingChanged: 'Heç nə dəyişməyib — sorğu göndərilmədi.',
      formIncomplete: 'Bütün məcburi sahələri doldurun',
      created: 'Şirkət yaradıldı',
      createFailed: 'Şirkəti yaratmaq alınmadı',
      credentialsUpdated: 'Provayderə giriş yeniləndi',
      credentialsUpdateFailed: 'Provayderə girişi yeniləmək alınmadı',
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
      deleteQuestion: 'Hesab dərhal işləməyi dayandırır: istifadəçinin bütün sessiyaları bağlanır, girişə icazə verilmir. Qeyd bazada qalır, siyahılardan gizlədilir.',
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
      editSessionsHint: 'Yeni rol və ya şirkət istifadəçinin sessiyası növbəti dəfə yeniləndikdə, 15 dəqiqə ərzində qüvvəyə minir. Bloklama sessiyaları dərhal bitirir.',
      noCompany: 'Şirkətsiz',
      newPassword: 'Yeni parol (istəyə bağlı)',
      newPasswordHint: 'Cari parolu saxlamaq üçün boş buraxın. Ən azı 12 simvol: böyük və kiçik hərf, rəqəm və xüsusi simvol.',
      passwordWillChange: 'Parol dəyişdiriləcək',
      selfHint: 'Öz rolunuzu və statusunuzu burada dəyişə bilməzsiniz.',
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
      searchPlaceholder: 'İstifadəçi, əməliyyat, ID və ya təfərrüat üzrə axtarış...',
      outcome: 'Nəticə',
      outcomeSuccess: 'Uğurlu',
      outcomeDenied: 'Rədd edilib',
      outcomeUnresolved: 'Təsdiqlənməyib',
      filterOutcome: 'Nəticə üzrə filtr',
      dateFrom: 'Tarixdən',
      dateTo: 'Tarixədək',
      entityAuth: 'Autentifikasiya',
      entityAuditLog: 'Audit jurnalı',
      detailsTitle: 'Audit jurnalı qeydi',
      entityId: 'Obyektin ID-si',
      company: 'Şirkət',
      recordId: 'Qeydin ID-si',
      openTransaction: 'Əməliyyatı aç',
      openPaymentLink: 'Ödəniş linkini aç',
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
      subtitleAll: 'Оплаты картой по всем терминалам портала — по выписке провайдера, по заказам, созданным за период.',
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
      finalizeDMS: 'Завершить DMS платеж',
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
        keepTransaction: 'Оставить транзакцию',
        customerInfo: 'Информация о клиенте',
        paymentInfo: 'Параметры платежа',
        technicalInfo: 'Техническая информация шлюза',
        approvalCode: 'Код одобрения (Approval Code)',
        unresolvedTitle: 'Эквайер не подтвердил исход',
        unresolvedHint: 'Операция могла уже пройти. Не отправляйте её повторно — сначала проверьте статус операции.',
        checkStatusAction: 'Проверить статус',
        statusChecked: 'Статус операции обновлён.',
        statusStillUnresolved: 'Перечитывание ничего не изменило: исход по-прежнему не подтверждён, операция остаётся закрытой для повтора. Разберите её по журналу аудита.',
        checkStatusFailed: 'Не удалось проверить статус. Попробуйте ещё раз.',
        refundableLeft: 'Остаток к возврату',
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
      providerTerminalHint: 'Название и логин берутся из справочника провайдера. Пароля у терминала нет: к провайдеру ходят с логином и паролем компании.',
      providerTerminalEmpty: 'Справочник провайдера пуст. Обновите его — плановое обновление могло ещё не пройти.',
      providerTerminalLoadFailed: 'Не удалось загрузить справочник провайдера.',
      syncDirectory: 'Обновить справочник',
      syncApplied: 'Справочник обновлён. Получено терминалов',
      syncSkipped: 'Справочник не обновлён',
      login: 'Логин терминала мерчанта',
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
      deactivateTitle: 'Пометить компанию неактивной?',
      deactivateQuestion: 'Это пометка в справочнике и запись в журнале аудита. Вход сотрудников, создание ссылок и приём платежей она не останавливает — чтобы остановить платежи, блокируйте терминалы.',
      activateTitle: 'Пометить компанию активной?',
      activateQuestion: 'Компания снова будет помечена активной. Больше ничего не меняется.',
      providerLogin: 'Логин к провайдеру',
      providerLoginHint: 'Логин мультимерчанта целиком: MultiMerchantSys/<логин>. Он должен быть активен в справочнике провайдера и иметь хотя бы одного активного мерчанта — только что заведённый подтяните кнопкой «Обновить справочник».',
      loginsSyncApplied: 'Справочник обновлён. Получено логинов мультимерчантов',
      loginsSyncSkipped: 'Справочник логинов не обновлён',
      providerPassword: 'Пароль к провайдеру',
      providerPasswordHint: 'Хранится зашифрованным. Посмотреть его потом нельзя — только заменить.',
      newProviderPassword: 'Новый пароль к провайдеру (необязательно)',
      newProviderPasswordHint: 'Оставьте пустым, чтобы не менять пароль',
      editCredentials: 'Доступ к провайдеру',
      credentialsConfirmTitle: 'Изменить доступ к провайдеру?',
      credentialsConfirmQuestion: 'С этими данными пойдут все запросы компании к провайдеру — новые платежи, списания, возвраты и проверки статуса. Неверные остановят платежи компании.',
      providerPasswordReplaced: 'Пароль к провайдеру будет заменён',
      editNothingChanged: 'Ничего не изменилось — запрос не отправлен.',
      formIncomplete: 'Заполните все обязательные поля',
      created: 'Компания создана',
      createFailed: 'Не удалось создать компанию',
      credentialsUpdated: 'Доступ к провайдеру обновлён',
      credentialsUpdateFailed: 'Не удалось обновить доступ к провайдеру',
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
      deleteQuestion: 'Учётная запись перестанет работать сразу: все сессии пользователя завершатся, вход будет отклонён. Запись останется в базе, скрытая из списков.',
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
      editSessionsHint: 'Новая роль или компания вступит в силу при следующем обновлении сессии пользователя, в течение 15 минут. Блокировка завершает сессии сразу.',
      noCompany: 'Без компании',
      newPassword: 'Новый пароль (необязательно)',
      newPasswordHint: 'Оставьте пустым, чтобы не менять. Не меньше 12 символов: заглавные и строчные буквы, цифра и спецсимвол.',
      passwordWillChange: 'Пароль будет заменён',
      selfHint: 'Свою роль и статус здесь поменять нельзя.',
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
      searchPlaceholder: 'Поиск по пользователю, действию, ID или деталям...',
      outcome: 'Результат',
      outcomeSuccess: 'Успешно',
      outcomeDenied: 'Отказано',
      outcomeUnresolved: 'Не подтверждён',
      filterOutcome: 'Фильтр по результату',
      dateFrom: 'С даты',
      dateTo: 'По дату',
      entityAuth: 'Аутентификация',
      entityAuditLog: 'Журнал аудита',
      detailsTitle: 'Запись журнала аудита',
      entityId: 'ID объекта',
      company: 'Компания',
      recordId: 'ID записи',
      openTransaction: 'Открыть операцию',
      openPaymentLink: 'Открыть платёжную ссылку',
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
