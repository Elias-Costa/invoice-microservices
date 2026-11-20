import { CurrencyPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { map } from 'rxjs';
import { ProductResponse } from '../../../shared/models/stock';
import { ProductApi } from '../../products/data-access/product.api';
import { lookupResults } from './lookup-results';

@Component({
  selector: 'app-invoice-product-picker',
  imports: [
    CurrencyPipe,
    MatAutocompleteModule,
    MatFormFieldModule,
    MatInputModule,
    ReactiveFormsModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-form-field appearance="outline" subscriptSizing="dynamic">
      <mat-label>Adicionar produto</mat-label>
      <input
        matInput
        #searchInput
        placeholder="Código ou descrição"
        [formControl]="search"
        [matAutocomplete]="options"
      />
      <mat-autocomplete
        #options="matAutocomplete"
        [displayWith]="displayCode"
        (optionSelected)="select()"
      >
        @let state = result();
        @switch (state.status) {
          @case ('loading') {
            <mat-option disabled>Buscando produtos…</mat-option>
          }
          @case ('failed') {
            <mat-option disabled>{{ state.message }}</mat-option>
          }
          @case ('ready') {
            @for (product of state.data; track product.id) {
              <mat-option [value]="product" [disabled]="selectedIds().has(product.id)">
                <span class="option-title">{{ product.code }} · {{ product.description }}</span>
                <small>
                  {{ product.unitPrice | currency: 'BRL' }} · Saldo {{ product.balance }}
                  @if (selectedIds().has(product.id)) {
                    · Já incluído
                  }
                </small>
              </mat-option>
            } @empty {
              @if (searchTerm()) {
                <mat-option disabled>Nenhum produto ativo encontrado.</mat-option>
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
    .option-title {
      display: block;
      padding-block-start: 0.3rem;
    }
    small {
      display: block;
      padding-block-end: 0.3rem;
    }
  `,
})
export class InvoiceProductPickerComponent {
  private readonly api = inject(ProductApi);
  private readonly searchInput = viewChild.required<ElementRef<HTMLInputElement>>('searchInput');

  readonly selectedIds = input.required<ReadonlySet<string>>();
  readonly productSelected = output<ProductResponse>();

  protected readonly search = new FormControl<string | ProductResponse>('', { nonNullable: true });
  protected readonly result = lookupResults(
    this.search,
    (value) => value.trim(),
    (search) =>
      this.api
        .list({ search, active: true, page: 0, size: 10, sortField: 'code', sortDirection: 'asc' })
        .pipe(map((page) => page.content)),
    'Não foi possível buscar produtos.',
  );

  focus(): void {
    this.searchInput().nativeElement.focus();
  }

  protected select(): void {
    const product = this.search.value;
    if (typeof product !== 'string' && !this.selectedIds().has(product.id)) {
      this.productSelected.emit(product);
    }
    this.search.setValue('');
  }

  protected searchTerm(): string {
    const value = this.search.value;
    return typeof value === 'string' ? value.trim() : '';
  }

  protected displayCode(value: string | ProductResponse): string {
    return typeof value === 'string' ? value : (value?.code ?? '');
  }
}
