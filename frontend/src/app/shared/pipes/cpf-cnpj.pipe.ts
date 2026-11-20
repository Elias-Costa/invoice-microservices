import { Pipe, PipeTransform } from '@angular/core';
import { CNPJ_MASK, CPF_MASK, documentDigits } from '../cpf-cnpj';

@Pipe({ name: 'cpfCnpj' })
export class CpfCnpjPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (!value) {
      return '';
    }
    const digits = documentDigits(value);
    return digits?.replace(CPF_MASK, '$1.$2.$3-$4').replace(CNPJ_MASK, '$1.$2.$3/$4-$5') ?? value;
  }
}
