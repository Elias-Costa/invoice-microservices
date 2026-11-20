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
import { CustomerResponse } from '../../../shared/models/billing';
import { readProblemDetail } from '../../../shared/models/problem-detail';
import { CustomerApi } from '../data-access/customer.api';

@Component({
  selector: 'app-customer-status-action',
  imports: [MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      mat-button
      type="button"
      [attr.aria-label]="(customer().active ? 'Inativar ' : 'Reativar ') + customer().name"
      [disabled]="busy()"
      (click)="confirm()"
    >
      {{ customer().active ? 'Inativar' : 'Reativar' }}
    </button>
    @if (failure()) {
      <p role="alert">{{ failure() }}</p>
    }
  `,
  styles: `
    p {
      margin: 0.25rem 0 0;
      color: var(--mat-sys-error);
      font-size: 0.875rem;
    }
  `,
})
export class CustomerStatusActionComponent {
  private readonly api = inject(CustomerApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);
  private readonly snackbar = inject(MatSnackBar);

  readonly customer = input.required<CustomerResponse>();
  readonly statusChanged = output<void>();
  protected readonly busy = signal(false);
  protected readonly failure = signal('');

  protected confirm(): void {
    if (this.busy()) {
      return;
    }

    const { id, name, active } = this.customer();
    const action = active ? 'Inativar' : 'Reativar';
    this.failure.set('');
    this.busy.set(true);
    this.dialog
      .open<ConfirmDialogComponent, ConfirmDialogData, boolean>(ConfirmDialogComponent, {
        data: {
          title: `${action} cliente`,
          message: active
            ? `${name} sairá da lista de clientes ativos e ficará indisponível para novas notas.`
            : `${name} voltará à lista de clientes ativos.`,
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
          this.snackbar.open(active ? 'Cliente inativado.' : 'Cliente reativado.', 'Fechar', {
            duration: 3000,
          });
          this.statusChanged.emit();
        },
        error: (error: unknown) => {
          const detail =
            error instanceof HttpErrorResponse ? readProblemDetail(error)?.detail : undefined;
          this.failure.set(detail ?? 'Não foi possível alterar a situação do cliente.');
        },
      });
  }
}
