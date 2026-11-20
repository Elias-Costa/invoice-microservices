import { HttpErrorResponse } from '@angular/common/http';
import { catchError, map, Observable, of, startWith } from 'rxjs';
import { readProblemDetail } from '../models/problem-detail';

export type ReadState<T> =
  { status: 'loading' } | { status: 'ready'; data: T } | { status: 'failed'; message: string };

export function readState<T>(request: Observable<T>, fallback: string): Observable<ReadState<T>> {
  return request.pipe(
    map((data): ReadState<T> => ({ status: 'ready', data })),
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
