import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_SNACK_BAR_DATA, MatSnackBarModule, MatSnackBarRef } from '@angular/material/snack-bar';

@Component({
  imports: [MatButtonModule, MatSnackBarModule],
  template: `
    <span matSnackBarLabel>Serviço temporariamente indisponível.</span>
    <span matSnackBarActions>
      @if (data.retryAllowed) {
        <button mat-button matSnackBarAction type="button" (click)="notice.dismissWithAction()">
          Tentar novamente
        </button>
      }
      <button mat-button matSnackBarAction type="button" (click)="notice.dismiss()">Fechar</button>
    </span>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UnavailableNoticeComponent {
  protected readonly data = inject<{ retryAllowed: boolean }>(MAT_SNACK_BAR_DATA);
  protected readonly notice = inject<MatSnackBarRef<UnavailableNoticeComponent>>(MatSnackBarRef);
}
