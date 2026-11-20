import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatInputHarness } from '@angular/material/input/testing';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../../app.routes';
import { errorInterceptor } from '../../../core/interceptors/error.interceptor';
import {
  IDEMPOTENCY_KEY,
  idempotencyInterceptor,
} from '../../../core/interceptors/idempotency.interceptor';
import { customer, customerPath, customersPath, problemHeaders } from '../customer-test-data';

describe('CustomerFormComponent', () => {
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes, withComponentInputBinding()),
        provideHttpClient(withInterceptors([idempotencyInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => http.verify());

  async function open(url: string) {
    const routing = await RouterTestingHarness.create(url);
    const loader = TestbedHarnessEnvironment.loader(routing.fixture);
    const overlay = TestbedHarnessEnvironment.documentRootLoader(routing.fixture);
    const field = (name: string) =>
      loader.getHarness(MatInputHarness.with({ selector: `[formControlName="${name}"]` }));
    const submit = () => loader.getHarness(MatButtonHarness.with({ text: 'Salvar cliente' }));
    return { field, overlay, routing, submit };
  }

  async function enter(control: MatInputHarness, value: string): Promise<void> {
    const host = await control.host();
    await host.clear();
    await host.sendKeys(value);
  }

  async function chooseDiscardOption(label: string): Promise<void> {
    const button = await vi.waitFor(() => {
      // The router stays pending until the guard's dialog closes.
      const buttons = document.querySelectorAll<HTMLButtonElement>(
        'mat-dialog-container.mdc-dialog--open button',
      );
      const matching = Array.from(buttons).find((item) => item.textContent?.trim() === label);
      expect(matching).toBeDefined();
      return matching;
    });
    button?.click();
  }

  it('masks and validates CPF and CNPJ, then creates with normalized contact data and an idempotency key', async () => {
    const { field, routing, submit } = await open('/customers/new');
    const name = await field('name');
    const document = await field('document');
    const phone = await field('phone');
    const email = await field('email');
    const save = await submit();

    await name.setValue('  Oficina Horizonte  ');
    await enter(document, '52998224724');
    await document.focus();
    await document.blur();
    expect(await document.getValue()).toBe('529.982.247-24');
    expect(routing.routeNativeElement?.textContent).toContain('Informe um CPF ou CNPJ válido.');
    expect(await save.isDisabled()).toBe(true);

    await enter(document, '52998224725');
    expect(await save.isDisabled()).toBe(false);
    await enter(document, '11222333000181');
    expect(await document.getValue()).toBe('11.222.333/0001-81');
    await enter(phone, '5511987654321');
    expect(await phone.getValue()).toBe('+55 (11) 98765-4321');
    await email.setValue('contato@example.com');

    await save.click();
    const create = http.expectOne({ method: 'POST', url: customersPath });
    expect(create.request.headers.get(IDEMPOTENCY_KEY)).toMatch(/^[\da-f-]{36}$/);
    expect(create.request.body).toEqual({
      name: 'Oficina Horizonte',
      document: '11222333000181',
      email: 'contato@example.com',
      phone: '5511987654321',
    });
    create.flush(customer, { status: 201, statusText: 'Created' });
    await vi.waitFor(() => expect(router.url).toBe(`/customers/${customer.id}`));
    await routing.fixture.whenStable();
    http.expectOne({ method: 'GET', url: customerPath }).flush({ ...customer, recentInvoices: [] });
  });

  it('keeps the draft visible and offers reload when the document is already registered', async () => {
    const { field, overlay, submit } = await open('/customers/new');
    const document = await field('document');
    await (await field('name')).setValue(customer.name);
    await enter(document, customer.document);
    await (await submit()).click();
    http.expectOne({ method: 'POST', url: customersPath }).flush(
      {
        type: 'customer-document-already-exists',
        detail: 'Já existe um cliente com o CPF ou CNPJ informado.',
      },
      { status: 409, statusText: 'Conflict', headers: problemHeaders },
    );
    expect(await overlay.hasHarness(MatButtonHarness.with({ text: 'Recarregar' }))).toBe(true);
    expect(await document.getValue()).toBe('11.222.333/0001-81');
    expect(router.url).toBe('/customers/new');
  });

  it('locks the document during editing, sends version and displays API field errors', async () => {
    const { field, routing, submit } = await open(`/customers/${customer.id}/edit`);
    http.expectOne({ method: 'GET', url: customerPath }).flush(customer);
    const document = await field('document');
    const phone = await field('phone');
    expect(await document.isDisabled()).toBe(true);
    expect(await document.getValue()).toBe('11.222.333/0001-81');
    await enter(phone, '14155550100');
    expect(await phone.getValue()).toBe('+14155550100');
    await (await submit()).click();
    const update = http.expectOne({ method: 'PUT', url: customerPath });
    expect(update.request.body).toEqual({
      name: customer.name,
      email: customer.email,
      phone: '14155550100',
      version: 4,
    });
    update.flush(
      { detail: 'Dados inválidos.', errors: [{ field: 'phone', message: 'Confira o telefone.' }] },
      { status: 400, statusText: 'Bad Request', headers: problemHeaders },
    );
    await routing.fixture.whenStable();
    expect(routing.routeNativeElement?.querySelector('mat-error')?.textContent).toContain(
      'Confira o telefone.',
    );
  });

  it('keeps an unsaved form open when discarding is canceled and leaves when confirmed', async () => {
    const { field } = await open('/customers/new');
    await (await field('name')).setValue('Cliente ainda não salvo');

    const leave = router.navigateByUrl('/');
    await chooseDiscardOption('Cancelar');
    expect(await leave).toBe(false);
    expect(router.url).toBe('/customers/new');
    expect(await (await field('name')).getValue()).toBe('Cliente ainda não salvo');

    const discard = router.navigateByUrl('/');
    await chooseDiscardOption('Descartar alterações');
    expect(await discard).toBe(true);
    expect(router.url).toBe('/');
  });
});
