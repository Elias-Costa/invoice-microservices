import { HttpClient, HttpContext } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { IDEMPOTENT_POST } from '../../../core/interceptors/idempotency.interceptor';
import { ApiPage } from '../../../shared/models/api-page';
import {
  CreateCustomerRequest,
  CustomerDetailResponse,
  CustomerResponse,
  UpdateCustomerRequest,
} from '../../../shared/models/billing';

export const CUSTOMER_SORT_FIELDS = ['name', 'document', 'active'] as const;
type CustomerSortField = (typeof CUSTOMER_SORT_FIELDS)[number];

export interface CustomerQuery {
  search: string;
  active: boolean;
  page: number;
  size: number;
  sortField: CustomerSortField;
  sortDirection: 'asc' | 'desc';
}

@Injectable({ providedIn: 'root' })
export class CustomerApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/api/billing/customers`;

  list({ search, active, page, size, sortField, sortDirection }: CustomerQuery) {
    return this.http.get<ApiPage<CustomerResponse>>(this.baseUrl, {
      params: { search, active, page, size, sort: `${sortField},${sortDirection}` },
    });
  }

  get(id: string) {
    return this.http.get<CustomerDetailResponse>(this.customerUrl(id));
  }

  create(request: CreateCustomerRequest, key: string) {
    return this.http.post<CustomerResponse>(this.baseUrl, request, {
      context: new HttpContext().set(IDEMPOTENT_POST, key),
    });
  }

  update(id: string, request: UpdateCustomerRequest) {
    return this.http.put<CustomerResponse>(this.customerUrl(id), request);
  }

  setActive(id: string, active: boolean) {
    const url = this.customerUrl(id);
    return active
      ? this.http.post<CustomerResponse>(`${url}/reactivate`, null).pipe(map(() => undefined))
      : this.http.delete<void>(url);
  }

  private customerUrl(id: string): string {
    return `${this.baseUrl}/${encodeURIComponent(id)}`;
  }
}
