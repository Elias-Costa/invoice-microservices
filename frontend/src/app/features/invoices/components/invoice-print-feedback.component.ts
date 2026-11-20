import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { PrintFeedback } from '../data-access/invoice-printing';

@Component({
  selector: 'app-invoice-print-feedback',
  imports: [MatButtonModule, MatProgressBarModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="notice" [class.warning]="status() !== 'processing'">
      <ng-content />
      @if (status() === 'processing') {
        <p role="status">Aguardando a baixa no estoque…</p>
        <mat-progress-bar mode="indeterminate" aria-label="Processamento da nota fiscal" />
      } @else if (status() === 'rejected') {
        <p role="alert">{{ reason() }}</p>
      } @else {
        <p role="alert">
          @if (status() === 'timed-out') {
            A nota continua em processamento. O serviço de estoque pode estar indisponível; a
            operação será concluída automaticamente quando ele se recuperar.
          } @else {
            Não foi possível acompanhar o processamento. Recarregue para consultar o status da nota.
          }
        </p>
        <button mat-button type="button" (click)="reload.emit()">Recarregar</button>
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }

    .notice {
      border: 1px solid var(--invoice-border);
      border-radius: 0.5rem;
      padding: 0.75rem 1rem;
      background: var(--mat-sys-surface);
    }

    .warning {
      background: #fff4db;
      color: #63420a;
    }

    p {
      margin: 0.5rem 0;
    }
  `,
})
export class InvoicePrintFeedbackComponent {
  readonly status = input.required<PrintFeedback | 'rejected'>();
  readonly reason = input<string>();
  readonly reload = output<void>();
}
