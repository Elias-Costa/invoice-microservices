import { DOCUMENT } from '@angular/common';
import {
  HttpClient,
  HttpContext,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar, MatSnackBarDismiss } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { readProblemDetail } from '../../shared/models/problem-detail';
import { ApiActivity } from './api-activity';
import { ALLOW_UNAVAILABLE_RETRY, errorInterceptor } from './error.interceptor';
import {
  IDEMPOTENCY_KEY,
  IDEMPOTENT_POST,
  idempotencyInterceptor,
} from './idempotency.interceptor';
import { loadingInterceptor } from './loading.interceptor';
import { UnavailableNoticeComponent } from './unavailable-notice.component';

describe('errorInterceptor', () => {
  const url = '/api/billing/invoices';
  const problemHeaders = { 'Content-Type': 'application/problem+json' };
  const unavailable = { status: 503, statusText: 'Unavailable' };
  const reload = vi.fn();
  const navigate = vi.fn();
  const dismiss = vi.fn();
  const snackbar = { open: vi.fn(), openFromComponent: vi.fn() };
  let action: Subject<void>;
  let closed: Subject<MatSnackBarDismiss>;
  let http: HttpClient;
  let requests: HttpTestingController;

  beforeEach(() => {
    vi.resetAllMocks();
    action = new Subject();
    closed = new Subject();
    snackbar.open.mockReturnValue({ onAction: () => action });
    snackbar.openFromComponent.mockReturnValue({ afterDismissed: () => closed, dismiss });
    navigate.mockResolvedValue(true);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(
          withInterceptors([idempotencyInterceptor, errorInterceptor, loadingInterceptor]),
        ),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: snackbar },
        { provide: Router, useValue: { navigate } },
        { provide: DOCUMENT, useValue: { defaultView: { location: { reload } } } },
      ],
    });
    http = TestBed.inject(HttpClient);
    requests = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    action.complete();
    closed.complete();
    requests.verify();
  });

  it.each([400, 422])('preserves the form error in a %s ProblemDetail', (status) => {
    const onError = vi.fn();
    const errors = [
      { field: 'items[0].quantity', message: 'Informe uma quantidade positiva.' },
      { field: null, message: 'Revise os dados da nota.' },
    ];
    http.post(url, {}).subscribe({ error: onError });
    requests.expectOne(url).flush(
      {
        type: 'urn:problem-type:validation-error',
        errors,
        detail: 'Dados inválidos.',
        status,
      },
      { status, statusText: 'Invalid', headers: problemHeaders },
    );

    expect(onError).toHaveBeenCalledOnce();
    expect(readProblemDetail(onError.mock.calls[0][0])).toMatchObject({
      status,
      detail: 'Dados inválidos.',
      errors,
    });
    expect(snackbar.open).not.toHaveBeenCalled();
  });

  it.each([
    [401, 'Sessão não autorizada.'],
    [404, 'Não encontrado.'],
    [500, 'Não foi possível concluir a solicitação.'],
  ])('shows readable feedback for %s without exposing an arbitrary body', (status, message) => {
    http.get(url).subscribe({ error: () => undefined });
    requests.expectOne(url).flush('java.lang.IllegalStateException', {
      status,
      statusText: 'Error',
    });
    expect(snackbar.open).toHaveBeenCalledWith(message, 'Fechar', expect.any(Object));
    expect(navigate).not.toHaveBeenCalled();
  });

  it('navigates to the forbidden page after a 403', () => {
    http.get(url).subscribe({ error: () => undefined });
    requests.expectOne(url).flush(null, { status: 403, statusText: 'Forbidden' });
    expect(navigate).toHaveBeenCalledWith(['/forbidden']);
  });

  it('shows the conflict detail and reloads after a 409 only through the action', () => {
    const message = 'A nota foi alterada por outra requisição.';
    http.put(url, {}).subscribe({ error: () => undefined });
    requests
      .expectOne(url)
      .flush({ detail: message }, { status: 409, statusText: 'Conflict', headers: problemHeaders });
    expect(snackbar.open).toHaveBeenCalledWith(message, 'Recarregar', expect.any(Object));
    expect(reload).not.toHaveBeenCalled();
    action.next();
    expect(reload).toHaveBeenCalledOnce();
  });

  it('repeats a read only when the user selects the persistent snackbar action', () => {
    const onSuccess = vi.fn();
    const activity = TestBed.inject(ApiActivity);
    http.get(url).subscribe(onSuccess);
    requests.expectOne(url).flush(null, unavailable);
    expect(activity.loading()).toBe(false);
    expect(snackbar.openFromComponent).toHaveBeenCalledWith(
      UnavailableNoticeComponent,
      expect.objectContaining({ data: { retryAllowed: true } }),
    );
    expect(snackbar.openFromComponent.mock.calls[0][1].duration).toBeUndefined();
    requests.expectNone(url);

    closed.next({ dismissedByAction: true });
    expect(activity.loading()).toBe(true);
    requests.expectOne(url).flush({ id: 'result' });
    expect(activity.loading()).toBe(false);
    expect(onSuccess).toHaveBeenCalledWith({ id: 'result' });
  });

  it('preserves the body and generated key when the marked POST is repeated', () => {
    const body = { customerId: 'customer', items: [] };
    const onSuccess = vi.fn();
    http
      .post(url, body, { context: new HttpContext().set(IDEMPOTENT_POST, true) })
      .subscribe(onSuccess);
    const original = requests.expectOne({ url, method: 'POST' });
    const key = original.request.headers.get(IDEMPOTENCY_KEY);
    expect(key).not.toBeNull();
    original.flush(null, unavailable);
    closed.next({ dismissedByAction: true });
    const repeated = requests.expectOne({ url, method: 'POST' });
    expect(repeated.request.headers.get(IDEMPOTENCY_KEY)).toBe(key);
    expect(repeated.request.body).toEqual(body);
    repeated.flush({ id: 'created' }, { status: 201, statusText: 'Created' });
    expect(onSuccess).toHaveBeenCalledWith({ id: 'created' });
  });

  it.each(['POST', 'PUT', 'DELETE'])('does not offer retry for an unapproved %s', (method) => {
    const onError = vi.fn();
    http.request(method, url).subscribe({ error: onError });
    requests.expectOne({ url, method }).flush(null, unavailable);
    expect(snackbar.openFromComponent).toHaveBeenCalledWith(
      UnavailableNoticeComponent,
      expect.objectContaining({ data: { retryAllowed: false } }),
    );
    expect(onError).toHaveBeenCalledWith(expect.objectContaining({ status: 503 }));
    requests.expectNone(url);
  });

  it('repeats another write when the feature has opted in', () => {
    const context = new HttpContext().set(ALLOW_UNAVAILABLE_RETRY, true);
    http.put(url, { version: 3 }, { context }).subscribe();
    requests.expectOne({ method: 'PUT', url }).flush(null, unavailable);
    closed.next({ dismissedByAction: true });
    requests.expectOne({ method: 'PUT', url }).flush({});
  });

  it('returns the original 503 upon dismissal without retrying', () => {
    const onError = vi.fn();
    http.get(url).subscribe({ error: onError });
    requests.expectOne(url).flush(null, unavailable);
    closed.next({ dismissedByAction: false });
    expect(onError).toHaveBeenCalledWith(expect.objectContaining({ status: 503 }));
    expect(snackbar.openFromComponent).toHaveBeenCalledOnce();
    requests.expectNone(url);
  });

  it('dismisses the retry prompt when the caller unsubscribes', () => {
    const subscription = http.get(url).subscribe();
    requests.expectOne(url).flush(null, unavailable);
    subscription.unsubscribe();
    expect(dismiss).toHaveBeenCalledOnce();
    closed.next({ dismissedByAction: true });
    requests.expectNone(url);
  });

  it('leaves non-API requests to their caller', () => {
    const onError = vi.fn<(error: HttpErrorResponse) => void>();
    http.get('/assets/manual').subscribe({ error: onError });
    requests.expectOne('/assets/manual').flush(null, { status: 404, statusText: 'Not Found' });
    expect(onError).toHaveBeenCalledOnce();
    expect(snackbar.open).not.toHaveBeenCalled();
  });
});
