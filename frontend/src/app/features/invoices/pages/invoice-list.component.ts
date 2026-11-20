import { CurrencyPipe, DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatDatepickerIntl, MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { InvoiceStatus, InvoiceSummaryResponse } from '../../../shared/models/billing';
import { InvoiceCustomerPickerComponent } from '../components/invoice-customer-picker.component';
import { InvoiceNumberPipe } from '../components/invoice-number.pipe';
import { InvoicePrintFeedbackComponent } from '../components/invoice-print-feedback.component';
import {
  INVOICE_STATUS_LABELS,
  InvoiceStatusChipComponent,
} from '../components/invoice-status-chip.component';
import { InvoicePrinting, PrintFeedback } from '../data-access/invoice-printing';
import { InvoiceStore } from '../data-access/invoice.store';

type PrintAttempt = Pick<InvoiceSummaryResponse, 'id' | 'number'> &
  ({ status: 'starting' | PrintFeedback } | { status: 'rejected'; reason: string });

function datepickerLabels(): MatDatepickerIntl {
  return Object.assign(new MatDatepickerIntl(), {
    calendarLabel: 'Calendário',
    openCalendarLabel: 'Abrir calendário',
    closeCalendarLabel: 'Fechar calendário',
    prevMonthLabel: 'Mês anterior',
    nextMonthLabel: 'Próximo mês',
    prevYearLabel: 'Ano anterior',
    nextYearLabel: 'Próximo ano',
    prevMultiYearLabel: 'Período de anos anterior',
    nextMultiYearLabel: 'Próximo período de anos',
    switchToMonthViewLabel: 'Escolher data',
    switchToMultiYearViewLabel: 'Escolher mês e ano',
  });
}

@Component({
  imports: [
    CurrencyPipe,
    DatePipe,
    InvoiceCustomerPickerComponent,
    InvoiceNumberPipe,
    InvoicePrintFeedbackComponent,
    InvoiceStatusChipComponent,
    MatButtonModule,
    MatCardModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatSelectModule,
    MatTableModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  providers: [
    InvoiceStore,
    provideNativeDateAdapter(),
    { provide: MatDatepickerIntl, useFactory: datepickerLabels },
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './invoice-list.component.html',
  styleUrl: './invoice-list.component.scss',
})
export class InvoiceListComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly printing = inject(InvoicePrinting);
  private readonly attempts = signal(new Map<string, PrintAttempt>());
  protected readonly store = inject(InvoiceStore);
  protected readonly printAttempts = this.attempts.asReadonly();
  protected readonly statuses = Object.entries(INVOICE_STATUS_LABELS);
  protected readonly from = new FormControl<Date | null>(null);
  protected readonly to = new FormControl<Date | null>(null);
  protected readonly columns = ['number', 'customer', 'status', 'createdAt', 'total', 'actions'];
  protected readonly page = computed(() => {
    const result = this.store.result();
    return result.status === 'ready' ? result.data : undefined;
  });

  protected filterStatus(status: InvoiceStatus | ''): void {
    this.store.setFilters({ status: status || undefined });
  }

  protected filterPeriod(): void {
    this.store.setFilters({
      from: this.from.value?.toISOString(),
      to: this.to.value?.toISOString(),
    });
  }

  protected clearDate(control: FormControl<Date | null>): void {
    control.reset();
    this.filterPeriod();
  }

  protected invoiceId(_index: number, invoice: InvoiceSummaryResponse): string {
    return invoice.id;
  }

  protected print(invoice: InvoiceSummaryResponse): void {
    const attempt = this.attempts().get(invoice.id);
    if (invoice.status !== 'OPEN' || (attempt && attempt.status !== 'rejected')) {
      return;
    }

    const { id, number } = invoice;
    this.setAttempt(id, { id, number, status: 'starting' });
    this.printing
      .start(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (update) => {
          if (update.status === 'finished') {
            const reason = update.invoice.lastFailureReason;
            this.setAttempt(
              id,
              update.invoice.status === 'OPEN' && reason
                ? { id, number, status: 'rejected', reason }
                : undefined,
            );
            this.store.reload();
            return;
          }

          this.setAttempt(id, { id, number, status: update.status });
          if (update.status === 'processing') {
            this.store.reload();
          }
        },
        error: () => this.setAttempt(id),
      });
  }

  protected reloadAfterPrint(id: string): void {
    this.setAttempt(id);
    this.store.reload();
  }

  private setAttempt(id: string, attempt?: PrintAttempt): void {
    this.attempts.update((previous) => {
      const next = new Map(previous);
      if (attempt) {
        next.set(id, attempt);
      } else {
        next.delete(id);
      }
      return next;
    });
  }
}
