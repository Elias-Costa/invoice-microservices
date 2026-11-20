import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { page, product, productsPath } from '../product-test-data';
import { ProductStore } from './product.store';

describe('ProductStore', () => {
  let store: ProductStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ProductStore, provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(ProductStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function nextListRequest() {
    TestBed.tick();
    return http.expectOne((request) => request.url === productsPath && request.method === 'GET');
  }

  it('starts at the first active page and replaces a pending search request', () => {
    expect(store.result().status).toBe('loading');
    const initial = nextListRequest();
    expect(initial.request.params.get('active')).toBe('true');
    expect(initial.request.params.get('sort')).toBe('code,asc');
    expect(initial.request.params.get('page')).toBe('0');
    store.setSearch('broca');
    const filtered = nextListRequest();
    expect(initial.cancelled).toBe(true);
    expect(filtered.request.params.get('search')).toBe('broca');
    filtered.flush(page([product]));
    expect(store.result()).toEqual({ status: 'ready', data: page([product]) });
  });

  it('sends pagination and resets to page zero when sorting or changing status', () => {
    nextListRequest().flush(page([]));
    store.setPage(2, 5);
    const paged = nextListRequest();
    expect(paged.request.params.get('page')).toBe('2');
    expect(paged.request.params.get('size')).toBe('5');
    paged.flush(page([]));

    store.setSort('balance', 'desc');
    const sorted = nextListRequest();
    expect(sorted.request.params.get('page')).toBe('0');
    expect(sorted.request.params.get('sort')).toBe('balance,desc');
    sorted.flush(page([]));

    store.setPage(1, 5);
    store.setActive(false);
    const inactive = nextListRequest();
    expect(inactive.request.params.get('active')).toBe('false');
    expect(inactive.request.params.get('page')).toBe('0');
    inactive.flush(page([]));
  });

  it('exposes a recoverable error and retries with the same criteria on reload', () => {
    const first = nextListRequest();
    first.flush(null, { status: 500, statusText: 'Error' });
    expect(store.result()).toEqual({
      status: 'failed',
      message: 'Não foi possível carregar os produtos.',
    });
    store.reload();
    const retry = nextListRequest();
    expect(retry.request.urlWithParams).toBe(first.request.urlWithParams);
    retry.flush(page([]));
    expect(store.result()).toEqual({ status: 'ready', data: page([]) });
  });
});
