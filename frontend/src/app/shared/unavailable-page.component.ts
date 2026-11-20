import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';

@Component({
  imports: [MatButtonModule, RouterLink],
  template: `
    <h1 class="page-heading">{{ heading() }}</h1>
    <p class="page-description">Esta seção ainda está indisponível.</p>
    <a mat-button routerLink="/">Voltar ao início</a>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UnavailablePageComponent {
  readonly heading = input.required<string>();
}
