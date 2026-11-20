import { CpfCnpjPipe } from './cpf-cnpj.pipe';

describe('CpfCnpjPipe', () => {
  const pipe = new CpfCnpjPipe();

  it.each([
    ['01234567890', '012.345.678-90'],
    ['04252011000110', '04.252.011/0001-10'],
    ['04.252.011/0001-10', '04.252.011/0001-10'],
    ['inválido', 'inválido'],
    [null, ''],
    [undefined, ''],
  ])('formats %s as %s', (input, expected) => {
    expect(pipe.transform(input)).toBe(expected);
  });
});
