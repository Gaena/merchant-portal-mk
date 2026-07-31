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
    metrics: {
      totalSales: string;
      totalTransactions: string;
      successRate: string;
      activeLinks: string;
      vsLastMonth: string;
    };
    charts: {
      salesOverview: string;
      paymentMethods: string;
    };
    recentTransactions: string;
    quickActions: {
      title: string;
      createLink: string;
      viewTransactions: string;
      manageTerminals: string;
    };
  };
  settings: {
    title: string;
    subtitle: string;
    saveSuccess: string;
    saveChanges: string;
    tabs: {
      account: string;
      security: string;
      notifications: string;
      payment: string;
      display: string;
      api: string;
    };
    account: {
      title: string;
      merchantName: string;
      merchantEmail: string;
      businessPhone: string;
      taxId: string;
      contactPerson: string;
      businessAddress: string;
    };
    security: {
      title: string;
      twoFactor: string;
      twoFactorDesc: string;
      changePassword: string;
      passwordLastChanged: string;
      sessionTimeout: string;
    };
    notifications: {
      title: string;
      emailHeader: string;
      smsHeader: string;
      newTransactions: string;
      failedPayments: string;
      dailySummary: string;
      weeklySummary: string;
      smsHighValue: string;
    };
    payment: {
      title: string;
      minAmount: string;
      maxAmount: string;
      allowSMS: string;
      allowDMS: string;
      autoSettlement: string;
    };
    display: {
      title: string;
      appearance: string;
      theme: string;
      themeLight: string;
      themeDark: string;
      themeAuto: string;
      language: string;
      regional: string;
      dateFormat: string;
      timezone: string;
      currency: string;
    };
    api: {
      title: string;
      apiKey: string;
      webhookUrl: string;
      secretKey: string;
      regenerate: string;
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
    noTerminalsWarning: string;
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
    cancelConfirmTitle: string;
    cancelConfirmText: string;
    linkCancelledSuccess: string;
    statuses: {
      active: string;
      paid: string;
      completed: string;
      expired: string;
      canceled: string;
    };
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
    statuses: {
      approved: string;
      failed: string;
      refunded: string;
      pending: string;
      declined: string;
    };
    columns: {
      id: string;
      date: string;
      amount: string;
      customer: string;
      status: string;
      rrn: string;
      method: string;
      terminal: string;
      actions: string;
    };
    detail: {
      title: string;
      backToTransactions: string;
      refundAction: string;
      refundTitle: string;
      refundAmount: string;
      confirmRefund: string;
      customerInfo: string;
      paymentInfo: string;
      technicalInfo: string;
      approvalCode: string;
      providerOrderId: string;
      clientIp: string;
    };
  };
  terminals: {
    title: string;
    subtitle: string;
    addTerminal: string;
    editTerminal: string;
    createDialogTitle: string;
    editDialogTitle: string;
    name: string;
    terminalId: string;
    login: string;
    password: string;
    location: string;
    company: string;
    status: string;
    searchPlaceholder: string;
  };
  companies: {
    title: string;
    subtitle: string;
    addCompany: string;
    editCompany: string;
    createDialogTitle: string;
    editDialogTitle: string;
    companyId: string;
    name: string;
    email: string;
    phone: string;
    taxId: string;
    status: string;
    searchPlaceholder: string;
  };
  users: {
    title: string;
    subtitle: string;
    addUser: string;
    editUser: string;
    createDialogTitle: string;
    username: string;
    password: string;
    name: string;
    email: string;
    role: string;
    company: string;
    status: string;
    searchPlaceholder: string;
    filterRole: string;
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
  };
}

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
      subtitle: 'Overview of your sales performance, transactions and active payment channels.',
      metrics: {
        totalSales: 'Total Sales Volume',
        totalTransactions: 'Total Transactions',
        successRate: 'Payment Success Rate',
        activeLinks: 'Active Payment Links',
        vsLastMonth: 'vs last month',
      },
      charts: {
        salesOverview: 'Sales Performance Trend',
        paymentMethods: 'Payment Methods Breakdown',
      },
      recentTransactions: 'Recent Transactions',
      quickActions: {
        title: 'Quick Actions',
        createLink: 'Create Payment Link',
        viewTransactions: 'View Transactions',
        manageTerminals: 'Manage Terminals',
      },
    },
    settings: {
      title: 'Settings',
      subtitle: 'Manage your merchant account preferences, security, notifications and display settings.',
      saveSuccess: 'Settings saved successfully!',
      saveChanges: 'Save Changes',
      tabs: {
        account: 'Account & Business',
        security: 'Security',
        notifications: 'Notifications',
        payment: 'Payment Methods',
        display: 'Display & Region',
        api: 'API & Webhooks',
      },
      account: {
        title: 'Business Information',
        merchantName: 'Merchant / Business Name',
        merchantEmail: 'Business Email',
        businessPhone: 'Business Phone Number',
        taxId: 'Tax Identification Number (VÖEN)',
        contactPerson: 'Contact Person Name',
        businessAddress: 'Business Address',
      },
      security: {
        title: 'Security Settings',
        twoFactor: 'Two-Factor Authentication (2FA)',
        twoFactorDesc: 'Add an extra layer of security using an authenticator app',
        changePassword: 'Change Password',
        passwordLastChanged: 'Password last changed 30 days ago',
        sessionTimeout: 'Session Inactivity Timeout',
      },
      notifications: {
        title: 'Notification Preferences',
        emailHeader: 'Email Notifications',
        smsHeader: 'SMS Notifications',
        newTransactions: 'Notify on new successful transactions',
        failedPayments: 'Alert on failed payment attempts',
        dailySummary: 'Receive daily summary report',
        weeklySummary: 'Receive weekly analytics report',
        smsHighValue: 'Send SMS alerts for high-value transactions (> 1,000 AZN)',
      },
      payment: {
        title: 'Payment Processing Rules',
        minAmount: 'Minimum Transaction Amount (AZN)',
        maxAmount: 'Maximum Transaction Amount (AZN)',
        allowSMS: 'Enable Single Message System (SMS)',
        allowDMS: 'Enable Dual Message System (DMS Hold)',
        autoSettlement: 'Enable Automatic Daily Settlement',
      },
      display: {
        title: 'Display & Regional Preferences',
        appearance: 'Appearance',
        theme: 'Theme Mode',
        themeLight: 'Light',
        themeDark: 'Dark',
        themeAuto: 'Auto (System)',
        language: 'System Language',
        regional: 'Regional Settings',
        dateFormat: 'Date Format',
        timezone: 'Timezone',
        currency: 'Default Currency',
      },
      api: {
        title: 'Developer & API Integration',
        apiKey: 'Production API Key',
        webhookUrl: 'Webhook Endpoint URL',
        secretKey: 'Webhook Secret Key',
        regenerate: 'Regenerate API Keys',
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
      noTerminalsWarning: '⚠️ No registered terminals found. Please create a company and terminal in Settings first.',
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
      cancelConfirmTitle: 'Cancel Payment Link?',
      cancelConfirmText: 'Are you sure you want to cancel this payment link? Customers will no longer be able to pay using it.',
      linkCancelledSuccess: 'Payment link cancelled successfully',
      statuses: {
        active: 'Active',
        paid: 'Paid',
        completed: 'Completed',
        expired: 'Expired',
        canceled: 'Canceled',
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
        search: 'Search by ID, customer or RRN...',
        paymentMethod: 'Payment Method',
        terminal: 'Terminal',
        clearFilters: 'Clear Filters',
      },
      statuses: {
        approved: 'Approved',
        failed: 'Failed',
        refunded: 'Refunded',
        pending: 'Pending',
        declined: 'Declined',
      },
      columns: {
        id: 'Transaction ID',
        date: 'Date & Time',
        amount: 'Amount',
        customer: 'Customer',
        status: 'Status',
        rrn: 'RRN',
        method: 'Method',
        terminal: 'Terminal',
        actions: 'Details',
      },
      detail: {
        title: 'Transaction Details',
        backToTransactions: 'Back to Transactions',
        refundAction: 'Process Refund',
        refundTitle: 'Issue Transaction Refund',
        refundAmount: 'Refund Amount (AZN)',
        confirmRefund: 'Confirm Refund',
        customerInfo: 'Customer Information',
        paymentInfo: 'Payment Breakdown',
        technicalInfo: 'Technical Gateway Info',
        approvalCode: 'Approval Code',
        providerOrderId: 'Provider Order ID',
        clientIp: 'Client IP Address',
      },
    },
    terminals: {
      title: 'Terminals',
      subtitle: 'Manage payment processing POS and E-commerce acquiring terminals.',
      addTerminal: 'Add Terminal',
      editTerminal: 'Edit Terminal',
      createDialogTitle: 'Create New Terminal',
      editDialogTitle: 'Edit Terminal Details',
      name: 'Terminal Name',
      terminalId: 'Numeric Terminal ID',
      login: 'Merchant Login ID',
      password: 'Terminal Password',
      location: 'Location',
      company: 'Assigned Company',
      status: 'Status',
      searchPlaceholder: 'Search terminals by name, ID or login...',
    },
    companies: {
      title: 'Companies',
      subtitle: 'Manage merchant companies and legal entities.',
      addCompany: 'Add Company',
      editCompany: 'Edit Company',
      createDialogTitle: 'Add New Merchant Company',
      editDialogTitle: 'Edit Company Details',
      companyId: 'Company Code / ID',
      name: 'Company Legal Name',
      email: 'Email',
      phone: 'Phone Number',
      taxId: 'Tax ID (VÖEN)',
      status: 'Status',
      searchPlaceholder: 'Search companies by ID or name...',
    },
    users: {
      title: 'Users',
      subtitle: 'Manage merchant portal staff users, roles and access permissions.',
      addUser: 'Add User',
      editUser: 'Edit User',
      createDialogTitle: 'Create Portal User',
      username: 'Username (Login ID)',
      password: 'Password',
      name: 'Full Name',
      email: 'Email',
      role: 'System Role',
      company: 'Assigned Company',
      status: 'Status',
      searchPlaceholder: 'Search users by name, login or email...',
      filterRole: 'Filter by Role',
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
      title: 'İdarəetmə Paneli',
      subtitle: 'Satış dinamikanız, əməliyyatlarınız və aktiv ödəniş kanallarınızın icmalı.',
      metrics: {
        totalSales: 'Ümumi Satış Həcmi',
        totalTransactions: 'Ümumi Əməliyyatlar',
        successRate: 'Ödəniş Uğurluluq Faizi',
        activeLinks: 'Aktiv Ödəniş Linkləri',
        vsLastMonth: 'ötən ayla müqayisədə',
      },
      charts: {
        salesOverview: 'Satış Trendi',
        paymentMethods: 'Ödəniş Üsullarının Bölgüsü',
      },
      recentTransactions: 'Son Əməliyyatlar',
      quickActions: {
        title: 'Cəld Əməliyyatlar',
        createLink: 'Ödəniş Linki Yarat',
        viewTransactions: 'Əməliyyatlara Bax',
        manageTerminals: 'Terminalları İdarə Et',
      },
    },
    settings: {
      title: 'Tənzimləmələr',
      subtitle: 'Ticarət obyektinizin parametrlərini, təhlükəsizliyini, bildirişlərini və görünüşünü idarə edin.',
      saveSuccess: 'Tənzimləmələr uğurla yadda saxlanıldı!',
      saveChanges: 'Dəyişiklikləri Yadda Saxla',
      tabs: {
        account: 'Hesab və Biznes',
        security: 'Təhlükəsizlik',
        notifications: 'Bildirişlər',
        payment: 'Ödəniş Üsulları',
        display: 'Görünüş və Region',
        api: 'API və Vebhaklar',
      },
      account: {
        title: 'Biznes Məlumatları',
        merchantName: 'Təşkilat / Şirkət Adı',
        merchantEmail: 'İş E-poçtu',
        businessPhone: 'Əlaqə Telefonu',
        taxId: 'VÖEN (Vergi Ödəyicisinin Kodu)',
        contactPerson: 'Əlaqələndirici Şəxs',
        businessAddress: 'Biznes Ünvanı',
      },
      security: {
        title: 'Təhlükəsizlik Tənzimləmələri',
        twoFactor: 'İkiamilli Doğrulama (2FA)',
        twoFactorDesc: 'Qorunmanı artırmaq üçün autentifikasiya tətbiqindən istifadə edin',
        changePassword: 'Şifrəni Dəyiş',
        passwordLastChanged: 'Şifrə son dəfə 30 gün əvvəl dəyişdirilib',
        sessionTimeout: 'Sessiyanın Qeyri-aktivlik Vaxtı',
      },
      notifications: {
        title: 'Bildiriş Seçimləri',
        emailHeader: 'E-poçt Bildirişləri',
        smsHeader: 'SMS Bildirişləri',
        newTransactions: 'Yeni uğurlu ödənişlər haqqında xəbərdar et',
        failedPayments: 'Uğursuz ödəniş cəhdləri haqqında xəbərdar et',
        dailySummary: 'Gündəlik xülasə hesabatını al',
        weeklySummary: 'Həftəlik analitika hesabatını al',
        smsHighValue: 'Böyük məbləğli ödənişlər (> 1000 AZN) üçün SMS göndər',
      },
      payment: {
        title: 'Ödəniş Qaydaları',
        minAmount: 'Minimum Ödəniş Məbləği (AZN)',
        maxAmount: 'Maksimum Ödəniş Məbləği (AZN)',
        allowSMS: 'Birfazalı Ödəniş Sisteminə (SMS) İcazə Ver',
        allowDMS: 'İkifazalı Ödəniş Sisteminə (DMS Depozit) İcazə Ver',
        autoSettlement: 'Avtomatik Gündəlik Klirinqi Aktivləşdir',
      },
      display: {
        title: 'Görünüş və Regional Parametrlər',
        appearance: 'Görünüş Rejimi',
        theme: 'Mövzu Rejimi',
        themeLight: 'Açıq (Light)',
        themeDark: 'Tünd (Dark)',
        themeAuto: 'Avto (Sistem)',
        language: 'Sistem Dili',
        regional: 'Regional Parametrlər',
        dateFormat: 'Tarix Formatı',
        timezone: 'Saat Qurşağı',
        currency: 'Əsas Valyuta',
      },
      api: {
        title: 'Tərtibatçı və API İnteqrasiyası',
        apiKey: 'İstehsalat API Açarı',
        webhookUrl: 'Vebhak URL Ünvanı',
        secretKey: 'Vebhak Məxfi Açarı',
        regenerate: 'API Açarını Yenilə',
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
      noTerminalsWarning: '⚠️ Sistemdə qeydiyyatdan keçmiş terminal tapılmadı. Əvvəlcə Tənzimləmələr bölməsində terminal əlavə edin.',
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
      cancelConfirmTitle: 'Ödəniş linki ləğv edilsin?',
      cancelConfirmText: 'Bu ödəniş linkini ləğv etmək istədiyinizdən əminsiniz? Müştərilər bundan sonra bu linklə ödəniş edə bilməyəcəklər.',
      linkCancelledSuccess: 'Ödəniş linki uğurla ləğv edildi',
      statuses: {
        active: 'Aktiv',
        paid: 'Ödənilib',
        completed: 'Tamamlanıb',
        expired: 'Müddəti bitib',
        canceled: 'Ləğv edilib',
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
        search: 'ID, müştəri və ya RRN üzrə axtarış...',
        paymentMethod: 'Ödəniş Üsulu',
        terminal: 'Terminal',
        clearFilters: 'Filtrləri Sıfırla',
      },
      statuses: {
        approved: 'Təsdiqləndi',
        failed: 'Uğursuz',
        refunded: 'Qaytarıldı',
        pending: 'Gözləmədə',
        declined: 'İmtina edildi',
      },
      columns: {
        id: 'Əməliyyat ID',
        date: 'Tarix və Vaxt',
        amount: 'Məbləğ',
        customer: 'Müştəri',
        status: 'Status',
        rrn: 'RRN',
        method: 'Üsul',
        terminal: 'Terminal',
        actions: 'Ətraflı',
      },
      detail: {
        title: 'Əməliyyat Təfərrüatları',
        backToTransactions: 'Əməliyyatlara Geri Dön',
        refundAction: 'Ödənişi Qaytar (Refund)',
        refundTitle: 'Məbləğin Qaytarılması',
        refundAmount: 'Qaytarılan Məbləğ (AZN)',
        confirmRefund: 'Qaytarılmanı Təsdiqlə',
        customerInfo: 'Müştəri Məlumatları',
        paymentInfo: 'Ödəniş Bölgüsü',
        technicalInfo: 'Texniki Əlaqə Məlumatı',
        approvalCode: 'Təsdiq Kodu (Approval Code)',
        providerOrderId: 'Provayder Sifariş ID',
        clientIp: 'Müştərinin IP Ünvanı',
      },
    },
    terminals: {
      title: 'Terminallar',
      subtitle: 'POS və E-ticarət ekvayrinq terminallarını idarə edin.',
      addTerminal: 'Terminal Əlavə Et',
      editTerminal: 'Terminalı Redaktə Et',
      createDialogTitle: 'Yeni Terminal Yarat',
      editDialogTitle: 'Terminal Parametrlərini Redaktə Et',
      name: 'Terminalın Adı',
      terminalId: 'Reqamli Terminal ID',
      login: 'Mərfəti Terminal Logini',
      password: 'Terminal Şifrəsi',
      location: 'Məkan',
      company: 'Təyin Olunmuş Şirkət',
      status: 'Status',
      searchPlaceholder: 'Ad, ID və ya login üzrə axtarış...',
    },
    companies: {
      title: 'Şirkətlər',
      subtitle: 'Ticarət şirkətlərini və hüquqi şəxsləri idarə edin.',
      addCompany: 'Şirkət Əlavə Et',
      editCompany: 'Şirkəti Redaktə Et',
      createDialogTitle: 'Yeni Şirkət Əlavə Et',
      editDialogTitle: 'Şirkət Məlumatlarını Redaktə Et',
      companyId: 'Şirkət Kodu / ID',
      name: 'Şirkətin Rəsmi Adı',
      email: 'E-poçt',
      phone: 'Telefon Nömrəsi',
      taxId: 'VÖEN',
      status: 'Status',
      searchPlaceholder: 'ID və ya ad üzrə axtarış...',
    },
    users: {
      title: 'İstifadəçilər',
      subtitle: 'Portal istifadəçilərini, rolları və icazələri idarə edin.',
      addUser: 'İstifadəçi Əlavə Et',
      editUser: 'İstifadəçini Redaktə Et',
      createDialogTitle: 'Portal İstifadəçisi Yarat',
      username: 'İstifadəçi Adı (Login)',
      password: 'Şifrə',
      name: 'Ad və Soyad',
      email: 'E-poçt',
      role: 'Sistem Rolu',
      company: 'Təyin Olunmuş Şirkət',
      status: 'Status',
      searchPlaceholder: 'Ad, login və ya e-poçt üzrə axtarış...',
      filterRole: 'Rola görə filtr',
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
      title: 'Панель управления',
      subtitle: 'Обзор объема продаж, проведенных транзакций и активных платежных каналов.',
      metrics: {
        totalSales: 'Общий объем продаж',
        totalTransactions: 'Всего транзакций',
        successRate: 'Успешность платежей %',
        activeLinks: 'Активные ссылки на оплату',
        vsLastMonth: 'по сравнению с прошлым месяцем',
      },
      charts: {
        salesOverview: 'Динамика продаж',
        paymentMethods: 'Разбивка по методам оплаты',
      },
      recentTransactions: 'Последние транзакции',
      quickActions: {
        title: 'Быстрые действия',
        createLink: 'Создать ссылку на оплату',
        viewTransactions: 'Просмотр транзакций',
        manageTerminals: 'Управление терминалами',
      },
    },
    settings: {
      title: 'Настройки',
      subtitle: 'Управление параметрами мерчанта, безопасностью, уведомлениями и интерфейсом.',
      saveSuccess: 'Настройки успешно сохранены!',
      saveChanges: 'Сохранить изменения',
      tabs: {
        account: 'Аккаунт и Бизнес',
        security: 'Безопасность',
        notifications: 'Уведомления',
        payment: 'Методы оплаты',
        display: 'Оформление и Регион',
        api: 'API и Вебхуки',
      },
      account: {
        title: 'Информация о бизнесе',
        merchantName: 'Название организации / Торговой точки',
        merchantEmail: 'Рабочий Email',
        businessPhone: 'Контактный телефон',
        taxId: 'ИНН / VÖEN',
        contactPerson: 'Контактное лицо',
        businessAddress: 'Адрес организации',
      },
      security: {
        title: 'Параметры безопасности',
        twoFactor: 'Двухфакторная аутентификация (2FA)',
        twoFactorDesc: 'Используйте приложение-аутентификатор для защиты входа',
        changePassword: 'Изменить пароль',
        passwordLastChanged: 'Пароль изменен 30 дней назад',
        sessionTimeout: 'Тайм-аут неактивности сессии',
      },
      notifications: {
        title: 'Настройки уведомлений',
        emailHeader: 'Email-уведомления',
        smsHeader: 'SMS-уведомления',
        newTransactions: 'Уведомлять о новых успешных платежах',
        failedPayments: 'Оповещать о неудачных попытках оплаты',
        dailySummary: 'Получать ежедневный сводный отчет',
        weeklySummary: 'Получать еженедельный аналитический отчет',
        smsHighValue: 'SMS-оповещения для крупных транзакций (> 1000 AZN)',
      },
      payment: {
        title: 'Правила обработки платежей',
        minAmount: 'Минимальная сумма транзакции (AZN)',
        maxAmount: 'Максимальная сумма транзакции (AZN)',
        allowSMS: 'Разрешить однофазную оплату (SMS)',
        allowDMS: 'Разрешить двухфазную оплату (DMS Холд)',
        autoSettlement: 'Включить автоматический ежедневный клиринг',
      },
      display: {
        title: 'Оформление и региональные настройки',
        appearance: 'Внешний вид',
        theme: 'Тема оформления',
        themeLight: 'Светлая',
        themeDark: 'Темная',
        themeAuto: 'Авто (Системная)',
        language: 'Язык системы',
        regional: 'Региональные настройки',
        dateFormat: 'Формат даты',
        timezone: 'Часовой пояс',
        currency: 'Основная валюта',
      },
      api: {
        title: 'Интеграция и API',
        apiKey: 'Продакшн API ключ',
        webhookUrl: 'URL-адрес вебхука',
        secretKey: 'Секретный ключ вебхука',
        regenerate: 'Обновить API ключи',
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
      noTerminalsWarning: '⚠️ В системе нет зарегистрированных терминалов. Сначала добавьте терминал в Настройках.',
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
      cancelConfirmTitle: 'Отменить платежную ссылку?',
      cancelConfirmText: 'Вы уверены, что хотите отменить эту ссылку? Клиенты больше не смогут провести по ней оплату.',
      linkCancelledSuccess: 'Ссылка на оплату успешно отменена',
      statuses: {
        active: 'Активна',
        paid: 'Оплачена',
        completed: 'Завершена',
        expired: 'Истекла',
        canceled: 'Отменена',
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
        search: 'Поиск по ID, клиенту или RRN...',
        paymentMethod: 'Метод оплаты',
        terminal: 'Терминал',
        clearFilters: 'Сбросить фильтры',
      },
      statuses: {
        approved: 'Одобрено',
        failed: 'Ошибка',
        refunded: 'Возврат',
        pending: 'В обработке',
        declined: 'Отклонено',
      },
      columns: {
        id: 'ID Транзакции',
        date: 'Дата и Время',
        amount: 'Сумма',
        customer: 'Клиент',
        status: 'Статус',
        rrn: 'RRN',
        method: 'Метод',
        terminal: 'Терминал',
        actions: 'Детали',
      },
      detail: {
        title: 'Детали транзакции',
        backToTransactions: 'Назад к транзакциям',
        refundAction: 'Оформить возврат (Refund)',
        refundTitle: 'Возврат средств по транзакции',
        refundAmount: 'Сумма возврата (AZN)',
        confirmRefund: 'Подтвердить возврат',
        customerInfo: 'Информация о клиенте',
        paymentInfo: 'Параметры платежа',
        technicalInfo: 'Техническая информация шлюза',
        approvalCode: 'Код одобрения (Approval Code)',
        providerOrderId: 'ID заказа провайдера',
        clientIp: 'IP адрес клиента',
      },
    },
    terminals: {
      title: 'Терминалы',
      subtitle: 'Управление эквайринговыми POS и E-commerce терминалами.',
      addTerminal: 'Добавить терминал',
      editTerminal: 'Редактировать терминал',
      createDialogTitle: 'Создать новый терминал',
      editDialogTitle: 'Редактировать параметры терминала',
      name: 'Название терминала',
      terminalId: 'Цифровой Terminal ID',
      login: 'Логин терминала мерчанта',
      password: 'Пароль терминала',
      location: 'Локация',
      company: 'Назначенная компания',
      status: 'Статус',
      searchPlaceholder: 'Поиск по названию, ID или логину...',
    },
    companies: {
      title: 'Компании',
      subtitle: 'Управление организациями и юридическими лицами.',
      addCompany: 'Добавить компанию',
      editCompany: 'Редактировать компанию',
      createDialogTitle: 'Добавить новую компанию',
      editDialogTitle: 'Редактировать данные компании',
      companyId: 'Код / ID Компании',
      name: 'Официальное название компании',
      email: 'Email',
      phone: 'Номер телефона',
      taxId: 'ИНН / VÖEN',
      status: 'Статус',
      searchPlaceholder: 'Поиск по ID или названию...',
    },
    users: {
      title: 'Пользователи',
      subtitle: 'Управление пользователями портала, ролями и правами доступа.',
      addUser: 'Добавить пользователя',
      editUser: 'Редактировать пользователя',
      createDialogTitle: 'Создать пользователя портала',
      username: 'Имя пользователя (Логин)',
      password: 'Пароль',
      name: 'ФИО',
      email: 'Email',
      role: 'Системная роль',
      company: 'Назначенная компания',
      status: 'Статус',
      searchPlaceholder: 'Поиск по имени, логину или email...',
      filterRole: 'Фильтр по роли',
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
    },
  },
};
