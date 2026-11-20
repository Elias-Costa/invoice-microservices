import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, map } from 'rxjs';
import { CustomerResponse } from '../../../shared/models/billing';
import { CpfCnpjPipe } from '../../../shared/pipes/cpf-cnpj.pipe';
import { CustomerStatusActionComponent } from '../components/customer-status-action.component';
import { documentSearchTerm } from '../customer-masks';
import { CUSTOMER_SORT_FIELDS } from '../data-access/customer.api';
import { CustomerStore } from '../data-access/customer.store';

@Component({
  imports: [
    CpfCnpjPipe,
    CustomerStatusActionComponent,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatSlideToggleModule,
    MatSortModule,
    MatTableModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  providers: [CustomerStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './customer-list.component.html',
  styleUrl: './customer-list.component.scss',
})
export class CustomerListComponent {
  protected readonly store = inject(CustomerStore);
  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly columns = [...CUSTOMER_SORT_FIELDS, 'actions'];
  protected readonly page = computed(() => {
    const result = this.store.result();
    return result.status === 'ready' ? result.data : undefined;
  });

  constructor() {
    this.search.valueChanges
      .pipe(
        map(documentSearchTerm),
        debounceTime(300),
        distinctUntilChanged(),
        takeUntilDestroyed(),
      )
      .subscribe((search) => this.store.setSearch(search));
  }

  protected sort({ active, direction }: Sort): void {
    const field = CUSTOMER_SORT_FIELDS.find((field) => field === active);
    if (field && direction) {
      this.store.setSort(field, direction);
    }
  }

  protected refreshAfterStatusChange(): void {
    this.store.setPage(0, this.store.query().size);
  }

  protected customerId(_index: number, customer: CustomerResponse): string {
    return customer.id;
  }
}
