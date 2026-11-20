import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { InvoiceStatus } from '../../../shared/models/billing';

export const INVOICE_STATUS_LABELS: Record<InvoiceStatus, string> = {
  OPEN: 'Aberta',
  PROCESSING: 'Em processamento',
  CLOSED: 'Fechada',
  CANCELED: 'Cancelada',
};

@Component({
  selector: 'app-invoice-status-chip',
  imports: [MatChipsModule, MatProgressSpinnerModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-chip [attr.data-status]="status()">
      @if (status() === 'PROCESSING') {
        <mat-spinner matChipAvatar [diameter]="14" aria-hidden="true" />
      }
      {{ labels[status()] }}
    </mat-chip>
  `,
  styles: `
    mat-chip {
      --mat-chip-label-text-weight: 600;
      --mat-chip-outline-width: 0;
      --mat-chip-elevated-container-color: #e9ecef;
      --mat-chip-label-text-color: #374151;
    }
    mat-chip[data-status='OPEN'] {
      --mat-chip-elevated-container-color: #e2efff;
      --mat-chip-label-text-color: #174679;
    }
    mat-chip[data-status='PROCESSING'] {
      --mat-chip-elevated-container-color: #ffefcd;
      --mat-chip-label-text-color: #6e4904;
      --mat-progress-spinner-active-indicator-color: #90620c;
    }
    mat-chip[data-status='CLOSED'] {
      --mat-chip-elevated-container-color: #dcf4e5;
      --mat-chip-label-text-color: #205b36;
    }
  `,
})
export class InvoiceStatusChipComponent {
  readonly status = input.required<InvoiceStatus>();
  protected readonly labels = INVOICE_STATUS_LABELS;
}
