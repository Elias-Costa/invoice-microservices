import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { defer, finalize } from 'rxjs';
import { ApiActivity } from './api-activity';

export const loadingInterceptor: HttpInterceptorFn = (request, next) => {
  const activity = inject(ApiActivity);

  return defer(() => {
    activity.startRequest();
    return next(request);
  }).pipe(finalize(() => activity.finishRequest()));
};
