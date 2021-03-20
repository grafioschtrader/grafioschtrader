import {Component} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {LoginService} from './shared/login/service/log-in.service';
import {PrimeNGConfig} from 'primeng/api';
import {AppSettings} from './shared/app.settings';

/**
 * The main component of Grafioschtrader
 */
@Component({
  selector: 'app-root',
  template: `
    <div>
      <toast-message></toast-message>
      <router-outlet></router-outlet>
    </div>
  `
})
export class AppComponent {

  constructor(translateService: TranslateService,  primeNGConfig: PrimeNGConfig) {
    translateService.addLangs(['en', 'de']);
    // this language will be used as a fallback when a translation isn't found in the current language
    translateService.setDefaultLang(AppSettings.DEFAULT_LANGUAGE);
    LoginService.setGlobalLang(translateService, primeNGConfig);
  }

}

