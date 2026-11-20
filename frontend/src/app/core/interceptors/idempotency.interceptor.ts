import { HttpContextToken, HttpInterceptorFn } from '@angular/common/http';

// A caller can provide a UUID for a logical operation that spans multiple subscriptions.
export const IDEMPOTENT_POST = new HttpContextToken<string | true | null>(() => null);
export const IDEMPOTENCY_KEY = 'Idempotency-Key';

export const idempotencyInterceptor: HttpInterceptorFn = (request, next) => {
  const operation = request.context.get(IDEMPOTENT_POST);
  if (request.method !== 'POST' || operation === null || request.headers.has(IDEMPOTENCY_KEY)) {
    return next(request);
  }

  const key = operation === true ? crypto.randomUUID() : operation;
  request.context.set(IDEMPOTENT_POST, key);
  return next(request.clone({ setHeaders: { [IDEMPOTENCY_KEY]: key } }));
};
