import { bootstrapApplication } from '@angular/platform-browser';
import { MobileAppComponent } from './app/mobile.app.component';
import { mobileAppConfig } from './app/mobile.app.config';

bootstrapApplication(MobileAppComponent, mobileAppConfig).catch((err) => console.error(err));
