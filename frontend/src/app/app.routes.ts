import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'Visão geral | Notas Fiscais',
    loadComponent: () =>
      import('./features/dashboard/dashboard.component').then((page) => page.DashboardComponent),
  },
  {
    path: 'products',
    loadChildren: () =>
      import('./features/products/product.routes').then((feature) => feature.productRoutes),
  },
  {
    path: 'customers',
    loadChildren: () =>
      import('./features/customers/customer.routes').then((feature) => feature.customerRoutes),
  },
  {
    path: 'invoices',
    loadChildren: () =>
      import('./features/invoices/invoice.routes').then((feature) => feature.invoiceRoutes),
  },
  {
    path: 'forbidden',
    title: 'Acesso negado | Notas Fiscais',
    loadComponent: () =>
      import('./core/forbidden/forbidden.component').then((page) => page.ForbiddenComponent),
  },
];
