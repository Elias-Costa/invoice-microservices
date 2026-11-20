import { inject, Injectable, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs';
import { readState } from '../../../shared/http/read-state';
import { CustomerApi, CustomerQuery } from './customer.api';

@Injectable()
export class CustomerStore {
  private readonly api = inject(CustomerApi);
  private readonly criteria = signal<CustomerQuery>({
    search: '',
    active: true,
    page: 0,
    size: 20,
    sortField: 'name',
    sortDirection: 'asc',
  });

  readonly query = this.criteria.asReadonly();
  readonly result = toSignal(
    toObservable(this.query).pipe(
      switchMap((query) =>
        readState(this.api.list(query), 'Não foi possível carregar os clientes.'),
      ),
    ),
    { initialValue: { status: 'loading' } as const },
  );

  setSearch(search: string): void {
    this.criteria.update((query) =>
      search === query.search ? query : { ...query, search, page: 0 },
    );
  }

  setActive(active: boolean): void {
    this.criteria.update((query) => ({ ...query, active, page: 0 }));
  }

  setSort(
    sortField: CustomerQuery['sortField'],
    sortDirection: CustomerQuery['sortDirection'],
  ): void {
    this.criteria.update((query) => ({ ...query, sortField, sortDirection, page: 0 }));
  }

  setPage(page: number, size: number): void {
    this.criteria.update((query) => ({ ...query, page, size }));
  }

  reload(): void {
    this.criteria.update((query) => ({ ...query }));
  }
}
