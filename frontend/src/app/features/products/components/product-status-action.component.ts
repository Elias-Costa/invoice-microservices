import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { filter, finalize, switchMap } from 'rxjs';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog.component';
import { readProblemDetail } from '../../../shared/models/problem-detail';
import { ProductResponse } from '../../../shared/models/stock';
import { ProductApi } from '../data-access/product.api';

@Component({
  selector: 'app-product-status-action',
  imports: [MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      mat-button
      type="button"
      [attr.aria-label]="(product().active ? 'Inativar ' : 'Reativar ') + product().code"
      [disabled]="busy()"
      (click)="confirm()"
    >
      {{ product().active ? 'Inativar' : 'Reativar' }}
    </button>
    @if (failure()) {
      <p class="failure" role="alert">{{ failure() }}</p>
    }
  `,
  styles: `
    .failure {
      margin: 0.25rem 0 0;
      color: var(--mat-sys-error);
      font-size: 0.875rem;
    }
  `,
})
export class ProductStatusActionComponent {
  private readonly api = inject(ProductApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);
  private readonly snackbar = inject(MatSnackBar);

  readonly product = input.required<ProductResponse>();
  readonly statusChanged = output<void>();
  protected readonly busy = signal(false);
  protected readonly failure = signal('');

  protected confirm(): void {
    if (this.busy()) {
      return;
    }

    const { id, code, active } = this.product();
    const action = active ? 'Inativar' : 'Reativar';
    this.failure.set('');
    this.busy.set(true);
    this.dialog
      .open<ConfirmDialogComponent, ConfirmDialogData, boolean>(ConfirmDialogComponent, {
        data: {
          title: `${action} produto`,
          message: active
            ? `O produto ${code} sairá da lista de ativos e ficará indisponível para novas notas.`
            : `O produto ${code} voltará à lista de ativos.`,
          confirmLabel: action,
        },
      })
      .afterClosed()
      .pipe(
        filter((confirmed) => confirmed === true),
        switchMap(() => this.api.setActive(id, !active)),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: () => {
          this.snackbar.open(active ? 'Produto inativado.' : 'Produto reativado.', 'Fechar', {
            duration: 3000,
          });
          this.statusChanged.emit();
        },
        error: (error: unknown) => {
          const detail =
            error instanceof HttpErrorResponse ? readProblemDetail(error)?.detail : undefined;
          this.failure.set(detail ?? 'Não foi possível alterar a situação do produto.');
        },
      });
  }
}
