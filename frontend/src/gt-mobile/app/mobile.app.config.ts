import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  isDevMode,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners
} from '@angular/core';
import { provideRouter, withComponentInputBinding, withHashLocation } from '@angular/router';
import { provideServiceWorker } from '@angular/service-worker';
import { provideIonicAngular } from '@ionic/angular';
import { provideTranslateService, TranslateLoader, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { authInterceptor } from '../../app/lib/login/service/auth.http.interceptor';
import { MultiTranslateHttpLoader } from '../../app/lib/translator/multi.translate.http.loader';
import { showNlsBootstrapFailure } from '../../app/lib/translator/nls.bootstrap.failure';
import { MOBILE_ROUTES } from './mobile.routes';
import { mobileBootstrapLanguage } from './mobile.language';

// The backend is the only source of texts, as for the desktop application.
const createTranslateLoader = (http: HttpClient) =>
  new MultiTranslateHttpLoader(http, [{ prefix: '/api/globalparameters/properties/', suffix: '', mandatory: true }]);

export const mobileAppConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(MOBILE_ROUTES, withHashLocation(), withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideIonicAngular({ mode: 'md' }),
    provideTranslateService({
      loader: { provide: TranslateLoader, useFactory: createTranslateLoader, deps: [HttpClient] }
    }),
    // Without texts there is nothing to render, so a failed load stops the bootstrap behind the shared overlay.
    provideAppInitializer(() => {
      const translateService = inject(TranslateService);
      return firstValueFrom(translateService.use(mobileBootstrapLanguage(translateService.getBrowserLang()))).catch(
        () => {
          showNlsBootstrapFailure();
          return new Promise<never>(() => {});
        }
      );
    }),
    // Caches the application shell only; financial data is always read from the server.
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000'
    })
  ]
};
