import { ApiPage } from '../../shared/models/api-page';
import { CustomerDetailResponse, CustomerResponse } from '../../shared/models/billing';

export const customersPath = '/api/billing/customers';
export const customer: CustomerDetailResponse = {
  id: 'c0000000-0000-4000-8000-000000000001',
  name: 'Oficina Silva',
  document: '11222333000181',
  documentType: 'CNPJ',
  email: 'contato@example.com',
  phone: '5511987654321',
  active: true,
  version: 4,
  recentInvoices: [
    {
      id: 'invoice-2',
      number: 2,
      status: 'CLOSED',
      total: 23.5,
      createdAt: '2026-11-20T12:00:00Z',
    },
    {
      id: 'invoice-1',
      number: 1,
      status: 'CANCELED',
      total: 10,
      createdAt: '2026-11-19T12:00:00Z',
    },
  ],
};
export const customerPath = `${customersPath}/${customer.id}`;
export const problemHeaders = { 'Content-Type': 'application/problem+json' };

export function customerPage(
  content: CustomerResponse[],
  totalElements = content.length,
): ApiPage<CustomerResponse> {
  return {
    content,
    page: { number: 0, size: 20, totalElements, totalPages: Math.ceil(totalElements / 20) },
  };
}
