import {RouterModule, Routes} from '@angular/router';
import {HistoryquoteTableComponent} from './historyquote/component/historyquote-table.component';
import {AppSettings} from './shared/app.settings';
import {PortfolioCashaccountSummaryComponent} from './portfolio/component/portfolio.cashaccount.summary.component';
import {SecurityaccountSummariesComponent} from './securityaccount/component/securityaccount.summaries.component';
import {SecurityaccountSummaryComponent} from './securityaccount/component/securityaccount.summary.component';
import {SplitLayoutComponent} from './shared/layout/component/split.layout.component';
import {SecurityaccountEmptyComponent} from './securityaccount/component/securityaccount.empty.component';
import {AssetclassTableComponent} from './assetclass/component/assetclass.table.component';
import {StockexchangeTableComponent} from './stockexchange/component/stockexchange.table.component';
import {RegisterComponent} from './shared/login/component/register.component';
import {TenantEditFullPageComponent} from './tenant/component/tenant.edit.full.page.component';
import {WatchlistTabMenuComponent} from './watchlist/component/watchlist.tab.menu.component';
import {WatchlistPerformanceComponent} from './watchlist/component/watchlist.performance.component';
import {TimeSeriesChartComponent} from './historyquote/component/time.series.chart.component';
import {ChartGeneralPurposeComponent} from './shared/chart/component/chart.general.purpose.component';
import {RegistrationTokenVerifyComponent} from './shared/login/component/registration.token.verify.component';
import {CorrelationComponent} from './watchlist/component/correlation.component';
import {TradingPlatformPlanTableComponent} from './tradingplatform/component/trading.platform.plan.table.component';
import {TenantDividendsComponent} from './tenant/component/tenant.dividends.component';
import {TenantTransactionCostComponent} from './tenant/component/tenant.transaction.cost.component';
import {TenantSummariesAssetclassComponent} from './tenant/component/tenant.summaries.assetclass.component';
import {TenantSummariesCashaccountComponent} from './tenant/component/tenant.summaries.cashaccount.component';
import {TenantSummariesSecurityaccountComponent} from './tenant/component/tenant.summaries.securityaccount.component';
import {TenantTabMenuComponent} from './tenant/component/tenant.tab.menu.component';
import {PortfolioTabMenuComponent} from './portfolio/component/portfolio.tab.menu.component';
import {TenantTransactionTableComponent} from './tenant/component/tenant.transaction.table.component';
import {PortfolioTransactionTableComponent} from './portfolio/component/portfolio.transaction.table.component';
import {CashaccountEditComponent} from './cashaccount/component/cashaccount-edit.component';
import {SecurityaccountTabMenuComponent} from './securityaccount/component/securityaccount.tab.menu.component';
import {LoginComponent} from './shared/login/component/login.component';
import {
  SecurityaccountImportTransactionComponent
} from './securityaccount/component/securityaccount.import.transaction.component';
import {ImportTransactionTemplateComponent} from './imptranstemplate/component/import.transaction.template.component';
import {ProposeChangeTabMenuComponent} from './proposechange/component/propose.change.tab.menu.component';
import {RequestForYouTableComponent} from './proposechange/component/request.for.you.table.component';
import {YourProposalTableComponent} from './proposechange/component/your.proposal.table.component';
import {StrategyOverviewComponent} from './algo/component/strategy.overview.component';
import {AlgoTopDataViewComponent} from './algo/component/algo.top.data.view.component';
import {UserTableComponent} from './user/component/user.table.component';
import {TradingCalendarGlobalComponent} from './tradingcalendar/component/trading.calendar.global.component';
import {TenantPerformanceTabMenuComponent} from './tenant/component/tenant.performance.tab.menu.component';
import {PerformancePeriodComponent} from './shared/performanceperiod/component/performance.period.component';
import {TenantPerformanceEodMissingComponent} from './tenant/component/tenant.performance.eod.missing.component';
import {
  SecurityHistoryquoteQualityTreetableComponent
} from './securitycurrency/component/security.historyquote.quality.treetable.component';
import {WatchlistPriceFeedComponent} from './watchlist/component/watchlist.price.feed.component';
import {WatchlistDividendSplitFeedComponent} from './watchlist/component/watchlist.dividend.split.feed.component';
import {GlobalSettingsTableComponent} from './shared/globalsettings/global.settings.table.component';
import {TaskDataChangeTableComponent} from './shared/taskdatamonitor/component/task.data.change.table.component';
import {ConnectorApiKeyTableComponent} from './connectorapikey/component/connector.api.key.table.component';
import {GTNetConsumerMonitorComponent} from './gtnet/component/gtnet.consumer.monitor.component';
import {GTNetSetupTableComponent} from './gtnet/component/gtnet.setup.table.component';
import {GTNetProviderMonitorComponent} from './gtnet/component/gtnet.provider.monitor.component';
import {GTNetMessageAutoAnswerComponent} from './gtnet/component/gtnet.message.auto.answer.component';
import {SendRecvTreetableComponent} from './mail/component/send.recv.treetable.component';
import {adminGuard, authGuard} from './shared/service/guards.definition';
import {SendRecvForwardTabMenuComponent} from './mail/component/send.recv.forward.tab.menu.component';
import {MailForwardSettingTableComponent} from './mail/component/mail.forward.setting.table.component';
import {UDFMetadataSecurityTableComponent} from './shared/udfmeta/components/udf.metadata.security.table.component';


const APP_ROUTES: Routes = [
  {path: '', redirectTo: '/' + AppSettings.LOGIN_KEY, pathMatch: 'full'},
  {path: AppSettings.LOGIN_KEY, component: LoginComponent},
  {path: AppSettings.REGISTER_KEY, component: RegisterComponent},
  {path: AppSettings.TOKEN_VERIFY_KEY, component: RegistrationTokenVerifyComponent},
  {path: AppSettings.TENANT_KEY, component: TenantEditFullPageComponent, canActivate: [authGuard]},
  {
    path: AppSettings.MAINVIEW_KEY, component: SplitLayoutComponent, canActivate: [authGuard],
    children: [
      {
        //   path: AppSettings.TENANT_TAB_MENU_KEY + '/:id', component: TenantTabMenuComponent, canActivate: [authGuard],
        path: AppSettings.TENANT_TAB_MENU_KEY, component: TenantTabMenuComponent, canActivate: [authGuard],
        children: [
          {path: AppSettings.PORTFOLIO_KEY, component: TenantSummariesCashaccountComponent, canActivate: [authGuard]},
          {
            path: AppSettings.PERFORMANCE_TAB_KEY,
            component: TenantPerformanceTabMenuComponent,
            canActivate: [authGuard],
            children: [
              {path: AppSettings.PERFORMANCE_KEY, component: PerformancePeriodComponent, canActivate: [authGuard]},
              {
                path: AppSettings.EOD_DATA_QUALITY_KEY,
                component: TenantPerformanceEodMissingComponent,
                canActivate: [authGuard]
              }
            ]
          },
          {path: AppSettings.DEPOT_KEY, component: TenantSummariesSecurityaccountComponent, canActivate: [authGuard]},
          {path: AppSettings.DEPOT_CASH_KEY, component: TenantSummariesAssetclassComponent, canActivate: [authGuard]},
          {path: AppSettings.DIVIDENDS_ROUTER_KEY, component: TenantDividendsComponent, canActivate: [authGuard]},
          {path: AppSettings.TENANT_TRANSACTION, component: TenantTransactionTableComponent, canActivate: [authGuard]},
          {path: AppSettings.TRANSACTION_COST_KEY, component: TenantTransactionCostComponent, canActivate: [authGuard]}
        ]
      },
      {
        path: AppSettings.PORTFOLIO_TAB_MENU_KEY + '/:id',
        component: PortfolioTabMenuComponent,
        canActivate: [authGuard],
        children: [
          {
            path: AppSettings.PORTFOLIO_SUMMARY_KEY + '/:id',
            component: PortfolioCashaccountSummaryComponent,
            canActivate: [authGuard]
          },
          {path: AppSettings.PERFORMANCE_KEY + '/:id', component: PerformancePeriodComponent, canActivate: [authGuard]},
          {
            path: AppSettings.PORTFOLIO_TRANSACTION_KEY + '/:id', component: PortfolioTransactionTableComponent,
            canActivate: [authGuard]
          }
        ]
      },

      {
        path: AppSettings.CASHACCOUNT_DETAIL_ROUTE_KEY + ':id',
        component: CashaccountEditComponent,
        canActivate: [authGuard]
      },
      {
        path: AppSettings.SECURITYACCOUNT_EMPTY_ROUTE_KEY + '/:id',
        component: SecurityaccountEmptyComponent,
        canActivate: [authGuard]
      },
      {
        path: AppSettings.SECURITYACCOUNT_SUMMARIES_ROUTE_KEY + '/:id',
        component: SecurityaccountSummariesComponent,
        canActivate: [authGuard]
      },

      {
        path: AppSettings.SECURITYACCOUNT_TAB_MENU_KEY + '/:id',
        component: SecurityaccountTabMenuComponent,
        canActivate: [authGuard],
        children: [
          {
            path: AppSettings.SECURITYACCOUNT_SUMMERY_ROUTE_KEY + '/:id', component: SecurityaccountSummaryComponent,
            canActivate: [authGuard]
          },
          {
            path: AppSettings.SECURITYACCOUNT_IMPORT_KEY + '/:id', component: SecurityaccountImportTransactionComponent,
            canActivate: [authGuard]
          }
        ]
      },

      {
        path: AppSettings.PROPOSE_CHANGE_TAB_MENU_KEY,
        component: ProposeChangeTabMenuComponent,
        canActivate: [authGuard],
        children: [
          {
            path: AppSettings.PROPOSE_CHANGE_REQUEST_FOR_YOU_KEY,
            component: RequestForYouTableComponent,
            canActivate: [authGuard]
          },
          {
            path: AppSettings.PROPOSE_CHANGE_YOUR_PROPOSAL_KEY,
            component: YourProposalTableComponent,
            canActivate: [authGuard]
          },
        ]
      },

      {
        path: AppSettings.CHART_GENERAL_PURPOSE + '/:id', component: ChartGeneralPurposeComponent,
        outlet: AppSettings.MAIN_BOTTOM, canActivate: [authGuard]
      },

      {
        path: AppSettings.TIME_SERIE_QUOTES, component: TimeSeriesChartComponent,
        outlet: AppSettings.MAIN_BOTTOM, canActivate: [authGuard]
      },
      {
        path: AppSettings.HISTORYQUOTE_P_KEY, component: HistoryquoteTableComponent, outlet: AppSettings.MAIN_BOTTOM,
        canActivate: [authGuard]
      },
      {path: AppSettings.STRATEGY_OVERVIEW_KEY, component: StrategyOverviewComponent, canActivate: [authGuard]},
      {path: AppSettings.ALGO_TOP_KEY + '/:id', component: AlgoTopDataViewComponent, canActivate: [authGuard]},
      {path: AppSettings.WATCHLIST_KEY + '/:id', component: CorrelationComponent, canActivate: [authGuard]},
      {
        path: AppSettings.WATCHLIST_TAB_MENU_KEY + '/:id',
        component: WatchlistTabMenuComponent,
        canActivate: [authGuard],
        children: [
          {
            path: AppSettings.WATCHLIST_PERFORMANCE_KEY + '/:id',
            component: WatchlistPerformanceComponent,
            canActivate: [authGuard]
          },
          {
            path: AppSettings.WATCHLIST_PRICE_FEED_KEY + '/:id',
            component: WatchlistPriceFeedComponent,
            canActivate: [authGuard]
          },
          {
            path: AppSettings.WATCHLIST_DIVIDEND_SPLIT_FEED_KEY + '/:id',
            component: WatchlistDividendSplitFeedComponent, canActivate: [authGuard]
          },
        ]
      },
      // Base data
      {path: AppSettings.ASSETCLASS_KEY, component: AssetclassTableComponent, canActivate: [authGuard]},
      {path: AppSettings.STOCKEXCHANGE_KEY, component: StockexchangeTableComponent, canActivate: [authGuard]},
      {
        path: AppSettings.TRADING_PLATFORM_PLAN_KEY,
        component: TradingPlatformPlanTableComponent,
        canActivate: [authGuard]
      },
      {
        path: AppSettings.IMP_TRANS_TEMPLATE_KEY,
        component: ImportTransactionTemplateComponent,
        canActivate: [authGuard]
      },
      {
        path:  AppSettings.UDF_METADATA_SECURITY_KEY,
        component: UDFMetadataSecurityTableComponent,
        canActivate: [authGuard]
      },
      // Admin data
      {
        path: AppSettings.USER_MESSAGE_KEY, component: SendRecvForwardTabMenuComponent, canActivate: [authGuard]
      },
      {
        path: AppSettings.USER_MESSAGE_KEY, component: SendRecvForwardTabMenuComponent, canActivate: [authGuard],
        children: [
          {path: AppSettings.MAIL_SEND_RECV_KEY, component: SendRecvTreetableComponent, canActivate: [authGuard]},
          {
            path: AppSettings.MAIL_SETTING_FORWARD_KEY,
            component: MailForwardSettingTableComponent,
            canActivate: [authGuard]
          }
        ]
      },
      {
        path: AppSettings.TRADING_CALENDAR_GLOBAL_KEY,
        component: TradingCalendarGlobalComponent,
        canActivate: [authGuard]
      },
      {
        path: AppSettings.SECURITY_HISTORY_QUALITY_KEY,
        component: SecurityHistoryquoteQualityTreetableComponent,
        canActivate: [authGuard]
      },
      {
        path: AppSettings.GLOBAL_SETTINGS_KEY,
        component: GlobalSettingsTableComponent,
        canActivate: [authGuard]
      },
      {
        path: AppSettings.GT_NET_CONSUME_MONITOR_KEY,
        component: GTNetConsumerMonitorComponent,
        canActivate: [authGuard]
      },
      {
        path: AppSettings.GT_NET_KEY,
        component: GTNetSetupTableComponent,
        canActivate: [authGuard]
      },
      {
        path: AppSettings.GT_NET_AUTO_ANSWER_KEY,
        component: GTNetMessageAutoAnswerComponent,
        canActivate: [authGuard]
      },
      {
        path: AppSettings.GT_NET_PROVIDER_MONITOR_KEY,
        component: GTNetProviderMonitorComponent,
        canActivate: [authGuard]
      },
      {
        path: AppSettings.TASK_DATA_CHANGE_MONITOR_KEY,
        component: TaskDataChangeTableComponent,
        canActivate: [authGuard]
      },

      {path: AppSettings.CONNECTOR_API_KEY_KEY, component: ConnectorApiKeyTableComponent, canActivate: [adminGuard]},
      {path: AppSettings.USER_ENTITY_LIMIT_KEY, component: UserTableComponent, canActivate: [adminGuard]}
    ]
  },


  // otherwise redirect to home
  // { path: '**', redirectTo: '' }

];
export const routing = RouterModule.forRoot(APP_ROUTES);
