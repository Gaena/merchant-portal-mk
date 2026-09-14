import * as XLSX from 'xlsx';
import { formatDateTime } from './format';
import type { EcomOrder } from '../types/ecom';
import { translations } from '../i18n/translations';

// Выгрузки операций портала здесь больше нет: страница списка операций снята (Р-65 — операции
// портала показываются только под платёжными ссылками), а вместе с ней и её Excel.

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
    'Currency': order.currency ?? '',
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
