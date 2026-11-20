import { FormControl } from '@angular/forms';
import {
  customerEmailValidator,
  customerNameValidator,
  customerPhoneValidator,
} from './customer-contact.validators';

describe('customer contact validators', () => {
  it('requires a nonblank name and counts Unicode code points', () => {
    expect(customerNameValidator(new FormControl('  '))).toEqual({ required: true });
    expect(customerNameValidator(new FormControl('𐐀'.repeat(150)))).toBeNull();
    expect(customerNameValidator(new FormControl('𐐀'.repeat(151)))).toEqual({ maxlength: true });
  });

  it('accepts an optional email in the format supported by billing', () => {
    expect(customerEmailValidator(new FormControl(''))).toBeNull();
    expect(customerEmailValidator(new FormControl(' usuário@exemplo.рф '))).toBeNull();
    expect(customerEmailValidator(new FormControl('nome..sobrenome@example.com'))).toEqual({
      email: true,
    });
    expect(customerEmailValidator(new FormControl(`${'a'.repeat(140)}@example.com`))).toEqual({
      email: true,
    });
  });

  it('accepts an optional formatted phone with up to 20 digits', () => {
    expect(customerPhoneValidator(new FormControl(''))).toBeNull();
    expect(customerPhoneValidator(new FormControl('+55 (11) 98765-4321'))).toBeNull();
    expect(customerPhoneValidator(new FormControl(`+${'1'.repeat(20)}`))).toBeNull();
    expect(customerPhoneValidator(new FormControl(`+${'1'.repeat(21)}`))).toEqual({ phone: true });
  });
});
