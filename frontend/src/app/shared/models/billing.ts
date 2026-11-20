export type InvoiceStatus = 'OPEN' | 'PROCESSING' | 'CLOSED' | 'CANCELED';

export interface CustomerResponse {
  id: string;
  name: string;
  document: string;
  documentType: 'CPF' | 'CNPJ';
  email: string | null;
  phone: string | null;
  active: boolean;
  version: number;
}

export interface CustomerDetailResponse extends CustomerResponse {
  recentInvoices: Pick<
    InvoiceSummaryResponse,
    'id' | 'number' | 'status' | 'total' | 'createdAt'
  >[];
}

interface CustomerContactRequest {
  name: string;
  email?: string | null;
  phone?: string | null;
}

export interface CreateCustomerRequest extends CustomerContactRequest {
  document: string;
}

export interface UpdateCustomerRequest extends CustomerContactRequest {
  version: number;
}

export interface InvoiceSummaryResponse {
  id: string;
  number: number;
  status: InvoiceStatus;
  customerId: string;
  customerName: string;
  total: number;
  createdAt: string;
}

export interface InvoiceResponse {
  id: string;
  number: number;
  status: InvoiceStatus;
  customer: Pick<CustomerResponse, 'id' | 'name' | 'document'>;
  items: InvoiceItemResponse[];
  total: number;
  createdAt: string;
  closedAt: string | null;
  canceledAt: string | null;
  lastFailureReason: string | null;
  cancelReason: string | null;
  version: number;
  allowedActions: ('EDIT_ITEMS' | 'CANCEL' | 'PRINT')[];
}

interface InvoiceItemResponse {
  id: string;
  productId: string;
  productCode: string;
  productDescription: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  lineOrder: number;
}

interface InvoiceItemRequest {
  productId: string;
  quantity: number;
}

export interface CreateInvoiceRequest {
  customerId: string;
  items: InvoiceItemRequest[];
}

export interface ReplaceInvoiceItemsRequest {
  items: InvoiceItemRequest[];
  version: number;
}

export interface CancelInvoiceRequest {
  reason: string;
}

export type PrintInvoiceResponse = Pick<InvoiceResponse, 'id' | 'status'>;
