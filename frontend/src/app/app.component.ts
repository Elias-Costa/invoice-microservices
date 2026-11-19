import { BreakpointObserver } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';

@Component({
  selector: 'app-root',
  imports: [
    MatButtonModule,
    MatListModule,
    MatSidenavModule,
    MatToolbarModule,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent {
  private readonly breakpoints = inject(BreakpointObserver);

  protected readonly compact = toSignal(
    this.breakpoints.observe('(max-width: 820px)').pipe(map(({ matches }) => matches)),
    { requireSync: true },
  );

  protected readonly upcomingSections = [
    { label: 'Produtos', icon: 'products' },
    { label: 'Clientes', icon: 'customers' },
    { label: 'Notas fiscais', icon: 'invoices' },
    { label: 'Status do sistema', icon: 'status' },
  ];
}
