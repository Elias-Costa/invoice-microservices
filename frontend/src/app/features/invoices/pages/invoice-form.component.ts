import { CurrencyPipe } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  Injector,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import {
  FormControl,
  FormGroup,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { finalize, map, Observable, of, switchMap, tap } from 'rxjs';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog.component';
import { fieldMessage, setFormProblem, SubmissionKeys } from '../../../shared/forms/form-feedback';
import { readState } from '../../../shared/http/read-state';
import { CustomerResponse, InvoiceResponse } from '../../../shared/models/billing';
import { BatchProductResponse } from '../../../shared/models/stock';
import { CpfCnpjPipe } from '../../../shared/pipes/cpf-cnpj.pipe';
import { integerQuantity, MAX_STOCK_QUANTITY } from '../../../shared/validators/stock.validators';
import { ProductApi } from '../../products/data-access/product.api';
import { InvoiceCustomerPickerComponent } from '../components/invoice-customer-picker.component';
import { InvoiceNumberPipe } from '../components/invoice-number.pipe';
import { InvoiceProductPickerComponent } from '../components/invoice-product-picker.component';
import { InvoiceApi } from '../data-access/invoice.api';

type InvoiceProduct = Omit<BatchProductResponse, 'balance'> & { balance: number | null };
type ItemForm = FormGroup<{
  product: FormControl<InvoiceProduct>;
  quantity: FormControl<number | null>;
}>;

const MAX_TOTAL_CENTS = 999_999_999_999_999n;

@Component({
  imports: [
    CpfCnpjPipe,
    CurrencyPipe,
    InvoiceCustomerPickerComponent,
    InvoiceNumberPipe,
    InvoiceProductPickerComponent,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './invoice-form.component.html',
  styleUrl: './invoice-form.component.scss',
})
export class InvoiceFormComponent {
  private readonly api = inject(InvoiceApi);
  private readonly products = inject(ProductApi);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly snackbar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly builder = inject(NonNullableFormBuilder);
  private readonly customerPicker = viewChild(InvoiceCustomerPickerComponent);
  private readonly productPicker = viewChild(InvoiceProductPickerComponent);
  private readonly keys = new SubmissionKeys();
  private readonly refresh = signal(0);

  readonly id = input<string>();
  protected readonly saving = signal(false);
  protected readonly failure = signal('');
  protected readonly fieldMessage = fieldMessage;
  protected readonly form = this.builder.group({
    customerId: ['', Validators.required],
    items: this.builder.array<ItemForm>([], Validators.required),
  });
  protected get items() {
    return this.form.controls.items;
  }
  private readonly values = toSignal(
    this.form.valueChanges.pipe(map(() => this.form.getRawValue())),
    {
      initialValue: this.form.getRawValue(),
    },
  );
  protected readonly selectedIds = computed(
    () => new Set(this.values().items.map(({ product }) => product.id)),
  );
  protected readonly amounts = computed(() => {
    let totalCents = 0n;
    const lines = this.values().items.map(({ product, quantity }) => {
      const valid =
        quantity !== null &&
        Number.isInteger(quantity) &&
        quantity >= 1 &&
        quantity <= MAX_STOCK_QUANTITY;
      // Multiply and sum integer cents to keep previews exact up to the API's monetary limit.
      const cents = valid ? BigInt(Math.round(product.unitPrice * 100)) * BigInt(quantity) : 0n;
      totalCents += cents;
      return { product, quantity, subtotal: cents <= MAX_TOTAL_CENTS ? Number(cents) / 100 : null };
    });
    return {
      lines,
      total: totalCents <= MAX_TOTAL_CENTS ? Number(totalCents) / 100 : null,
      hasInactive: lines.some(({ product }) => !product.active),
    };
  });

  private readonly query = computed(() => ({ id: this.id(), refresh: this.refresh() }));
  protected readonly state = toSignal(
    toObservable(this.query).pipe(
      switchMap(({ id }) => {
        this.failure.set('');
        if (!id) {
          this.loadForm(null, []);
          return of({ status: 'ready', data: null } as const);
        }

        return readState(
          this.api.get(id).pipe(
            switchMap((invoice) => {
              if (!invoice.allowedActions.includes('EDIT_ITEMS')) {
                return of(invoice);
              }
              return this.products.getBatch(invoice.items.map((item) => item.productId)).pipe(
                tap((products) => this.loadForm(invoice, products)),
                map(() => invoice),
              );
            }),
          ),
          'Não foi possível carregar a nota fiscal para edição.',
        );
      }),
    ),
    { initialValue: { status: 'loading' } as const },
  );

  confirmExit() {
    if (this.saving()) {
      return false;
    }
    if (this.form.pristine) {
      return true;
    }
    return this.dialog
      .open<ConfirmDialogComponent, ConfirmDialogData, boolean>(ConfirmDialogComponent, {
        data: {
          title: 'Descartar alterações?',
          message: 'As alterações deste formulário ainda não foram salvas.',
          confirmLabel: 'Descartar alterações',
        },
      })
      .afterClosed()
      .pipe(map((confirmed) => confirmed === true));
  }

  protected retryLoad(): void {
    this.refresh.update((count) => count + 1);
  }

  protected selectCustomer(customer: CustomerResponse | null): void {
    this.form.controls.customerId.setValue(customer?.id ?? '');
    this.form.controls.customerId.markAsDirty();
  }

  protected addProduct(product: BatchProductResponse): void {
    if (!product.active || this.selectedIds().has(product.id)) {
      return;
    }
    this.items.push(this.itemForm(product, 1));
    this.items.markAsDirty();
  }

  protected removeProduct(index: number): void {
    this.items.removeAt(index);
    this.items.markAsDirty();
  }

  protected save(): void {
    const state = this.state();
    const amounts = this.amounts();
    if (
      state.status !== 'ready' ||
      this.form.invalid ||
      this.saving() ||
      amounts.total === null ||
      amounts.hasInactive
    ) {
      return;
    }

    const { customerId, items } = this.values();
    const requestItems = items.flatMap(({ product, quantity }) =>
      quantity === null ? [] : [{ productId: product.id, quantity }],
    );
    if (state.data) {
      const { id, version, allowedActions } = state.data;
      if (allowedActions.includes('EDIT_ITEMS')) {
        this.submit(
          this.api.replaceItems(id, { items: requestItems, version }),
          'Itens da nota atualizados.',
        );
      }
      return;
    }

    const request = { customerId, items: requestItems };
    this.submit(this.api.create(request, this.keys.forPayload(request)), 'Nota fiscal criada.');
  }

  private itemForm(product: InvoiceProduct, quantity: number): ItemForm {
    return this.builder.group({
      product,
      quantity: new FormControl<number | null>(quantity, [
        Validators.required,
        integerQuantity,
        Validators.min(1),
        Validators.max(MAX_STOCK_QUANTITY),
      ]),
    });
  }

  private loadForm(invoice: InvoiceResponse | null, products: BatchProductResponse[]): void {
    const byId = new Map(products.map((product) => [product.id, product]));
    const items = (invoice?.items ?? []).map((item) =>
      this.itemForm(
        byId.get(item.productId) ?? {
          id: item.productId,
          code: item.productCode,
          description: item.productDescription,
          unitPrice: item.unitPrice,
          balance: null,
          active: false,
        },
        item.quantity,
      ),
    );
    this.form.setControl('items', this.builder.array(items, Validators.required));
    this.form.controls.customerId.setValue(invoice?.customer.id ?? '');
    this.form.markAsPristine();
    afterNextRender(
      { write: () => (invoice ? this.productPicker() : this.customerPicker())?.focus() },
      { injector: this.injector },
    );
  }

  private submit(request: Observable<InvoiceResponse>, message: string): void {
    this.failure.set('');
    this.saving.set(true);
    request
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: (invoice) => {
          this.form.markAsPristine();
          this.saving.set(false);
          this.snackbar.open(message, 'Fechar', { duration: 3000 });
          void this.router.navigate(['/invoices', invoice.id]);
        },
        error: (error: unknown) =>
          this.failure.set(
            setFormProblem(this.form, error, 'Não foi possível salvar a nota fiscal.'),
          ),
      });
  }
}
