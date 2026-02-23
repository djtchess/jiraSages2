import '@angular/localize/init';
import {importProvidersFrom} from '@angular/core';
import {bootstrapApplication} from '@angular/platform-browser';
import {provideHttpClient} from '@angular/common/http';
import {provideAnimations} from '@angular/platform-browser/animations';
import {VERSION as MAT_VERSION, MatNativeDateModule} from '@angular/material/core';
import { AppComponent } from './app/app.component';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { appConfig } from './app/app.config';


bootstrapApplication(AppComponent, {
  providers: [
    provideAnimations(),
    provideHttpClient(),
    importProvidersFrom(MatNativeDateModule), provideAnimationsAsync(),
    ...appConfig.providers
  ]
}).catch(err => console.error(err));



// import { bootstrapApplication } from '@angular/platform-browser';
// import { AppComponent } from './app/app.component';
// import { appConfig } from './app/app.config';


// bootstrapApplication(AppComponent, appConfig)
//   .catch(err => console.error(err));
