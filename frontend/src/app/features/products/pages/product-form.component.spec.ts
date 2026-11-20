import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatInputHarness } from '@angular/material/input/testing';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../../app.routes';
import {
  IDEMPOTENCY_KEY,
  idempotencyInterceptor,
} from '../../../core/interceptors/idempotency.interceptor';
import { PRODUCT_CODE_ALREADY_EXISTS } from '../../../shared/models/stock';
import {
  historyPath,
  page,
  problemHeaders,
  product,
  productPath,
  productsPath,
} from '../product-test-data';

describe('ProductFormComponent and navigation guard', () => {
  let routing: RouterTestingHarness;
  let router: Router;
  let http: HttpTestingController;

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

  async function open(url: string) {
    routing = await RouterTestingHarness.create(url);
    return TestbedHarnessEnvironment.loader(routing.fixture);
  }

  function byField(field: string) {
    return MatInputHarness.with({ selector: `[formControlName="${field}"]` });
  }

  async function finishNavigation() {
    await vi.waitFor(() => expect(router.url).toBe(`/products/${product.id}`));
    await routing.fixture.whenStable();
    http.expectOne({ url: productPath, method: 'GET' }).flush(product);
    http.expectOne((request) => request.url === historyPath).flush(page([]));
    expect(document.querySelector('[role="dialog"]')).toBeNull();
  }

  it('validates fields, normalizes code, renders duplicate 409 inline and saves a corrected code', async () => {
    const loader = await open('/products/new');
    const code = await loader.getHarness(byField('code'));
    const description = await loader.getHarness(byField('description'));
    const price = await loader.getHarness(byField('unitPrice'));
    const initialBalance = await loader.getHarness(byField('initialBalance'));
    const submit = await loader.getHarness(MatButtonHarness.with({ text: 'Salvar produto' }));
    expect(await submit.isDisabled()).toBe(true);

    await (await code.host()).sendKeys('  bro-aco-8  ');
    expect(await code.getValue()).toBe('  BRO-ACO-8  ');
    await description.setValue('  Broca  ');
    await price.setValue('-1');
    await price.focus();
    await price.blur();
    await initialBalance.setValue('0.5');
    await initialBalance.focus();
    await initialBalance.blur();
    expect(routing.routeNativeElement?.textContent).toContain('Informe um preço de 0');
    expect(routing.routeNativeElement?.textContent).toContain('Informe um inteiro entre 0');
    expect(await submit.isDisabled()).toBe(true);

    await price.setValue('8.75');
    await initialBalance.setValue('0');
    await submit.click();
    const duplicate = http.expectOne({ method: 'POST', url: productsPath });
    const initialKey = duplicate.request.headers.get(IDEMPOTENCY_KEY);
    expect(initialKey).toMatch(/^[\da-f-]{36}$/);
    expect(duplicate.request.body).toEqual({
      code: 'BRO-ACO-8',
      description: 'Broca',
      unitPrice: 8.75,
      initialBalance: 0,
    });
    duplicate.flush(
      { type: PRODUCT_CODE_ALREADY_EXISTS, detail: 'O código já foi cadastrado.' },
      { status: 409, statusText: 'Conflict', headers: problemHeaders },
    );
    await routing.fixture.whenStable();
    expect(routing.routeNativeElement?.querySelector('mat-error')?.textContent).toContain(
      'O código já foi cadastrado.',
    );
    expect(await submit.isDisabled()).toBe(true);

    await code.setValue('bro-nova');
    expect(await submit.isDisabled()).toBe(false);
    await submit.click();
    const corrected = http.expectOne({ method: 'POST', url: productsPath });
    expect(corrected.request.body.code).toBe('BRO-NOVA');
    expect(corrected.request.headers.get(IDEMPOTENCY_KEY)).not.toBe(initialKey);
    corrected.flush({ ...product, code: 'BRO-NOVA' }, { status: 201, statusText: 'Created' });
    await finishNavigation();
  });

  it('keeps the code disabled in editing and sends only editable fields and version', async () => {
    const loader = await open(`/products/${product.id}/edit`);
    expect(routing.routeNativeElement?.textContent).toContain('Carregando produto');
    http.expectOne({ url: productPath, method: 'GET' }).flush(product);
    const code = await loader.getHarness(byField('code'));
    expect(await code.isDisabled()).toBe(true);
    expect(await code.getValue()).toBe(product.code);
    expect(await loader.hasHarness(byField('initialBalance'))).toBe(false);

    const description = await loader.getHarness(byField('description'));
    const submit = await loader.getHarness(MatButtonHarness.with({ text: 'Salvar produto' }));
    await description.setValue('Descrição atualizada');
    await submit.click();
    http
      .expectOne({ method: 'PUT', url: productPath })
      .flush(
        { errors: [{ field: 'description', message: 'Revise a descrição.' }] },
        { status: 400, statusText: 'Invalid', headers: problemHeaders },
      );
    await routing.fixture.whenStable();
    expect(routing.routeNativeElement?.querySelector('mat-error')?.textContent).toContain(
      'Revise a descrição.',
    );

    await description.setValue('Descrição revisada');
    await submit.click();
    const update = http.expectOne({ method: 'PUT', url: productPath });
    expect(update.request.body).toEqual({
      description: 'Descrição revisada',
      unitPrice: 8.75,
      version: 7,
    });
    update.flush({ ...product, description: 'Descrição revisada', version: 8 });
    await finishNavigation();
  });

  it.each(['/products/new', `/products/${product.id}/edit`])(
    'preserves dirty fields if leaving %s is cancelled and discards them after confirmation',
    async (route) => {
      const loader = await open(route);
      if (route.endsWith('/edit')) {
        http.expectOne(productPath).flush(product);
      }
      const description = await loader.getHarness(byField('description'));
      await description.setValue('Rascunho');

      const leave = router.navigateByUrl('/');
      await clickExitDialog('Cancelar');
      expect(await leave).toBe(false);
      expect(router.url).toBe(route);
      expect(await description.getValue()).toBe('Rascunho');

      const discard = router.navigateByUrl('/');
      await clickExitDialog('Descartar alterações');
      expect(await discard).toBe(true);
      expect(router.url).toBe('/');
    },
  );

  async function clickExitDialog(text: string) {
    const button = await vi.waitFor(() => {
      // A pending navigation keeps Angular unstable until the guard's dialog closes.
      const dialog = document.querySelector('[role="dialog"]');
      const button = Array.from(dialog?.querySelectorAll<HTMLButtonElement>('button') ?? []).find(
        (button) =>
          button.textContent?.trim() === text &&
          button.closest('mat-dialog-container')?.classList.contains('mdc-dialog--open'),
      );
      expect(button).toBeDefined();
      return button;
    });
    button?.click();
  }
});
