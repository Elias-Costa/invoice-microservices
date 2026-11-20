import { ApiPage } from '../../shared/models/api-page';
import { ProductResponse, StockMovementResponse } from '../../shared/models/stock';

export const productsPath = '/api/stock/products';
export const problemHeaders = { 'Content-Type': 'application/problem+json' };
export const product: ProductResponse = {
  id: 'a0000000-0000-4000-8000-000000000009',
  code: 'BRO-ACO-8',
  description: 'Broca aço rápido 8 mm',
  unitPrice: 8.75,
  balance: 0,
  active: true,
  version: 7,
};
export const productPath = `${productsPath}/${product.id}`;
export const historyPath = `${productPath}/movements`;
export const movement: StockMovementResponse = {
  id: 'movement-id',
  productId: product.id,
  type: 'INBOUND',
  quantity: 3,
  balanceAfter: 3,
  reason: 'Reposição de teste',
  referenceId: null,
  referenceNumber: null,
  createdBy: 'anonymous',
  createdAt: '2026-11-20T14:30:00Z',
};

export function page<T>(
  content: T[],
  totalElements = content.length,
  number = 0,
  size = 20,
): ApiPage<T> {
  return {
    content,
    page: { number, size, totalElements, totalPages: Math.ceil(totalElements / size) },
  };
}
