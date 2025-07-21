import {Component, InjectionToken, OnDestroy} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {LoginService} from './shared/login/service/log-in.service';
import {PrimeNG} from 'primeng/config';
import {AppSettings} from './shared/app.settings';
import {Subscription} from 'rxjs';
import {NavigationStart, Router} from '@angular/router';
import Aura from '@primeng/themes/aura';
import {definePreset} from '@primeng/themes';
import {ITaskExtendService} from './shared/taskdatamonitor/component/itask.extend.service';
import {Security} from './entities/security';
import {AuditHelper} from './lib/helper/audit.helper';
import {DynamicFieldHelper} from './lib/helper/dynamic.field.helper';
import {validISIN} from './shared/validator/gt.validator';
import {RuleEvent} from './dynamic-form/error/error.message.rules';

export const TASK_EXTENDED_SERVICE = new InjectionToken<ITaskExtendService>('SecurityService');

const MyPreset = definePreset(Aura, {
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
  },
  components: {
    datatable: {
      row: {
        selectedBackground: "{blue.600}",
        selectedColor: "#ffffff",
      }
    },
    menubar: {
      root: {
        padding: "0rem 0rem",
      }
    },
    tabs: {
      tab: {
        borderWidth: "1px 1px 1px 1px"
      }
    },
    tree: {
      root: {
        padding: "0.5rem"
      },
      node: {
        padding: "0rem 0rem",
      },
      nodeToggleButton: {
        size: "0.5rem"
      },
    },
    treetable: {
      headerCell: {
        padding: "0rem 0rem",
      },
      bodyCell: {
        padding: "0rem 0rem",
        gap: "0rem"
       },
      nodeToggleButton: {
        size: "1rem"
      }
    },
    button: {
      root: {
        paddingX: "0.25rem",
        paddingY: "0.25rem",
      }
    },
    inputtext: {
       root: {
         paddingX: "0rem",
         paddingY: "0rem",
       }
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
      <toast-message></toast-message>
      <router-outlet></router-outlet>
    </div>
  `,
    standalone: false
})


export class AppComponent implements OnDestroy {

  private readonly subscription: Subscription;

  constructor(translateService: TranslateService, primeNGConfig: PrimeNG, private router: Router) {
    translateService.addLangs(['en', 'de']);
    // this language will be used as a fallback when a translation isn't found in the current language
    translateService.setDefaultLang(AppSettings.DEFAULT_LANGUAGE);
    LoginService.setGlobalLang(translateService, primeNGConfig);
    this.initializePrimeNGStyles(primeNGConfig);
    AuditHelper.setCustomOwnershipCheck((entity: any, userId: number) => {
      return entity instanceof Security &&
        entity.idTenantPrivate &&
        entity.idTenantPrivate === userId;
    });

    DynamicFieldHelper.registerCustomValidation({
         key: 'ISIN',
         validatorFn: validISIN,
         errorConfig: {name: 'validISIN', keyi18n: 'validISIN', rules: [RuleEvent.TOUCHED, RuleEvent.DIRTY]}
       });

    this.subscription = router.events.subscribe((event) => {
      if (event instanceof NavigationStart) {
        if (!router.navigated) {
          AppSettings.resetInterFractionLimit();
        }
      }
    });
  }

  private initializePrimeNGStyles(primeNGConfig: PrimeNG): void {
    primeNGConfig.theme.set({ preset: MyPreset });
  }

  ngOnDestroy(): void {
    this.subscription && this.subscription.unsubscribe();
  }
}

