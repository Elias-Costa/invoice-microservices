import { FormControl } from '@angular/forms';
import { integerQuantity, productCode, requiredStockText } from './stock.validators';

describe('stock form validators', () => {
  it.each([
    ['  ar-1._  ', null],
    ['  ', { required: true }],
    ['_AR', { pattern: true }],
    ['AR Ç', { pattern: true }],
    ['X'.repeat(31), { pattern: true }],
  ])('validates product code %j after normalization', (value, expected) => {
    expect(productCode(new FormControl(value))).toEqual(expected);
  });

  it('counts code points for text limits and rejects whitespace-only values', () => {
    const validate = requiredStockText(2);
    expect(validate(new FormControl('  👍👍  '))).toBeNull();
    expect(validate(new FormControl('  👍👍a  '))).toEqual({ maxlength: true });
    expect(validate(new FormControl(' '))).toEqual({ required: true });
  });

  it('rejects fractions while letting the required validator handle missing numbers', () => {
    expect(integerQuantity(new FormControl(1.5))).toEqual({ integer: true });
    expect(integerQuantity(new FormControl(2))).toBeNull();
    expect(integerQuantity(new FormControl(null))).toBeNull();
  });
});
