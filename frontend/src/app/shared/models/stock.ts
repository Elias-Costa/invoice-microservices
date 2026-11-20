export interface BatchProductResponse {
  id: string;
  code: string;
  description: string;
  unitPrice: number;
  balance: number;
  active: boolean;
}

export interface ProductResponse extends BatchProductResponse {
  version: number;
}

export interface CreateProductRequest {
  code: string;
  description: string;
  unitPrice: number;
  initialBalance: number;
}

export interface UpdateProductRequest {
  description: string;
  unitPrice: number;
  version: number;
}

type ManualMovementType = 'INBOUND' | 'OUTBOUND';

export interface StockMovementResponse {
  id: string;
  productId: string;
  type: ManualMovementType | 'INITIAL' | 'INVOICE_DEBIT';
  quantity: number;
  balanceAfter: number;
  reason: string | null;
  referenceId: string | null;
  referenceNumber: number | null;
  createdBy: string;
  createdAt: string;
}

export interface RegisterStockMovementRequest {
  type: ManualMovementType;
  quantity: number;
  reason: string;
}
