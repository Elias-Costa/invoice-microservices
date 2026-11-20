import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { environment } from '../../../../environments/environment';
import { IDEMPOTENT_POST } from '../../../core/interceptors/idempotency.interceptor';
import { ApiPage } from '../../../shared/models/api-page';
import {
  CancelInvoiceRequest,
  CreateInvoiceRequest,
  InvoiceResponse,
  InvoiceStatus,
  InvoiceSummaryResponse,
  ReplaceInvoiceItemsRequest,
} from '../../../shared/models/billing';

export interface InvoiceFilters {
  status?: InvoiceStatus;
  customerId?: string;
  from?: string;
  to?: string;
}

export interface InvoiceQuery extends InvoiceFilters {
  page: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class InvoiceApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/api/billing/invoices`;

  list({ page, size, ...filters }: InvoiceQuery) {
    let params = new HttpParams({ fromObject: { page, size, sort: 'number,desc' } });
    for (const [key, value] of Object.entries(filters)) {
      if (value) {
        params = params.set(key, value);
      }
    }
    return this.http.get<ApiPage<InvoiceSummaryResponse>>(this.baseUrl, { params });
  }

  get(id: string) {
    return this.http.get<InvoiceResponse>(this.invoiceUrl(id));
  }

  create(request: CreateInvoiceRequest, key: string) {
    return this.http.post<InvoiceResponse>(this.baseUrl, request, {
      context: new HttpContext().set(IDEMPOTENT_POST, key),
    });
  }

  replaceItems(id: string, request: ReplaceInvoiceItemsRequest) {
    return this.http.put<InvoiceResponse>(`${this.invoiceUrl(id)}/items`, request);
  }

  cancel(id: string, request: CancelInvoiceRequest) {
    return this.http.post<InvoiceResponse>(`${this.invoiceUrl(id)}/cancel`, request);
  }

  private invoiceUrl(id: string): string {
    return `${this.baseUrl}/${encodeURIComponent(id)}`;
  }
}
