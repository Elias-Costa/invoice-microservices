import { Injectable } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';

@Injectable()
export class ProductPaginatorIntl extends MatPaginatorIntl {
  private readonly number = new Intl.NumberFormat('pt-BR');

  override itemsPerPageLabel = 'Itens por página';
  override nextPageLabel = 'Próxima página';
  override previousPageLabel = 'Página anterior';
  override firstPageLabel = 'Primeira página';
  override lastPageLabel = 'Última página';

  override getRangeLabel = (page: number, pageSize: number, length: number): string => {
    const number = this.number;
    if (!length || !pageSize) {
      return `0 de ${number.format(length)}`;
    }

    const start = page * pageSize;
    return `${number.format(start + 1)}–${number.format(Math.min(start + pageSize, length))} de ${number.format(length)}`;
  };
}
