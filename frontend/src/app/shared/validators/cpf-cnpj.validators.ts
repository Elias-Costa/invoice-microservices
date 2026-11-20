import { ValidatorFn } from '@angular/forms';
import { documentDigits } from '../cpf-cnpj';

export const cpfValidator = documentValidator('cpf', 11);
export const cnpjValidator = documentValidator('cnpj', 14);
export const cpfCnpjValidator = documentValidator('cpfCnpj');

function documentValidator(errorKey: string, expectedLength?: number): ValidatorFn {
  return (control) => {
    const value: unknown = control.value;
    if (value == null || value === '') {
      return null;
    }

    const digits = typeof value === 'string' ? documentDigits(value) : null;
    const valid =
      digits !== null &&
      (expectedLength === undefined || digits.length === expectedLength) &&
      hasValidCheckDigits(digits);
    return valid ? null : { [errorKey]: true };
  };
}

function hasValidCheckDigits(digits: string): boolean {
  if (/^(\d)\1+$/.test(digits)) {
    return false;
  }
  const last = digits.length - 1;
  const maxWeight = digits.length === 11 ? 11 : 9;
  return (
    checkDigit(digits, last - 1, maxWeight) === Number(digits[last - 1]) &&
    checkDigit(digits, last, maxWeight) === Number(digits[last])
  );
}

function checkDigit(digits: string, end: number, maxWeight: number): number {
  let sum = 0;
  let weight = 2;
  for (let index = end - 1; index >= 0; index--) {
    sum += Number(digits[index]) * weight;
    weight = weight === maxWeight ? 2 : weight + 1;
  }
  const remainder = sum % 11;
  return remainder < 2 ? 0 : 11 - remainder;
}
