import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { finalize } from 'rxjs';
import { fieldMessage, setFormProblem } from '../../../shared/forms/form-feedback';
import { InvoiceResponse } from '../../../shared/models/billing';
import { requiredStockText } from '../../../shared/validators/stock.validators';
import { InvoiceApi } from '../data-access/invoice.api';
import { InvoiceNumberPipe } from './invoice-number.pipe';

@Component({
  imports: [
    InvoiceNumberPipe,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    ReactiveFormsModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Cancelar nota {{ invoice.number | invoiceNumber }}?</h2>
    <form [formGroup]="form" (ngSubmit)="submit()">
      <mat-dialog-content>
        <mat-form-field appearance="outline">
          <mat-label>Motivo do cancelamento</mat-label>
          <textarea matInput cdkFocusInitial formControlName="reason" rows="3" required></textarea>
          <mat-error>{{
            fieldMessage(form.controls.reason, 'Informe até 500 caracteres.')
          }}</mat-error>
        </mat-form-field>
        @if (failure()) {
          <p role="alert">{{ failure() }}</p>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button mat-dialog-close type="button" [disabled]="saving()">Voltar</button>
        <button mat-flat-button type="submit" [disabled]="form.invalid || saving()">
          {{ saving() ? 'Cancelando…' : 'Confirmar cancelamento' }}
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    mat-form-field {
      inline-size: 100%;
      margin-block-start: 0.25rem;
    }
  `,
})
export class CancelInvoiceDialogComponent {
  private readonly api = inject(InvoiceApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog =
    inject<MatDialogRef<CancelInvoiceDialogComponent, InvoiceResponse>>(MatDialogRef);

  protected readonly invoice = inject<Pick<InvoiceResponse, 'id' | 'number'>>(MAT_DIALOG_DATA);
  protected readonly form = inject(NonNullableFormBuilder).group({
    reason: ['', requiredStockText(500)],
  });
  protected readonly saving = signal(false);
  protected readonly failure = signal('');
  protected readonly fieldMessage = fieldMessage;

  protected submit(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }

    this.failure.set('');
    this.saving.set(true);
    this.dialog.disableClose = true;
    this.api
      .cancel(this.invoice.id, { reason: this.form.controls.reason.value.trim() })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.saving.set(false);
          this.dialog.disableClose = false;
        }),
      )
      .subscribe({
        next: (invoice) => this.dialog.close(invoice),
        error: (error: unknown) =>
          this.failure.set(
            setFormProblem(this.form, error, 'Não foi possível cancelar a nota fiscal.'),
          ),
      });
  }
}
