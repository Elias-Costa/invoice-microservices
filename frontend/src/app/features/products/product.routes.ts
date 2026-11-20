import { MatPaginatorIntl } from '@angular/material/paginator';
import { Routes } from '@angular/router';
import { ProductPaginatorIntl } from './components/product-paginator-intl';
import { confirmProductFormExit } from './pages/product-form.guard';

export const productRoutes: Routes = [
  {
    path: '',
    providers: [{ provide: MatPaginatorIntl, useClass: ProductPaginatorIntl }],
    children: [
      {
        path: '',
        pathMatch: 'full',
        title: 'Produtos | Notas Fiscais',
        loadComponent: () =>
          import('./pages/product-list.component').then((page) => page.ProductListComponent),
      },
      {
        path: 'new',
        title: 'Novo produto | Notas Fiscais',
        canDeactivate: [confirmProductFormExit],
        loadComponent: () =>
          import('./pages/product-form.component').then((page) => page.ProductFormComponent),
      },
      {
        path: ':id/edit',
        title: 'Editar produto | Notas Fiscais',
        canDeactivate: [confirmProductFormExit],
        loadComponent: () =>
          import('./pages/product-form.component').then((page) => page.ProductFormComponent),
      },
      {
        path: ':id',
        title: 'Detalhe do produto | Notas Fiscais',
        loadComponent: () =>
          import('./pages/product-detail.component').then((page) => page.ProductDetailComponent),
      },
    ],
  },
];
