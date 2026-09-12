import type { TransactionStatus } from '../types/transaction';
import type { LinkStatus } from '../utils/payByLinkData';
import type { TerminalStatus } from '../types/dto';

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
    language: string;
    adminBadge: string;
  };
  home: {
    title: string;
    subtitle: string;
    period: string;
    loadFailed: string;
    empty: string;
    metrics: {
      netRevenue: string;
      netRevenueHint: string;
      paidCount: string;
      refunded: string;
      averagePayment: string;
    };
    charts: {
      daily: string;
      hourly: string;
      statuses: string;
      terminals: string;
      links: string;
      byPaymentType: string;
      byUsageType: string;
    };
    recentTransactions: {
      title: string;
      providerOrderId: string;
      merchantRid: string;
      id: string;
      date: string;
      terminal: string;
      ip: string;
      device: string;
      amount: string;
      status: string;
      empty: string;
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
    ecommerceTitle: string;
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
      merchantRid: string;
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
      merchantRid: string;
      clientIp: string;
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
    terminalId: string;
    login: string;
    password: string;
    /**
     * Раскрытие пароля терминала: ключ от эквайринга показывается по нажатию, только системному
     * администратору и с записью в журнал аудита (`TerminalService.revealPassword`).
     */
    revealPassword: string;
    hidePassword: string;
    newPassword: string;
    newPasswordHint: string;
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
    editPasswordReplaced: string;
    editNothingChanged: string;
    searchPlaceholder: string;
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
  };
  auth: {
    unknownRole: string;
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
      language: 'Language',
      adminBadge: 'SYSTEM ADMIN',
    },
    home: {
      title: 'Merchant Dashboard',
      subtitle: 'Payments, revenue and terminals, counted in the database over the period below.',
      period: 'Period',
      loadFailed: 'Could not load the dashboard summary.',
      empty: 'No payments in this period.',
      metrics: {
        netRevenue: 'Net revenue',
        netRevenueHint: 'Received minus refunds',
        paidCount: 'Payments received',
        refunded: 'Refunded',
        averagePayment: 'Average payment',
      },
      charts: {
        daily: 'Revenue by day',
        hourly: 'Payments by hour of day',
        statuses: 'Payment outcomes',
        terminals: 'Terminals by revenue',
        links: 'Payment links',
        byPaymentType: 'By payment type',
        byUsageType: 'By usage type',
      },
      recentTransactions: {
        title: 'Latest payments',
        providerOrderId: 'Provider Order ID',
        merchantRid: 'Merchant RID',
        id: 'Transaction',
        date: 'Date & time',
        terminal: 'Terminal',
        ip: 'Payer IP',
        device: 'Device',
        amount: 'Amount',
        status: 'Status',
        empty: 'No payments recorded yet.',
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
      ecommerceTitle: 'E-commerce Transactions',
      filters: {
        dateRange: 'Date Range',
        search: 'Search by Provider Order ID, Merchant RID, customer, email or transaction ID...',
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
        merchantRid: 'Merchant RID',
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
        refundAmount: 'Refund Amount (AZN)',
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
        statusChecked: 'Status re-read from the acquirer.',
        refundableLeft: 'Left to refund',
        eventCreated: 'Transaction opened',
        eventCaptured: 'Hold captured',
        eventRefunded: 'Refund confirmed',
        historyEmpty: 'No recorded events for this transaction yet.',
        identifiers: 'Payment Identifiers',
        providerOrderId: 'Provider Order ID',
        merchantRid: 'Merchant RID',
        clientIp: 'Client IP Address',
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
      terminalId: 'Numeric Terminal ID',
      login: 'Merchant Login ID',
      password: 'Terminal Password',
      revealPassword: 'Show password',
      hidePassword: 'Hide password',
      newPassword: 'New Terminal Password (Optional)',
      newPasswordHint: 'Leave blank to keep the current terminal password',
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
      editConfirmQuestion: 'The following will change. The login, the password and the owning company all live in this one form — check the list before confirming.',
      editPasswordReplaced: 'The terminal password will be replaced',
      editNothingChanged: 'Nothing changed — no request was sent.',
      searchPlaceholder: 'Search terminals by name, ID or login...',
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
    },
    auth: {
      unknownRole: 'The server returned a role this application does not recognise. Sign-in was refused — contact your administrator.',
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
      language: 'Dil',
      adminBadge: 'SİSTEM ADMİNİ',
    },
    home: {
      title: 'Merchant Paneli',
      subtitle: 'Ödənişlər, gəlir və terminallar — aşağıdakı dövr üzrə bazada hesablanır.',
      period: 'Dövr',
      loadFailed: 'İcmalı yükləmək mümkün olmadı.',
      empty: 'Bu dövrdə ödəniş yoxdur.',
      metrics: {
        netRevenue: 'Xalis gəlir',
        netRevenueHint: 'Alınan məbləğ, geri qaytarmalar çıxılmaqla',
        paidCount: 'Alınan ödənişlər',
        refunded: 'Geri qaytarılıb',
        averagePayment: 'Orta ödəniş',
      },
      charts: {
        daily: 'Günlər üzrə gəlir',
        hourly: 'Sutkanın saatları üzrə ödənişlər',
        statuses: 'Ödənişlərin nəticələri',
        terminals: 'Gəlirə görə terminallar',
        links: 'Ödəniş linkləri',
        byPaymentType: 'Ödəniş növü üzrə',
        byUsageType: 'İstifadə növü üzrə',
      },
      recentTransactions: {
        title: 'Son ödənişlər',
        providerOrderId: 'Provayder Sifariş ID',
        merchantRid: 'Merchant RID',
        id: 'Əməliyyat',
        date: 'Tarix və vaxt',
        terminal: 'Terminal',
        ip: 'Ödəyicinin IP-si',
        device: 'Cihaz',
        amount: 'Məbləğ',
        status: 'Status',
        empty: 'Hələ ödəniş yoxdur.',
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
      ecommerceTitle: 'E-ticarət Əməliyyatları',
      filters: {
        dateRange: 'Tarix Aralığı',
        search: 'Provayder Sifariş ID, Merchant RID, müştəri, e-poçt və ya əməliyyat ID üzrə axtarış...',
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
        merchantRid: 'Merchant RID',
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
        refundAmount: 'Qaytarılan Məbləğ (AZN)',
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
        statusChecked: 'Status ekvayerdən yenidən oxundu.',
        refundableLeft: 'Qaytarıla bilən qalıq',
        eventCreated: 'Əməliyyat açıldı',
        eventCaptured: 'Blok məbləği silindi',
        eventRefunded: 'Qaytarma təsdiqləndi',
        historyEmpty: 'Bu əməliyyat üzrə qeydə alınmış hadisə yoxdur.',
        identifiers: 'Ödəniş identifikatorları',
        providerOrderId: 'Provayder Sifariş ID',
        merchantRid: 'Merchant RID',
        clientIp: 'Müştərinin IP Ünvanı',
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
      terminalId: 'Reqamli Terminal ID',
      login: 'Mərfəti Terminal Logini',
      password: 'Terminal Şifrəsi',
      revealPassword: 'Şifrəni göstər',
      hidePassword: 'Şifrəni gizlət',
      newPassword: 'Yeni terminal şifrəsi (istəyə görə)',
      newPasswordHint: 'Cari şifrəni saxlamaq üçün boş buraxın',
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
      editConfirmQuestion: 'Aşağıdakılar dəyişəcək. Bu formada login, şifrə və sahib şirkət yan-yana durur — təsdiqləməzdən əvvəl siyahını yoxlayın.',
      editPasswordReplaced: 'Terminalın şifrəsi əvəz olunacaq',
      editNothingChanged: 'Dəyişiklik yoxdur — sorğu göndərilmədi.',
      searchPlaceholder: 'Ad, ID və ya login üzrə axtarış...',
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
    },
    auth: {
      unknownRole: 'Server bu tətbiqin tanımadığı bir rol qaytardı. Giriş rədd edildi — administratorla əlaqə saxlayın.',
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
      language: 'Язык',
      adminBadge: 'СИСТЕМНЫЙ АДМИН',
    },
    home: {
      title: 'Панель мерчанта',
      subtitle: 'Платежи, выручка и терминалы — посчитаны в базе за период ниже.',
      period: 'Период',
      loadFailed: 'Не удалось загрузить сводку.',
      empty: 'За период платежей нет.',
      metrics: {
        netRevenue: 'Выручка',
        netRevenueHint: 'Получено за вычетом возвратов',
        paidCount: 'Платежей получено',
        refunded: 'Возвращено',
        averagePayment: 'Средний платёж',
      },
      charts: {
        daily: 'Выручка по дням',
        hourly: 'Платежи по часам суток',
        statuses: 'Исходы платежей',
        terminals: 'Терминалы по выручке',
        links: 'Платёжные ссылки',
        byPaymentType: 'По типу платежа',
        byUsageType: 'По типу использования',
      },
      recentTransactions: {
        title: 'Последние платежи',
        providerOrderId: 'ID заказа провайдера',
        merchantRid: 'Merchant RID',
        id: 'Операция',
        date: 'Дата и время',
        terminal: 'Терминал',
        ip: 'IP плательщика',
        device: 'Устройство',
        amount: 'Сумма',
        status: 'Статус',
        empty: 'Платежей пока нет.',
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
      ecommerceTitle: 'Электронные транзакции',
      filters: {
        dateRange: 'Диапазон дат',
        search: 'Поиск по ID заказа провайдера, Merchant RID, клиенту, email или ID транзакции...',
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
        merchantRid: 'Merchant RID',
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
        refundAmount: 'Сумма возврата (AZN)',
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
        statusChecked: 'Статус перечитан у эквайера.',
        refundableLeft: 'Остаток к возврату',
        eventCreated: 'Операция заведена',
        eventCaptured: 'Холд списан',
        eventRefunded: 'Возврат подтверждён',
        historyEmpty: 'По этой операции не записано ни одного события.',
        identifiers: 'Идентификаторы платежа',
        providerOrderId: 'ID заказа провайдера',
        merchantRid: 'Merchant RID',
        clientIp: 'IP адрес клиента',
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
      terminalId: 'Цифровой Terminal ID',
      login: 'Логин терминала мерчанта',
      password: 'Пароль терминала',
      revealPassword: 'Показать пароль',
      hidePassword: 'Скрыть пароль',
      newPassword: 'Новый пароль терминала (необязательно)',
      newPasswordHint: 'Оставьте пустым, чтобы сохранить текущий пароль терминала',
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
      editConfirmQuestion: 'Изменится следующее. В этой форме рядом лежат логин, пароль и компания-владелец — сверьтесь со списком перед подтверждением.',
      editPasswordReplaced: 'Пароль терминала будет заменён',
      editNothingChanged: 'Изменений нет — запрос не отправлялся.',
      searchPlaceholder: 'Поиск по названию, ID или логину...',
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
    },
    auth: {
      unknownRole: 'Сервер вернул роль, неизвестную приложению. Вход отклонён — обратитесь к администратору.',
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
