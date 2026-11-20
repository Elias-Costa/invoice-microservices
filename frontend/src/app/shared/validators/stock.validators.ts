import { ValidatorFn } from '@angular/forms';

const PRODUCT_CODE = /^[A-Z0-9][A-Z0-9._-]{0,29}$/;
export const MAX_STOCK_QUANTITY = 2_147_483_647;
export const MAX_UNIT_PRICE = 9_999_999_999_999.99;

export function normalizeProductCode(code: string): string {
  return code.trim().toUpperCase();
}

export const productCode: ValidatorFn = (control) => {
  const code: unknown = control.value;
  const value = typeof code === 'string' ? normalizeProductCode(code) : '';
  if (!value) {
    return { required: true };
  }
  return PRODUCT_CODE.test(value) ? null : { pattern: true };
};

export const integerQuantity: ValidatorFn = (control) =>
  control.value === null || Number.isInteger(control.value) ? null : { integer: true };

export function requiredStockText(maxLength: number): ValidatorFn {
  return (control) => {
    const value = String(control.value ?? '').trim();
    if (!value) {
      return { required: true };
    }
    return Array.from(value).length > maxLength ? { maxlength: true } : null;
  };
}
