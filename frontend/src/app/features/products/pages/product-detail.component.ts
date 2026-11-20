import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
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
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';
import { ProductResponse, StockMovementResponse } from '../../../shared/models/stock';
import { ProductStatusActionComponent } from '../components/product-status-action.component';
import { StockMovementDialogComponent } from '../components/stock-movement-dialog.component';
import { ProductApi } from '../data-access/product.api';
import { productReadState } from '../data-access/product-read-state';

@Component({
  imports: [
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    MatButtonModule,
    MatCardModule,
    MatPaginatorModule,
    MatTableModule,
    ProductStatusActionComponent,
    RouterLink,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-detail.component.html',
  styleUrl: './product-detail.component.scss',
})
export class ProductDetailComponent {
  private readonly api = inject(ProductApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackbar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);
  private readonly productRevision = signal(0);
  private readonly historyRevision = signal(0);
  private readonly pagination = signal({ id: '', pageIndex: 0, pageSize: 20 });

  readonly id = input.required<string>();
  protected readonly columns = [
    'createdAt',
    'type',
    'quantity',
    'balanceAfter',
    'reason',
    'reference',
    'createdBy',
  ];
  private readonly typeLabels: Record<StockMovementResponse['type'], string> = {
    INITIAL: 'Saldo inicial',
    INBOUND: 'Entrada',
    OUTBOUND: 'Saída',
    INVOICE_DEBIT: 'Baixa por nota',
  };
  protected readonly pageIndex = computed(() =>
    this.pagination().id === this.id() ? this.pagination().pageIndex : 0,
  );
  protected readonly pageSize = computed(() => this.pagination().pageSize);
  protected readonly product = toSignal(
    toObservable(computed(() => ({ id: this.id(), revision: this.productRevision() }))).pipe(
      switchMap(({ id }) =>
        productReadState(this.api.get(id), 'Não foi possível carregar o produto.'),
      ),
    ),
    { initialValue: { status: 'loading' } as const },
  );
  protected readonly history = toSignal(
    toObservable(
      computed(() => ({
        id: this.id(),
        page: this.pageIndex(),
        size: this.pageSize(),
        revision: this.historyRevision(),
      })),
    ).pipe(
      switchMap(({ id, page, size }) =>
        productReadState(
          this.api.listMovements(id, page, size),
          'Não foi possível carregar as movimentações.',
        ),
      ),
    ),
    { initialValue: { status: 'loading' } as const },
  );
  protected readonly movementPage = computed(() => {
    const history = this.history();
    return history.status === 'ready' ? history.data : undefined;
  });

  protected setPage({ pageIndex, pageSize }: PageEvent): void {
    this.pagination.set({ id: this.id(), pageIndex, pageSize });
  }

  protected movementLabel(type: StockMovementResponse['type']): string {
    return this.typeLabels[type];
  }

  protected reloadProduct(): void {
    this.productRevision.update((revision) => revision + 1);
  }

  protected reloadHistory(): void {
    this.historyRevision.update((revision) => revision + 1);
  }

  protected move(product: ProductResponse): void {
    this.dialog
      .open<StockMovementDialogComponent, ProductResponse, StockMovementResponse>(
        StockMovementDialogComponent,
        {
          data: product,
          width: '30rem',
          autoFocus: false,
        },
      )
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((movement) => {
        if (movement?.productId !== this.id()) {
          return;
        }
        this.snackbar.open('Movimentação registrada.', 'Fechar', { duration: 3000 });
        this.pagination.update((pagination) => ({ ...pagination, pageIndex: 0 }));
        this.reloadProduct();
        this.reloadHistory();
      });
  }
}
