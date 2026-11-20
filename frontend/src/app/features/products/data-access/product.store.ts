import { inject, Injectable, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs';
import { ProductApi, ProductQuery, ProductSortDirection, ProductSortField } from './product.api';
import { productReadState } from './product-read-state';

@Injectable()
export class ProductStore {
  private readonly api = inject(ProductApi);
  private readonly criteria = signal<ProductQuery>({
    search: '',
    active: true,
    page: 0,
    size: 20,
    sortField: 'code',
    sortDirection: 'asc',
  });

  readonly query = this.criteria.asReadonly();
  readonly result = toSignal(
    toObservable(this.query).pipe(
      switchMap((query) =>
        productReadState(this.api.list(query), 'Não foi possível carregar os produtos.'),
      ),
    ),
    { initialValue: { status: 'loading' } as const },
  );

  setSearch(search: string): void {
    this.criteria.update((query) =>
      query.search === search ? query : { ...query, search, page: 0 },
    );
  }

  setActive(active: boolean): void {
    this.criteria.update((query) => ({ ...query, active, page: 0 }));
  }

  setSort(sortField: ProductSortField, sortDirection: ProductSortDirection): void {
    this.criteria.update((query) => ({ ...query, sortField, sortDirection, page: 0 }));
  }

  setPage(page: number, size: number): void {
    this.criteria.update((query) => ({ ...query, page, size }));
  }

  reload(): void {
    this.criteria.update((query) => ({ ...query }));
  }
}
