import { provideRouter, withNavigationErrorHandler, withPreloading, withComponentInputBinding } from '@angular/router';
import { APP_ROUTES } from './app.routes';
import { DialogComponentLoader } from './lib/maintree/handler/lazy-dialog';
import { AuthenticatedPreloadingStrategy } from './lib/login/service/authenticated-preloading.strategy';
import { handleLazyLoadNavigationError } from './lib/login/service/lazy-load-failure';
import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { TASK_EXTENDED_SERVICE } from './lib/taskdatamonitor/service/task.extend.service.token';
import { TASK_TYPE_ENUM } from './lib/taskdatamonitor/service/task.type.enum.token';
import { PERSONAL_DATA_ZIP_NAME } from './lib/mainmenubar/service/personal.data.zip.token';
import {
  DASHBOARD_CONFIG_SUMMARIES,
  DASHBOARD_MAXIMIZABLE,
  DASHBOARD_RENDERERS
} from './lib/dashboard/dashboard.tokens';
import { ShowRecordConfigBase } from './lib/datashowbase/show.record.config.base';
import { DataType } from './lib/dynamic-form/models/data.type';
import { TaskType } from './shared/types/task.type';
import { CurrencypairService } from './securitycurrency/service/currencypair.service';
import { provideTranslateService, TranslateLoader, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { StockexchangeService } from './stockexchange/service/stockexchange.service';
import { AssetclassService } from './assetclass/service/assetclass.service';
import { HttpClient, provideHttpClient, withInterceptors, withInterceptorsFromDi, withXhr } from '@angular/common/http';
import { authInterceptor } from './lib/login/service/auth.http.interceptor';
import { provideDialogCloseKeepsFocus } from './lib/edit/dialog.close.focus';
import { ImportTransactionPlatformService } from './imptranstemplate/service/import.transaction.platform.service';
import { setupProposeChangeEntityHandlers } from './shared/changerequest/propose.change.entity.handlers.setup';
import { EntityPrepareRegistry } from './lib/proposechange/service/entity.prepare.registry';
import { MultiTranslateHttpLoader } from './lib/translator/multi.translate.http.loader';
import { showNlsBootstrapFailure } from './lib/translator/nls.bootstrap.failure';
import { AppHelper } from './lib/helper/app.helper';
import { GlobalSessionNames } from './lib/global.session.names';
import { ConfirmationService, MessageService } from '@openng/optimus-ui/api';
import { provideAngularSvgIcon } from 'angular-svg-icon';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { SecurityService } from './securitycurrency/service/security.service';
import { GlobalparameterService } from './lib/services/globalparameter.service';
import { TabMenuService } from './lib/tabmenu/service/tab.menu.service';
import { MAIN_TREE_CONTRIBUTOR } from './lib/maintree/contributor/main-tree-contributor.interface';
import { DashboardMainTreeContributor } from './lib/maintree/contributor/dashboard-main-tree.contributor';
import { PortfolioMainTreeContributor } from './portfolio/contributor/portfolio-main-tree.contributor';
import { WatchlistMainTreeContributor } from './watchlist/contributor/watchlist-main-tree.contributor';
import { AlgoMainTreeContributor } from './algo/contributor/algo-main-tree.contributor';
import { BaseDataMainTreeContributor } from './shared/contributor/basedata-main-tree.contributor';
import { AdminDataMainTreeContributor } from './shared/contributor/admindata-main-tree.contributor';
import { DIALOG_HANDLER } from './lib/maintree/handler/dialog-handler.interface';
import { SETTINGS_MENU_EXTENSIONS } from './lib/mainmenubar/service/settings-menu-extensions';
import { aiSettingsMenu } from './tenantaiprovider/ai-settings-menu';
import { mcpSettingsMenu } from './mcp/mcp-settings-menu';
import { AppDialogHandler } from './shared/maintree/handler/app-dialog.handler';
import { AfterLoginHandler } from './lib/login/service/after-login.handler';
import { GtAfterLoginHandler } from './shared/login/gt-after-login.handler';
import { registerHelpIds } from './lib/help/help.ids';
import { AppHelpIds, AppTaskTypeHelpIds } from './shared/help/help.ids';
import { registerTaskTypeHelpIds } from './lib/taskdatamonitor/task.type.help';

// Dashboard widgets are loaded with the dashboard, not with the application shell.
const loadDashboardSummary: DialogComponentLoader = () =>
  import('./lib/dashboard/dashboard-summary.component').then((m) => m.DashboardSummaryComponent);
const loadHoldingMoversWidget: DialogComponentLoader = () =>
  import('./dashboard/component/holding-movers-widget.component').then((m) => m.HoldingMoversWidgetComponent);

// Single source of user interface texts since issue #214. Marked mandatory: if this request fails there is nothing
// left to render text with, so the loader must surface the failure instead of merging an empty object.
const createTranslateLoader = (http: HttpClient) =>
  new MultiTranslateHttpLoader(http, [
    {
      prefix: '/api/globalparameters/properties/',
      suffix: '',
      mandatory: true
    }
  ]);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      APP_ROUTES,
      withComponentInputBinding(),
      withPreloading(AuthenticatedPreloadingStrategy),
      withNavigationErrorHandler(handleLazyLoadNavigationError)
    ),
    provideAngularSvgIcon(),
    provideTranslateService({
      loader: {
        provide: TranslateLoader,
        useFactory: createTranslateLoader,
        deps: [HttpClient]
      }
    }),
    // Application help ids replace the library ids before any component renders a help link.
    provideAppInitializer(() => {
      registerHelpIds(AppHelpIds);
      registerTaskTypeHelpIds(AppTaskTypeHelpIds);
    }),
    provideHttpClient(withXhr(), withInterceptors([authInterceptor]), withInterceptorsFromDi()),
    provideDialogCloseKeepsFocus(),
    ConfirmationService,
    DialogService,
    TabMenuService,
    MessageService,
    { provide: TASK_EXTENDED_SERVICE, useClass: SecurityService },
    { provide: TASK_TYPE_ENUM, useValue: TaskType },
    // Load the user interface texts before anything renders. The backend is their only source since issue #214, so a
    // failure here must stop the bootstrap: without it every component would paint raw translation keys and look
    // broken rather than disconnected. The gate belongs in the initializer and not in the TranslateLoader, because
    // the loader is also used by the lib-e2e-host and by ngx-translate's own reloadLang().
    provideAppInitializer(() => {
      const translateService = inject(TranslateService);
      const language = AppHelper.getNonUserDefinedLanguage(
        sessionStorage.getItem(GlobalSessionNames.LANGUAGE) ?? translateService.getBrowserLang()
      );
      return firstValueFrom(translateService.use(language)).catch(() => {
        showNlsBootstrapFailure();
        // Never resolves on purpose, so Angular does not continue and render app-root behind the overlay.
        return new Promise<never>(() => {});
      });
    }),
    // Propose Change Entity Handler Registration (Modern Angular approach)
    provideAppInitializer(() => {
      const registry = inject(EntityPrepareRegistry);
      const assetclassService = inject(AssetclassService);
      const stockexchangeService = inject(StockexchangeService);
      const importTransactionPlatformService = inject(ImportTransactionPlatformService);
      const securityService = inject(SecurityService);
      const currencypairService = inject(CurrencypairService);
      const gps = inject(GlobalparameterService);

      setupProposeChangeEntityHandlers(
        registry,
        assetclassService,
        stockexchangeService,
        importTransactionPlatformService,
        securityService,
        currencypairService,
        gps
      );
    }),
    // Main Tree Dialog Handler
    { provide: DIALOG_HANDLER, useClass: AppDialogHandler },
    { provide: SETTINGS_MENU_EXTENSIONS, useValue: aiSettingsMenu, multi: true },
    { provide: SETTINGS_MENU_EXTENSIONS, useValue: mcpSettingsMenu, multi: true },
    // File name of the personal data export, overrides the neutral default of the lib layer
    { provide: PERSONAL_DATA_ZIP_NAME, useValue: 'gtPersonalData.zip' },
    // Dashboard renderers by backend type code. The token is not multi, so this replaces the library map and has to
    // relist its entries; dropping one would leave that card blank.
    {
      provide: DASHBOARD_RENDERERS,
      useValue: {
        UNREAD_MAIL: loadDashboardSummary,
        PROPOSE_CHANGE_OPEN: loadDashboardSummary,
        USER_LIMIT_REQUESTS: loadDashboardSummary,
        HOLDING_WINNERS: loadHoldingMoversWidget,
        HOLDING_LOSERS: loadHoldingMoversWidget,
        PERFORMANCE_LAST_SESSIONS: () =>
          import('./dashboard/component/last-sessions-performance-widget.component').then(
            (m) => m.LastSessionsPerformanceWidgetComponent
          ),
        PERFORMANCE_TOTAL_CHART: () =>
          import('./dashboard/component/performance-total-chart-widget.component').then(
            (m) => m.PerformanceTotalChartWidgetComponent
          ),
        ALGO_MONITORING: () =>
          import('./dashboard/component/algo-monitoring-widget.component').then((m) => m.AlgoMonitoringWidgetComponent)
      }
    },
    // Only the chart gains from the space of the whole dashboard area; the table cards are not maximizable.
    { provide: DASHBOARD_MAXIMIZABLE, useValue: new Set(['PERFORMANCE_TOTAL_CHART']) },
    {
      provide: DASHBOARD_CONFIG_SUMMARIES,
      useValue: {
        HOLDING_WINNERS: ShowRecordConfigBase.createColumnConfig(
          DataType.NumericInteger,
          'topN',
          'DASHBOARD_MOVERS_TOP_N'
        ),
        HOLDING_LOSERS: ShowRecordConfigBase.createColumnConfig(
          DataType.NumericInteger,
          'topN',
          'DASHBOARD_MOVERS_TOP_N'
        ),
        PERFORMANCE_LAST_SESSIONS: ShowRecordConfigBase.createColumnConfig(
          DataType.NumericInteger,
          'days',
          'DASHBOARD_LAST_SESSIONS_DAYS'
        )
      }
    },
    // After Login Handler for GT-specific initialization
    { provide: AfterLoginHandler, useClass: GtAfterLoginHandler },
    {
      provide: MAIN_TREE_CONTRIBUTOR,
      useClass: DashboardMainTreeContributor,
      multi: true
    },
    {
      provide: MAIN_TREE_CONTRIBUTOR,
      useClass: PortfolioMainTreeContributor,
      multi: true
    },
    {
      provide: MAIN_TREE_CONTRIBUTOR,
      useClass: AlgoMainTreeContributor,
      multi: true
    },
    {
      provide: MAIN_TREE_CONTRIBUTOR,
      useClass: WatchlistMainTreeContributor,
      multi: true
    },
    {
      provide: MAIN_TREE_CONTRIBUTOR,
      useClass: BaseDataMainTreeContributor,
      multi: true
    },
    {
      provide: MAIN_TREE_CONTRIBUTOR,
      useClass: AdminDataMainTreeContributor,
      multi: true
    }
  ]
};
