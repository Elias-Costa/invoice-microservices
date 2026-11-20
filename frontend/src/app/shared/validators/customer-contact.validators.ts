import { ValidatorFn } from '@angular/forms';

const EMAIL_ATOM = "[\\p{L}\\p{N}!#$%&'*+/=?^_`{|}~-]+";
const DOMAIN_LABEL = '[\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]{0,61}[\\p{L}\\p{N}])?';
const EMAIL = new RegExp(
  `^${EMAIL_ATOM}(?:\\.${EMAIL_ATOM})*@${DOMAIN_LABEL}(?:\\.${DOMAIN_LABEL})*$`,
  'u',
);

export const customerNameValidator: ValidatorFn = (control) => {
  const value = String(control.value ?? '').trim();
  if (!value) {
    return { required: true };
  }
  return Array.from(value).length > 150 ? { maxlength: true } : null;
};

export const customerEmailValidator: ValidatorFn = (control) => {
  const value = String(control.value ?? '').trim();
  return !value || (Array.from(value).length <= 150 && EMAIL.test(value)) ? null : { email: true };
};

export const customerPhoneValidator: ValidatorFn = (control) => {
  const value = String(control.value ?? '').replace(/\D/g, '');
  return value.length <= 20 ? null : { phone: true };
};
