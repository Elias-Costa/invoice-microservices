import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  Injector,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import {
  FormControl,
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
import { finalize, map, of, switchMap, tap } from 'rxjs';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog.component';
import { readProblemDetail } from '../../../shared/models/problem-detail';
import { PRODUCT_CODE_ALREADY_EXISTS, ProductResponse } from '../../../shared/models/stock';
import {
  integerQuantity,
  MAX_STOCK_QUANTITY,
  MAX_UNIT_PRICE,
  normalizeProductCode,
  productCode,
  requiredStockText,
} from '../../../shared/validators/stock.validators';
import { ProductApi } from '../data-access/product.api';
import { fieldMessage, setFormProblem, SubmissionKeys } from '../data-access/product-form-feedback';
import { productReadState } from '../data-access/product-read-state';

@Component({
  imports: [
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-form.component.html',
  styleUrl: './product-form.component.scss',
})
export class ProductFormComponent {
  private readonly api = inject(ProductApi);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snackbar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly keys = new SubmissionKeys();
  private readonly codeInput = viewChild<ElementRef<HTMLInputElement>>('codeInput');
  private readonly descriptionInput =
    viewChild<ElementRef<HTMLTextAreaElement>>('descriptionInput');
  private readonly query = signal(0);

  readonly id = input<string>();
  protected readonly saving = signal(false);
  protected readonly failure = signal('');
  protected readonly fieldMessage = fieldMessage;
  protected readonly form = inject(NonNullableFormBuilder).group({
    code: ['', productCode],
    description: ['', requiredStockText(200)],
    unitPrice: new FormControl<number | null>(null, [
      Validators.required,
      Validators.min(0),
      Validators.max(MAX_UNIT_PRICE),
    ]),
    initialBalance: new FormControl<number | null>(null, [
      Validators.required,
      Validators.min(0),
      Validators.max(MAX_STOCK_QUANTITY),
      integerQuantity,
    ]),
  });
  protected readonly product = toSignal(
    toObservable(computed(() => ({ id: this.id(), revision: this.query() }))).pipe(
      switchMap(({ id }) => {
        this.failure.set('');
        if (!id) {
          this.form.reset();
          this.form.controls.code.enable();
          this.form.controls.initialBalance.enable();
          this.focusForm(false);
          return of({ status: 'ready', data: null } as const);
        }

        return productReadState(
          this.api.get(id).pipe(tap((product) => this.loadForm(product))),
          'Não foi possível carregar o produto para edição.',
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
    this.query.update((revision) => revision + 1);
  }

  protected uppercaseCode(): void {
    const control = this.form.controls.code;
    const input = this.codeInput()?.nativeElement;
    if (!input) {
      return;
    }

    const { value, selectionStart, selectionEnd } = input;
    const uppercase = value.toUpperCase();
    if (uppercase !== value) {
      control.setValue(uppercase);
      if (selectionStart !== null && selectionEnd !== null) {
        input.setSelectionRange(
          value.slice(0, selectionStart).toUpperCase().length,
          value.slice(0, selectionEnd).toUpperCase().length,
        );
      }
    }
  }

  protected save(): void {
    const product = this.product();
    const { code, description, unitPrice, initialBalance } = this.form.getRawValue();
    if (this.saving() || this.form.invalid || product.status !== 'ready' || unitPrice === null) {
      return;
    }

    const fields = { description: description.trim(), unitPrice };
    const current = product.data;
    if (current) {
      this.submit(this.api.update(current.id, { ...fields, version: current.version }), true);
    } else if (initialBalance !== null) {
      const request = { ...fields, code: normalizeProductCode(code), initialBalance };
      this.submit(this.api.create(request, this.keys.forPayload(request)), false);
    }
  }

  private loadForm(product: ProductResponse): void {
    this.form.reset({
      code: product.code,
      description: product.description,
      unitPrice: product.unitPrice,
    });
    this.form.controls.code.disable();
    this.form.controls.initialBalance.disable();
    this.focusForm(true);
  }

  private focusForm(editing: boolean): void {
    afterNextRender(
      {
        write: () => (editing ? this.descriptionInput() : this.codeInput())?.nativeElement.focus(),
      },
      { injector: this.injector },
    );
  }

  private submit(request: ReturnType<ProductApi['get']>, editing: boolean): void {
    this.failure.set('');
    this.saving.set(true);
    request
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: (saved) => {
          this.form.markAsPristine();
          this.saving.set(false);
          this.snackbar.open(editing ? 'Produto atualizado.' : 'Produto cadastrado.', 'Fechar', {
            duration: 3000,
          });
          void this.router.navigate(['/products', saved.id]);
        },
        error: (error: unknown) => {
          const problem = error instanceof HttpErrorResponse ? readProblemDetail(error) : undefined;
          if (!editing && problem?.status === 409 && problem.type === PRODUCT_CODE_ALREADY_EXISTS) {
            const code = this.form.controls.code;
            code.setErrors({ server: problem.detail ?? 'Este código já está cadastrado.' });
            this.codeInput()?.nativeElement.focus();
            return;
          }

          this.failure.set(setFormProblem(this.form, error, 'Não foi possível salvar o produto.'));
        },
      });
  }
}
