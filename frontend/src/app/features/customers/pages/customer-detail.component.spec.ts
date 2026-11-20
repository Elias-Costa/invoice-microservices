import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { registerLocaleData } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import localePt from '@angular/common/locales/pt';
import { LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatDialogHarness } from '@angular/material/dialog/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../../app.routes';
import { customer, customerPath, problemHeaders } from '../customer-test-data';

describe('CustomerDetailComponent', () => {
  let http: HttpTestingController;
  let routing: RouterTestingHarness;

  beforeEach(async () => {
    registerLocaleData(localePt, 'pt-BR');
    TestBed.configureTestingModule({
      providers: [
        { provide: LOCALE_ID, useValue: 'pt-BR' },
        provideRouter(routes, withComponentInputBinding()),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    routing = await RouterTestingHarness.create(`/customers/${customer.id}`);
  });

  afterEach(() => http.verify());

  function getDetail() {
    return http.expectOne({ method: 'GET', url: customerPath });
  }

  it('renders customer data and the latest invoices in the order supplied by billing', async () => {
    expect(routing.routeNativeElement?.querySelector('[role="status"]')?.textContent).toContain(
      'Carregando cliente',
    );
    getDetail().flush(customer);
    await routing.fixture.whenStable();
    const view = routing.routeNativeElement;
    expect(view?.querySelector('h1')?.textContent).toBe(customer.name);
    expect(view?.textContent).toContain('+55 (11) 98765-4321');
    expect(view?.textContent).toContain('11.222.333/0001-81');
    const notes = Array.from(
      view?.querySelectorAll('mat-list-item') ?? [],
      (row) => row.textContent,
    );
    expect(notes).toHaveLength(2);
    expect(notes[0]).toMatch(/Nota nº 2.*Fechada/);
    expect(notes[0]).toContain('23,50');
    expect(notes[1]).toMatch(/Nota nº 1.*Cancelada/);
  });

  it('can retry a failed detail and displays a customer without invoices or contact data', async () => {
    getDetail().flush(
      { detail: 'Cliente não encontrado.' },
      { status: 404, statusText: 'Not Found', headers: problemHeaders },
    );
    await routing.fixture.whenStable();
    expect(routing.routeNativeElement?.querySelector('[role="alert"]')?.textContent).toContain(
      'Cliente não encontrado.',
    );
    const loader = TestbedHarnessEnvironment.loader(routing.fixture);
    await (await loader.getHarness(MatButtonHarness.with({ text: 'Tentar novamente' }))).click();
    getDetail().flush({ ...customer, email: null, phone: null, recentInvoices: [] });
    await routing.fixture.whenStable();
    expect(routing.routeNativeElement?.textContent).toContain(
      'Nenhuma nota fiscal para este cliente.',
    );
    expect(routing.routeNativeElement?.textContent).toContain('Não informado');
  });

  it('requires confirmation and refreshes after inactivating and reactivating', async () => {
    getDetail().flush(customer);
    const loader = TestbedHarnessEnvironment.loader(routing.fixture);
    const overlay = TestbedHarnessEnvironment.documentRootLoader(routing.fixture);
    const deactivate = await loader.getHarness(MatButtonHarness.with({ text: 'Inativar' }));
    await deactivate.click();
    let dialog = await overlay.getHarness(MatDialogHarness);
    await (await dialog.getHarness(MatButtonHarness.with({ text: 'Cancelar' }))).click();
    await vi.waitFor(async () => expect(await deactivate.isDisabled()).toBe(false));
    http.expectNone({ method: 'DELETE', url: customerPath });

    await deactivate.click();
    dialog = await overlay.getHarness(MatDialogHarness);
    await (await dialog.getHarness(MatButtonHarness.with({ text: 'Inativar' }))).click();
    const deactivation = await vi.waitFor(() =>
      http.expectOne({ method: 'DELETE', url: customerPath }),
    );
    deactivation.flush(null, { status: 204, statusText: 'No Content' });
    (await vi.waitFor(getDetail)).flush({ ...customer, active: false });
    const reactivate = await loader.getHarness(MatButtonHarness.with({ text: 'Reativar' }));
    await reactivate.click();
    dialog = await overlay.getHarness(MatDialogHarness);
    await (await dialog.getHarness(MatButtonHarness.with({ text: 'Reativar' }))).click();
    const reactivation = await vi.waitFor(() =>
      http.expectOne({ method: 'POST', url: `${customerPath}/reactivate` }),
    );
    reactivation.flush(customer);
    (await vi.waitFor(getDetail)).flush(customer);
    expect(await loader.hasHarness(MatButtonHarness.with({ text: 'Inativar' }))).toBe(true);
  });
});
