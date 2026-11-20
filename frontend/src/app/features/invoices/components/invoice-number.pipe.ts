import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'invoiceNumber' })
export class InvoiceNumberPipe implements PipeTransform {
  transform(value: number): string {
    return String(value).padStart(6, '0');
  }
}
