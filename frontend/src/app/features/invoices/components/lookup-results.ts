import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl } from '@angular/forms';
import { distinctUntilChanged, map, Observable, of, startWith, switchMap, timer } from 'rxjs';
import { readState, ReadState } from '../../../shared/http/read-state';

export function lookupResults<T>(
  input: FormControl<string | T>,
  normalize: (term: string) => string,
  search: (term: string) => Observable<T[]>,
  fallback: string,
) {
  const idle = { status: 'ready', data: [] } as const satisfies ReadState<T[]>;
  return toSignal(
    input.valueChanges.pipe(
      map((value) => (typeof value === 'string' ? normalize(value) : '')),
      distinctUntilChanged(),
      switchMap((term) =>
        term
          ? timer(300).pipe(
              switchMap(() => readState(search(term), fallback)),
              startWith({ status: 'loading' } as const),
            )
          : of(idle),
      ),
    ),
    { initialValue: idle },
  );
}
