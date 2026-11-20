import { DOCUMENT } from '@angular/common';
import {
  HttpContextToken,
  HttpErrorResponse,
  HttpEventType,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { catchError, defer, finalize, switchMap, take, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { readProblemDetail } from '../../shared/models/problem-detail';
import { ApiActivity } from './api-activity';
import { IDEMPOTENCY_KEY, IDEMPOTENT_POST } from './idempotency.interceptor';
import { UnavailableNoticeComponent } from './unavailable-notice.component';

export const ALLOW_UNAVAILABLE_RETRY = new HttpContextToken(() => false);
const API_PATH = `${environment.apiUrl}/api`;
const SNACKBAR_DURATION = 5000;

export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  if (request.url !== API_PATH && !request.url.startsWith(`${API_PATH}/`)) {
    return next(request);
  }

  const activity = inject(ApiActivity);
  const snackbar = inject(MatSnackBar);
  const router = inject(Router);
  const document = inject(DOCUMENT);

  return defer(() => next(request)).pipe(
    tap((event) => {
      if (event.type === HttpEventType.Response) {
        activity.setDisconnected(false);
      }
    }),
    catchError((error: unknown, requestAttempt) => {
      const fail = throwError(() => error);
      if (!(error instanceof HttpErrorResponse)) {
        return fail;
      }

      activity.setDisconnected(error.status === 0);

      switch (error.status) {
        case 0:
        case 400:
        case 422:
          // The shell displays network failures. The form consumes validation problems.
          break;
        case 401:
          snackbar.open('Sessão não autorizada.', 'Fechar', { duration: SNACKBAR_DURATION });
          break;
        case 403:
          void router.navigate(['/forbidden']);
          break;
        case 404:
          snackbar.open('Não encontrado.', 'Fechar', { duration: SNACKBAR_DURATION });
          break;
        case 409:
          snackbar
            .open(
              readProblemDetail(error)?.detail ?? 'Conflito ao processar a solicitação.',
              'Recarregar',
              {
                duration: SNACKBAR_DURATION,
              },
            )
            .onAction()
            .pipe(take(1))
            .subscribe(() => document.defaultView?.location.reload());
          break;
        case 503: {
          const retryAllowed = canRetry(request);
          const notice = snackbar.openFromComponent(UnavailableNoticeComponent, {
            data: { retryAllowed },
            announcementMessage: 'Serviço temporariamente indisponível.',
          });
          if (retryAllowed) {
            return notice.afterDismissed().pipe(
              switchMap(({ dismissedByAction }) => (dismissedByAction ? requestAttempt : fail)),
              finalize(() => notice.dismiss()),
            );
          }
          break;
        }
        default:
          snackbar.open('Não foi possível concluir a solicitação.', 'Fechar', {
            duration: SNACKBAR_DURATION,
          });
      }

      return fail;
    }),
  );
};

function canRetry(request: HttpRequest<unknown>): boolean {
  return (
    request.method === 'GET' ||
    request.method === 'HEAD' ||
    request.context.get(ALLOW_UNAVAILABLE_RETRY) ||
    (request.method === 'POST' &&
      request.context.get(IDEMPOTENT_POST) !== null &&
      request.headers.has(IDEMPOTENCY_KEY))
  );
}
