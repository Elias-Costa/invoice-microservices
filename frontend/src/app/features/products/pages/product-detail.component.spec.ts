import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatDialogHarness } from '@angular/material/dialog/testing';
import { MatInputHarness } from '@angular/material/input/testing';
import { MatPaginatorHarness } from '@angular/material/paginator/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../../app.routes';
import {
  IDEMPOTENCY_KEY,
  idempotencyInterceptor,
} from '../../../core/interceptors/idempotency.interceptor';
import {
  historyPath,
  movement,
  page,
  problemHeaders,
  product,
  productPath,
} from '../product-test-data';

describe('ProductDetailComponent', () => {
  let routing: RouterTestingHarness;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes, withComponentInputBinding()),
        provideHttpClient(withInterceptors([idempotencyInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    routing = await RouterTestingHarness.create(`/products/${product.id}`);
    await routing.fixture.whenStable();
  });

  afterEach(() => http.verify());

  function historyRequest() {
    return http.expectOne((request) => request.method === 'GET' && request.url === historyPath);
  }

  it('shows the history loading, error and empty states independently of product data', async () => {
    expect(routing.routeNativeElement?.textContent).toContain('Carregando produto');
    http.expectOne(productPath).flush(product);
    await routing.fixture.whenStable();
    expect(routing.routeNativeElement?.textContent).toContain('Carregando movimentações');
    historyRequest().flush(null, { status: 500, statusText: 'Error' });
    const loader = TestbedHarnessEnvironment.loader(routing.fixture);
    const retry = await loader.getHarness(MatButtonHarness.with({ text: 'Tentar novamente' }));
    expect(routing.routeNativeElement?.textContent).toContain(
      'Não foi possível carregar as movimentações.',
    );
    await retry.click();
    historyRequest().flush(page([]));
    await routing.fixture.whenStable();
    expect(routing.routeNativeElement?.textContent).toContain('Nenhuma movimentação encontrada.');
    expect(routing.routeNativeElement?.textContent).toContain(product.description);
  });

  it('requests the next server page and renders invoice references and users', async () => {
    http.expectOne(productPath).flush(product);
    historyRequest().flush(page([movement], 21));
    const loader = TestbedHarnessEnvironment.loader(routing.fixture);
    const paginator = await loader.getHarness(MatPaginatorHarness);
    await paginator.goToNextPage();
    const next = historyRequest();
    expect(next.request.params.get('page')).toBe('1');
    next.flush(
      page(
        [{ ...movement, type: 'INVOICE_DEBIT', referenceId: 'invoice-id', referenceNumber: 5 }],
        21,
        1,
      ),
    );
    await routing.fixture.whenStable();
    expect(
      routing.routeNativeElement?.querySelector('[title="invoice-id"]')?.textContent,
    ).toContain('NF 5');
    expect(routing.routeNativeElement?.textContent).toContain('Baixa por nota');
    expect(routing.routeNativeElement?.textContent).toContain('anonymous');
  });

  it('confirms status changes, then loads the latest product', async () => {
    http.expectOne(productPath).flush(product);
    historyRequest().flush(page([]));
    const loader = TestbedHarnessEnvironment.loader(routing.fixture);
    const overlay = TestbedHarnessEnvironment.documentRootLoader(routing.fixture);
    await (await loader.getHarness(MatButtonHarness.with({ text: 'Inativar' }))).click();
    const confirmation = await overlay.getHarness(MatDialogHarness);
    http.expectNone({ method: 'DELETE', url: productPath });
    await (await confirmation.getHarness(MatButtonHarness.with({ text: 'Inativar' }))).click();
    const write = await vi.waitFor(() => http.expectOne({ method: 'DELETE', url: productPath }));
    write.flush(null, { status: 204, statusText: 'No Content' });
    const read = await vi.waitFor(() => http.expectOne({ method: 'GET', url: productPath }));
    read.flush({ ...product, active: false });
    expect(await loader.hasHarness(MatButtonHarness.with({ text: 'Reativar' }))).toBe(true);
  });

  it('keeps the dialog open on errors and refreshes balance and history after a movement', async () => {
    http.expectOne(productPath).flush({ ...product, active: false });
    historyRequest().flush(page([]));
    const loader = TestbedHarnessEnvironment.loader(routing.fixture);
    const overlay = TestbedHarnessEnvironment.documentRootLoader(routing.fixture);
    await (await loader.getHarness(MatButtonHarness.with({ text: 'Movimentar' }))).click();
    const dialog = await overlay.getHarness(MatDialogHarness);
    const quantity = await dialog.getHarness(
      MatInputHarness.with({ selector: '[formControlName="quantity"]' }),
    );
    const reason = await dialog.getHarness(
      MatInputHarness.with({ selector: '[formControlName="reason"]' }),
    );
    const submit = await dialog.getHarness(
      MatButtonHarness.with({ text: 'Registrar movimentação' }),
    );
    expect(document.querySelector<HTMLOptionElement>('option[value="OUTBOUND"]')?.disabled).toBe(
      true,
    );
    expect(await submit.isDisabled()).toBe(true);
    await quantity.setValue('3');
    await reason.setValue('  Reposição de teste  ');
    await submit.click();
    const first = http.expectOne({ method: 'POST', url: historyPath });
    expect(first.request.body).toEqual({
      type: 'INBOUND',
      quantity: 3,
      reason: 'Reposição de teste',
    });
    first.error(new ProgressEvent('error'));
    expect(await dialog.getText()).toContain('Não foi possível registrar a movimentação.');

    await submit.click();
    const retry = http.expectOne({ method: 'POST', url: historyPath });
    expect(retry.request.headers.get(IDEMPOTENCY_KEY)).toBe(
      first.request.headers.get(IDEMPOTENCY_KEY),
    );
    retry.flush(
      {
        type: 'urn:problem-type:validation-error',
        errors: [{ field: 'quantity', message: 'O saldo excederia o limite.' }],
      },
      { status: 400, statusText: 'Invalid', headers: problemHeaders },
    );
    expect(await dialog.getText()).toContain('O saldo excederia o limite.');

    await quantity.setValue('4');
    await quantity.setValue('3');
    await submit.click();
    const corrected = http.expectOne({ method: 'POST', url: historyPath });
    expect(corrected.request.headers.get(IDEMPOTENCY_KEY)).not.toBe(
      retry.request.headers.get(IDEMPOTENCY_KEY),
    );
    corrected.flush(movement, { status: 201, statusText: 'Created' });
    const reloadProduct = await vi.waitFor(() =>
      http.expectOne({ method: 'GET', url: productPath }),
    );
    reloadProduct.flush({ ...product, active: false, balance: 3 });
    historyRequest().flush(page([movement]));
    await routing.fixture.whenStable();
    expect(routing.routeNativeElement?.querySelector('.balance dd')?.textContent).toBe('3');
    expect(routing.routeNativeElement?.textContent).toContain('Reposição de teste');
    expect(await overlay.hasHarness(MatDialogHarness)).toBe(false);
  });
});
