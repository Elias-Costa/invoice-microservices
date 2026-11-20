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
    title: 'Produtos | Notas Fiscais',
    data: { heading: 'Produtos' },
    loadComponent: () =>
      import('./shared/unavailable-page.component').then((page) => page.UnavailablePageComponent),
  },
  {
    path: 'customers',
    title: 'Clientes | Notas Fiscais',
    data: { heading: 'Clientes' },
    loadComponent: () =>
      import('./shared/unavailable-page.component').then((page) => page.UnavailablePageComponent),
  },
  {
    path: 'invoices',
    title: 'Notas fiscais | Notas Fiscais',
    data: { heading: 'Notas fiscais' },
    loadComponent: () =>
      import('./shared/unavailable-page.component').then((page) => page.UnavailablePageComponent),
  },
  {
    path: 'forbidden',
    title: 'Acesso negado | Notas Fiscais',
    loadComponent: () =>
      import('./core/forbidden/forbidden.component').then((page) => page.ForbiddenComponent),
  },
];
