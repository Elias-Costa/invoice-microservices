import { inject, Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import {
  catchError,
  concat,
  filter,
  map,
  Observable,
  of,
  switchMap,
  takeWhile,
  tap,
  timeout,
  TimeoutError,
  timer,
} from 'rxjs';
import { InvoiceResponse } from '../../../shared/models/billing';
import { InvoiceApi } from './invoice.api';

export type PrintFeedback = 'processing' | 'timed-out' | 'unavailable';

type PrintUpdate = { status: PrintFeedback } | { status: 'finished'; invoice: InvoiceResponse };

@Injectable({ providedIn: 'root' })
export class InvoicePrinting {
  private readonly api = inject(InvoiceApi);
  private readonly snackbar = inject(MatSnackBar);

  start(id: string): Observable<PrintUpdate> {
    return this.api
      .print(id)
      .pipe(
        switchMap((accepted) =>
          concat(of({ status: 'processing' } as const), this.poll(accepted.id)),
        ),
      );
  }

  private poll(id: string): Observable<PrintUpdate> {
    return timer(0, 1500).pipe(
      switchMap(() => this.api.get(id)),
      takeWhile((invoice) => invoice.status === 'PROCESSING', true),
      // Only the outcome satisfies the deadline; PROCESSING responses must not reset it.
      filter((invoice) => invoice.status !== 'PROCESSING'),
      timeout({ first: 30_000 }),
      tap((invoice) => {
        if (invoice.status === 'CLOSED') {
          this.snackbar.open('Nota fiscal fechada com sucesso.', 'Fechar', { duration: 3000 });
        }
      }),
      map((invoice): PrintUpdate => ({ status: 'finished', invoice })),
      catchError((error: unknown) =>
        of({ status: error instanceof TimeoutError ? 'timed-out' : 'unavailable' } as const),
      ),
    );
  }
}
