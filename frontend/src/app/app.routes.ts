import { Routes } from '@angular/router';
import { DASHBOARD_ROUTES } from './lib/dashboard/dashboard.routes';
import { AppSettings } from './shared/app.settings';
import { SplitLayoutComponent } from './lib/layout/component/split.layout.component';
import { RegisterComponent } from './lib/login/component/register.component';
import { TenantEditFullPageComponent } from './tenant/component/tenant.edit.full.page.component';
import { RegistrationTokenVerifyComponent } from './lib/login/component/registration.token.verify.component';
import { LoginComponent } from './lib/login/component/login.component';
import { adminGuard, authGuard } from './lib/services/guards.definition';
import { BaseSettings } from './lib/base.settings';

export const APP_ROUTES: Routes = [
  { path: '', redirectTo: '/' + BaseSettings.LOGIN_KEY, pathMatch: 'full' },
  { path: BaseSettings.LOGIN_KEY, component: LoginComponent },
  // OAuth authorization endpoint of the MCP server; it sends the user through the login itself when necessary
  {
    path: 'oauth/authorize',
    loadComponent: () =>
      import('./mcp/component/mcp-oauth-authorize.component').then((m) => m.McpOAuthAuthorizeComponent)
  },
  { path: BaseSettings.REGISTER_KEY, component: RegisterComponent },
  { path: AppSettings.TOKEN_VERIFY_KEY, component: RegistrationTokenVerifyComponent },
  { path: BaseSettings.TENANT_KEY, component: TenantEditFullPageComponent, canActivate: [authGuard] },
  {
    path: BaseSettings.MAINVIEW_KEY,
    component: SplitLayoutComponent,
    canActivate: [authGuard],
    children: [
      ...DASHBOARD_ROUTES,
      {
        //   path: AppSettings.TENANT_TAB_MENU_KEY + '/:id', component: TenantTabMenuComponent, canActivate: [authGuard],
        path: AppSettings.TENANT_TAB_MENU_KEY,
        loadComponent: () =>
          import('./tenant/component/tenant.tab.menu.component').then((m) => m.TenantTabMenuComponent),
        canActivate: [authGuard],
        children: [
          {
            path: AppSettings.PORTFOLIO_KEY,
            loadComponent: () =>
              import('./tenant/component/tenant.summaries.cashaccount.component').then(
                (m) => m.TenantSummariesCashaccountComponent
              ),
            canActivate: [authGuard]
          },
          {
            path: AppSettings.PERFORMANCE_TAB_KEY,
            loadComponent: () =>
              import('./tenant/component/tenant.performance.tab.menu.component').then(
                (m) => m.TenantPerformanceTabMenuComponent
              ),
            canActivate: [authGuard],
            children: [
              {
                path: AppSettings.PERFORMANCE_KEY,
                loadComponent: () =>
                  import('./performanceperiod/component/performance.period.component').then(
                    (m) => m.PerformancePeriodComponent
                  ),
                canActivate: [authGuard]
              },
              {
                path: AppSettings.EOD_DATA_QUALITY_KEY,
                loadComponent: () =>
                  import('./tenant/component/tenant.performance.eod.missing.component').then(
                    (m) => m.TenantPerformanceEodMissingComponent
                  ),
                canActivate: [authGuard]
              }
            ]
          },
          {
            path: AppSettings.DEPOT_KEY,
            loadComponent: () =>
              import('./tenant/component/tenant.summaries.securityaccount.component').then(
                (m) => m.TenantSummariesSecurityaccountComponent
              ),
            canActivate: [authGuard]
          },
          {
            path: AppSettings.DEPOT_CASH_KEY,
            loadComponent: () =>
              import('./tenant/component/tenant.summaries.assetclass.component').then(
                (m) => m.TenantSummariesAssetclassComponent
              ),
            canActivate: [authGuard]
          },
          {
            path: AppSettings.DIVIDENDS_ROUTER_KEY,
            loadComponent: () =>
              import('./tenant/component/tenant.dividends.component').then((m) => m.TenantDividendsComponent),
            canActivate: [authGuard]
          },
          {
            path: AppSettings.TENANT_TRANSACTION,
            loadComponent: () =>
              import('./tenant/component/tenant.transaction.table.component').then(
                (m) => m.TenantTransactionTableComponent
              ),
            canActivate: [authGuard]
          },
          {
            path: AppSettings.TRANSACTION_COST_KEY,
            loadComponent: () =>
              import('./tenant/component/tenant-transaction-cost-tab-menu.component').then(
                (m) => m.TenantTransactionCostTabMenuComponent
              ),
            canActivate: [authGuard],
            children: [
              {
                path: AppSettings.TRANSACTION_COST_SUMMARY_KEY,
                loadComponent: () =>
                  import('./tenant/component/tenant.transaction.cost.component').then(
                    (m) => m.TenantTransactionCostComponent
                  ),
                canActivate: [authGuard]
              },
              {
                path: AppSettings.FEE_MODEL_COMPARISON_KEY,
                loadComponent: () =>
                  import('./tenant/component/fee-model-comparison.component').then(
                    (m) => m.FeeModelComparisonComponent
                  ),
                canActivate: [authGuard]
              }
            ]
          },
          {
            path: AppSettings.STANDING_ORDER_TAB_KEY,
            loadComponent: () =>
              import('./standingorder/component/standing-order-tab-menu.component').then(
                (m) => m.StandingOrderTabMenuComponent
              ),
            canActivate: [authGuard],
            children: [
              {
                path: AppSettings.TENANT_STANDING_ORDER_CASHACCOUNT,
                loadComponent: () =>
                  import('./standingorder/component/standing-order-cashaccount-table.component').then(
                    (m) => m.StandingOrderCashaccountTableComponent
                  ),
                canActivate: [authGuard]
              },
              {
                path: AppSettings.TENANT_STANDING_ORDER_SECURITY,
                loadComponent: () =>
                  import('./standingorder/component/standing-order-security-table.component').then(
                    (m) => m.StandingOrderSecurityTableComponent
                  ),
                canActivate: [authGuard]
              }
            ]
          }
        ]
      },
      {
        path: AppSettings.PORTFOLIO_TAB_MENU_KEY + '/:id',
        loadComponent: () =>
          import('./portfolio/component/portfolio.tab.menu.component').then((m) => m.PortfolioTabMenuComponent),
        canActivate: [authGuard],
        children: [
          {
            path: AppSettings.PORTFOLIO_SUMMARY_KEY + '/:id',
            loadComponent: () =>
              import('./portfolio/component/portfolio.cashaccount.summary.component').then(
                (m) => m.PortfolioCashaccountSummaryComponent
              ),
            canActivate: [authGuard]
          },
          {
            path: AppSettings.PERFORMANCE_KEY + '/:id',
            loadComponent: () =>
              import('./performanceperiod/component/performance.period.component').then(
                (m) => m.PerformancePeriodComponent
              ),
            canActivate: [authGuard]
          },
          {
            path: AppSettings.PORTFOLIO_TRANSACTION_KEY + '/:id',
            loadComponent: () =>
              import('./portfolio/component/portfolio.transaction.table.component').then(
                (m) => m.PortfolioTransactionTableComponent
              ),
            canActivate: [authGuard]
          }
        ]
      },

      {
        path: AppSettings.CASHACCOUNT_DETAIL_ROUTE_KEY + ':id',
        loadComponent: () =>
          import('./cashaccount/component/cashaccount-edit.component').then((m) => m.CashaccountEditComponent),
        canActivate: [authGuard]
      },
      {
        path: AppSettings.SECURITYACCOUNT_EMPTY_ROUTE_KEY + '/:id',
        loadComponent: () =>
          import('./securityaccount/component/securityaccount.empty.component').then(
            (m) => m.SecurityaccountEmptyComponent
          ),
        canActivate: [authGuard]
      },
      {
        path: AppSettings.SECURITYACCOUNT_SUMMARIES_ROUTE_KEY + '/:id',
        loadComponent: () =>
          import('./securityaccount/component/securityaccount.summaries.component').then(
            (m) => m.SecurityaccountSummariesComponent
          ),
        canActivate: [authGuard]
      },

      {
        path: AppSettings.SECURITYACCOUNT_TAB_MENU_KEY + '/:id',
        loadComponent: () =>
          import('./securityaccount/component/securityaccount.tab.menu.component').then(
            (m) => m.SecurityaccountTabMenuComponent
          ),
        canActivate: [authGuard],
        children: [
          {
            path: AppSettings.SECURITYACCOUNT_SUMMERY_ROUTE_KEY + '/:id',
            loadComponent: () =>
              import('./securityaccount/component/securityaccount.summary.component').then(
                (m) => m.SecurityaccountSummaryComponent
              ),
            canActivate: [authGuard]
          },
          {
            path: AppSettings.SECURITYACCOUNT_IMPORT_TAB_MENU_KEY + '/:id',
            loadComponent: () =>
              import('./imptransaction/component/securityaccount-import-tab-menu.component').then(
                (m) => m.SecurityaccountImportTabMenuComponent
              ),
            canActivate: [authGuard],
            children: [
              {
                path: AppSettings.SECURITYACCOUNT_IMPORT_KEY + '/:id',
                loadComponent: () =>
                  import('./imptransaction/component/securityaccount.import.transaction.component').then(
                    (m) => m.SecurityaccountImportTransactionComponent
                  ),
                canActivate: [authGuard]
              },
              {
                path: AppSettings.SECURITYACCOUNT_GTNET_IMPORT_KEY + '/:id',
                loadComponent: () =>
                  import('./shared/gtnet/component/gtnet-security-import.component').then(
                    (m) => m.GTNetSecurityImportComponent
                  ),
                canActivate: [authGuard]
              }
            ]
          }
        ]
      },

      {
        path: AppSettings.PROPOSE_CHANGE_TAB_MENU_KEY,
        loadComponent: () =>
          import('./lib/proposechange/component/propose.change.tab.menu.component').then(
            (m) => m.ProposeChangeTabMenuComponent
          ),
        canActivate: [authGuard],
        children: [
          {
            path: BaseSettings.PROPOSE_CHANGE_REQUEST_FOR_YOU_KEY,
            loadComponent: () =>
              import('./lib/proposechange/component/request.for.you.table.component').then(
                (m) => m.RequestForYouTableComponent
              ),
            canActivate: [authGuard]
          },
          {
            path: BaseSettings.PROPOSE_CHANGE_YOUR_PROPOSAL_KEY,
            loadComponent: () =>
              import('./lib/proposechange/component/your.proposal.table.component').then(
                (m) => m.YourProposalTableComponent
              ),
            canActivate: [authGuard]
          }
        ]
      },

      {
        path: AppSettings.CHART_GENERAL_PURPOSE + '/' + AppSettings.CORRELATION_CHART,
        loadComponent: () =>
          import('./correlation/component/correlation-chart.component').then((m) => m.CorrelationChartComponent),
        outlet: AppSettings.MAIN_BOTTOM,
        canActivate: [authGuard]
      },

      {
        path: AppSettings.CHART_GENERAL_PURPOSE + '/:id',
        loadComponent: () =>
          import('./shared/chart/component/chart.general.purpose.component').then(
            (m) => m.ChartGeneralPurposeComponent
          ),
        outlet: AppSettings.MAIN_BOTTOM,
        canActivate: [authGuard]
      },

      {
        path: AppSettings.TIME_SERIE_QUOTES,
        loadComponent: () =>
          import('./historyquote/component/time.series.chart.component').then((m) => m.TimeSeriesChartComponent),
        outlet: AppSettings.MAIN_BOTTOM,
        canActivate: [authGuard]
      },
      {
        path: AppSettings.HISTORYQUOTE_P_KEY,
        loadComponent: () =>
          import('./historyquote/component/historyquote-host.component').then((m) => m.HistoryquoteHostComponent),
        outlet: AppSettings.MAIN_BOTTOM,
        canActivate: [authGuard]
      },
      {
        path: AppSettings.SEASONALITY,
        loadComponent: () =>
          import('./historyquote/component/seasonality.component').then((m) => m.SeasonalityComponent),
        outlet: AppSettings.MAIN_BOTTOM,
        canActivate: [authGuard]
      },
      {
        path: AppSettings.TENANT_DIVIDENDS_CHART,
        loadComponent: () =>
          import('./tenant/component/tenant-dividends-chart.component').then((m) => m.TenantDividendsChartComponent),
        outlet: AppSettings.MAIN_BOTTOM,
        canActivate: [authGuard]
      },
      {
        path: AppSettings.DEPOT_CASH_CHART,
        loadComponent: () =>
          import('./tenant/component/tenant-holdings-chart.component').then((m) => m.TenantHoldingsChartComponent),
        outlet: AppSettings.MAIN_BOTTOM,
        canActivate: [authGuard]
      },
      {
        path: AppSettings.ALGO_OVERVIEW_KEY,
        loadComponent: () =>
          import('./algo/component/algo-standalone-alert.component').then((m) => m.AlgoStandaloneAlertComponent),
        canActivate: [authGuard]
      },
      {
        path: AppSettings.ALGO_TOP_KEY + '/:id',
        loadComponent: () =>
          import('./algo/component/algo.top.data.view.component').then((m) => m.AlgoTopDataViewComponent),
        canActivate: [authGuard]
      },
      {
        path: AppSettings.SIMULATION_RUN_TAB_MENU_KEY + '/:id',
        loadComponent: () =>
          import('./algo/component/simulation-run-tab-menu.component').then((m) => m.SimulationRunTabMenuComponent),
        canActivate: [authGuard],
        children: [
          {
            path: AppSettings.SIMULATION_RUN_KEY + '/:id',
            loadComponent: () =>
              import('./algo/component/algo-simulation-run.component').then((m) => m.AlgoSimulationRunComponent),
            canActivate: [authGuard]
          },
          {
            path: AppSettings.SIMULATION_RUN_SETTINGS_KEY + '/:id',
            loadComponent: () =>
              import('./algo/component/algo-simulation-run-settings.component').then(
                (m) => m.AlgoSimulationRunSettingsComponent
              ),
            canActivate: [authGuard]
          }
        ]
      },
      {
        path: AppSettings.WATCHLIST_KEY + '/:id',
        loadComponent: () =>
          import('./correlation/component/correlation.component').then((m) => m.CorrelationComponent),
        canActivate: [authGuard]
      },
      {
        path: AppSettings.WATCHLIST_TAB_MENU_KEY + '/:id',
        loadComponent: () =>
          import('./watchlist/component/watchlist.tab.menu.component').then((m) => m.WatchlistTabMenuComponent),
        canActivate: [authGuard],
        children: [
          {
            path: AppSettings.WATCHLIST_PERFORMANCE_KEY + '/:id',
            loadComponent: () =>
              import('./watchlist/component/watchlist.performance.component').then(
                (m) => m.WatchlistPerformanceComponent
              ),
            canActivate: [authGuard]
          },
          {
            path: AppSettings.WATCHLIST_PRICE_FEED_KEY + '/:id',
            loadComponent: () =>
              import('./watchlist/component/watchlist.price.feed.component').then((m) => m.WatchlistPriceFeedComponent),
            canActivate: [authGuard]
          },
          {
            path: AppSettings.WATCHLIST_INDICATOR_KEY + '/:id',
            loadComponent: () =>
              import('./watchlist/component/watchlist.indicator.component').then((m) => m.WatchlistIndicatorComponent),
            canActivate: [authGuard]
          },
          {
            path: AppSettings.WATCHLIST_UDF_KEY + '/:id',
            loadComponent: () =>
              import('./watchlist/component/watchlist.udf.component').then((m) => m.WatchlistUdfComponent),
            canActivate: [authGuard]
          },
          {
            path: AppSettings.WATCHLIST_DIVIDEND_SPLIT_FEED_KEY + '/:id',
            loadComponent: () =>
              import('./watchlist/component/watchlist.dividend.split.feed.component').then(
                (m) => m.WatchlistDividendSplitFeedComponent
              ),
            canActivate: [authGuard]
          }
        ]
      },
      // Base data
      {
        path: AppSettings.ASSETCLASS_KEY,
        loadComponent: () =>
          import('./assetclass/component/assetclass.table.component').then((m) => m.AssetclassTableComponent),
        canActivate: [authGuard]
      },
      {
        path: AppSettings.STOCKEXCHANGE_KEY,
        loadComponent: () =>
          import('./stockexchange/component/stockexchange-tabmenu.component').then(
            (m) => m.StockexchangeTabMenuComponent
          ),
        canActivate: [authGuard],
        children: [
          {
            path: AppSettings.STOCKEXCHANGE_TAB_EXCHANGES_KEY,
            loadComponent: () =>
              import('./stockexchange/component/stockexchange.table.component').then(
                (m) => m.StockexchangeTableComponent
              ),
            canActivate: [authGuard]
          },
          {
            path: AppSettings.STOCKEXCHANGE_TAB_RULE_SETS_KEY,
            loadComponent: () =>
              import('./stockexchange/component/trading-calendar-rule-set-table.component').then(
                (m) => m.TradingCalendarRuleSetTableComponent
              ),
            canActivate: [authGuard]
          }
        ]
      },
      {
        path: AppSettings.TRADING_PLATFORM_PLAN_KEY,
        loadComponent: () =>
          import('./tradingplatform/component/trading.platform.plan.table.component').then(
            (m) => m.TradingPlatformPlanTableComponent
          ),
        canActivate: [authGuard]
      },
      {
        path: AppSettings.GENERIC_CONNECTOR_KEY,
        loadComponent: () =>
          import('./genericconnector/component/generic-connector.component').then((m) => m.GenericConnectorComponent),
        canActivate: [authGuard]
      },
      {
        path: AppSettings.IMP_TRANS_TEMPLATE_KEY,
        loadComponent: () =>
          import('./imptranstemplate/component/import.transaction.template.component').then(
            (m) => m.ImportTransactionTemplateComponent
          ),
        canActivate: [authGuard]
      },
      {
        path: AppSettings.RISK_FREE_RATE_MAPPING_KEY,
        loadComponent: () =>
          import('./riskfreeratemapping/component/risk.free.rate.mapping.table.component').then(
            (m) => m.RiskFreeRateMappingTableComponent
          ),
        canActivate: [authGuard]
      },
      {
        path: AppSettings.BANKRUPT_SECURITY_KEY,
        loadComponent: () =>
          import('./bankruptsecurity/component/bankrupt-security-table.component').then(
            (m) => m.BankruptSecurityTableComponent
          ),
        canActivate: [authGuard]
      },
      {
        path: BaseSettings.UDF_METADATA_GENERAL_KEY,
        loadComponent: () =>
          import('./lib/udfmeta/components/udf.metadata.general.table.component').then(
            (m) => m.UDFMetadataGeneralTableComponent
          ),
        canActivate: [authGuard]
      },
      {
        path: AppSettings.UDF_METADATA_SECURITY_KEY,
        loadComponent: () =>
          import('./udfmetasecurity/components/udf.metadata.security.table.component').then(
            (m) => m.UDFMetadataSecurityTableComponent
          ),
        canActivate: [authGuard]
      },
      {
        path: AppSettings.GT_NET_SECURITY_IMPORT_KEY,
        loadComponent: () =>
          import('./shared/gtnet/component/gtnet-security-import.component').then(
            (m) => m.GTNetSecurityImportComponent
          ),
        canActivate: [authGuard]
      },
      {
        path: AppSettings.SECURITY_ACTION_KEY,
        loadComponent: () =>
          import('./securityaction/component/security-action-treetable.component').then(
            (m) => m.SecurityActionTreetableComponent
          ),
        canActivate: [authGuard]
      },
      // Admin data
      {
        path: AppSettings.USER_MESSAGE_KEY,
        loadComponent: () =>
          import('./lib/mail/component/send.recv.forward.tab.menu.component').then(
            (m) => m.SendRecvForwardTabMenuComponent
          ),
        canActivate: [authGuard]
      },
      {
        path: AppSettings.USER_MESSAGE_KEY,
        loadComponent: () =>
          import('./lib/mail/component/send.recv.forward.tab.menu.component').then(
            (m) => m.SendRecvForwardTabMenuComponent
          ),
        canActivate: [authGuard],
        children: [
          {
            path: BaseSettings.MAIL_SEND_RECV_KEY,
            loadComponent: () =>
              import('./lib/mail/component/send.recv.treetable.component').then((m) => m.SendRecvTreetableComponent),
            canActivate: [authGuard]
          },
          {
            path: BaseSettings.MAIL_SETTING_FORWARD_KEY,
            loadComponent: () =>
              import('./lib/mail/component/mail.forward.setting.table.edit.component').then(
                (m) => m.MailForwardSettingTableEditComponent
              ),
            canActivate: [authGuard]
          }
        ]
      },
      {
        path: AppSettings.TRADING_CALENDAR_GLOBAL_KEY,
        loadComponent: () =>
          import('./tradingcalendar/component/trading.calendar.global.component').then(
            (m) => m.TradingCalendarGlobalComponent
          ),
        canActivate: [authGuard]
      },
      {
        path: AppSettings.SECURITY_HISTORY_QUALITY_KEY,
        loadComponent: () =>
          import('./securitycurrency/component/security.historyquote.quality.treetable.component').then(
            (m) => m.SecurityHistoryquoteQualityTreetableComponent
          ),
        canActivate: [authGuard]
      },
      {
        path: BaseSettings.GLOBAL_SETTINGS_KEY,
        loadComponent: () =>
          import('./lib/globalsettings/global.settings.table.component').then((m) => m.GlobalSettingsTableComponent),
        canActivate: [authGuard]
      },
      {
        path: AppSettings.GT_NET_TAB_MENU_KEY,
        loadComponent: () =>
          import('./lib/gnet/component/gtnet-tabmenu.component').then((m) => m.GTNetTabMenuComponent),
        canActivate: [authGuard],
        children: [
          {
            path: BaseSettings.GT_NET_SETUP_KEY,
            loadComponent: () =>
              import('./lib/gnet/component/gtnet.setup.table.component').then((m) => m.GTNetSetupTableComponent),
            canActivate: [authGuard]
          },
          {
            path: BaseSettings.GT_NET_ADMIN_MESSAGES_KEY,
            loadComponent: () =>
              import('./lib/gnet/component/gtnet-admin-messages.component').then((m) => m.GTNetAdminMessagesComponent),
            canActivate: [authGuard]
          }
        ]
      },
      {
        // The auto-answer rules publish the terms on which this instance admits a peer, so reading them is restricted
        // to administrators on the backend as well; without adminGuard a normal user would reach a screen that 403s.
        path: BaseSettings.GT_NET_MESSAGE_ANSWER_KEY,
        loadComponent: () =>
          import('./lib/gnet/component/gtnet-message-answer-table.component').then(
            (m) => m.GTNetMessageAnswerTableComponent
          ),
        canActivate: [adminGuard]
      },
      {
        path: BaseSettings.GT_NET_EXCHANGE_LOG_KEY,
        loadComponent: () =>
          import('./lib/gnet/component/gtnet-exchange-log-tabmenu.component').then(
            (m) => m.GTNetExchangeLogTabMenuComponent
          ),
        canActivate: [authGuard],
        children: [
          {
            path: ':entityKind',
            loadComponent: () =>
              import('./lib/gnet/component/gtnet-exchange-log.component').then((m) => m.GTNetExchangeLogComponent),
            canActivate: [authGuard]
          }
        ]
      },
      {
        path: AppSettings.GT_NET_EXCHANGE_KEY,
        loadComponent: () =>
          import('./gtnet/component/gtnet-exchange-tabmenu.component').then((m) => m.GTNetExchangeTabMenuComponent),
        canActivate: [authGuard],
        children: [
          {
            path: AppSettings.GT_NET_EXCHANGE_SECURITIES_KEY,
            loadComponent: () =>
              import('./gtnet/component/gtnet-exchange-securities.component').then(
                (m) => m.GTNetExchangeSecuritiesComponent
              ),
            canActivate: [authGuard]
          },
          {
            path: AppSettings.GT_NET_EXCHANGE_CURRENCYPAIRS_KEY,
            loadComponent: () =>
              import('./gtnet/component/gtnet-exchange-currencypairs.component').then(
                (m) => m.GTNetExchangeCurrencypairsComponent
              ),
            canActivate: [authGuard]
          }
        ]
      },
      {
        path: BaseSettings.TASK_DATA_CHANGE_MONITOR_KEY,
        loadComponent: () =>
          import('./lib/taskdatamonitor/component/task-data-change-tab-menu.component').then(
            (m) => m.TaskDataChangeTabMenuComponent
          ),
        canActivate: [authGuard],
        children: [
          {
            path: ':queue',
            loadComponent: () =>
              import('./lib/taskdatamonitor/component/task.data.change.table.component').then(
                (m) => m.TaskDataChangeTableComponent
              ),
            canActivate: [authGuard]
          }
        ]
      },

      {
        path: AppSettings.TAX_DATA_KEY,
        loadComponent: () =>
          import('./taxdata/component/tax-data-treetable.component').then((m) => m.TaxDataTreetableComponent),
        canActivate: [authGuard]
      },
      {
        path: BaseSettings.CONNECTOR_API_KEY_KEY,
        loadComponent: () =>
          import('./lib/connectorapikey/component/connector.api.key.table.component').then(
            (m) => m.ConnectorApiKeyTableComponent
          ),
        canActivate: [adminGuard]
      },
      {
        path: BaseSettings.USER_ADMIN_KEY,
        loadComponent: () => import('./lib/user/component/user.table.component').then((m) => m.UserTableComponent),
        canActivate: [adminGuard]
      },
      {
        path: BaseSettings.ENTITY_LIMIT_KEY,
        loadComponent: () =>
          import('./lib/entitylimit/component/entity.limit.table.component').then((m) => m.EntityLimitTableComponent),
        canActivate: [adminGuard]
      }
    ]
  }

  // otherwise redirect to home
  // { path: '**', redirectTo: '' }
];
