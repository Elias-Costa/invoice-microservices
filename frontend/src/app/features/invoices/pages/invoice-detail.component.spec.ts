import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatInputHarness } from '@angular/material/input/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../../app.routes';
import { invoice, invoicePath, problemHeaders } from '../invoice-test-data';

describe('InvoiceDetailComponent and cancellation', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes, withComponentInputBinding()),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('shows stored snapshots and requires a reason before cancellation', async () => {
    const routing = await RouterTestingHarness.create(`/invoices/${invoice.id}`);
    expect(routing.routeNativeElement?.textContent).toContain('Carregando nota fiscal');
    http.expectOne(invoicePath).flush(invoice);
    const loader = TestbedHarnessEnvironment.loader(routing.fixture);
    const cancel = await loader.getHarness(MatButtonHarness.with({ text: 'Cancelar nota' }));
    expect(routing.routeNativeElement?.textContent).toContain('Nota fiscal 000012');
    expect(routing.routeNativeElement?.textContent).toContain('529.982.247-25');
    expect(routing.routeNativeElement?.textContent).toMatch(/0[.,]60/);
    expect(routing.routeNativeElement?.textContent).toContain('Editar itens');
    expect(routing.routeNativeElement?.textContent).not.toContain('Imprimir');

    await cancel.click();
    const overlay = TestbedHarnessEnvironment.documentRootLoader(routing.fixture);
    const reason = await overlay.getHarness(
      MatInputHarness.with({ selector: '[formControlName="reason"]' }),
    );
    const confirm = await overlay.getHarness(
      MatButtonHarness.with({ text: 'Confirmar cancelamento' }),
    );
    expect(await confirm.isDisabled()).toBe(true);
    await reason.setValue('  ');
    expect(await confirm.isDisabled()).toBe(true);
    await reason.setValue('a'.repeat(501));
    expect(await confirm.isDisabled()).toBe(true);
    await reason.setValue('  Pedido cancelado  ');
    await confirm.click();
    const write = http.expectOne({ url: `${invoicePath}/cancel`, method: 'POST' });
    expect(write.request.body).toEqual({ reason: 'Pedido cancelado' });
    write.flush({
      ...invoice,
      status: 'CANCELED',
      cancelReason: 'Pedido cancelado',
      canceledAt: '2026-11-20T10:00:00Z',
      allowedActions: [],
    });
    await vi.waitFor(() =>
      expect(routing.routeNativeElement?.textContent).toContain('Pedido cancelado'),
    );
    expect(routing.routeNativeElement?.textContent).toContain('Cancelamento');
    expect(routing.routeNativeElement?.textContent).toContain('Cancelada');
    expect(routing.routeNativeElement?.textContent).not.toContain('Editar itens');
    expect(await loader.hasHarness(MatButtonHarness.with({ text: 'Cancelar nota' }))).toBe(false);
  });

  it('shows the 404 returned by the detail endpoint', async () => {
    const routing = await RouterTestingHarness.create(`/invoices/${invoice.id}`);
    http
      .expectOne(invoicePath)
      .flush(
        { detail: 'Nota fiscal não encontrada.' },
        { status: 404, statusText: 'Not Found', headers: problemHeaders },
      );
    await routing.fixture.whenStable();
    expect(routing.routeNativeElement?.querySelector('[role="alert"]')?.textContent).toContain(
      'Nota fiscal não encontrada.',
    );
  });
});
