import * as XLSX from 'xlsx';
import type { Transaction } from '../types/transaction';
import { formatCurrency, formatDateTime, getPaymentMethodLabel, getStatusLabel } from './mockData';
import { terminalLabel } from './terminals';

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
