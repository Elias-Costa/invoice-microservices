export function documentSearchTerm(value: string): string {
  const term = value.trim();
  return /^[0-9./-]+$/.test(term) && /\d/.test(term) ? term.replace(/[./-]/g, '') : term;
}

export function formatCustomerDocument(value: string): string {
  const digits = value.replace(/\D/g, '');
  return digits.length <= 11
    ? applyGroups(digits, [3, 3, 3], ['.', '.', '-'])
    : applyGroups(digits, [2, 3, 3, 4], ['.', '.', '/', '-']);
}

export function formatCustomerPhone(value: string): string {
  const digits = value.replace(/\D/g, '');
  if (!digits) {
    return '';
  }
  if (!/^55\d{10,11}$/.test(digits)) {
    return `+${digits}`;
  }

  const prefix = digits.length === 13 ? 5 : 4;
  return `+${applyGroups(digits, [2, 2, prefix], [' (', ') ', '-'])}`;
}

function applyGroups(digits: string, lengths: number[], separators: string[]): string {
  let formatted = '';
  let offset = 0;
  for (const [index, length] of lengths.entries()) {
    const end = offset + length;
    if (digits.length <= end) {
      return formatted + digits.slice(offset);
    }
    formatted += digits.slice(offset, end) + separators[index];
    offset = end;
  }
  return formatted + digits.slice(offset);
}
