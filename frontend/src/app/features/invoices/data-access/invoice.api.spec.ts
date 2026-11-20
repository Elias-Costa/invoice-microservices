import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  IDEMPOTENCY_KEY,
  idempotencyInterceptor,
} from '../../../core/interceptors/idempotency.interceptor';
import { invoice, invoicePath, invoicesPath, page } from '../invoice-test-data';
import { InvoiceApi } from './invoice.api';

describe('InvoiceApi', () => {
  let api: InvoiceApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([idempotencyInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    api = TestBed.inject(InvoiceApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('queries by optional status, customer and independent date boundaries in number-descending order', () => {
    const from = '2026-11-10T00:00:00.000Z';
    const to = '2026-11-20T00:00:00.000Z';
    api.list({ page: 2, size: 10, status: 'OPEN', customerId: 'customer-1', from, to }).subscribe();
    const filtered = http.expectOne(
      (request) => request.method === 'GET' && request.url === invoicesPath,
    );
    expect(filtered.request.params.get('status')).toBe('OPEN');
    expect(filtered.request.params.get('customerId')).toBe('customer-1');
    expect(filtered.request.params.get('from')).toBe(from);
    expect(filtered.request.params.get('to')).toBe(to);
    expect(filtered.request.params.get('page')).toBe('2');
    expect(filtered.request.params.get('size')).toBe('10');
    expect(filtered.request.params.get('sort')).toBe('number,desc');
    filtered.flush(page([]));

    api.list({ page: 0, size: 20, to }).subscribe();
    const onlyEnd = http.expectOne((request) => request.url === invoicesPath);
    expect(onlyEnd.request.params.keys()).toEqual(['page', 'size', 'sort', 'to']);
    onlyEnd.flush(page([]));
  });

  it('reads the detail and uses an idempotency key only to create the invoice', () => {
    api.get(invoice.id).subscribe();
    http.expectOne({ url: invoicePath, method: 'GET' }).flush(invoice);
    const items = [{ productId: 'product-1', quantity: 3 }];
    const createBody = { customerId: 'customer-1', items };
    api.create(createBody, 'invoice-key').subscribe();
    const create = http.expectOne({ url: invoicesPath, method: 'POST' });
    expect(create.request.headers.get(IDEMPOTENCY_KEY)).toBe('invoice-key');
    expect(create.request.body).toEqual(createBody);
    create.flush(invoice);

    api.replaceItems(invoice.id, { items, version: 4 }).subscribe();
    const update = http.expectOne({ url: `${invoicePath}/items`, method: 'PUT' });
    expect(update.request.body).toEqual({ items, version: 4 });
    expect(update.request.headers.has(IDEMPOTENCY_KEY)).toBe(false);
    update.flush(invoice);

    api.cancel(invoice.id, { reason: 'Pedido cancelado' }).subscribe();
    const cancel = http.expectOne({ url: `${invoicePath}/cancel`, method: 'POST' });
    expect(cancel.request.body).toEqual({ reason: 'Pedido cancelado' });
    expect(cancel.request.headers.has(IDEMPOTENCY_KEY)).toBe(false);
    cancel.flush(invoice);
  });
});
