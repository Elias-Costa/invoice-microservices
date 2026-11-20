import { CanDeactivateFn } from '@angular/router';
import type { ProductFormComponent } from './product-form.component';

export const confirmProductFormExit: CanDeactivateFn<ProductFormComponent> = (form) =>
  form.confirmExit();
