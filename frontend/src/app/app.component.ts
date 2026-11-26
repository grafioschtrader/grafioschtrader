import { Component, OnDestroy, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MessageToastComponent } from './lib/message/message.toast.component';
import { TranslateService } from '@ngx-translate/core';
import { LoginService } from './lib/login/service/log-in.service';
import { Optimus } from '@openng/optimus-ui/config';
import { AppSettings } from './shared/app.settings';
import { Subscription } from 'rxjs';
import { NavigationStart, Router } from '@angular/router';
import { definePreset } from '@openng/optimus-ui-themes';
import { Security } from './entities/security';
import { AuditHelper } from './lib/helper/audit.helper';
import { DynamicFieldHelper } from './lib/helper/dynamic.field.helper';
import { validISIN } from './shared/validator/gt.validator';
import { RuleEvent } from './lib/dynamic-form/error/error.message.rules';
import { BaseSettings } from './lib/base.settings';
import { GlobalSessionNames } from './lib/global.session.names';
import { UDFSpecialTypeRegistry } from './lib/udfmeta/model/udf.special.type.registry';
import { UDFSpecialType } from './udfmetasecurity/model/udf.metadata.security';
import { ThemeService } from './lib/theme/theme.service';
import { CompactPreset } from './lib/theme/compact.preset';

/**
 * Grafioschtrader's preset: the shared compact density with blue as primary color.
 */
const MyPreset = definePreset(CompactPreset, {
  semantic: {
    primary: {
      50: '{blue.50}',
      100: '{blue.100}',
      200: '{blue.200}',
      300: '{blue.300}',
      400: '{blue.400}',
      500: '{blue.500}',
      600: '{blue.600}',
      700: '{blue.700}',
      800: '{blue.800}',
      900: '{blue.900}',
      950: '{blue.950}'
    }
  }
});
/**
 * The main component of Grafioschtrader
 */
@Component({
  selector: 'app-root',
  template: `
    <div>
      <toast-message />
      <router-outlet />
    </div>
  `,
  imports: [RouterOutlet, MessageToastComponent]
})
export class AppComponent implements OnDestroy {
  private router = inject(Router);
  // Injected for its constructor, which applies the stored color scheme to the document at bootstrap.
  private readonly themeService = inject(ThemeService);

  private readonly subscription: Subscription;

  constructor() {
    const translateService = inject(TranslateService);
    const optimusConfig = inject(Optimus);
    const router = this.router;

    translateService.addLangs(['en', 'de']);
    // this language will be used as a fallback when a translation isn't found in the current language
    translateService.setDefaultLang(AppSettings.DEFAULT_LANGUAGE);
    // TODO Move it to the session storage.
    try {
      localStorage.setItem(GlobalSessionNames.EXTERNAL_HELP_URL, AppSettings.HELP_URL_BASE);
    } catch {
      // A disabled or full storage must not abort the bootstrap.
    }
    LoginService.setGlobalLang(translateService, optimusConfig);
    this.initializeOptimusStyles(optimusConfig);
    AuditHelper.setCustomOwnershipCheck((entity: any, userId: number) => {
      return entity instanceof Security && entity.idTenantPrivate != null && entity.idTenantPrivate === userId;
    });

    DynamicFieldHelper.registerCustomValidation({
      key: 'ISIN',
      validatorFn: validISIN,
      errorConfig: {
        name: 'validISIN',
        keyi18n: 'validISIN',
        rules: [RuleEvent.TOUCHED, RuleEvent.DIRTY]
      }
    });

    // Initialize UDF special type registry with application-specific special types
    UDFSpecialTypeRegistry.register(Object.values(UDFSpecialType));

    this.subscription = router.events.subscribe((event) => {
      if (event instanceof NavigationStart) {
        if (!router.navigated) {
          BaseSettings.resetInterFractionLimit(AppSettings, GlobalSessionNames.STANDARD_CURRENCY_PRECISIONS_AND_LIMITS);
        }
      }
    });
  }

  private initializeOptimusStyles(optimusConfig: Optimus): void {
    optimusConfig.theme.set({ preset: MyPreset, options: { darkModeSelector: ThemeService.DARK_MODE_SELECTOR } });
  }

  ngOnDestroy(): void {
    this.subscription && this.subscription.unsubscribe();
  }
}
