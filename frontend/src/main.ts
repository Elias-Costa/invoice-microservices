import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localePt from '@angular/common/locales/pt';
import { LOCALE_ID } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { AppComponent } from './app/app.component';
import { routes } from './app/app.routes';
import { errorInterceptor } from './app/core/interceptors/error.interceptor';
import { idempotencyInterceptor } from './app/core/interceptors/idempotency.interceptor';
import { loadingInterceptor } from './app/core/interceptors/loading.interceptor';

registerLocaleData(localePt, 'pt-BR');

bootstrapApplication(AppComponent, {
  providers: [
    { provide: LOCALE_ID, useValue: 'pt-BR' },
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(
      withInterceptors([idempotencyInterceptor, errorInterceptor, loadingInterceptor]),
    ),
  ],
}).catch((error: unknown) => console.error(error));
