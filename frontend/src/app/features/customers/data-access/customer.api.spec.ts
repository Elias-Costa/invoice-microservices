import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  IDEMPOTENCY_KEY,
  idempotencyInterceptor,
} from '../../../core/interceptors/idempotency.interceptor';
import { customer, customerPath, customersPath } from '../customer-test-data';
import { CustomerApi } from './customer.api';

describe('CustomerApi', () => {
  let api: CustomerApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([idempotencyInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    api = TestBed.inject(CustomerApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses the billing query contract for a customer page', () => {
    api
      .list({
        active: false,
        search: '11222',
        page: 2,
        size: 5,
        sortField: 'document',
        sortDirection: 'desc',
      })
      .subscribe();
    const { request } = http.expectOne(
      (request) => request.method === 'GET' && request.url === customersPath,
    );
    expect(
      Object.fromEntries(request.params.keys().map((key) => [key, request.params.get(key)])),
    ).toEqual({
      active: 'false',
      search: '11222',
      page: '2',
      size: '5',
      sort: 'document,desc',
    });
  });

  it('encodes path parameters and uses the detail and status routes', () => {
    api.get('customer/name').subscribe();
    http.expectOne({ method: 'GET', url: `${customersPath}/customer%2Fname` }).flush(customer);

    api.setActive(customer.id, false).subscribe();
    http
      .expectOne({ method: 'DELETE', url: customerPath })
      .flush(null, { status: 204, statusText: 'No Content' });

    api.setActive(customer.id, true).subscribe();
    const reactivate = http.expectOne({ method: 'POST', url: `${customerPath}/reactivate` });
    expect(reactivate.request.headers.has(IDEMPOTENCY_KEY)).toBe(false);
    reactivate.flush(customer);
  });

  it('marks creation as idempotent and sends the version only on update', () => {
    const created = { name: customer.name, document: customer.document };
    api.create(created, 'customer-create-key').subscribe();
    const post = http.expectOne({ method: 'POST', url: customersPath });
    expect(post.request.headers.get(IDEMPOTENCY_KEY)).toBe('customer-create-key');
    expect(post.request.body).toEqual(created);
    post.flush(customer, { status: 201, statusText: 'Created' });

    const edited = { name: customer.name, version: 4 };
    api.update(customer.id, edited).subscribe();
    const put = http.expectOne({ method: 'PUT', url: customerPath });
    expect(put.request.headers.has(IDEMPOTENCY_KEY)).toBe(false);
    expect(put.request.body).toEqual(edited);
    put.flush(customer);
  });
});
