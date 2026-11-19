import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'Visão geral | Notas Fiscais',
    loadComponent: () =>
      import('./features/dashboard/dashboard.component').then((page) => page.DashboardComponent),
  },
  {
    path: 'forbidden',
    title: 'Acesso negado | Notas Fiscais',
    loadComponent: () =>
      import('./core/forbidden/forbidden.component').then((page) => page.ForbiddenComponent),
  },
];
