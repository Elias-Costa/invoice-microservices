import { FormControl, Validators } from '@angular/forms';
import { cnpjValidator, cpfCnpjValidator, cpfValidator } from './cpf-cnpj.validators';

describe('CPF/CNPJ validators', () => {
  it.each(['52998224725', '529.982.247-25', '04252011000110', '04.252.011/0001-10'])(
    'accepts valid document %s',
    (value) => {
      expect(cpfCnpjValidator(new FormControl(value))).toBeNull();
    },
  );

  it.each([
    '52998224726',
    '04252011000111',
    '111.111.111-11',
    '00.000.000/0000-00',
    '529.982/247-25',
    'x52998224725',
    '1234',
    ' ',
    52998224725,
  ])('rejects invalid document %s', (value) => {
    expect(cpfCnpjValidator(new FormControl(value))).toEqual({ cpfCnpj: true });
  });

  it('allows a form to choose just CPF or CNPJ', () => {
    const cpf = new FormControl('52998224725');
    const cnpj = new FormControl('04252011000110');
    expect(cpfValidator(cpf)).toBeNull();
    expect(cnpjValidator(cnpj)).toBeNull();
    expect(cpfValidator(cnpj)).toEqual({ cpf: true });
    expect(cnpjValidator(cpf)).toEqual({ cnpj: true });
  });

  it('leaves missing values to the required validator', () => {
    const control = new FormControl('', [Validators.required, cpfCnpjValidator]);
    expect(control.errors).toEqual({ required: true });
    expect(cpfCnpjValidator(new FormControl(null))).toBeNull();
  });
});
