import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { retry } from 'rxjs';
import {
  IDEMPOTENCY_KEY,
  IDEMPOTENT_POST,
  idempotencyInterceptor,
} from './idempotency.interceptor';

describe('idempotencyInterceptor', () => {
  const url = '/api/billing/invoices';
  const uuid = '88888888-e1db-4a9c-aaaa-b5718255daba';
  let http: HttpClient;
  let requests: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([idempotencyInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    requests = TestBed.inject(HttpTestingController);
  });

  afterEach(() => requests.verify());

  it('generates a UUID for a marked POST and reuses it on resubscription', () => {
    const context = new HttpContext().set(IDEMPOTENT_POST, true);
    http.post(url, {}, { context }).pipe(retry(1)).subscribe();

    const first = requests.expectOne(url);
    const key = first.request.headers.get(IDEMPOTENCY_KEY);
    expect(key).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i);
    first.flush(null, { status: 503, statusText: 'Unavailable' });

    const repeated = requests.expectOne(url);
    expect(repeated.request.headers.get(IDEMPOTENCY_KEY)).toBe(key);
    repeated.flush({}, { status: 201, statusText: 'Created' });
  });

  it('uses the UUID provided by the caller and preserves an existing header', () => {
    const context = new HttpContext().set(IDEMPOTENT_POST, uuid);
    http.post(url, {}, { context }).subscribe();
    const first = requests.expectOne(url);
    expect(first.request.headers.get(IDEMPOTENCY_KEY)).toBe(uuid);
    first.flush({});

    const header = '44444444-abcd-4beb-bbbb-e9de3f2fb5a5';
    http.post(url, {}, { context, headers: { [IDEMPOTENCY_KEY]: header } }).subscribe();
    const second = requests.expectOne(url);
    expect(second.request.headers.get(IDEMPOTENCY_KEY)).toBe(header);
    second.flush({});
  });

  it('leaves unmarked POSTs and marked PUTs without a generated key', () => {
    http.post(url, {}).subscribe();
    const unmarked = requests.expectOne({ method: 'POST', url });
    expect(unmarked.request.headers.has(IDEMPOTENCY_KEY)).toBe(false);
    unmarked.flush({});

    http.put(url, {}, { context: new HttpContext().set(IDEMPOTENT_POST, true) }).subscribe();
    const marked = requests.expectOne({ method: 'PUT', url });
    expect(marked.request.headers.has(IDEMPOTENCY_KEY)).toBe(false);
    marked.flush({});
  });
});
