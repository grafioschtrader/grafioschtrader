import {Component} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {LoginService} from './shared/login/service/log-in.service';


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

  constructor(translateService: TranslateService) {
    translateService.addLangs(['en', 'de']);
    translateService.setDefaultLang('de');

    const browserLang: string = translateService.getBrowserLang();
    // translateService.use(browserLang.match(/en|de/) ? browserLang : 'en');
    LoginService.setGlobalLang(translateService);
  }

}

