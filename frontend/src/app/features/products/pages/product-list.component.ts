import { CurrencyPipe, DecimalPipe } from '@angular/common';
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
import { ProductResponse } from '../../../shared/models/stock';
import { ProductStatusActionComponent } from '../components/product-status-action.component';
import { PRODUCT_SORT_FIELDS } from '../data-access/product.api';
import { ProductStore } from '../data-access/product.store';

@Component({
  imports: [
    CurrencyPipe,
    DecimalPipe,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatSlideToggleModule,
    MatSortModule,
    MatTableModule,
    ProductStatusActionComponent,
    ReactiveFormsModule,
    RouterLink,
  ],
  providers: [ProductStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-list.component.html',
  styleUrl: './product-list.component.scss',
})
export class ProductListComponent {
  protected readonly store = inject(ProductStore);
  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly columns = [...PRODUCT_SORT_FIELDS, 'actions'];
  protected readonly page = computed(() => {
    const result = this.store.result();
    return result.status === 'ready' ? result.data : undefined;
  });

  constructor() {
    this.search.valueChanges
      .pipe(
        map((search) => search.trim()),
        debounceTime(300),
        distinctUntilChanged(),
        takeUntilDestroyed(),
      )
      .subscribe((search) => this.store.setSearch(search));
  }

  protected sort({ active, direction }: Sort): void {
    const field = PRODUCT_SORT_FIELDS.find((field) => field === active);
    if (field && direction) {
      this.store.setSort(field, direction);
    }
  }

  protected refreshAfterStatusChange(): void {
    this.store.setPage(0, this.store.query().size);
  }

  protected productId(_index: number, product: ProductResponse): string {
    return product.id;
  }
}
