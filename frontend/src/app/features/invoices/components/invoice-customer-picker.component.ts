import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { distinctUntilChanged, forkJoin, map, startWith } from 'rxjs';
import { CustomerResponse } from '../../../shared/models/billing';
import { CpfCnpjPipe } from '../../../shared/pipes/cpf-cnpj.pipe';
import { documentSearchTerm } from '../../customers/customer-masks';
import { CustomerApi } from '../../customers/data-access/customer.api';
import { lookupResults } from './lookup-results';

@Component({
  selector: 'app-invoice-customer-picker',
  imports: [
    CpfCnpjPipe,
    MatAutocompleteModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    ReactiveFormsModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-form-field appearance="outline" subscriptSizing="dynamic">
      <mat-label>Cliente</mat-label>
      <input
        matInput
        #searchInput
        placeholder="Nome, CPF ou CNPJ"
        [formControl]="search"
        [matAutocomplete]="options"
        [attr.aria-required]="required()"
      />
      @if (search.value) {
        <button
          mat-icon-button
          matSuffix
          type="button"
          aria-label="Limpar cliente"
          (click)="clear()"
        >
          ×
        </button>
      }
      <mat-autocomplete #options="matAutocomplete" [displayWith]="displayName">
        @let state = result();
        @switch (state.status) {
          @case ('loading') {
            <mat-option disabled>Buscando clientes…</mat-option>
          }
          @case ('failed') {
            <mat-option disabled>{{ state.message }}</mat-option>
          }
          @case ('ready') {
            @for (customer of state.data; track customer.id) {
              <mat-option [value]="customer">
                {{ customer.name }} · {{ customer.document | cpfCnpj
                }}{{ customer.active ? '' : ' · Inativo' }}
              </mat-option>
            } @empty {
              @if (searchTerm()) {
                <mat-option disabled>Nenhum cliente encontrado.</mat-option>
              }
            }
          }
        }
      </mat-autocomplete>
    </mat-form-field>
  `,
  styles: `
    :host,
    mat-form-field {
      display: block;
      inline-size: 100%;
    }
  `,
})
export class InvoiceCustomerPickerComponent {
  private readonly api = inject(CustomerApi);
  private readonly searchInput = viewChild.required<ElementRef<HTMLInputElement>>('searchInput');

  readonly includeInactive = input(false);
  readonly required = input(false);
  readonly selectionChanged = output<CustomerResponse | null>();

  protected readonly search = new FormControl<string | CustomerResponse>('', { nonNullable: true });
  protected readonly result = lookupResults(
    this.search,
    documentSearchTerm,
    (term) => this.findCustomers(term),
    'Não foi possível buscar clientes.',
  );

  constructor() {
    this.search.valueChanges
      .pipe(
        map((value) => (typeof value === 'string' ? null : value)),
        startWith(null),
        distinctUntilChanged((previous, current) => previous?.id === current?.id),
        takeUntilDestroyed(),
      )
      .subscribe((customer) => this.selectionChanged.emit(customer));
  }

  focus(): void {
    this.searchInput().nativeElement.focus();
  }

  protected clear(): void {
    this.search.setValue('');
    this.focus();
  }

  protected searchTerm(): string {
    const value = this.search.value;
    return typeof value === 'string' ? documentSearchTerm(value) : '';
  }

  protected displayName(value: string | CustomerResponse): string {
    return typeof value === 'string' ? value : (value?.name ?? '');
  }

  private findCustomers(search: string) {
    const query = { search, page: 0, size: 10, sortField: 'name', sortDirection: 'asc' } as const;
    const active = this.api.list({ ...query, active: true });
    if (!this.includeInactive()) {
      return active.pipe(map((page) => page.content));
    }
    return forkJoin([active, this.api.list({ ...query, active: false })]).pipe(
      map((pages) => pages.flatMap((page) => page.content)),
    );
  }
}
