import {BrowserModule} from '@angular/platform-browser';
import {NgModule} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {HistoryquoteTableComponent} from './historyquote/component/historyquote-table.component';
import {HistoryquoteService} from './historyquote/service/historyquote.service';
import {MenubarComponent} from './shared/mainmenubar/component/menubar.component';
import {MenuModule} from 'primeng/menu';
import {MenubarModule} from 'primeng/menubar';
import {routing} from './app.routes';
import {AppComponent} from './app.component';
import {PortfolioService} from './portfolio/service/portfolio.service';
import {MainTreeComponent} from './shared/maintree/component/main-tree.component';
import {TreeModule} from 'primeng/tree';
import {WatchlistService} from './watchlist/service/watchlist.service';
import {ContextMenuModule} from 'primeng/contextmenu';
import {SecurityService} from './securitycurrency/service/security.service';
import {GlobalparameterService} from './shared/service/globalparameter.service';
import {PortfolioCashaccountSummaryComponent} from './portfolio/component/portfolio.cashaccount.summary.component';
import {DropdownModule} from 'primeng/dropdown';
import {SecurityaccountSummariesComponent} from './securityaccount/component/securityaccount.summaries.component';
import {SecurityaccountSummaryComponent} from './securityaccount/component/securityaccount.summary.component';
import {SecurityaccountService} from './securityaccount/service/securityaccount.service';
import {TieredMenuModule} from 'primeng/tieredmenu';
import {CashaccountService} from './cashaccount/service/cashaccount.service';
import {TransactionService} from './transaction/service/transaction.service';
import {CurrencypairService} from './securitycurrency/service/currencypair.service';
import {ReplacePipe} from './shared/pipe/replace.pipe';
import {SplitLayoutComponent} from './shared/layout/component/split.layout.component';
import {LoginService} from './shared/login/service/log-in.service';
import {AuthGuard} from './shared/service/auth.guard';
import {TabMenuModule} from 'primeng/tabmenu';
import {MessageToastComponent} from './shared/message/message.toast.component';
import {MessageToastService} from './shared/message/message.toast.service';
import {TranslateLoader, TranslateModule} from '@ngx-translate/core';
import {UserSettingsService} from './shared/service/user.settings.service';
import {BrowserAnimationsModule} from '@angular/platform-browser/animations';
import {DynamicFormModule} from './dynamic-form/dynamic-form.module';
import {ActivePanelService} from './shared/mainmenubar/service/active.panel.service';
import {TenantService} from './tenant/service/tenant.service';
import {SecurityaccountEmptyComponent} from './securityaccount/component/securityaccount.empty.component';
import {AssetclassTableComponent} from './assetclass/component/assetclass.table.component';
import {StockexchangeTableComponent} from './stockexchange/component/stockexchange.table.component';
import {StockexchangeService} from './stockexchange/service/stockexchange.service';
import {DataChangedService} from './shared/maintree/service/data.changed.service';
import {AssetclassService} from './assetclass/service/assetclass.service';
import {SecuritysplitService} from './securitycurrency/service/securitysplit.service';
import {RegisterComponent} from './shared/login/component/register.component';
import {TenantEditFullPageComponent} from './tenant/component/tenant.edit.full.page.component';
import {MainDialogComponent} from './shared/mainmenubar/component/main.dialog.component';
import {MainDialogService} from './shared/mainmenubar/service/main.dialog.service';
import {TransactionSecurityEditComponent} from './transaction/component/transaction-security-edit.component';
import {ParentChildRegisterService} from './shared/service/parent.child.register.service';
import {FormButtonComponent} from './dynamic-form/components/form-button/form-button.component';
import {FormPButtonComponent} from './dynamic-form/components/form-button/form-pbutton.component';
import {FormCheckboxComponent} from './dynamic-form/components/form-input/form-checkbox.component';
import {FormInputComponent} from './dynamic-form/components/form-input/form-input.component';
import {FormPCalendarComponent} from './dynamic-form/components/form-input/form-pcalendar.component';
import {FormPInputTextareaComponent} from './dynamic-form/components/form-input/form-pinputtextarea.component';
import {HttpClient, HttpClientModule} from '@angular/common/http';
import {WatchlistTabMenuComponent} from './watchlist/component/watchlist.tab.menu.component';
import {WatchlistPerformanceComponent} from './watchlist/component/watchlist.performance.component';
import {TimeSeriesChartComponent} from './historyquote/component/time.series.chart.component';
import {ViewSizeChangedService} from './shared/layout/service/view.size.changed.service';
import {ChartGeneralPurposeComponent} from './shared/chart/component/chart.general.purpose.component';
import {ChartDataService} from './shared/chart/service/chart.data.service';
import {RegistrationTokenVerifyComponent} from './shared/login/component/registration.token.verify.component';
import {TableModule} from 'primeng/table';
import {TimeSeriesQuotesService} from './historyquote/service/time.series.quotes.service';
import {CorrelationComponent} from './watchlist/component/correlation.component';
import {AllEditGuard} from './shared/service/all.edit.guard.service';
import {TradingPlatformPlanTableComponent} from './tradingplatform/component/trading.platform.plan.table.component';
import {TradingPlatformPlanService} from './tradingplatform/service/trading.platform.plan.service';
import {TenantDividendsComponent} from './tenant/component/tenant.dividends.component';
import {TenantTransactionCostComponent} from './tenant/component/tenant.transaction.cost.component';
import {TenantSummariesAssetclassComponent} from './tenant/component/tenant.summaries.assetclass.component';
import {TenantSummariesCashaccountComponent} from './tenant/component/tenant.summaries.cashaccount.component';
import {TenantSummariesSecurityaccountComponent} from './tenant/component/tenant.summaries.securityaccount.component';
import {TenantTabMenuComponent} from './tenant/component/tenant.tab.menu.component';
import {PortfolioTabMenuComponent} from './portfolio/component/portfolio.tab.menu.component';
import {TenantTransactionTableComponent} from './tenant/component/tenant.transaction.table.component';
import {PortfolioTransactionTableComponent} from './portfolio/component/portfolio.transaction.table.component';
import {FormInputSuggestionComponent} from './dynamic-form/components/form-input/form-input-suggestion.component';
import {FormFileUploadComponent} from './dynamic-form/components/form-input-file/form-file-upload.component';
import {FormInputSelectComponent} from './dynamic-form/components/form-input/form-input-select.component';
import {
  TransactionCashaccountEditDoubleComponent
} from './transaction/component/transaction-cashaccount-editdouble.component';
import {
  TransactionCashaccountEditSingleComponent
} from './transaction/component/transaction-cashaccount-editsingle.component';
import {SecurityEditComponent} from './securitycurrency/component/security-edit.component';
import {CurrencypairEditComponent} from './securitycurrency/component/currencypair-edit.component';
import {WatchlistAddInstrumentComponent} from './watchlist/component/watchlist-add-instrument.component';
import {WatchlistEditComponent} from './watchlist/component/watchlist-edit.component';
import {TradingPlatformPlanEditComponent} from './tradingplatform/component/trading-platform-plan-edit.component';
import {SecurityaccountEditComponent} from './securityaccount/component/securityaccount-edit.component';
import {StockexchangeEditComponent} from './stockexchange/component/stockexchange-edit.component';
import {CashaccountEditComponent} from './cashaccount/component/cashaccount-edit.component';
import {HistoryquoteEditComponent} from './historyquote/component/historyquote-edit.component';
import {TransactionCashaccountTableComponent} from './transaction/component/transaction-cashaccount-table.component';
import {TransactionSecurityTableComponent} from './transaction/component/transaction-security-table.component';
import {TenantEditDialogComponent} from './tenant/component/tenant-edit-dialog.component';
import {TenantDividendsExtendedComponent} from './tenant/component/tenant-dividends-extended.component';
import {SecuritycurrencyExtendedInfoComponent} from './watchlist/component/securitycurrency-extended-info.component';
import {TenantTransactionCostExtendedComponent} from './tenant/component/tenant-transaction-cost-extended.component';
import {SecuritysplitEditTableComponent} from './securitycurrency/component/securitysplit-edit-table.component';
import {PortfolioEditComponent} from './portfolio/component/portfolio-edit.component';
import {AssetclassEditComponent} from './assetclass/component/assetclass-edit.component';
import {SecurityaccountTabMenuComponent} from './securityaccount/component/securityaccount.tab.menu.component';
import {ImportTransactionHeadService} from './securityaccount/service/import.transaction.head.service';
import {LoginComponent} from './shared/login/component/login.component';
import {PasswordEditComponent} from './shared/login/component/password-edit.component';
import {UploadFileDialogComponent} from './shared/generaldialog/upload-file-dialog.component';
import {
  SecurityaccountImportTransactionEditHeadComponent
} from './securityaccount/component/securityaccount-import-transaction-edit-head.component';
import {
  SecurityaccountImportTransactionComponent
} from './securityaccount/component/securityaccount.import.transaction.component';
import {
  SecurityaccountImportTransactionTableComponent
} from './securityaccount/component/securityaccount-import-transaction-table.component';
import {ImportTransactionTemplateComponent} from './imptranstemplate/component/import.transaction.template.component';
import {ImportTransactionTemplateService} from './imptranstemplate/service/import.transaction.template.service';
import {ImportTransactionPlatformService} from './imptranstemplate/service/import.transaction.platform.service';
import {
  ImportTransactionEditTemplateComponent
} from './imptranstemplate/component/import-transaction-edit-template.component';
import {
  ImportTransactionEditPlatformComponent
} from './imptranstemplate/component/import-transaction-edit-platform.component';
import {
  ImportTransactionTemplateTableComponent
} from './imptranstemplate/component/import-transaction-template-table.component';
import {TransformPdfToTxtDialogComponent} from './imptranstemplate/component/transform-pdf-to-txt-dialog.component';
import {TemplateFormCheckDialogComponent} from './imptranstemplate/component/template-form-check-dialog.component';
import {
  TemplateFormCheckDialogResultSuccessComponent
} from './imptranstemplate/component/template-form-check-dialog-result-success.component';
// eslint-disable-next-line max-len
import {
  TemplateFormCheckDialogResultFailedComponent
} from './imptranstemplate/component/template-form-check-dialog-result-failed.component';
import {ImportTransactionPosService} from './securityaccount/service/import.transaction.pos.service';
import {
  SecuritycurrencySearchAndSetComponent
} from './securitycurrency/component/securitycurrency-search-and-set.component';
import {
  SecuritycurrencySearchAndSetTableComponent
} from './securitycurrency/component/securitycurrency-search-and-set-table.component';
import {
  SecurityaccountImportSetCashaccountComponent
} from './securityaccount/component/securityaccount-import-set-cashaccount.component';
import {
  SecurityaccountImportExtendedInfoComponent
} from './securityaccount/component/securityaccount-import-extended-info.component';
import {ToastrModule} from 'ngx-toastr';
import {ProposeChangeTabMenuComponent} from './proposechange/component/propose.change.tab.menu.component';
import {YourProposalTableComponent} from './proposechange/component/your.proposal.table.component';
import {RequestForYouTableComponent} from './proposechange/component/request.for.you.table.component';
import {ProposeChangeEntityService} from './proposechange/service/propose.change.entity.service';
import {
  TransactionCashaccountConnectDebitCreditComponent
} from './transaction/component/transaction-cashaccount-connect-debit-credit-component';
import {AlgoTopService} from './algo/service/algo.top.service';
import {StrategyOverviewComponent} from './algo/component/strategy.overview.component';
import {AlgoTopDataViewComponent} from './algo/component/algo.top.data.view.component';
import {FormTriStateCheckboxComponent} from './dynamic-form/components/form-input/form-tri-state-checkbox.component';
import {AlgoRuleStrategyCreateWizardComponent} from './algo/component/algo-rule-strategy-create-wizard.component';
import {StepComponent} from './shared/wizard/component/step.component';
import {StepsComponent} from './shared/wizard/component/steps.component';
import {AlgoAssetclassService} from './algo/service/algo.assetclass.service';
import {AlgoStrategyService} from './algo/service/algo.strategy.service';
import {StrategyDetailComponent} from './algo/component/strategy-detail.component';
import {AlgoAssetclassEditComponent} from './algo/component/algo-assetclass-edit.component';
import {AlgoSecurityEditComponent} from './algo/component/algo-security-edit.component';
import {AlgoSecurityService} from './algo/service/algo.security.service';
import {AlgoStrategyEditComponent} from './algo/component/algo-strategy-edit.component';
import {IndicatorEditComponent} from './historyquote/component/indicator-edit.component';
import {AlgoRuleStrategyCreateComponent} from './algo/component/algo-rule-strategy-create.component';
import {
  TenantDividendSecurityAccountSelectionDialogComponent
} from './tenant/component/tenant-dividend-security-account-selection-dialog.component';
import {TenantDividendAccountSelectionComponent} from './tenant/component/tenant-dividend-account-selection.component';
import {MailInboxTableComponent} from './mail/component/mail.inbox.table.component';
import {UserAdminService} from './user/service/user.admin.service';
import {UserTableComponent} from './user/component/user.table.component';
import {UserEntityChangeLimitTableComponent} from './user/component/user-entity-change-limit-table.component';
import {UserEditComponent} from './user/component/user-edit-component';
import {AdminGuard} from './shared/service/admin.guard';
import {NicknameLangEditComponent} from './shared/login/component/nickname-lang-edit.component';
import {UserEntityChangeLimitService} from './user/service/user.entity.change.limit.service';
import {UserEntityChangeLimitEditComponent} from './user/component/user-entity-change-limit-edit.component';
import {
  LimitTransactionRequestDynamicComponent
} from './shared/dynamicdialog/component/limit.transaction.request.dynamic.component';
import {DynamicDialogModule} from 'primeng/dynamicdialog';
import {
  LogoutReleaseRequestDynamicComponent
} from './shared/dynamicdialog/component/logout.release.request.dynamic.component';
import {ProposeUserTaskService} from './shared/dynamicdialog/service/propose.user.task.service';
import {ActuatorService} from './shared/service/actuator.service';
import {ApplicationInfoComponent} from './shared/login/component/application-info.component';
import {MultiTranslateHttpLoader} from './shared/translator/multi.translate.http.loader';
import {
  SecurityaccountImportExtendedInfoFilenameComponent
} from './securityaccount/component/securityaccount-import-extended-info-filename.component';
import {TradingCalendarGlobalComponent} from './tradingcalendar/component/trading.calendar.global.component';
import {FullyearcalendarLibModule} from './fullyearcalendar/fullyearcalendar-lib.module';
import {TradingDaysPlusService} from './tradingcalendar/service/trading.days.plus.service';
import {TradingDaysMinusService} from './stockexchange/service/trading.days.minus.service';
import {
  TradingCalendarStockexchangeComponent
} from './stockexchange/component/trading-calendar-stockexchange.component';
import {TenantPerformanceTabMenuComponent} from './tenant/component/tenant.performance.tab.menu.component';
import {PerformancePeriodComponent} from './shared/performanceperiod/component/performance.period.component';
import {TenantPerformanceEodMissingComponent} from './tenant/component/tenant.performance.eod.missing.component';
import {HoldingService} from './shared/performanceperiod/service/holding.service';
import {
  TenantPerformanceTreetableComponent
} from './shared/performanceperiod/component/performance-period-treetable.component';
import {
  TradingCalendarOtherExchangeDynamicComponent
} from './stockexchange/component/trading.calendar.other.exchange.dynamic.component';
import {
  TenantPerformanceEodMissingTableComponent
} from './tenant/component/tenant-performance-eod-missing-table.component';
import {HistoryquoteQualityComponent} from './historyquote/component/historyquote-quality.component';
// eslint-disable-next-line max-len
import {
  SecurityHistoryquoteQualityTreetableComponent
} from './securitycurrency/component/security.historyquote.quality.treetable.component';
import {
  SecurityHistoryquoteQualityTableComponent
} from './securitycurrency/component/security-historyquote-quality-table.component';
import {HistoryquoteQualityFillGapsComponent} from './historyquote/component/historyquote-quality-fill-gaps.component';
import {
  TenantPerformanceFromToDiffComponent
} from './shared/performanceperiod/component/performance-period-from-to-diff.component';
import {NgxFileDropModule} from 'ngx-file-drop';
import {ButtonModule} from 'primeng/button';
import {CalendarModule} from 'primeng/calendar';
import {DialogModule} from 'primeng/dialog';
import {InputTextModule} from 'primeng/inputtext';
import {MessagesModule} from 'primeng/messages';
import {PanelModule} from 'primeng/panel';
import {ProgressBarModule} from 'primeng/progressbar';
import {PasswordModule} from 'primeng/password';
import {TreeTableModule} from 'primeng/treetable';
import {CheckboxModule} from 'primeng/checkbox';
import {ConfirmDialogModule} from 'primeng/confirmdialog';
import {InputMaskModule} from 'primeng/inputmask';
import {InputTextareaModule} from 'primeng/inputtextarea';
import {TabViewModule} from 'primeng/tabview';
import {StepsModule} from 'primeng/steps';
import {TooltipModule} from 'primeng/tooltip';
import {ConfirmationService, SharedModule} from 'primeng/api';
import {
  TransactionSecurityMarginTreetableComponent
} from './transaction/component/transaction-security-margin-treetable.component';
import {SecurityDerivedEditComponent} from './securitycurrency/component/security-derived-edit.component';
import {FormInputButtonComponent} from './dynamic-form/components/form-input/form-input-button.component';
import {AngularSvgIconModule} from 'angular-svg-icon';
import {ProductIconService} from './securitycurrency/service/product.icon.service';
import {
  SecurityHistoryquotePeriodEditTableComponent
} from './securitycurrency/component/security-historyquote-period-edit-table.component';
import {HistoryquotePeriodService} from './securitycurrency/service/historyquote.period.service';
import {HistoryquoteDeleteDialogComponent} from './historyquote/component/historyquote-delete-dialog.component';

import {DragDropModule} from 'primeng/dragdrop';
import {MailInboxService} from './mail/service/mail.inbox.service';
import {MailMessageComponent} from './mail/component/mail.message.component';
import {MailInOutTabMenuComponent} from './mail/component/mail.in.out.tab.menu.component';
import {MailSendboxTableComponent} from './mail/component/mail.sendbox.table.component';
import {MailSendDynamicComponent} from './shared/dynamicdialog/component/mail.send.dynamic.component';
import {MailSendboxService} from './mail/service/mail.sendbox.service';
import {ScrollPanelModule} from 'primeng/scrollpanel';
import {CommonModule} from '@angular/common';
import {InputNumberModule} from 'primeng/inputnumber';
import {FormInputNumberComponent} from './dynamic-form/components/form-input/form-input-number.component';
import {NgxCurrencyModule} from 'ngx-currency';
import {WatchlistDividendSplitFeedComponent} from './watchlist/component/watchlist.dividend.split.feed.component';
import {WatchlistPriceFeedComponent} from './watchlist/component/watchlist.price.feed.component';
import {WatchlistSecuritysplitTableComponent} from './watchlist/component/watchlist-securitysplit-table.component';
import {WatchlistDividendTableComponent} from './watchlist/component/watchlist-dividend-table.component';
import {DividendService} from './watchlist/service/dividend.service';
import {CardModule} from 'primeng/card';
import {GlobalSettingsTableComponent} from './shared/globalsettings/global.settings.table.component';
import {GlobalSettingsEditComponent} from './shared/globalsettings/global.settings-edit.component';
import {UserChangeOwnerEntitiesComponent} from './user/component/user-change-owner-entities.component';
import {MultipleRequestToOneService} from './shared/service/multiple.request.to.one.service';
import {TaskDataChangeService} from './shared/taskdatamonitor/service/task.data.change.service';
import {TaskDataChangeTableComponent} from './shared/taskdatamonitor/component/task.data.change.table.component';
import {TaskDataChangeEditComponent} from './shared/taskdatamonitor/component/task-data-change-edit.component';
import {CorrelationSetService} from './watchlist/service/correlation.set.service';
import {CorrelationTableComponent} from './watchlist/component/correlation-table.component';
import {CorrelationSetEditComponent} from './watchlist/component/correlation-set-edit.component';
import {WatchlistAddInstrumentTableComponent} from './watchlist/component/watchlist-add-instrument-table.component';
import {
  CorrelationSetAddInstrumentTableComponent
} from './watchlist/component/correlation-set-add-instrument-table.component';
import {CorrelationAddInstrumentComponent} from './watchlist/component/correlation-add-instrument.component';
import {InstrumentStatisticsResultComponent} from './securitycurrency/component/instrument-statistics-result.component';
import {InstrumentAnnualisedReturnComponent} from './securitycurrency/component/instrument.annualised.return.component';
import {
  InstrumentYearPerformanceTableComponent
} from './securitycurrency/component/instrument-year-performance-table.component';
import {
  InstrumentStatisticsSummaryComponent
} from './securitycurrency/component/instrument-statistics-summary.component';
import {ConnectorApiKeyTableComponent} from './connectorapikey/component/connector.api.key.table.component';
import {ConnectorApiKeyEditComponent} from './connectorapikey/component/connector.api.key.edit.component';
import {ConnectorApiKeyService} from './connectorapikey/service/connector.api.key.service';
import {GTNetConsumerMonitorComponent} from './gtnet/component/gtnet.consumer.monitor.component';
import {GTNetSetupTableComponent} from './gtnet/component/gtnet.setup.table.component';
import {GTNetProviderMonitorComponent} from './gtnet/component/gtnet.provider.monitor.component';
import {GTNetService} from './gtnet/service/gtnet.service';
import {GTNetMessageTreeTableComponent} from './gtnet/component/gtnet-message-treetable.component';
import {GTNetEditComponent} from './gtnet/component/gtnet-edit.component';
import {GTNetMessageEditComponent} from './gtnet/component/gtnet-message-edit.component';
import {GTNetMessageService} from './gtnet/service/gtnet.message.service';
import {GTNetMessageAutoAnswerComponent} from './gtnet/component/gtnet.message.auto.answer.component';

const createTranslateLoader = (http: HttpClient) => new MultiTranslateHttpLoader(http, [
  {prefix: './assets/i18n/', suffix: '.json'},
  {prefix: '/api/globalparameters/properties/', suffix: ''}
]);

@NgModule({
  declarations: [
    AlgoAssetclassEditComponent, AlgoRuleStrategyCreateComponent, AlgoRuleStrategyCreateWizardComponent,
    AlgoSecurityEditComponent, GlobalSettingsEditComponent, CorrelationTableComponent,
    AlgoStrategyEditComponent, AlgoTopDataViewComponent, AppComponent, ApplicationInfoComponent, AssetclassEditComponent,
    ConnectorApiKeyTableComponent, ConnectorApiKeyEditComponent,
    AssetclassTableComponent, CashaccountEditComponent, ChartGeneralPurposeComponent, CurrencypairEditComponent,
    HistoryquoteQualityFillGapsComponent, HistoryquoteDeleteDialogComponent, GlobalSettingsTableComponent,
    GTNetEditComponent, GTNetMessageEditComponent, GTNetMessageAutoAnswerComponent,
    GTNetConsumerMonitorComponent, GTNetSetupTableComponent, GTNetProviderMonitorComponent, GTNetMessageTreeTableComponent,
    HistoryquoteEditComponent, HistoryquoteTableComponent, ImportTransactionEditPlatformComponent, HistoryquoteQualityComponent,
    SecurityHistoryquoteQualityTreetableComponent, SecurityHistoryquoteQualityTableComponent, InstrumentStatisticsSummaryComponent,
    InstrumentYearPerformanceTableComponent, InstrumentStatisticsResultComponent, InstrumentAnnualisedReturnComponent,
    ImportTransactionEditTemplateComponent, ImportTransactionTemplateComponent, ImportTransactionTemplateTableComponent,
    IndicatorEditComponent, LimitTransactionRequestDynamicComponent, LoginComponent, LogoutReleaseRequestDynamicComponent,
    MailMessageComponent, MailInOutTabMenuComponent, MailSendboxTableComponent, MailSendDynamicComponent,
    MainDialogComponent, MainTreeComponent, MenubarComponent, MessageToastComponent, NicknameLangEditComponent,
    PasswordEditComponent, PortfolioCashaccountSummaryComponent, PortfolioEditComponent, PortfolioTabMenuComponent,
    PortfolioTransactionTableComponent, ProposeChangeTabMenuComponent, RegisterComponent, RegistrationTokenVerifyComponent,
    ReplacePipe, RequestForYouTableComponent, SecurityaccountEditComponent, SecurityaccountEmptyComponent,
    SecurityDerivedEditComponent, SecurityHistoryquotePeriodEditTableComponent, CorrelationSetEditComponent,
    SecurityaccountImportExtendedInfoComponent, SecurityaccountImportExtendedInfoFilenameComponent,
    SecurityaccountImportSetCashaccountComponent, SecuritycurrencySearchAndSetComponent, CorrelationSetAddInstrumentTableComponent,
    SecuritycurrencySearchAndSetTableComponent, SecurityaccountImportTransactionComponent, CorrelationAddInstrumentComponent,
    SecurityaccountImportTransactionEditHeadComponent, SecurityaccountImportTransactionTableComponent,
    SecurityaccountSummariesComponent, SecurityaccountSummaryComponent, SecurityaccountTabMenuComponent, SecurityEditComponent,
    SecuritysplitEditTableComponent, SplitLayoutComponent, StepComponent, StepsComponent, StockexchangeEditComponent,
    StockexchangeTableComponent, StrategyDetailComponent, StrategyOverviewComponent, TemplateFormCheckDialogComponent,
    TenantPerformanceTabMenuComponent, PerformancePeriodComponent, TenantPerformanceEodMissingComponent,
    TemplateFormCheckDialogResultFailedComponent, TemplateFormCheckDialogResultSuccessComponent, TenantPerformanceFromToDiffComponent,
    TenantDividendAccountSelectionComponent, TenantDividendsComponent, TenantDividendSecurityAccountSelectionDialogComponent,
    TenantDividendsExtendedComponent, TenantEditDialogComponent, TenantEditFullPageComponent, TenantSummariesAssetclassComponent,
    TenantPerformanceTreetableComponent, TradingCalendarOtherExchangeDynamicComponent, TenantPerformanceEodMissingTableComponent,
    TransactionSecurityMarginTreetableComponent, TaskDataChangeTableComponent, TaskDataChangeEditComponent,
    TenantSummariesCashaccountComponent, TenantSummariesSecurityaccountComponent, TenantTabMenuComponent,
    TenantTransactionCostComponent, TenantTransactionCostExtendedComponent, TenantTransactionTableComponent,
    TimeSeriesChartComponent, TradingCalendarGlobalComponent, TradingCalendarStockexchangeComponent,
    TradingPlatformPlanTableComponent, TradingPlatformPlanEditComponent, TransactionCashaccountConnectDebitCreditComponent,
    TransactionCashaccountEditDoubleComponent, TransactionCashaccountEditSingleComponent, TransactionCashaccountTableComponent,
    TransactionSecurityEditComponent, TransactionSecurityTableComponent, TransformPdfToTxtDialogComponent,
    UploadFileDialogComponent, UserEditComponent, UserEntityChangeLimitEditComponent, UserEntityChangeLimitTableComponent,
    MailInboxTableComponent, UserTableComponent, WatchlistAddInstrumentComponent, WatchlistAddInstrumentTableComponent,
    CorrelationComponent, WatchlistPriceFeedComponent, WatchlistEditComponent,
    SecuritycurrencyExtendedInfoComponent, UserChangeOwnerEntitiesComponent,
    WatchlistPerformanceComponent, WatchlistTabMenuComponent, WatchlistDividendSplitFeedComponent,
    WatchlistSecuritysplitTableComponent, WatchlistDividendTableComponent, YourProposalTableComponent
  ],
  imports: [
    AngularSvgIconModule.forRoot(),
    BrowserAnimationsModule,
    BrowserModule,
    ButtonModule,
    CalendarModule,
    CardModule,
    ConfirmDialogModule,
    CheckboxModule,
    ContextMenuModule,
    TableModule,
    TreeTableModule,
    DialogModule,
    DragDropModule,
    DropdownModule,
    DynamicDialogModule,
    DynamicFormModule.withComponents([
      FormButtonComponent,
      FormPButtonComponent,
      FormCheckboxComponent,
      FormTriStateCheckboxComponent,
      FormFileUploadComponent,
      FormInputComponent,
      FormInputButtonComponent,
      FormInputNumberComponent,
      FormInputSuggestionComponent,
      FormInputSelectComponent,
      FormPCalendarComponent,
      FormPInputTextareaComponent
    ]),
    NgxCurrencyModule,
    NgxFileDropModule,
    FormsModule,
    FullyearcalendarLibModule,
    HttpClientModule,
    InputMaskModule,
    InputTextareaModule,
    InputTextModule,
    MenubarModule,
    MenuModule,
    MessagesModule,
    PanelModule,
    InputNumberModule,
    PasswordModule,
    ProgressBarModule,
    ReactiveFormsModule,
    routing,
    ScrollPanelModule,
    SharedModule,
    StepsModule,
    TabViewModule,
    TabMenuModule,
    TieredMenuModule,
    ToastrModule.forRoot({
      timeOut: 10000,
      positionClass: 'toast-top-right',
      preventDuplicates: true,
    }),
    TooltipModule,
    TranslateModule.forRoot({
      loader: {
        provide: TranslateLoader,
        useFactory: (createTranslateLoader),
        deps: [HttpClient]
      }
    }),
    TreeModule,
    CommonModule
  ],
  providers: [ActivePanelService, AllEditGuard, ActuatorService, AdminGuard, AlgoTopService, AlgoAssetclassService,
    AssetclassService, AuthGuard, CashaccountService, ChartDataService, AlgoStrategyService, AlgoSecurityService,
    ConfirmationService, CurrencypairService, DataChangedService, DividendService, GlobalparameterService, GTNetMessageService,
    GTNetService, HistoryquoteService, HistoryquotePeriodService, HoldingService, ImportTransactionPlatformService,
    ImportTransactionTemplateService, ImportTransactionPosService, ImportTransactionHeadService, LoginService,
    MainDialogService, MessageToastService, ParentChildRegisterService, MailInboxService, MailSendboxService,
    MultipleRequestToOneService, TaskDataChangeService, CorrelationSetService, ConnectorApiKeyService,
    ProposeChangeEntityService, ProposeUserTaskService, ProductIconService, PortfolioService, SecurityaccountService,
    SecurityService, SecuritysplitService, StockexchangeService, TenantService, TimeSeriesQuotesService,
    TradingDaysMinusService, TradingDaysPlusService, TradingPlatformPlanService, TransactionService, UserAdminService,
    UserEntityChangeLimitService, UserSettingsService, ViewSizeChangedService,
    WatchlistService],
  bootstrap: [AppComponent]
})
export class AppModule {
}
