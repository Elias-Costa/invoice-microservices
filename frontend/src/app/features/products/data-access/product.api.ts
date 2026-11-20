import { HttpClient, HttpContext } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { IDEMPOTENT_POST } from '../../../core/interceptors/idempotency.interceptor';
import { ApiPage } from '../../../shared/models/api-page';
import {
  CreateProductRequest,
  ProductResponse,
  RegisterStockMovementRequest,
  StockMovementResponse,
  UpdateProductRequest,
} from '../../../shared/models/stock';

export const PRODUCT_SORT_FIELDS = [
  'code',
  'description',
  'unitPrice',
  'balance',
  'active',
] as const;
export type ProductSortField = (typeof PRODUCT_SORT_FIELDS)[number];
export type ProductSortDirection = 'asc' | 'desc';

export interface ProductQuery {
  search: string;
  active: boolean;
  page: number;
  size: number;
  sortField: ProductSortField;
  sortDirection: ProductSortDirection;
}

@Injectable({ providedIn: 'root' })
export class ProductApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/api/stock/products`;

  list({ search, active, page, size, sortField, sortDirection }: ProductQuery) {
    return this.http.get<ApiPage<ProductResponse>>(this.baseUrl, {
      params: { search, active, page, size, sort: `${sortField},${sortDirection}` },
    });
  }

  get(id: string) {
    return this.http.get<ProductResponse>(this.productUrl(id));
  }

  create(request: CreateProductRequest, key: string) {
    return this.http.post<ProductResponse>(this.baseUrl, request, {
      context: new HttpContext().set(IDEMPOTENT_POST, key),
    });
  }

  update(id: string, request: UpdateProductRequest) {
    return this.http.put<ProductResponse>(this.productUrl(id), request);
  }

  setActive(id: string, active: boolean) {
    const url = this.productUrl(id);
    if (active) {
      return this.http.post<ProductResponse>(`${url}/reactivate`, null).pipe(map(() => undefined));
    }

    return this.http.delete<void>(url);
  }

  listMovements(id: string, page: number, size: number) {
    return this.http.get<ApiPage<StockMovementResponse>>(`${this.productUrl(id)}/movements`, {
      params: { page, size },
    });
  }

  registerMovement(id: string, request: RegisterStockMovementRequest, key: string) {
    return this.http.post<StockMovementResponse>(`${this.productUrl(id)}/movements`, request, {
      context: new HttpContext().set(IDEMPOTENT_POST, key),
    });
  }

  private productUrl(id: string): string {
    return `${this.baseUrl}/${encodeURIComponent(id)}`;
  }
}
