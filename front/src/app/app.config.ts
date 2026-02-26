import { ApplicationConfig, LOCALE_ID, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import * as echarts from 'echarts';
import { provideEchartsCore } from 'ngx-echarts';
import { API_BASE_URL } from './core/api.tokens';
import { environment } from '../environments/environment';
import { apiErrorInterceptor } from './interceptors/api-error.interceptor';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideHttpClient(withInterceptors([apiErrorInterceptor])),
    provideRouter(routes),
    provideEchartsCore({ echarts }),
    { provide: API_BASE_URL, useValue: environment.apiBaseUrl },
    { provide: LOCALE_ID, useValue: 'fr' }
  ]
};
