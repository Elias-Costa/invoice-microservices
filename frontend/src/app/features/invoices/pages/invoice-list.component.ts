import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
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
import {
  INVOICE_STATUS_LABELS,
  InvoiceStatusChipComponent,
} from '../components/invoice-status-chip.component';
import { InvoiceStore } from '../data-access/invoice.store';

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
  protected readonly store = inject(InvoiceStore);
  protected readonly statuses = Object.entries(INVOICE_STATUS_LABELS);
  protected readonly from = new FormControl<Date | null>(null);
  protected readonly to = new FormControl<Date | null>(null);
  protected readonly columns = ['number', 'customer', 'status', 'createdAt', 'total'];
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
}
