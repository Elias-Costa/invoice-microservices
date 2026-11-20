import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatInputHarness } from '@angular/material/input/testing';
import { MatPaginatorHarness } from '@angular/material/paginator/testing';
import { MatSlideToggleHarness } from '@angular/material/slide-toggle/testing';
import { MatSortHarness } from '@angular/material/sort/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../../app.routes';
import { customer, customerPage, customersPath, problemHeaders } from '../customer-test-data';

describe('CustomerListComponent', () => {
  let http: HttpTestingController;
  let routing: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes, withComponentInputBinding()),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    routing = await RouterTestingHarness.create('/customers');
  });

  afterEach(() => http.verify());

  function query() {
    return http.expectOne((request) => request.method === 'GET' && request.url === customersPath);
  }

  it('shows loading, an API error and the empty state after retry', async () => {
    expect(routing.routeNativeElement?.querySelector('[role="status"]')?.textContent).toContain(
      'Carregando clientes',
    );
    query().flush(
      { detail: 'Erro ao consultar clientes.' },
      { status: 500, statusText: 'Error', headers: problemHeaders },
    );
    await routing.fixture.whenStable();
    expect(routing.routeNativeElement?.querySelector('[role="alert"]')?.textContent).toContain(
      'Erro ao consultar clientes.',
    );

    const loader = TestbedHarnessEnvironment.loader(routing.fixture);
    const retry = await loader.getHarness(MatButtonHarness.with({ text: 'Tentar novamente' }));
    await retry.click();
    query().flush(customerPage([]));
    await routing.fixture.whenStable();
    expect(routing.routeNativeElement?.querySelector('[role="status"]')?.textContent).toContain(
      'Nenhum cliente ativo encontrado.',
    );
  });

  it('formats documents and forwards pagination, sort, search and activity to the API', async () => {
    const initial = query();
    expect(initial.request.params.get('sort')).toBe('name,asc');
    expect(initial.request.params.get('active')).toBe('true');
    initial.flush(customerPage([customer], 21));
    await routing.fixture.whenStable();
    const loader = TestbedHarnessEnvironment.loader(routing.fixture);
    expect(routing.routeNativeElement?.textContent).toContain('11.222.333/0001-81');
    expect(routing.routeNativeElement?.querySelector('td a')?.getAttribute('href')).toBe(
      `/customers/${customer.id}`,
    );

    await (await loader.getHarness(MatPaginatorHarness)).goToNextPage();
    const page = query();
    expect(page.request.params.get('page')).toBe('1');
    page.flush(customerPage([customer], 21));

    const sort = await loader.getHarness(MatSortHarness);
    await (await sort.getSortHeaders({ label: 'CPF ou CNPJ' }))[0].click();
    const sorted = query();
    expect(sorted.request.params.get('page')).toBe('0');
    expect(sorted.request.params.get('sort')).toBe('document,asc');
    sorted.flush(customerPage([customer]));

    const search = await loader.getHarness(MatInputHarness);
    await search.setValue('11.222.333/0001-81');
    const matchDocument = (request: { url: string }) => request.url === customersPath;
    http.expectNone(matchDocument);
    await vi.waitFor(() =>
      expect(
        http.match(matchDocument).map((item) => {
          item.flush(customerPage([customer]));
          return item.request.params.get('search');
        }),
      ).toEqual(['11222333000181']),
    );

    await search.setValue('   Oficina  ');
    await vi.waitFor(() =>
      expect(
        http.match(matchDocument).map((item) => {
          item.flush(customerPage([customer]));
          return item.request.params.get('search');
        }),
      ).toEqual(['Oficina']),
    );

    await (await loader.getHarness(MatSlideToggleHarness)).check();
    const inactive = query();
    expect(inactive.request.params.get('active')).toBe('false');
    inactive.flush(customerPage([]));
  });
});
