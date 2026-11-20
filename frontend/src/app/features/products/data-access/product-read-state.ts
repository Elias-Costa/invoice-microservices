import { HttpErrorResponse } from '@angular/common/http';
import { catchError, map, Observable, of, startWith } from 'rxjs';
import { readProblemDetail } from '../../../shared/models/problem-detail';

export type ProductReadState<T> =
  { status: 'loading' } | { status: 'ready'; data: T } | { status: 'failed'; message: string };

export function productReadState<T>(
  request: Observable<T>,
  fallback: string,
): Observable<ProductReadState<T>> {
  return request.pipe(
    map((data): ProductReadState<T> => ({ status: 'ready', data })),
    catchError((error: unknown) =>
      of({
        status: 'failed' as const,
        message:
          (error instanceof HttpErrorResponse ? readProblemDetail(error)?.detail : undefined) ??
          fallback,
      }),
    ),
    startWith({ status: 'loading' as const }),
  );
}
