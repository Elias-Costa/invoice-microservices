import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { customer, customerPage, customersPath, problemHeaders } from '../customer-test-data';
import { CustomerStore } from './customer.store';

describe('CustomerStore', () => {
  let store: CustomerStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [CustomerStore, provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(CustomerStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('cancels stale reads and resets pagination when query criteria change', () => {
    expect(store.result().status).toBe('loading');
    TestBed.tick();
    const initial = http.expectOne((request) => request.url === customersPath);
    store.setPage(3, 5);
    TestBed.tick();
    const previousPage = http.expectOne((request) => request.params.get('page') === '3');
    expect(initial.cancelled).toBe(true);

    store.setSearch('Silva');
    store.setActive(false);
    store.setSort('document', 'desc');
    TestBed.tick();
    expect(previousPage.cancelled).toBe(true);
    expect(store.query()).toEqual({
      search: 'Silva',
      active: false,
      page: 0,
      size: 5,
      sortField: 'document',
      sortDirection: 'desc',
    });
    const result = customerPage([{ ...customer, active: false }]);
    http.expectOne((request) => request.params.get('search') === 'Silva').flush(result);
    expect(store.result()).toEqual({ status: 'ready', data: result });
  });

  it('can reload the same query after an API failure', () => {
    TestBed.tick();
    const request = http.expectOne((request) => request.url === customersPath);
    request.flush(
      { detail: 'Faturamento indisponível.' },
      { status: 500, statusText: 'Error', headers: problemHeaders },
    );
    expect(store.result()).toEqual({ status: 'failed', message: 'Faturamento indisponível.' });
    store.reload();
    TestBed.tick();
    expect(store.result().status).toBe('loading');
    http.expectOne(request.request.urlWithParams).flush(customerPage([]));
    expect(store.result()).toEqual({ status: 'ready', data: customerPage([]) });
  });
});
