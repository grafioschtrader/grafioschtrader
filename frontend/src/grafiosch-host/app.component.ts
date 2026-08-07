import {Component} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {PrimeNG} from 'primeng/config';
import Aura from '@primeuix/themes/aura';
import {definePreset} from '@primeuix/themes';

import {GlobalSessionNames} from '../app/lib/global.session.names';
import {LoginService} from '../app/lib/login/service/log-in.service';
import {MessageToastComponent} from '../app/lib/message/message.toast.component';
import {GrafioschSettings} from './grafiosch.settings';

/**
 * Compact PrimeNG preset. It only narrows the default paddings so tables and menus have the same density as in
 * Grafioschtrader; the component tokens are the ones that actually decide the spacing, a component level CSS rule
 * would lose the cascade.
 */
const GrafioschPreset = definePreset(Aura, {
  components: {
    button: {root: {paddingX: '0.25rem', paddingY: '0.25rem'}},
    inputtext: {root: {paddingX: '0rem', paddingY: '0rem'}},
    menubar: {root: {padding: '0rem 0rem'}},
    select: {root: {paddingX: '0rem', paddingY: '0rem'}},
    tree: {root: {padding: '0.5rem'}, node: {padding: '0rem 0rem'}},
    treetable: {headerCell: {padding: '0rem 0rem'}, bodyCell: {padding: '0rem 0rem', gap: '0rem'}}
  }
});

/**
 * Root of the standalone Grafiosch application. It is the consumer that proves {@code src/app/lib} runs without any
 * Grafioschtrader module: everything below the router outlet comes from the library, this component only provides the
 * toast outlet and the global configuration a host has to decide on.
 */
@Component({
  selector: 'grafiosch-root',
  template: `
    <div>
      <toast-message></toast-message>
      <router-outlet></router-outlet>
    </div>
  `,
  standalone: true,
  imports: [RouterOutlet, MessageToastComponent]
})
export class GrafioschAppComponent {

  constructor(translateService: TranslateService, primeNGConfig: PrimeNG) {
    translateService.addLangs(['en', 'de']);
    translateService.setDefaultLang(GrafioschSettings.DEFAULT_LANGUAGE);
    localStorage.setItem(GlobalSessionNames.EXTERNAL_HELP_URL, GrafioschSettings.HELP_URL_BASE);
    LoginService.setGlobalLang(translateService, primeNGConfig);
    primeNGConfig.theme.set({preset: GrafioschPreset});
  }
}
