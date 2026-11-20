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
import { switchMap } from 'rxjs';
import { readState } from '../../../shared/http/read-state';
import { InvoiceResponse } from '../../../shared/models/billing';
import { CpfCnpjPipe } from '../../../shared/pipes/cpf-cnpj.pipe';
import { CancelInvoiceDialogComponent } from '../components/cancel-invoice-dialog.component';
import { InvoiceNumberPipe } from '../components/invoice-number.pipe';
import { InvoiceStatusChipComponent } from '../components/invoice-status-chip.component';
import { InvoiceApi } from '../data-access/invoice.api';

@Component({
  imports: [
    CpfCnpjPipe,
    CurrencyPipe,
    DatePipe,
    InvoiceNumberPipe,
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
  private readonly refresh = signal(0);
  private readonly canceledInvoice = signal<InvoiceResponse | null>(null);

  readonly id = input.required<string>();
  private readonly query = computed(() => ({ id: this.id(), refresh: this.refresh() }));
  private readonly loaded = toSignal(
    toObservable(this.query).pipe(
      switchMap(({ id }) =>
        readState(this.api.get(id), 'Não foi possível carregar a nota fiscal.'),
      ),
    ),
    { initialValue: { status: 'loading' } as const },
  );
  protected readonly state = computed(() => {
    const canceled = this.canceledInvoice();
    return canceled?.id === this.id()
      ? ({ status: 'ready', data: canceled } as const)
      : this.loaded();
  });

  protected reload(): void {
    this.refresh.update((count) => count + 1);
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
          this.canceledInvoice.set(canceled);
          this.snackbar.open('Nota fiscal cancelada.', 'Fechar', { duration: 3000 });
        }
      });
  }
}
