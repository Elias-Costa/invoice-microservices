import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { provideHttpClient, withInterceptors, HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { AppComponent } from './app.component';
import { routes } from './app.routes';
import { ApiActivity } from './core/interceptors/api-activity';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { loadingInterceptor } from './core/interceptors/loading.interceptor';

describe('AppComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter(routes, withComponentInputBinding()),
        provideHttpClient(withInterceptors([errorInterceptor, loadingInterceptor])),
        provideHttpClientTesting(),
      ],
    });
  });

  it('renders the workspace and marks the home link as current', async () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.autoDetectChanges();
    await TestBed.inject(Router).navigateByUrl('/');
    await fixture.whenStable();

    const shell: HTMLElement = fixture.nativeElement;
    expect(shell.querySelector('mat-toolbar')?.textContent).toContain('Notas Fiscais');
    expect(shell.querySelector('h1')?.textContent).toBe('Visão geral');
    expect(shell.querySelector('main, [role="main"]')?.textContent).toContain('Painel sem dados');
    const navigation = shell.querySelector('[role="navigation"]');
    expect(navigation?.querySelector('a[aria-current="page"]')?.textContent).toContain(
      'Visão geral',
    );
    expect(navigation?.querySelectorAll('[aria-disabled="true"]')).toHaveLength(4);
  });

  it('opens the forbidden page and returns home through its link', async () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.autoDetectChanges();
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/forbidden');
    await fixture.whenStable();

    const shell: HTMLElement = fixture.nativeElement;
    expect(shell.querySelector('h1')?.textContent).toBe('Acesso negado');
    const homeLink = shell.querySelector<HTMLAnchorElement>('[role="main"] a');
    expect(homeLink?.textContent).toBe('Voltar ao início');

    homeLink?.click();
    await fixture.whenStable();
    expect(router.url).toBe('/');
    expect(shell.querySelector('h1')?.textContent).toBe('Visão geral');
  });

  it.each([
    ['/products', 'Produtos'],
    ['/customers', 'Clientes'],
    ['/invoices', 'Notas fiscais'],
  ])('opens the provisional page for %s by URL', async (url, title) => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.autoDetectChanges();
    await TestBed.inject(Router).navigateByUrl(url);
    await fixture.whenStable();

    const shell: HTMLElement = fixture.nativeElement;
    expect(shell.querySelector('h1')?.textContent).toBe(title);
    expect(shell.querySelector('[role="main"]')?.textContent).toContain(
      'Esta seção ainda está indisponível.',
    );
    expect(shell.querySelectorAll('[aria-disabled="true"]')).toHaveLength(4);
  });

  it('announces a network failure and hides it after the API responds', async () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.autoDetectChanges();
    const http = TestBed.inject(HttpClient);
    const requests = TestBed.inject(HttpTestingController);
    const shell: HTMLElement = fixture.nativeElement;

    http.get('/api/stock/products').subscribe({ error: () => undefined });
    await fixture.whenStable();
    expect(shell.querySelector('[role="progressbar"]')?.getAttribute('aria-label')).toBe(
      'Carregando dados',
    );
    requests.expectOne('/api/stock/products').error(new ProgressEvent('error'));
    await fixture.whenStable();
    expect(shell.querySelector('.connection-status')?.textContent?.trim()).toBe(
      'Sem conexão com o servidor',
    );
    expect(shell.querySelector('mat-progress-bar')).toBeNull();

    http.get('/api/stock/products').subscribe();
    requests.expectOne('/api/stock/products').flush([]);
    await fixture.whenStable();
    expect(shell.querySelector('.connection-status')?.textContent?.trim()).toBe('');
    requests.verify();
  });

  it('retries a read when the user clicks the Material snackbar action', async () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.autoDetectChanges();
    const loader = TestbedHarnessEnvironment.documentRootLoader(fixture);
    const http = TestBed.inject(HttpClient);
    const requests = TestBed.inject(HttpTestingController);
    const onSuccess = vi.fn();
    const url = '/api/stock/products';

    http.get(url).subscribe(onSuccess);
    requests.expectOne(url).flush(null, { status: 503, statusText: 'Unavailable' });
    requests.expectNone(url);
    const retry = await loader.getHarness(MatButtonHarness.with({ text: 'Tentar novamente' }));
    await retry.click();
    const activity = TestBed.inject(ApiActivity);
    await vi.waitFor(() => expect(activity.loading()).toBe(true));
    requests.expectOne(url).flush([{ id: 'product' }]);
    expect(onSuccess).toHaveBeenCalledWith([{ id: 'product' }]);
    requests.verify();
  });
});
