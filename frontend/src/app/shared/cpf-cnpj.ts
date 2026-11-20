export const CPF_MASK = /^(\d{3})(\d{3})(\d{3})(\d{2})$/;
export const CNPJ_MASK = /^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/;

export function documentDigits(value: string): string | null {
  const document = value.trim();
  if (/^(?:\d{11}|\d{14})$/.test(document)) {
    return document;
  }
  if (/^\d{3}\.\d{3}\.\d{3}-\d{2}$|^\d{2}\.\d{3}\.\d{3}\/\d{4}-\d{2}$/.test(document)) {
    return document.replace(/[./-]/g, '');
  }
  return null;
}
