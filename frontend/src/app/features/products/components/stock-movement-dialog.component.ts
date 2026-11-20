import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { finalize } from 'rxjs';
import {
  ProductResponse,
  RegisterStockMovementRequest,
  StockMovementResponse,
} from '../../../shared/models/stock';
import {
  integerQuantity,
  MAX_STOCK_QUANTITY,
  requiredStockText,
} from '../../../shared/validators/stock.validators';
import { fieldMessage, setFormProblem, SubmissionKeys } from '../data-access/product-form-feedback';
import { ProductApi } from '../data-access/product.api';

@Component({
  imports: [
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    ReactiveFormsModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './stock-movement-dialog.component.html',
  styles: `
    mat-dialog-content {
      display: grid;
      gap: 0.5rem;
    }

    fieldset {
      display: grid;
      gap: 0.5rem;
      min-inline-size: 0;
      margin: 0;
      padding: 0;
      border: 0;
    }

    .failure {
      margin: 0;
      color: var(--mat-sys-error);
    }
  `,
})
export class StockMovementDialogComponent {
  private readonly api = inject(ProductApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog =
    inject<MatDialogRef<StockMovementDialogComponent, StockMovementResponse>>(MatDialogRef);
  private readonly keys = new SubmissionKeys();
  private readonly typeInput = viewChild<ElementRef<HTMLSelectElement>>('typeInput');

  protected readonly product = inject<ProductResponse>(MAT_DIALOG_DATA);
  protected readonly saving = signal(false);
  protected readonly failure = signal('');
  protected readonly fieldMessage = fieldMessage;
  protected readonly form = inject(NonNullableFormBuilder).group({
    type: ['INBOUND' as RegisterStockMovementRequest['type'], Validators.required],
    quantity: new FormControl<number | null>(null, [
      Validators.required,
      Validators.min(1),
      Validators.max(MAX_STOCK_QUANTITY),
      integerQuantity,
    ]),
    reason: ['', requiredStockText(200)],
  });

  constructor() {
    afterNextRender({ write: () => this.typeInput()?.nativeElement.focus() });
  }

  protected save(): void {
    const { type, quantity, reason } = this.form.getRawValue();
    if (this.saving() || this.form.invalid || quantity === null) {
      return;
    }

    const request = { type, quantity, reason: reason.trim() };
    this.failure.set('');
    this.saving.set(true);
    this.dialog.disableClose = true;
    this.api
      .registerMovement(this.product.id, request, this.keys.forPayload(request))
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.dialog.disableClose = false;
          this.saving.set(false);
        }),
      )
      .subscribe({
        next: (movement) => this.dialog.close(movement),
        error: (error: unknown) => {
          if (
            error instanceof HttpErrorResponse &&
            (error.status === 400 || error.status === 422)
          ) {
            this.keys.forget(request);
          }
          this.failure.set(
            setFormProblem(this.form, error, 'Não foi possível registrar a movimentação.'),
          );
        },
      });
  }
}
