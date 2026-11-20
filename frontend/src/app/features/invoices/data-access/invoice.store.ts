import { inject, Injectable, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs';
import { readState } from '../../../shared/http/read-state';
import { InvoiceApi, InvoiceFilters, InvoiceQuery } from './invoice.api';

@Injectable()
export class InvoiceStore {
  private readonly api = inject(InvoiceApi);
  private readonly criteria = signal<InvoiceQuery>({ page: 0, size: 20 });

  readonly query = this.criteria.asReadonly();
  readonly result = toSignal(
    toObservable(this.query).pipe(
      switchMap((query) => readState(this.api.list(query), 'Não foi possível carregar as notas.')),
    ),
    { initialValue: { status: 'loading' } as const },
  );

  setFilters(filters: InvoiceFilters): void {
    this.criteria.update((query) => ({ ...query, ...filters, page: 0 }));
  }

  setPage(page: number, size: number): void {
    this.criteria.update((query) => ({ ...query, page, size }));
  }

  reload(): void {
    this.criteria.update((query) => ({ ...query }));
  }
}
