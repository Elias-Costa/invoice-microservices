import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { RouterLink } from '@angular/router';

@Component({
  imports: [MatButtonModule, MatCardModule, RouterLink],
  template: `
    <h1 class="page-heading">Acesso negado</h1>
    <mat-card appearance="outlined" class="workspace-card">
      <mat-card-content class="forbidden-content">
        <p class="page-description">Você não tem permissão para acessar esta página.</p>
        <a mat-flat-button routerLink="/">Voltar ao início</a>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .forbidden-content {
      display: grid;
      justify-items: start;
      gap: 1.25rem;
      padding: 1.5rem;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForbiddenComponent {}
