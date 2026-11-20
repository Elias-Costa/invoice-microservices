import {
  CustomerResponse,
  InvoiceResponse,
  InvoiceSummaryResponse,
} from '../../shared/models/billing';
import { ProductResponse } from '../../shared/models/stock';

export const invoicesPath = '/api/billing/invoices';
export const invoicePath = `${invoicesPath}/invoice-1`;
export const customersPath = '/api/billing/customers';
export const productsPath = '/api/stock/products';

export const customer: CustomerResponse = {
  id: 'customer-1',
  name: 'Cliente de teste',
  document: '52998224725',
  documentType: 'CPF',
  email: null,
  phone: null,
  active: true,
  version: 0,
};

export const product: ProductResponse = {
  id: 'product-1',
  code: 'PAR-M6',
  description: 'Parafuso',
  unitPrice: 0.35,
  balance: 2,
  active: true,
  version: 0,
};

export const invoice: InvoiceResponse = {
  id: 'invoice-1',
  number: 12,
  status: 'OPEN',
  customer,
  items: [
    {
      id: 'item-1',
      productId: product.id,
      productCode: product.code,
      productDescription: product.description,
      unitPrice: 0.3,
      quantity: 2,
      lineTotal: 0.6,
      lineOrder: 1,
    },
  ],
  total: 0.6,
  createdAt: '2026-11-20T09:00:00Z',
  closedAt: null,
  canceledAt: null,
  lastFailureReason: null,
  cancelReason: null,
  version: 4,
  allowedActions: ['EDIT_ITEMS', 'CANCEL', 'PRINT'],
};

export const summary: InvoiceSummaryResponse = {
  id: invoice.id,
  number: invoice.number,
  status: invoice.status,
  customerId: customer.id,
  customerName: customer.name,
  total: invoice.total,
  createdAt: invoice.createdAt,
};

export function page<T>(content: T[], totalElements = content.length) {
  return {
    content,
    page: { number: 0, size: 20, totalElements, totalPages: Math.ceil(totalElements / 20) },
  };
}

export const problemHeaders = { 'Content-Type': 'application/problem+json' };
