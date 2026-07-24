// Shared types and mock data for Pay by Link feature

export type LinkStatus = 'active' | 'paid' | 'expired' | 'cancelled';
export type LinkUsageType = 'single' | 'multiple';
export type PaymentType = 'sms' | 'dms';
export type DmsStatus = 'authorized' | 'finalized';

export interface PaymentLink {
  id: string;
  shortCode: string;
  url: string;
  status: LinkStatus;
  amount: number;
  currency: string;
  description: string;
  customerName: string;
  customerEmail: string;
  customerPhone: string;
  usageType: LinkUsageType;
  maxUses: number;
  usedCount: number;
  createdAt: Date;
  expiresAt: Date;
  paidAt?: Date;
  redirectUrl: string;
  note: string;
  // payment type
  paymentType: PaymentType;
  dmsStatus?: DmsStatus;   // only relevant when paymentType === 'dms' && status === 'paid'
  finalizedAt?: Date;
  terminalRid?: string;    // merchant terminal used to process the payment
  // enriched fields for detail view
  paymentMethod?: string;
  cardNetwork?: string;
  cardLast4?: string;
  transactionId?: string;
  payerIp?: string;
  sentVia?: ('email' | 'whatsapp' | 'copy')[];
}

const customers = [
  { name: 'Anar Mammadov',   email: 'anar.m@gmail.com',    phone: '+994501234567' },
  { name: 'Leyla Aliyeva',   email: 'leyla.a@mail.ru',      phone: '+994552345678' },
  { name: 'Rauf Hasanov',    email: 'rauf.h@outlook.com',   phone: '+994703456789' },
  { name: 'Nigar Guliyeva',  email: 'nigar.g@yahoo.com',    phone: '+994514567890' },
  { name: 'Tural Rzayev',    email: 'tural.r@gmail.com',    phone: '+994555678901' },
  { name: 'Sevinc Abbasova', email: 'sevinc.a@mail.ru',     phone: '+994706789012' },
  { name: 'Kamran Quliyev',  email: 'kamran.q@gmail.com',   phone: '+994517890123' },
  { name: 'Aysel Ismayilova',email: 'aysel.i@outlook.com',  phone: '+994558901234' },
];

const descriptions = [
  'Invoice #INV-2024-0891 — Software Subscription',
  'Order #ORD-5534 — Product Purchase',
  'Service Fee — Consulting 2h',
  'Invoice #INV-2024-0892 — Annual License',
  'Event Ticket — Tech Conference',
  'Order #ORD-5535 — Equipment Rental',
  'Invoice #INV-2024-0893 — Web Development',
  'Donation — Charity Fund',
];

const statuses: LinkStatus[] = ['active', 'active', 'paid', 'expired', 'cancelled', 'active', 'paid'];
const paymentMethods = ['Visa', 'Mastercard', 'Visa', 'Mastercard', 'AmEx'];
const cardNetworks = ['Visa', 'Mastercard', 'Visa', 'Mastercard', 'AmEx'];
const terminalRids = [
  'TRM-001-AZE', 'TRM-002-BAK', 'TRM-003-GNJ',
  'TRM-004-SMX', 'TRM-005-MNG', 'TRM-006-AZE',
  'TRM-007-BAK', 'TRM-008-GNJ', 'TRM-009-SMX', 'TRM-010-MNG',
];

// Merchant terminal registry with metadata shown in the selector
export const merchantTerminals: { rid: string; label: string; location: string; type: 'ecommerce' | 'pos' }[] = [
  { rid: 'TRM-001-AZE', label: 'Main Gateway — Baku HQ',         location: 'Baku',       type: 'ecommerce' },
  { rid: 'TRM-002-BAK', label: 'POS Terminal 1 — Baku Store',    location: 'Baku',       type: 'pos'       },
  { rid: 'TRM-003-GNJ', label: 'POS Terminal 2 — Ganja Branch',  location: 'Ganja',      type: 'pos'       },
  { rid: 'TRM-004-SMX', label: 'POS Terminal 3 — Sheki Branch',  location: 'Sheki',      type: 'pos'       },
  { rid: 'TRM-005-MNG', label: 'Online Store Gateway',           location: 'Virtual',    type: 'ecommerce' },
  { rid: 'TRM-006-AZE', label: 'Mobile POS — Field Sales',       location: 'Baku',       type: 'pos'       },
  { rid: 'TRM-007-BAK', label: 'POS Terminal 4 — Airport Kiosk', location: 'Baku',       type: 'pos'       },
  { rid: 'TRM-008-GNJ', label: 'Secondary Gateway',              location: 'Virtual',    type: 'ecommerce' },
  { rid: 'TRM-009-SMX', label: 'POS Terminal 5 — Sumqayit',      location: 'Sumqayit',   type: 'pos'       },
  { rid: 'TRM-010-MNG', label: 'POS Terminal 6 — Mingachevir',   location: 'Mingachevir',type: 'pos'       },
];

export const generateLinks = (): PaymentLink[] =>
  Array.from({ length: 18 }, (_, i) => {
    const status    = statuses[i % statuses.length];
    const customer  = customers[i % customers.length];
    const createdAt = new Date(Date.now() - (i + 1) * 3.2 * 60 * 60 * 1000);
    const expiresAt = new Date(createdAt.getTime() + (i % 3 === 0 ? 1 : i % 3 === 1 ? 24 : 168) * 60 * 60 * 1000);
    const shortCode = `PL${String(1000 + i).padStart(4, '0')}`;
    const isPaid    = status === 'paid';

    const paymentType: PaymentType = i % 3 === 0 ? 'dms' : 'sms';
    const isDms = paymentType === 'dms';
    const dmsStatus: DmsStatus | undefined = isPaid && isDms
      ? (i % 2 === 0 ? 'authorized' : 'finalized')
      : undefined;
    const finalizedAt = dmsStatus === 'finalized'
      ? new Date(createdAt.getTime() + 90 * 60 * 1000)
      : undefined;

    return {
      id:            `link-${String(i + 1).padStart(3, '0')}`,
      shortCode,
      url:           `https://pay.gateway.az/${shortCode}`,
      status,
      amount:        [49.99, 120.00, 250.50, 75.00, 890.00, 34.99, 1500.00, 299.99][i % 8],
      currency:      'AZN',
      description:   descriptions[i % descriptions.length],
      customerName:  customer.name,
      customerEmail: customer.email,
      customerPhone: customer.phone,
      usageType:     i % 4 === 0 ? 'multiple' : 'single',
      maxUses:       i % 4 === 0 ? 5 : 1,
      usedCount:     isPaid ? 1 : status === 'active' && i % 4 === 0 ? Math.floor(i / 4) : 0,
      createdAt,
      expiresAt,
      paidAt:        isPaid ? new Date(createdAt.getTime() + 45 * 60 * 1000) : undefined,
      redirectUrl:   'https://yourstore.az/thank-you',
      note:          i % 3 === 0 ? 'Customer requested link via phone' : '',
      paymentType,
      dmsStatus,
      finalizedAt,
      terminalRid: terminalRids[i % terminalRids.length],
      // payment detail fields
      paymentMethod: isPaid ? paymentMethods[i % paymentMethods.length] : undefined,
      cardNetwork:   isPaid ? cardNetworks[i % cardNetworks.length] : undefined,
      cardLast4:     isPaid ? String(1000 + (i * 37) % 9000) : undefined,
      transactionId: isPaid ? `TXN-${shortCode}-${String(i * 7 + 1001).padStart(6, '0')}` : undefined,
      payerIp:       isPaid ? `185.${i + 10}.${i * 3 + 1}.${i * 7 + 2}` : undefined,
      sentVia:       i % 2 === 0 ? ['email'] : i % 3 === 0 ? ['whatsapp', 'copy'] : ['copy'],
    };
  });

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

export const statusConfig: Record<LinkStatus, {
  label: string;
  color: 'success' | 'info' | 'default' | 'error';
  bgColor: string;
  textColor: string;
}> = {
  active:    { label: 'Active',    color: 'success', bgColor: 'rgba(46,125,50,0.1)',    textColor: '#2e7d32' },
  paid:      { label: 'Paid',      color: 'info',    bgColor: 'rgba(21,101,192,0.1)',   textColor: '#1565c0' },
  expired:   { label: 'Expired',   color: 'default', bgColor: 'rgba(0,0,0,0.06)',       textColor: '#546e7a' },
  cancelled: { label: 'Cancelled', color: 'error',   bgColor: 'rgba(198,40,40,0.1)',    textColor: '#c62828' },
};
