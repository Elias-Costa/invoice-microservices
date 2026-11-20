import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatAutocompleteHarness } from '@angular/material/autocomplete/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatInputHarness } from '@angular/material/input/testing';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../../app.routes';
import {
  IDEMPOTENCY_KEY,
  idempotencyInterceptor,
} from '../../../core/interceptors/idempotency.interceptor';
import {
  customer,
  customersPath,
  invoice,
  invoicePath,
  invoicesPath,
  page,
  problemHeaders,
  product,
  productsPath,
} from '../invoice-test-data';

describe('InvoiceFormComponent', () => {
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes, withComponentInputBinding()),
        provideHttpClient(withInterceptors([idempotencyInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => http.verify());

  it('selects active references, prevents duplicate items and creates even above the current balance', async () => {
    const routing = await RouterTestingHarness.create('/invoices/new');
    const loader = TestbedHarnessEnvironment.loader(routing.fixture);
    const [customerPicker, productPicker] = await loader.getAllHarnesses(MatAutocompleteHarness);
    const save = await loader.getHarness(MatButtonHarness.with({ text: 'Criar nota fiscal' }));
    expect(await save.isDisabled()).toBe(true);

    await customerPicker.enterText('529.982');
    http.expectNone((request) => request.url === customersPath);
    const customerRequest = await vi.waitFor(() =>
      http.expectOne((request) => request.url === customersPath),
    );
    expect(customerRequest.request.params.get('active')).toBe('true');
    expect(customerRequest.request.params.get('search')).toBe('529982');
    customerRequest.flush(page([customer]));
    await customerPicker.selectOption({ text: /Cliente de teste/ });

    await productPicker.enterText('PAR');
    const productRequest = await vi.waitFor(() =>
      http.expectOne((request) => request.url === productsPath),
    );
    expect(productRequest.request.params.get('active')).toBe('true');
    expect(productRequest.request.params.get('search')).toBe('PAR');
    productRequest.flush(page([product]));
    const [option] = await productPicker.getOptions({ text: /PAR-M6/ });
    expect(await option.getText()).toContain('Saldo 2');
    await option.click();

    await productPicker.enterText('PAR');
    const repeat = await vi.waitFor(() =>
      http.expectOne((request) => request.url === productsPath),
    );
    repeat.flush(page([product]));
    const [duplicate] = await productPicker.getOptions({ text: /Já incluído/ });
    expect(await duplicate.isDisabled()).toBe(true);
    const quantity = await loader.getHarness(
      MatInputHarness.with({ selector: '[aria-label="Quantidade de PAR-M6"]' }),
    );
    await quantity.setValue('1.5');
    expect(await save.isDisabled()).toBe(true);
    await quantity.setValue('3');
    expect(routing.routeNativeElement?.textContent).toContain('Quantidade acima do saldo atual.');
    expect(routing.routeNativeElement?.querySelector('.total')?.textContent).toMatch(/1[.,]05/);
    expect(await save.isDisabled()).toBe(false);

    await save.click();
    const first = http.expectOne({ url: invoicesPath, method: 'POST' });
    const key = first.request.headers.get(IDEMPOTENCY_KEY);
    expect(key).toMatch(/^[\da-f-]{36}$/);
    expect(first.request.body).toEqual({
      customerId: customer.id,
      items: [{ productId: product.id, quantity: 3 }],
    });
    first.flush(
      { detail: 'O Estoque está indisponível.' },
      { status: 503, statusText: 'Unavailable', headers: problemHeaders },
    );
    expect(await save.isDisabled()).toBe(false);
    await save.click();
    const retried = http.expectOne({ url: invoicesPath, method: 'POST' });
    expect(retried.request.headers.get(IDEMPOTENCY_KEY)).toBe(key);
    retried.flush(invoice, { status: 201, statusText: 'Created' });
    await vi.waitFor(() => expect(router.url).toBe(`/invoices/${invoice.id}`));
    await routing.fixture.whenStable();
    http.expectOne(invoicePath).flush(invoice);
  });

  it('uses current stock data for editing and saves items with the version and the original customer', async () => {
    const routing = await RouterTestingHarness.create(`/invoices/${invoice.id}/edit`);
    expect(routing.routeNativeElement?.textContent).toContain('Carregando formulário');
    http.expectOne(invoicePath).flush(invoice);
    const batch = http.expectOne((request) => request.url === `${productsPath}/batch`);
    expect(batch.request.params.get('ids')).toBe(product.id);
    batch.flush([product]);
    const loader = TestbedHarnessEnvironment.loader(routing.fixture);
    expect(await loader.getAllHarnesses(MatAutocompleteHarness)).toHaveLength(1);
    expect(routing.routeNativeElement?.textContent).toContain(customer.name);
    expect(routing.routeNativeElement?.querySelector('.total')?.textContent).toMatch(/0[.,]70/);

    const quantity = await loader.getHarness(
      MatInputHarness.with({ selector: '[aria-label="Quantidade de PAR-M6"]' }),
    );
    await quantity.setValue('4');
    const left = router.navigateByUrl('/');
    const back = await vi.waitFor(() => {
      // The navigation stays pending until the dialog closes, so the harness cannot stabilize.
      const buttons = document.querySelectorAll<HTMLButtonElement>(
        'mat-dialog-container.mdc-dialog--open button',
      );
      const button = Array.from(buttons).find(
        (candidate) => candidate.textContent?.trim() === 'Cancelar',
      );
      expect(button).toBeDefined();
      return button;
    });
    back?.click();
    expect(await left).toBe(false);
    expect(await quantity.getValue()).toBe('4');

    const save = await loader.getHarness(MatButtonHarness.with({ text: 'Salvar itens' }));
    await save.click();
    const update = http.expectOne({ url: `${invoicePath}/items`, method: 'PUT' });
    expect(update.request.body).toEqual({
      items: [{ productId: product.id, quantity: 4 }],
      version: 4,
    });
    update.flush({ ...invoice, version: 5 });
    await vi.waitFor(() => expect(router.url).toBe(`/invoices/${invoice.id}`));
    await routing.fixture.whenStable();
    http.expectOne(invoicePath).flush({ ...invoice, version: 5 });
  });

  it('does not offer an editor when the API disallows editing', async () => {
    const routing = await RouterTestingHarness.create(`/invoices/${invoice.id}/edit`);
    http.expectOne(invoicePath).flush({ ...invoice, status: 'CLOSED', allowedActions: [] });
    await routing.fixture.whenStable();
    expect(routing.routeNativeElement?.textContent).toContain(
      'A edição de itens está indisponível',
    );
    expect(routing.routeNativeElement?.querySelector('form')).toBeNull();
  });

  it('keeps the remaining quantity editable after removing an inactive product', async () => {
    const inactive = { ...product, id: 'inactive', code: 'INATIVO', active: false };
    const item = invoice.items[0];
    const routing = await RouterTestingHarness.create(`/invoices/${invoice.id}/edit`);
    http.expectOne(invoicePath).flush({
      ...invoice,
      items: [
        { ...item, id: 'inactive-item', productId: inactive.id, productCode: inactive.code },
        { ...item, lineOrder: 2 },
      ],
    });
    http.expectOne((request) => request.url === `${productsPath}/batch`).flush([product, inactive]);
    const loader = TestbedHarnessEnvironment.loader(routing.fixture);
    const save = await loader.getHarness(MatButtonHarness.with({ text: 'Salvar itens' }));
    expect(await save.isDisabled()).toBe(true);
    expect(routing.routeNativeElement?.textContent).toContain('Produto indisponível');
    const remove = await loader.getHarness(
      MatButtonHarness.with({ selector: '[aria-label="Remover INATIVO"]' }),
    );
    await remove.click();

    const quantity = await loader.getHarness(
      MatInputHarness.with({ selector: '[aria-label="Quantidade de PAR-M6"]' }),
    );
    await quantity.setValue('7');
    expect(routing.routeNativeElement?.querySelector('.total')?.textContent).toMatch(/2[.,]45/);
    expect(await save.isDisabled()).toBe(false);
    expect(routing.routeNativeElement?.textContent).not.toContain('Produto indisponível');
  });
});
