import { provideRouter, withNavigationErrorHandler, withPreloading } from '@angular/router';
import { APP_ROUTES } from './app.routes';
import { DialogComponentLoader } from './lib/maintree/handler/lazy-dialog';
import { AuthenticatedPreloadingStrategy } from './lib/login/service/authenticated-preloading.strategy';
import { handleLazyLoadNavigationError } from './lib/login/service/lazy-load-failure';
import {
  ApplicationConfig,
  importProvidersFrom,
  inject,
  provideAppInitializer,
  provideZoneChangeDetection
} from '@angular/core';
import { HistoryquoteService } from './historyquote/service/historyquote.service';
import { HistoryquoteLegacyService } from './historyquote/service/historyquote.legacy.service';
import { TaxDataService } from './taxdata/service/tax-data.service';
import { UserChartShapeService } from './historyquote/service/user.chart.shape.service';
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
import { PortfolioService } from './portfolio/service/portfolio.service';
import { WatchlistService } from './watchlist/service/watchlist.service';
import { WatchlistFilterSortStateService } from './watchlist/service/watchlist.filter.sort.state.service';
import { SecurityaccountService } from './securityaccount/service/securityaccount.service';
import { CashaccountService } from './cashaccount/service/cashaccount.service';
import { TransactionService } from './transaction/service/transaction.service';
import { TransactionReceiptService } from './transaction/service/transaction.receipt.service';
import { CurrencypairService } from './securitycurrency/service/currencypair.service';
import { LoginService } from './lib/login/service/log-in.service';
import { MessageToastService } from './lib/message/message.toast.service';
import { TranslateLoader, TranslateModule, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { UserSettingsService } from './lib/services/user.settings.service';
import { UserDataService } from './lib/mainmenubar/service/user.data.service';
import { ActivePanelService } from './lib/mainmenubar/service/active.panel.service';
import { TenantService } from './tenant/service/tenant.service';
import { StockexchangeService } from './stockexchange/service/stockexchange.service';
import { DataChangedService } from './lib/maintree/service/data.changed.service';
import { TreeNavigationStateService } from './lib/maintree/service/tree.navigation.state.service';
import { AssetclassService } from './assetclass/service/assetclass.service';
import { SecuritysplitService } from './securitycurrency/service/securitysplit.service';
import { MainDialogService } from './lib/mainmenubar/service/main.dialog.service';
import { ManageClientService } from './lib/manageclient/service/manage-client.service';
import { ParentChildRegisterService } from './shared/service/parent.child.register.service';
import { HttpClient, provideHttpClient, withInterceptors, withInterceptorsFromDi, withXhr } from '@angular/common/http';
import { authInterceptor } from './lib/login/service/auth.http.interceptor';
import { provideDialogCloseKeepsFocus } from './lib/edit/dialog.close.focus';
import { ViewSizeChangedService } from './lib/layout/service/view.size.changed.service';
import { ChartDataService } from './shared/chart/service/chart.data.service';
import { PlotlyLoaderService } from './shared/chart/plotly.loader';
import { TenantDividendsChartService } from './tenant/service/tenant.dividends.chart.service';
import { TenantHoldingsChartService } from './tenant/service/tenant.holdings.chart.service';
import { TimeSeriesQuotesService } from './historyquote/service/time.series.quotes.service';
import { TradingPlatformPlanService } from './tradingplatform/service/trading.platform.plan.service';
import { TradingCalendarRuleSetService } from './stockexchange/service/trading.calendar.rule.set.service';
import { GenericConnectorDefService } from './genericconnector/service/generic.connector.def.service';
import { ImportTransactionHeadService } from './imptransaction/service/import.transaction.head.service';
import { ImportTransactionTemplateService } from './imptranstemplate/service/import.transaction.template.service';
import { ImportTransactionPlatformService } from './imptranstemplate/service/import.transaction.platform.service';
import { BankruptSecurityService } from './bankruptsecurity/service/bankrupt.security.service';
import { RiskFreeRateMappingService } from './riskfreeratemapping/service/risk.free.rate.mapping.service';
import { ImportTransactionPosService } from './imptransaction/service/import.transaction.pos.service';
import { ProposeChangeEntityService } from './lib/proposechange/service/propose.change.entity.service';
import { setupProposeChangeEntityHandlers } from './shared/changerequest/propose.change.entity.handlers.setup';
import { EntityPrepareRegistry } from './lib/proposechange/service/entity.prepare.registry';
import { AlgoTopService } from './algo/service/algo.top.service';
import { AlgoAssetclassService } from './algo/service/algo.assetclass.service';
import { AlgoStrategyService } from './algo/service/algo.strategy.service';
import { AlgoSecurityService } from './algo/service/algo.security.service';
import { UserAdminService } from './lib/user/service/user.admin.service';
import { EntityLimitService } from './lib/entitylimit/service/entity.limit.service';
import { ProposeUserTaskService } from './lib/dynamicdialog/service/propose.user.task.service';
import { ActuatorService } from './lib/services/actuator.service';
import { MultiTranslateHttpLoader } from './lib/translator/multi.translate.http.loader';
import { showNlsBootstrapFailure } from './lib/translator/nls.bootstrap.failure';
import { AppHelper } from './lib/helper/app.helper';
import { GlobalSessionNames } from './lib/global.session.names';
import { TradingDaysPlusService } from './tradingcalendar/service/trading.days.plus.service';
import { TradingDaysMinusService } from './stockexchange/service/trading.days.minus.service';
import { HoldingService } from './performanceperiod/service/holding.service';
import { ConfirmationService, MessageService } from '@openng/optimus-ui/api';
import { AngularSvgIconModule } from 'angular-svg-icon';
import { ProductIconService } from './securitycurrency/service/product.icon.service';
import { HistoryquotePeriodService } from './securitycurrency/service/historyquote.period.service';
import { MailSendRecvService } from './lib/mail/service/mail.send.recv.service';
import { DividendService } from './watchlist/service/dividend.service';
import { MultipleRequestToOneService } from './shared/service/multiple.request.to.one.service';
import { TaskDataChangeService } from './lib/taskdatamonitor/service/task.data.change.service';
import { CorrelationSetService } from './correlation/service/correlation.set.service';
import { ConnectorApiKeyService } from './lib/connectorapikey/service/connector.api.key.service';
import { GTNetService } from './lib/gnet/service/gtnet.service';
import { GTNetMessageService } from './lib/gnet/service/gtnet.message.service';
import { GTNetMessageAnswerService } from './lib/gnet/service/gtnet.message.answer.service';
import { GTNetProtocolService } from './lib/gnet/service/gtnet.protocol.service';
import { GtnetSecurityLookupService } from './gtnet/service/gtnet-security-lookup.service';
import { SecurityLookupService } from './securitylookup/service/security-lookup.service';
import { GTNetSecurityImpHeadService } from './shared/gtnet/service/gtnet-security-imp-head.service';
import { GTNetSecurityImpPosService } from './shared/gtnet/service/gtnet-security-imp-pos.service';
import { MailSettingForwardService } from './lib/mail/service/mail.setting.forward.service';
import { UDFMetadataSecurityService } from './udfmetasecurity/service/udf.metadata.security.service';
import { UDFDataService } from './lib/udfmeta/service/udf.data.service';
import { UDFMetadataGeneralService } from './lib/udfmeta/service/udf.metadata.general.service';
import { UDFSpecialTypeDisableUserService } from './lib/udfmeta/service/udf.special.type.disable.user.service';
import { AlarmSetupService } from './algo/service/alarm.setup.service';
import { SimulationContextService } from './algo/service/simulation.context.service';
import { StandingOrderService } from './standingorder/service/standing.order.service';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { SecurityService } from './securitycurrency/service/security.service';
import { GlobalparameterGTService } from './gtservice/globalparameter.gt.service';
import { GlobalparameterService } from './lib/services/globalparameter.service';
import { TabMenuService } from './lib/tabmenu/service/tab.menu.service';
import { ReleaseNoteService } from './lib/login/service/release.note.service';
import { MainTreeContributorManager } from './lib/maintree/contributor/main-tree-contributor.manager';
import { MAIN_TREE_CONTRIBUTOR } from './lib/maintree/contributor/main-tree-contributor.interface';
import { DashboardMainTreeContributor } from './lib/maintree/contributor/dashboard-main-tree.contributor';
import { MainTreeService } from './lib/maintree/service/main-tree.service';
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
import { SecurityActionService } from './securityaction/service/security-action.service';

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
    // The library components still rely on zone change detection; specification/angular_signals_onpush.md removes it.
    provideZoneChangeDetection(),
    provideRouter(
      APP_ROUTES,
      withPreloading(AuthenticatedPreloadingStrategy),
      withNavigationErrorHandler(handleLazyLoadNavigationError)
    ),
    AuthenticatedPreloadingStrategy,
    importProvidersFrom(
      AngularSvgIconModule.forRoot(),
      TranslateModule.forRoot({
        loader: {
          provide: TranslateLoader,
          useFactory: createTranslateLoader,
          deps: [HttpClient]
        }
      })
    ),
    // Application help ids replace the library ids before any component renders a help link.
    provideAppInitializer(() => {
      registerHelpIds(AppHelpIds);
      registerTaskTypeHelpIds(AppTaskTypeHelpIds);
    }),
    provideHttpClient(withXhr(), withInterceptors([authInterceptor]), withInterceptorsFromDi()),
    provideDialogCloseKeepsFocus(),
    ActivePanelService,
    ActuatorService,
    AlarmSetupService,
    SimulationContextService,
    AlgoAssetclassService,
    AlgoSecurityService,
    AlgoStrategyService,
    AlgoTopService,
    AssetclassService,
    CashaccountService,
    ChartDataService,
    PlotlyLoaderService,
    TenantDividendsChartService,
    TenantHoldingsChartService,
    ConfirmationService,
    ConnectorApiKeyService,
    CorrelationSetService,
    CurrencypairService,
    DataChangedService,
    DialogService,
    DividendService,
    GlobalparameterService,
    TreeNavigationStateService,
    GlobalparameterGTService,
    GTNetMessageService,
    TabMenuService,
    ReleaseNoteService,
    GTNetMessageAnswerService,
    GTNetProtocolService,
    GTNetService,
    GtnetSecurityLookupService,
    SecurityLookupService,
    GTNetSecurityImpHeadService,
    GTNetSecurityImpPosService,
    HistoryquotePeriodService,
    HistoryquoteService,
    HistoryquoteLegacyService,
    HoldingService,
    ImportTransactionHeadService,
    ImportTransactionPlatformService,
    ImportTransactionPosService,
    ImportTransactionTemplateService,
    BankruptSecurityService,
    RiskFreeRateMappingService,
    LoginService,
    MailSendRecvService,
    MailSendRecvService,
    MailSettingForwardService,
    MainDialogService,
    ManageClientService,
    MessageToastService,
    MessageService,
    MultipleRequestToOneService,
    ParentChildRegisterService,
    PortfolioService,
    ProductIconService,
    ProposeChangeEntityService,
    ProposeUserTaskService,
    SecurityaccountService,
    SecurityService,
    SecuritysplitService,
    StockexchangeService,
    SecurityActionService,
    StandingOrderService,
    TaskDataChangeService,
    TaxDataService,
    TenantService,
    TimeSeriesQuotesService,
    TradingDaysMinusService,
    TradingDaysPlusService,
    TradingPlatformPlanService,
    TradingCalendarRuleSetService,
    GenericConnectorDefService,
    TransactionService,
    TransactionReceiptService,
    UDFDataService,
    UDFMetadataGeneralService,
    UDFMetadataSecurityService,
    UDFSpecialTypeDisableUserService,
    UserAdminService,
    UserChartShapeService,
    UserDataService,
    EntityLimitService,
    UserSettingsService,
    ViewSizeChangedService,
    WatchlistService,
    WatchlistFilterSortStateService,
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
    // Main Tree Contributors
    MainTreeContributorManager,
    MainTreeService,
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
