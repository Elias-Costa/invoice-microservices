import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatAutocompleteHarness } from '@angular/material/autocomplete/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatDatepickerToggleHarness } from '@angular/material/datepicker/testing';
import { MatPaginatorHarness } from '@angular/material/paginator/testing';
import { MatSelectHarness } from '@angular/material/select/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../../app.routes';
import { customer, customersPath, invoicesPath, page, summary } from '../invoice-test-data';

describe('InvoiceListComponent and InvoiceStore', () => {
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

  function listRequest() {
    return http.expectOne((request) => request.url === invoicesPath);
  }

  it('shows each status, keeps the number sort across pages, and recovers from an error', async () => {
    const routing = await RouterTestingHarness.create('/invoices');
    const loader = TestbedHarnessEnvironment.loader(routing.fixture);
    expect(routing.routeNativeElement?.textContent).toContain('Carregando notas fiscais');
    listRequest().flush(null, { status: 500, statusText: 'Server error' });
    const retry = await loader.getHarness(MatButtonHarness.with({ text: 'Tentar novamente' }));
    await retry.click();
    const rows = [
      summary,
      { ...summary, id: '2', status: 'PROCESSING' },
      { ...summary, id: '3', status: 'CLOSED' },
      { ...summary, id: '4', status: 'CANCELED' },
    ];
    listRequest().flush(page(rows, 40));
    await routing.fixture.whenStable();
    expect(
      routing.routeNativeElement
        ?.querySelector('a[href="/invoices/invoice-1"]')
        ?.textContent?.trim(),
    ).toBe('000012');
    expect(
      Array.from(routing.routeNativeElement?.querySelectorAll('mat-chip') ?? [], (chip) =>
        chip.textContent?.trim(),
      ),
    ).toEqual(['Aberta', 'Em processamento', 'Fechada', 'Cancelada']);
    expect(
      routing.routeNativeElement?.querySelector('mat-chip[data-status="PROCESSING"] mat-spinner'),
    ).not.toBeNull();

    const paginator = await loader.getHarness(MatPaginatorHarness);
    await paginator.goToNextPage();
    const next = listRequest();
    expect(next.request.params.get('page')).toBe('1');
    expect(next.request.params.get('sort')).toBe('number,desc');
    next.flush(page(rows, 40));
    const status = await loader.getHarness(MatSelectHarness);
    await status.clickOptions({ text: 'Fechada' });
    const closed = listRequest();
    expect(closed.request.params.get('page')).toBe('0');
    expect(closed.request.params.get('status')).toBe('CLOSED');
    closed.flush(page([]));
    await routing.fixture.whenStable();
    expect(routing.routeNativeElement?.textContent).toContain('Nenhuma nota fiscal encontrada.');
  });

  it('filters historical notes by inactive customers and applies the independent date boundary', async () => {
    const routing = await RouterTestingHarness.create('/invoices');
    const loader = TestbedHarnessEnvironment.loader(routing.fixture);
    listRequest().flush(page([]));
    const autocomplete = await loader.getHarness(MatAutocompleteHarness);
    await autocomplete.enterText('Cliente');
    const lookups = await vi.waitFor(() => {
      const requests = http.match((request) => request.url === customersPath);
      expect(requests).toHaveLength(2);
      return requests;
    });
    for (const lookup of lookups) {
      lookup.flush(
        page(
          lookup.request.params.get('active') === 'false' ? [{ ...customer, active: false }] : [],
        ),
      );
    }
    await autocomplete.selectOption({ text: /Inativo/ });
    const byCustomer = listRequest();
    expect(byCustomer.request.params.get('customerId')).toBe(customer.id);
    byCustomer.flush(page([]));

    const [, before] = await loader.getAllHarnesses(MatDatepickerToggleHarness);
    await before.openCalendar();
    const calendar = await before.getCalendar();
    await calendar.selectCell({ text: '12' });
    const beforeDate = listRequest();
    const today = new Date();
    expect(beforeDate.request.params.get('to')).toBe(
      new Date(today.getFullYear(), today.getMonth(), 12).toISOString(),
    );
    expect(beforeDate.request.params.has('from')).toBe(false);
    expect(beforeDate.request.params.get('customerId')).toBe(customer.id);
    beforeDate.flush(page([]));

    const clear = await loader.getHarness(
      MatButtonHarness.with({ selector: '[aria-label="Limpar data final"]' }),
    );
    await clear.click();
    const cleared = listRequest();
    expect(cleared.request.params.has('to')).toBe(false);
    cleared.flush(page([]));
  });
});
