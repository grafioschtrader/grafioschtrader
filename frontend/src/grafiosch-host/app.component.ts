import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Optimus } from '@openng/optimus-ui/config';

import { GlobalSessionNames } from '../app/lib/global.session.names';
import { LoginService } from '../app/lib/login/service/log-in.service';
import { MessageToastComponent } from '../app/lib/message/message.toast.component';
import { CompactPreset } from '../app/lib/theme/compact.preset';
import { ThemeService } from '../app/lib/theme/theme.service';
import { GrafioschSettings } from './grafiosch.settings';

/**
 * Root of the standalone Grafiosch application. It is the consumer that proves {@code src/app/lib} runs without any
 * Grafioschtrader module: everything below the router outlet comes from the library, this component only provides the
 * toast outlet and the global configuration a host has to decide on.
 */
@Component({
  selector: 'grafiosch-root',
  template: `
    <div>
      <toast-message />
      <router-outlet />
    </div>
  `,
  imports: [RouterOutlet, MessageToastComponent]
})
export class GrafioschAppComponent {
  // Injected for its constructor, which applies the stored color scheme to the document at bootstrap.
  private readonly themeService = inject(ThemeService);

  constructor() {
    const translateService = inject(TranslateService);
    const optimusConfig = inject(Optimus);

    translateService.addLangs(['en', 'de']);
    translateService.setDefaultLang(GrafioschSettings.DEFAULT_LANGUAGE);
    try {
      localStorage.setItem(GlobalSessionNames.EXTERNAL_HELP_URL, GrafioschSettings.HELP_URL_BASE);
    } catch {
      // A disabled or full storage must not abort the bootstrap.
    }
    LoginService.setGlobalLang(translateService, optimusConfig);
    optimusConfig.theme.set({
      // The library host has no deltas of its own: it shows the shared compact density with Aura's colors.
      preset: CompactPreset,
      options: { darkModeSelector: ThemeService.DARK_MODE_SELECTOR }
    });
  }
}
