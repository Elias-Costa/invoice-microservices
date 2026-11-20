import { CurrencyPipe, DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { filter, switchMap, takeUntil, tap } from 'rxjs';
import { readState } from '../../../shared/http/read-state';
import { InvoiceResponse } from '../../../shared/models/billing';
import { CpfCnpjPipe } from '../../../shared/pipes/cpf-cnpj.pipe';
import { CancelInvoiceDialogComponent } from '../components/cancel-invoice-dialog.component';
import { InvoiceNumberPipe } from '../components/invoice-number.pipe';
import { InvoicePrintFeedbackComponent } from '../components/invoice-print-feedback.component';
import { InvoiceStatusChipComponent } from '../components/invoice-status-chip.component';
import { InvoicePrinting, PrintFeedback } from '../data-access/invoice-printing';
import { InvoiceApi } from '../data-access/invoice.api';

@Component({
  imports: [
    CpfCnpjPipe,
    CurrencyPipe,
    DatePipe,
    InvoiceNumberPipe,
    InvoicePrintFeedbackComponent,
    InvoiceStatusChipComponent,
    MatButtonModule,
    MatCardModule,
    RouterLink,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './invoice-detail.component.html',
  styleUrl: './invoice-detail.component.scss',
})
export class InvoiceDetailComponent {
  private readonly api = inject(InvoiceApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);
  private readonly snackbar = inject(MatSnackBar);
  private readonly printing = inject(InvoicePrinting);
  private readonly refresh = signal(0);
  private readonly updatedInvoice = signal<InvoiceResponse | null>(null);

  readonly id = input.required<string>();
  private readonly ids = toObservable(this.id);
  protected readonly printFeedback = signal<PrintFeedback | 'starting' | null>(null);
  private readonly query = computed(() => ({ id: this.id(), refresh: this.refresh() }));
  private readonly loaded = toSignal(
    toObservable(this.query).pipe(
      tap(() => {
        this.updatedInvoice.set(null);
        this.printFeedback.set(null);
      }),
      switchMap(({ id }) =>
        readState(this.api.get(id), 'Não foi possível carregar a nota fiscal.'),
      ),
    ),
    { initialValue: { status: 'loading' } as const },
  );
  protected readonly state = computed(() => {
    const updated = this.updatedInvoice();
    return updated?.id === this.id()
      ? ({ status: 'ready', data: updated } as const)
      : this.loaded();
  });

  protected reload(): void {
    this.refresh.update((count) => count + 1);
  }

  protected print(invoice: InvoiceResponse): void {
    if (
      this.printFeedback() ||
      invoice.status !== 'OPEN' ||
      !invoice.allowedActions.includes('PRINT')
    ) {
      return;
    }

    this.printFeedback.set('starting');
    this.printing
      .start(invoice.id)
      .pipe(
        takeUntil(this.ids.pipe(filter((id) => id !== invoice.id))),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (update) => {
          if (update.status === 'finished') {
            this.updatedInvoice.set(update.invoice);
            this.printFeedback.set(null);
            return;
          }

          if (update.status === 'processing') {
            this.updatedInvoice.set({ ...invoice, status: 'PROCESSING', allowedActions: [] });
          }
          this.printFeedback.set(update.status);
        },
        error: () => this.printFeedback.set(null),
      });
  }

  protected cancel(invoice: InvoiceResponse): void {
    this.dialog
      .open<CancelInvoiceDialogComponent, Pick<InvoiceResponse, 'id' | 'number'>, InvoiceResponse>(
        CancelInvoiceDialogComponent,
        { data: { id: invoice.id, number: invoice.number }, width: '30rem' },
      )
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((canceled) => {
        if (canceled) {
          this.updatedInvoice.set(canceled);
          this.snackbar.open('Nota fiscal cancelada.', 'Fechar', { duration: 3000 });
        }
      });
  }
}
