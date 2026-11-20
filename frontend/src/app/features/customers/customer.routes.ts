import { MatPaginatorIntl } from '@angular/material/paginator';
import { CanDeactivateFn, Routes } from '@angular/router';
import { PtBrPaginatorIntl } from '../../shared/components/pt-br-paginator-intl';
import type { CustomerFormComponent } from './pages/customer-form.component';

const confirmExit: CanDeactivateFn<CustomerFormComponent> = (form) => form.confirmExit();

export const customerRoutes: Routes = [
  {
    path: '',
    providers: [{ provide: MatPaginatorIntl, useClass: PtBrPaginatorIntl }],
    children: [
      {
        path: '',
        pathMatch: 'full',
        title: 'Clientes | Notas Fiscais',
        loadComponent: () =>
          import('./pages/customer-list.component').then((page) => page.CustomerListComponent),
      },
      {
        path: 'new',
        title: 'Novo cliente | Notas Fiscais',
        canDeactivate: [confirmExit],
        loadComponent: () =>
          import('./pages/customer-form.component').then((page) => page.CustomerFormComponent),
      },
      {
        path: ':id/edit',
        title: 'Editar cliente | Notas Fiscais',
        canDeactivate: [confirmExit],
        loadComponent: () =>
          import('./pages/customer-form.component').then((page) => page.CustomerFormComponent),
      },
      {
        path: ':id',
        title: 'Detalhe do cliente | Notas Fiscais',
        loadComponent: () =>
          import('./pages/customer-detail.component').then((page) => page.CustomerDetailComponent),
      },
    ],
  },
];
