import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatInputHarness } from '@angular/material/input/testing';
import { MatSlideToggleHarness } from '@angular/material/slide-toggle/testing';
import { MatSortHarness } from '@angular/material/sort/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../../app.routes';
import { page, product, productsPath } from '../product-test-data';

describe('ProductListComponent', () => {
  let routing: RouterTestingHarness;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes, withComponentInputBinding()),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    routing = await RouterTestingHarness.create('/products');
    await routing.fixture.whenStable();
  });

  afterEach(() => http.verify());

  function getList() {
    return http.expectOne((request) => request.url === productsPath);
  }

  it('distinguishes loading, error and empty states and can retry', async () => {
    expect(routing.routeNativeElement?.textContent).toContain('Carregando produtos');
    getList().flush(null, { status: 500, statusText: 'Error' });
    const loader = TestbedHarnessEnvironment.loader(routing.fixture);
    const retry = await loader.getHarness(MatButtonHarness.with({ text: 'Tentar novamente' }));
    expect(routing.routeNativeElement?.querySelector('[role="alert"]')?.textContent).toContain(
      'Não foi possível carregar os produtos.',
    );
    await retry.click();
    getList().flush(page([]));
    await routing.fixture.whenStable();
    expect(routing.routeNativeElement?.textContent).toContain('Nenhum produto ativo encontrado.');
  });

  it('marks zero stock, debounces searches, and sends sort and status to the API', async () => {
    getList().flush(page([product]));
    const loader = TestbedHarnessEnvironment.loader(routing.fixture);
    const search = await loader.getHarness(MatInputHarness);
    expect(routing.routeNativeElement?.querySelector('.zero-balance')?.textContent).toContain(
      'Sem estoque',
    );
    await search.setValue('br');
    await search.setValue('  broca  ');
    http.expectNone((request) => request.url === productsPath);
    const searchRequest = await vi.waitFor(getList);
    expect(searchRequest.request.params.get('search')).toBe('broca');
    searchRequest.flush(page([product]));

    const sort = await loader.getHarness(MatSortHarness);
    const [balance] = await sort.getSortHeaders({ label: 'Saldo' });
    await balance.click();
    const sorted = getList();
    expect(sorted.request.params.get('sort')).toBe('balance,asc');
    sorted.flush(page([product]));

    const inactive = await loader.getHarness(MatSlideToggleHarness);
    await inactive.check();
    const filtered = getList();
    expect(filtered.request.params.get('active')).toBe('false');
    expect(filtered.request.params.get('search')).toBe('broca');
    filtered.flush(page([]));
  });
});
