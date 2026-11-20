import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatListModule } from '@angular/material/list';
import { RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';
import { readState } from '../../../shared/http/read-state';
import { InvoiceStatus } from '../../../shared/models/billing';
import { CpfCnpjPipe } from '../../../shared/pipes/cpf-cnpj.pipe';
import { CustomerStatusActionComponent } from '../components/customer-status-action.component';
import { formatCustomerPhone } from '../customer-masks';
import { CustomerApi } from '../data-access/customer.api';

@Component({
  imports: [
    CpfCnpjPipe,
    CurrencyPipe,
    CustomerStatusActionComponent,
    DatePipe,
    MatButtonModule,
    MatCardModule,
    MatListModule,
    RouterLink,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './customer-detail.component.html',
  styleUrl: './customer-detail.component.scss',
})
export class CustomerDetailComponent {
  private readonly api = inject(CustomerApi);
  private readonly revision = signal(0);

  readonly id = input.required<string>();
  protected readonly customer = toSignal(
    toObservable(computed(() => ({ id: this.id(), revision: this.revision() }))).pipe(
      switchMap(({ id }) => readState(this.api.get(id), 'Não foi possível carregar o cliente.')),
    ),
    { initialValue: { status: 'loading' } as const },
  );
  protected readonly phone = computed(() => {
    const state = this.customer();
    return state.status === 'ready' ? formatCustomerPhone(state.data.phone ?? '') : '';
  });
  protected readonly invoiceStatusLabels: Record<InvoiceStatus, string> = {
    OPEN: 'Aberta',
    PROCESSING: 'Em processamento',
    CLOSED: 'Fechada',
    CANCELED: 'Cancelada',
  };

  protected reload(): void {
    this.revision.update((revision) => revision + 1);
  }
}
