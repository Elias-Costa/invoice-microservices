import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiActivity } from './api-activity';
import { loadingInterceptor } from './loading.interceptor';

describe('loadingInterceptor', () => {
  let http: HttpClient;
  let requests: HttpTestingController;
  let activity: ApiActivity;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([loadingInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    requests = TestBed.inject(HttpTestingController);
    activity = TestBed.inject(ApiActivity);
  });

  afterEach(() => requests.verify());

  it('counts concurrent requests and clears after the last one fails', () => {
    expect(activity.loading()).toBe(false);
    const error = vi.fn();
    http.get('/api/stock/products').subscribe();
    http.get('/api/billing/invoices').subscribe({ error });
    expect(activity.loading()).toBe(true);

    requests.expectOne('/api/stock/products').flush([]);
    expect(activity.loading()).toBe(true);
    requests.expectOne('/api/billing/invoices').flush(null, {
      status: 500,
      statusText: 'Internal Server Error',
    });
    expect(error).toHaveBeenCalledOnce();
    expect(activity.loading()).toBe(false);
  });

  it('clears the counter when a request is cancelled', () => {
    const subscription = http.get('/api/stock/products').subscribe();
    const pending = requests.expectOne('/api/stock/products');
    expect(activity.loading()).toBe(true);

    subscription.unsubscribe();
    expect(pending.cancelled).toBe(true);
    expect(activity.loading()).toBe(false);
  });
});
