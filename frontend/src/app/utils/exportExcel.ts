import * as XLSX from 'xlsx';
import type { Transaction } from '../types/transaction';
import { formatCurrency, formatDateTime, getPaymentMethodLabel, getStatusLabel } from './mockData';
import { terminalLabel } from './terminals';
import type { EcomOrder } from '../types/ecom';
import { translations } from '../i18n/translations';

// `merchantReference` (`merchantOrderId` из ответа) убран из интерфейса 11.09.2026: портал не
// спрашивает номер заказа при создании ссылки, поэтому у всех созданных через него ссылок поле
// пустое, а на экране оно значило пустую строку, «N/A» в выгрузке и ветку поиска, которая ничего
// не находила. В API поле осталось — вернётся на экран вместе с полем в форме создания ссылки.

export function exportTransactionsToExcel(transactions: Transaction[], filename: string = 'transactions') {
  // Prepare data for Excel
  // Порядок колонок — порядок таблицы на экране: то, по чему мерчант опознаёт платёж,
  // идёт первым, внутренний идентификатор операции — последним.
  const excelData = transactions.map(txn => ({
    'Provider Order ID': txn.providerOrderId || 'N/A',
    'RID by merchant': txn.ridByMerchant || 'N/A',
    // Терминал подписан логином — тем же, что и на экране.
    'Terminal Login': terminalLabel(txn),
    'Date & Time': formatDateTime(txn.timestamp),
    'Customer Name': txn.customer,
    'Customer Email': txn.customerEmail,
    'Amount': txn.amount,
    'Currency': txn.currency,
    'Formatted Amount': formatCurrency(txn.amount, txn.currency),
    'Status': getStatusLabel(txn.status, txn.statusRaw),
    'Payment Method': getPaymentMethodLabel(txn.paymentMethod),
    'Description': txn.description,
    'Card Last 4 Digits': txn.cardLast4 || 'N/A',
    'Transaction ID': txn.id
  }));

  // Create worksheet
  const worksheet = XLSX.utils.json_to_sheet(excelData);

  // Set column widths
  const columnWidths = [
    { wch: 25 }, // Provider Order ID
    { wch: 38 }, // RID by merchant
    { wch: 22 }, // Terminal Login
    { wch: 22 }, // Date & Time
    { wch: 20 }, // Customer Name
    { wch: 30 }, // Customer Email
    { wch: 12 }, // Amount
    { wch: 10 }, // Currency
    { wch: 15 }, // Formatted Amount
    { wch: 12 }, // Status
    { wch: 18 }, // Payment Method
    { wch: 30 }, // Description
    { wch: 18 }, // Card Last 4 Digits
    { wch: 38 }  // Transaction ID
  ];
  worksheet['!cols'] = columnWidths;

  // Create workbook
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Transactions');

  // Generate filename with timestamp
  const timestamp = new Date().toISOString().split('T')[0];
  const fullFilename = `${filename}_${timestamp}.xlsx`;

  // Download file
  XLSX.writeFile(workbook, fullFilename);
}

/**
 * Выгрузка выписки провайдера: строки, **уже загруженные** на экран, — выписка курсорная, и всего
 * периода у страницы нет. Подпись статуса английская, как и в выгрузке операций портала.
 */
export function exportEcomOrdersToExcel(
  orders: EcomOrder[],
  terminalOf: (order: EcomOrder) => string,
  filename: string = 'ecommerce_statement'
) {
  const excelData = orders.map(order => ({
    'Provider Order ID': order.orderId,
    'RID by merchant': order.ridByMerchant || '',
    'Terminal': terminalOf(order),
    'Created': order.createdAt ? formatDateTime(order.createdAt) : '',
    'Status': order.status ? translations.en.ecommerce.statuses[order.status] : order.statusRaw || '',
    'Provider Status': order.providerStatus || '',
    'Order Amount': order.amount ?? '',
    'Captured': order.capturedAmount,
    'Refunded': order.refundedAmount,
    'Currency': order.currency,
    'Card': order.cardMask || '',
    'RRN': order.rrn || '',
    'Decline Code': order.declineCode || '',
    'Description': order.description || '',
  }));

  const worksheet = XLSX.utils.json_to_sheet(excelData);
  worksheet['!cols'] = [
    { wch: 18 }, { wch: 38 }, { wch: 22 }, { wch: 16 }, { wch: 20 }, { wch: 16 }, { wch: 14 },
    { wch: 12 }, { wch: 12 }, { wch: 10 }, { wch: 20 }, { wch: 16 }, { wch: 16 }, { wch: 30 },
  ];
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Statement');
  XLSX.writeFile(workbook, `${filename}_${new Date().toISOString().split('T')[0]}.xlsx`);
}
