import * as XLSX from 'xlsx';
import type { Transaction } from '../types/transaction';
import { formatCurrency, formatDateTime, getPaymentMethodLabel, getStatusLabel } from './mockData';

export function exportTransactionsToExcel(transactions: Transaction[], filename: string = 'transactions') {
  // Prepare data for Excel
  const excelData = transactions.map(txn => ({
    'Transaction ID': txn.id,
    'Date & Time': formatDateTime(txn.timestamp),
    'Customer Name': txn.customer,
    'Customer Email': txn.customerEmail,
    'Amount': txn.amount,
    'Currency': txn.currency,
    'Formatted Amount': formatCurrency(txn.amount, txn.currency),
    'Status': getStatusLabel(txn.status),
    'Payment Method': getPaymentMethodLabel(txn.paymentMethod),
    'Description': txn.description,
    'Merchant Reference': txn.merchantReference || 'N/A',
    'Provider Order ID': txn.providerOrderId || 'N/A',
    'Card Last 4 Digits': txn.cardLast4 || 'N/A'
  }));

  // Create worksheet
  const worksheet = XLSX.utils.json_to_sheet(excelData);

  // Set column widths
  const columnWidths = [
    { wch: 25 }, // Transaction ID
    { wch: 22 }, // Date & Time
    { wch: 20 }, // Customer Name
    { wch: 30 }, // Customer Email
    { wch: 12 }, // Amount
    { wch: 10 }, // Currency
    { wch: 15 }, // Formatted Amount
    { wch: 12 }, // Status
    { wch: 18 }, // Payment Method
    { wch: 30 }, // Description
    { wch: 20 }, // Merchant Reference
    { wch: 25 }, // Provider Order ID
    { wch: 18 }  // Card Last 4 Digits
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
