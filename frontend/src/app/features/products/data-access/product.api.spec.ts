import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  IDEMPOTENCY_KEY,
  idempotencyInterceptor,
} from '../../../core/interceptors/idempotency.interceptor';
import { historyPath, movement, product, productPath, productsPath } from '../product-test-data';
import { ProductApi } from './product.api';

describe('ProductApi', () => {
  const key = 'c0000000-0000-4000-8000-000000000001';
  let api: ProductApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([idempotencyInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    api = TestBed.inject(ProductApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('marks only product creation and manual movements as idempotent POSTs', () => {
    const create = {
      code: product.code,
      description: product.description,
      unitPrice: product.unitPrice,
      initialBalance: 0,
    };
    api.create(create, key).subscribe();
    const creation = http.expectOne({ method: 'POST', url: productsPath });
    expect(creation.request.headers.get(IDEMPOTENCY_KEY)).toBe(key);
    expect(creation.request.body).toEqual(create);
    creation.flush(product);

    const entry = { type: 'INBOUND' as const, quantity: 3, reason: 'Reposição' };
    api.registerMovement(product.id, entry, key).subscribe();
    const registration = http.expectOne({ method: 'POST', url: historyPath });
    expect(registration.request.headers.get(IDEMPOTENCY_KEY)).toBe(key);
    expect(registration.request.body).toEqual(entry);
    registration.flush(movement);

    api.setActive(product.id, true).subscribe();
    const reactivation = http.expectOne({ method: 'POST', url: `${productPath}/reactivate` });
    expect(reactivation.request.headers.has(IDEMPOTENCY_KEY)).toBe(false);
    reactivation.flush(product);

    api.setActive(product.id, false).subscribe();
    http
      .expectOne({ method: 'DELETE', url: productPath })
      .flush(null, { status: 204, statusText: 'No Content' });
  });
});
