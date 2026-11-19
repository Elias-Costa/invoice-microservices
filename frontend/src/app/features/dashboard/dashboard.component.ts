import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

@Component({
  imports: [MatCardModule],
  template: `
    <h1 class="page-heading">Visão geral</h1>
    <mat-card appearance="outlined" class="workspace-card">
      <mat-card-content class="empty-state">
        <span class="panel-mark" aria-hidden="true"><span></span><span></span><span></span></span>
        <h2>Painel sem dados</h2>
        <p class="page-description">
          Os dados de estoque e faturamento aparecerão aqui após a integração.
        </p>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .empty-state {
      display: grid;
      justify-items: center;
      align-content: center;
      min-block-size: 19rem;
      padding: 2rem 1.5rem;
      text-align: center;
    }

    .panel-mark {
      display: flex;
      align-items: flex-end;
      gap: 0.3rem;
      box-sizing: border-box;
      inline-size: 3.5rem;
      block-size: 3.5rem;
      padding: 1rem;
      border: 1px solid #d0e7e4;
      border-radius: 0.95rem;
      background: #e8f3f2;
    }

    .panel-mark span {
      flex: 1;
      border-radius: 2px;
      background: #3e7776;
      block-size: 42%;
    }

    .panel-mark span:nth-child(2) {
      block-size: 72%;
    }

    .panel-mark span:nth-child(3) {
      block-size: 100%;
    }

    h2 {
      margin: 1rem 0 0.5rem;
      font-size: 1.125rem;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardComponent {}
