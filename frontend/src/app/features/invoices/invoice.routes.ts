import { MatPaginatorIntl } from '@angular/material/paginator';
import { CanDeactivateFn, Routes } from '@angular/router';
import { PtBrPaginatorIntl } from '../../shared/components/pt-br-paginator-intl';
import type { InvoiceFormComponent } from './pages/invoice-form.component';

const confirmExit: CanDeactivateFn<InvoiceFormComponent> = (form) => form.confirmExit();

export const invoiceRoutes: Routes = [
  {
    path: '',
    providers: [{ provide: MatPaginatorIntl, useClass: PtBrPaginatorIntl }],
    children: [
      {
        path: '',
        pathMatch: 'full',
        title: 'Notas fiscais | Notas Fiscais',
        loadComponent: () =>
          import('./pages/invoice-list.component').then((page) => page.InvoiceListComponent),
      },
      {
        path: 'new',
        title: 'Nova nota fiscal | Notas Fiscais',
        canDeactivate: [confirmExit],
        loadComponent: () =>
          import('./pages/invoice-form.component').then((page) => page.InvoiceFormComponent),
      },
      {
        path: ':id/edit',
        title: 'Editar nota fiscal | Notas Fiscais',
        canDeactivate: [confirmExit],
        loadComponent: () =>
          import('./pages/invoice-form.component').then((page) => page.InvoiceFormComponent),
      },
      {
        path: ':id',
        title: 'Detalhe da nota fiscal | Notas Fiscais',
        loadComponent: () =>
          import('./pages/invoice-detail.component').then((page) => page.InvoiceDetailComponent),
      },
    ],
  },
];
