import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AppComponent } from './app.component';
import { routes } from './app.routes';

describe('AppComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter(routes)],
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
});
