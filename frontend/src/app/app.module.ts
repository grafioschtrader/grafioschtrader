import {BrowserModule} from '@angular/platform-browser';
import {inject, NgModule, provideAppInitializer} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {HistoryquoteService} from './historyquote/service/historyquote.service';
import {MenuModule} from 'primeng/menu';
import {MenubarModule} from 'primeng/menubar';
import {routing} from './app.routes';
import {AppComponent} from './app.component';
import {TASK_EXTENDED_SERVICE} from './lib/taskdatamonitor/service/task.extend.service.token';
import {TASK_TYPE_ENUM} from './lib/taskdatamonitor/service/task.type.enum.token';
import {TaskType} from './shared/types/task.type';
import {PortfolioService} from './portfolio/service/portfolio.service';
import {TreeModule} from 'primeng/tree';
import {WatchlistService} from './watchlist/service/watchlist.service';
import {ContextMenuModule} from 'primeng/contextmenu';
import {SecurityaccountSummariesComponent} from './securityaccount/component/securityaccount.summaries.component';
import {SecurityaccountSummaryComponent} from './securityaccount/component/securityaccount.summary.component';
import {SecurityaccountService} from './securityaccount/service/securityaccount.service';
import {TieredMenuModule} from 'primeng/tieredmenu';
import {CashaccountService} from './cashaccount/service/cashaccount.service';
import {TransactionService} from './transaction/service/transaction.service';
import {CurrencypairService} from './securitycurrency/service/currencypair.service';
import {ReplacePipe} from './shared/pipe/replace.pipe';
import {LoginService} from './lib/login/service/log-in.service';
import {MessageToastComponent} from './lib/message/message.toast.component';
import {MessageToastService} from './lib/message/message.toast.service';
import {TranslateLoader, TranslateModule} from '@ngx-translate/core';
import {UserSettingsService} from './lib/services/user.settings.service';
import {UserDataService} from './lib/mainmenubar/service/user.data.service';
import {provideAnimations} from '@angular/platform-browser/animations';
import {DynamicFormModule} from './lib/dynamic-form/dynamic-form.module';
import {ActivePanelService} from './lib/mainmenubar/service/active.panel.service';
import {TenantService} from './tenant/service/tenant.service';
import {SecurityaccountEmptyComponent} from './securityaccount/component/securityaccount.empty.component';
import {StockexchangeService} from './stockexchange/service/stockexchange.service';
import {DataChangedService} from './lib/maintree/service/data.changed.service';
import {AssetclassService} from './assetclass/service/assetclass.service';
import {ConfigurableTableComponent} from './lib/datashowbase/configurable-table.component';
import {SecuritysplitService} from './securitycurrency/service/securitysplit.service';
import {TenantEditFullPageComponent} from './tenant/component/tenant.edit.full.page.component';
import {MainDialogService} from './lib/mainmenubar/service/main.dialog.service';
import {ParentChildRegisterService} from './shared/service/parent.child.register.service';
import {HttpClient, provideHttpClient, withInterceptorsFromDi} from '@angular/common/http';
import {WatchlistTabMenuComponent} from './watchlist/component/watchlist.tab.menu.component';
import {TimeSeriesChartComponent} from './historyquote/component/time.series.chart.component';
import {ViewSizeChangedService} from './lib/layout/service/view.size.changed.service';
import {ChartGeneralPurposeComponent} from './shared/chart/component/chart.general.purpose.component';
import {ChartDataService} from './shared/chart/service/chart.data.service';
import {TableModule} from 'primeng/table';
import {TimeSeriesQuotesService} from './historyquote/service/time.series.quotes.service';
import {CorrelationComponent} from './correlation/component/correlation.component';
import {TradingPlatformPlanTableComponent} from './tradingplatform/component/trading.platform.plan.table.component';
import {TradingPlatformPlanService} from './tradingplatform/service/trading.platform.plan.service';
import {TenantDividendsComponent} from './tenant/component/tenant.dividends.component';
import {TenantTransactionCostComponent} from './tenant/component/tenant.transaction.cost.component';
import {TenantSummariesAssetclassComponent} from './tenant/component/tenant.summaries.assetclass.component';
import {TenantSummariesCashaccountComponent} from './tenant/component/tenant.summaries.cashaccount.component';
import {TenantSummariesSecurityaccountComponent} from './tenant/component/tenant.summaries.securityaccount.component';
import {TenantTransactionTableComponent} from './tenant/component/tenant.transaction.table.component';
import {PortfolioTransactionTableComponent} from './portfolio/component/portfolio.transaction.table.component';
import {
  TransactionCashaccountEditDoubleComponent
} from './transaction/component/transaction-cashaccount-editdouble.component';
import {
  TransactionCashaccountEditSingleComponent
} from './transaction/component/transaction-cashaccount-editsingle.component';
import {TransactionSecurityEditComponent} from './transaction/component/transaction-security-edit.component';
import {TradingPlatformPlanEditComponent} from './tradingplatform/component/trading-platform-plan-edit.component';
import {SecurityaccountEditDynamicComponent} from './securityaccount/component/securityaccount.edit.dynamic.component';
import {TenantEditDynamicComponent} from './tenant/component/tenant.edit.dynamic.component';
import {TenantTransactionCostExtendedComponent} from './tenant/component/tenant-transaction-cost-extended.component';
import {SecuritysplitEditTableComponent} from './shared/securitycurrency/securitysplit-edit-table.component';
import {PortfolioEditDynamicComponent} from './portfolio/component/portfolio.edit.dynamic.component';
import {SecurityaccountTabMenuComponent} from './securityaccount/component/securityaccount.tab.menu.component';
import {SecurityaccountImportTabMenuComponent} from './imptransaction/component/securityaccount-import-tab-menu.component';
import {ImportTransactionHeadService} from './imptransaction/service/import.transaction.head.service';
import {UploadFileDialogComponent} from './lib/generaldialog/component/upload-file-dialog.component';
import {
  SecurityaccountImportTransactionEditHeadComponent
} from './imptransaction/component/securityaccount-import-transaction-edit-head.component';
import {
  SecurityaccountImportTransactionComponent
} from './imptransaction/component/securityaccount.import.transaction.component';
import {
  GTNetImportHeadSelectDialogComponent
} from './imptransaction/component/gtnet-import-head-select-dialog.component';
import {
  SecurityaccountImportTransactionTableComponent
} from './imptransaction/component/securityaccount-import-transaction-table.component';
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
import {ImportTransactionPosService} from './imptransaction/service/import.transaction.pos.service';
import {
  SecurityaccountImportSetCashaccountComponent
} from './imptransaction/component/securityaccount-import-set-cashaccount.component';
import {
  SecurityaccountImportExtendedInfoComponent
} from './imptransaction/component/securityaccount-import-extended-info.component';
import {ToastrModule} from 'ngx-toastr';
import {ProposeChangeEntityService} from './lib/proposechange/service/propose.change.entity.service';
import {setupProposeChangeEntityHandlers} from './shared/changerequest/propose.change.entity.handlers.setup';
import {EntityPrepareRegistry} from './lib/proposechange/service/entity.prepare.registry';
import {AlgoTopService} from './algo/service/algo.top.service';
import {StrategyOverviewComponent} from './algo/component/strategy.overview.component';
import {AlgoTopDataViewComponent} from './algo/component/algo.top.data.view.component';
import {AlgoRuleStrategyCreateWizardComponent} from './algo/component/algo-rule-strategy-create-wizard.component';
import {StepComponent} from './lib/wizard/component/step.component';
import {AlgoAssetclassService} from './algo/service/algo.assetclass.service';
import {AlgoStrategyService} from './algo/service/algo.strategy.service';
import {AlgoAssetclassEditComponent} from './algo/component/algo-assetclass-edit.component';
import {AlgoSecurityEditComponent} from './algo/component/algo-security-edit.component';
import {AlgoSecurityService} from './algo/service/algo.security.service';
import {IndicatorEditComponent} from './historyquote/component/indicator-edit.component';
import {UserAdminService} from './lib/user/service/user.admin.service';
import {UserTableComponent} from './lib/user/component/user.table.component';
import {UserEntityChangeLimitTableComponent} from './lib/user/component/user-entity-change-limit-table.component';
import {UserEditComponent} from './lib/user/component/user-edit-component';
import {UserEntityChangeLimitService} from './lib/user/service/user.entity.change.limit.service';
import {UserEntityChangeLimitEditComponent} from './lib/user/component/user-entity-change-limit-edit.component';
import {ProposeUserTaskService} from './lib/dynamicdialog/service/propose.user.task.service';
import {ActuatorService} from './lib/services/actuator.service';
import {MultiTranslateHttpLoader} from './lib/translator/multi.translate.http.loader';
import {
  SecurityaccountImportExtendedInfoFilenameComponent
} from './imptransaction/component/securityaccount-import-extended-info-filename.component';
import {TradingCalendarGlobalComponent} from './tradingcalendar/component/trading.calendar.global.component';
import {FullyearcalendarLibComponent} from './lib/fullyearcalendar/fullyearcalendar-lib.component';
import {TradingDaysPlusService} from './tradingcalendar/service/trading.days.plus.service';
import {TradingDaysMinusService} from './stockexchange/service/trading.days.minus.service';
import {TenantPerformanceTabMenuComponent} from './tenant/component/tenant.performance.tab.menu.component';
import {PerformancePeriodComponent} from './performanceperiod/component/performance.period.component';
import {TenantPerformanceEodMissingComponent} from './tenant/component/tenant.performance.eod.missing.component';
import {HoldingService} from './performanceperiod/service/holding.service';
import {
  TradingCalendarOtherExchangeDynamicComponent
} from './stockexchange/component/trading.calendar.other.exchange.dynamic.component';
import {
  TenantPerformanceEodMissingTableComponent
} from './tenant/component/tenant-performance-eod-missing-table.component';
// eslint-disable-next-line max-len
import {
  SecurityHistoryquoteQualityTreetableComponent
} from './securitycurrency/component/security.historyquote.quality.treetable.component';
import {
  SecurityHistoryquoteQualityTableComponent
} from './securitycurrency/component/security-historyquote-quality-table.component';
import {NgxFileDropModule} from 'ngx-file-drop';
import {ButtonModule} from 'primeng/button';
import {DialogModule} from 'primeng/dialog';
import {InputTextModule} from 'primeng/inputtext';
import {PanelModule} from 'primeng/panel';
import {ProgressBarModule} from 'primeng/progressbar';
import {PasswordModule} from 'primeng/password';
import {TreeTableModule} from 'primeng/treetable';
import {CheckboxModule} from 'primeng/checkbox';
import {ConfirmDialogModule} from 'primeng/confirmdialog';
import {InputMaskModule} from 'primeng/inputmask';
import {Textarea} from 'primeng/textarea';
import {TooltipModule} from 'primeng/tooltip';
import {ConfirmationService, SharedModule} from 'primeng/api';
import {AngularSvgIconModule} from 'angular-svg-icon';
import {ProductIconService} from './securitycurrency/service/product.icon.service';
import {
  SecurityHistoryquotePeriodEditTableComponent
} from './shared/securitycurrency/security-historyquote-period-edit-table.component';
import {HistoryquotePeriodService} from './securitycurrency/service/historyquote.period.service';
import {DragDropModule} from 'primeng/dragdrop';
import {MailSendRecvService} from './lib/mail/service/mail.send.recv.service';
import {ScrollPanelModule} from 'primeng/scrollpanel';
import {CommonModule} from '@angular/common';
import {InputNumberModule} from 'primeng/inputnumber';
import {DividendService} from './watchlist/service/dividend.service';
import {CardModule} from 'primeng/card';
import {GlobalSettingsTableComponent} from './lib/globalsettings/global.settings.table.component';
import {GlobalSettingsEditComponent} from './lib/globalsettings/global.settings-edit.component';
import {UserChangeOwnerEntitiesComponent} from './lib/user/component/user-change-owner-entities.component';
import {MultipleRequestToOneService} from './shared/service/multiple.request.to.one.service';
import {TaskDataChangeService} from './lib/taskdatamonitor/service/task.data.change.service';
import {TaskDataChangeTableComponent} from './lib/taskdatamonitor/component/task.data.change.table.component';
import {TaskDataChangeEditComponent} from './lib/taskdatamonitor/component/task-data-change-edit.component';
import {CorrelationSetService} from './correlation/service/correlation.set.service';
import {CorrelationTableComponent} from './correlation/component/correlation-table.component';
import {WatchlistAddInstrumentTableComponent} from './watchlist/component/watchlist-add-instrument-table.component';
import {
  CorrelationSetAddInstrumentTableComponent
} from './correlation/component/correlation-set-add-instrument-table.component';
import {ConnectorApiKeyTableComponent} from './lib/connectorapikey/component/connector.api.key.table.component';
import {ConnectorApiKeyEditComponent} from './lib/connectorapikey/component/connector.api.key.edit.component';
import {ConnectorApiKeyService} from './lib/connectorapikey/service/connector.api.key.service';
import {GTNetSetupTableComponent} from './gtnet/component/gtnet.setup.table.component';
import {GTNetExchangeLogTabMenuComponent} from './gtnet/component/gtnet-exchange-log-tabmenu.component';
import {GTNetExchangeLogComponent} from './gtnet/component/gtnet-exchange-log.component';
import {GTNetService} from './gtnet/service/gtnet.service';
import {GTNetMessageTreeTableComponent} from './gtnet/component/gtnet-message-treetable.component';
import {GTNetEditComponent} from './gtnet/component/gtnet-edit.component';
import {GTNetMessageEditComponent} from './gtnet/component/gtnet-message-edit.component';
import {GTNetMessageService} from './gtnet/service/gtnet.message.service';
import {GTNetMessageAnswerTableComponent} from './gtnet/component/gtnet-message-answer-table.component';
import {GTNetMessageAnswerService} from './gtnet/service/gtnet.message.answer.service';
import {GtnetSecurityLookupService} from './gtnet/service/gtnet-security-lookup.service';
import {GTNetSecurityImpHeadService} from './shared/gtnet/service/gtnet-security-imp-head.service';
import {GTNetSecurityImpPosService} from './shared/gtnet/service/gtnet-security-imp-pos.service';
import {GTNetSecurityImportComponent} from './shared/gtnet/component/gtnet-security-import.component';
import {GTNetSecurityImportEditHeadComponent} from './shared/gtnet/component/gtnet-security-import-edit-head.component';
import {GTNetSecurityImportTableComponent} from './shared/gtnet/component/gtnet-security-import-table.component';
import {SendRecvTreetableComponent} from './lib/mail/component/send.recv.treetable.component';
import {MailForwardSettingTableEditComponent} from './lib/mail/component/mail.forward.setting.table.edit.component';
import {SendRecvForwardTabMenuComponent} from './lib/mail/component/send.recv.forward.tab.menu.component';
import {MailSettingForwardService} from './lib/mail/service/mail.setting.forward.service';
import {MailForwardSettingTableComponent} from './lib/mail/component/mail.forward.setting.table.component';
import {MailForwardSettingEditComponent} from './lib/mail/component/mail-forward-setting-edit.component';
import {UDFMetadataSecurityService} from './udfmetasecurity/service/udf.metadata.security.service';
import {UDFMetadataSecurityTableComponent} from './udfmetasecurity/components/udf.metadata.security.table.component';
import {UDFMetadataSecurityEditComponent} from './udfmetasecurity/components/udf-metadata-security-edit.component';
import {WatchlistUdfComponent} from './watchlist/component/watchlist.udf.component';
import {UDFDataService} from './lib/udfmeta/service/udf.data.service';
import {UDFMetadataGeneralService} from './lib/udfmeta/service/udf.metadata.general.service';
import {UDFGeneralEditComponent} from './lib/udfmeta/components/udf-general-edit.component';
import {UDFSpecialTypeDisableUserService} from './lib/udfmeta/service/udf.special.type.disable.user.service';
import {AlarmSetupService} from './algo/service/alarm.setup.service';
import {TenantAlertComponent} from './tenant/component/tenant.alert.component';
import {DatePicker} from 'primeng/datepicker';
import {DialogService, DynamicDialogModule} from 'primeng/dynamicdialog';
import {SecurityService} from './securitycurrency/service/security.service';
import {GlobalparameterGTService} from './gtservice/globalparameter.gt.service';
import {GlobalparameterService} from './lib/services/globalparameter.service';
import {TabsModule} from 'primeng/tabs';
import {TabMenuService} from './lib/tabmenu/service/tab.menu.service';
import {SharedTabMenuComponent} from './lib/tabmenu/component/shared.tab.menu.component';
import {SelectModule} from 'primeng/select';
import {StepperModule} from 'primeng/stepper';
import {StepsComponent} from './lib/wizard/component/steps.component';
import {ReleaseNoteService} from './lib/login/service/release.note.service';
import {MainTreeContributorManager} from './lib/maintree/contributor/main-tree-contributor.manager';
import {MAIN_TREE_CONTRIBUTOR} from './lib/maintree/contributor/main-tree-contributor.interface';
import {MainTreeService} from './lib/maintree/service/main-tree.service';
import {PortfolioMainTreeContributor} from './portfolio/contributor/portfolio-main-tree.contributor';
import {WatchlistMainTreeContributor} from './watchlist/contributor/watchlist-main-tree.contributor';
import {AlgoMainTreeContributor} from './algo/contributor/algo-main-tree.contributor';
import {BaseDataMainTreeContributor} from './shared/contributor/basedata-main-tree.contributor';
import {AdminDataMainTreeContributor} from './shared/contributor/admindata-main-tree.contributor';
import {DIALOG_HANDLER} from './lib/maintree/handler/dialog-handler.interface';
import {AppDialogHandler} from './shared/maintree/handler/app-dialog.handler';
import {AfterLoginHandler} from './lib/login/service/after-login.handler';
import {GtAfterLoginHandler} from './shared/login/gt-after-login.handler';
import {registerHelpIds} from './lib/help/help.ids';
import {AppHelpIds} from './shared/help/help.ids';
import {AlgoStrategyEditComponent} from './algo/component/algo-strategy-edit.component';
import {SecurityDerivedEditComponent} from './securitycurrency/component/security-derived-edit.component';
import {SecurityEditComponent} from './shared/securitycurrency/security-edit.component';
import {SecurityUDFEditComponent} from './securitycurrency/component/security-udf-edit.component';
import {CurrencypairEditComponent} from './shared/securitycurrency/currencypair-edit.component';
import {CorrelationAddInstrumentComponent} from './correlation/component/correlation-add-instrument.component';
import {InstrumentStatisticsResultComponent} from './securitycurrency/component/instrument-statistics-result.component';
import {CorrelationSetEditComponent} from './correlation/component/correlation-set-edit.component';
import {WatchlistEditDynamicComponent} from './watchlist/component/watchlist.edit.dynamic.component';
import {
  SecuritycurrencySearchAndSetComponent
} from './securitycurrency/component/securitycurrency-search-and-set.component';
import {
  SecuritycurrencySearchAndSetTableComponent
} from './securitycurrency/component/securitycurrency-search-and-set-table.component';
import {InstrumentAnnualisedReturnComponent} from './securitycurrency/component/instrument.annualised.return.component';
import {
  InstrumentYearPerformanceTableComponent
} from './securitycurrency/component/instrument-year-performance-table.component';


const createTranslateLoader = (http: HttpClient) => new MultiTranslateHttpLoader(http, [
  {prefix: './assets/i18n/', suffix: '.json'},
  {prefix: './app/lib/assets/i18n/', suffix: '.json'},
  {prefix: '/api/globalparameters/properties/', suffix: ''}
]);

@NgModule({
  declarations: [
    AppComponent,
    ChartGeneralPurposeComponent,
    IndicatorEditComponent,
    SecurityaccountEditDynamicComponent, SecurityaccountEmptyComponent, SecurityaccountImportExtendedInfoComponent,
    SecurityaccountImportExtendedInfoFilenameComponent, SecurityaccountImportSetCashaccountComponent,
    SecurityaccountImportTransactionComponent, SecurityaccountImportTransactionEditHeadComponent,
    SecurityaccountImportTransactionTableComponent,
    SecurityaccountImportTabMenuComponent,
    SecurityaccountTabMenuComponent,
    SecurityHistoryquoteQualityTableComponent,
    SecurityHistoryquoteQualityTreetableComponent,
    TenantAlertComponent,
    TenantEditDynamicComponent, TenantEditFullPageComponent,
    TenantPerformanceEodMissingComponent, TenantPerformanceEodMissingTableComponent,
    TenantPerformanceTabMenuComponent,
    TenantTransactionCostComponent,
    TenantTransactionCostExtendedComponent, TimeSeriesChartComponent,
    TradingCalendarOtherExchangeDynamicComponent,
    GTNetImportHeadSelectDialogComponent,
    GTNetSecurityImportComponent,
    GTNetSecurityImportEditHeadComponent
  ],
  imports: [
    ReplacePipe,
    AlgoAssetclassEditComponent,
    AlgoRuleStrategyCreateWizardComponent,
    AlgoSecurityEditComponent,
    AlgoStrategyEditComponent,
    AlgoTopDataViewComponent,
    StrategyOverviewComponent,
    AngularSvgIconModule.forRoot(),
    BrowserModule,
    ButtonModule,
    DatePicker,
    CardModule,
    ConfirmDialogModule,
    CheckboxModule,
    ConfigurableTableComponent,
    ContextMenuModule,
    CorrelationComponent,
    CorrelationTableComponent,
    CorrelationAddInstrumentComponent,
    CorrelationSetEditComponent,
    CurrencypairEditComponent,
    GTNetEditComponent,
    GTNetExchangeLogComponent,
    GTNetMessageAnswerTableComponent,
    GTNetMessageEditComponent,
    GTNetMessageTreeTableComponent,
    GTNetSetupTableComponent,
    GTNetSecurityImportTableComponent,
    ImportTransactionEditPlatformComponent,
    ImportTransactionEditTemplateComponent,
    ImportTransactionTemplateComponent,
    ImportTransactionTemplateTableComponent,
    InstrumentAnnualisedReturnComponent,
    InstrumentYearPerformanceTableComponent,
    MessageToastComponent,
    PortfolioTransactionTableComponent,
    SecurityaccountSummariesComponent,
    SecurityaccountSummaryComponent,
    SecurityDerivedEditComponent,
    SecurityEditComponent,
    SecurityHistoryquotePeriodEditTableComponent,
    SecuritysplitEditTableComponent,
    SecurityUDFEditComponent,
    SecuritycurrencySearchAndSetComponent,
    SecuritycurrencySearchAndSetTableComponent,
    TemplateFormCheckDialogComponent,
    TemplateFormCheckDialogResultFailedComponent,
    TemplateFormCheckDialogResultSuccessComponent,
    TenantDividendsComponent,
    TenantSummariesAssetclassComponent,
    TenantSummariesCashaccountComponent,
    TenantSummariesSecurityaccountComponent,
    TenantTransactionTableComponent,
    TradingCalendarGlobalComponent,
    TransformPdfToTxtDialogComponent,
    WatchlistAddInstrumentTableComponent,
    CorrelationSetAddInstrumentTableComponent,
    WatchlistTabMenuComponent,
    WatchlistUdfComponent,
    WatchlistEditDynamicComponent,
    InstrumentStatisticsResultComponent,
    PerformancePeriodComponent,
    TableModule,
    TreeTableModule,
    DialogModule,
    DragDropModule,
    DynamicDialogModule,
    DynamicFormModule,
    NgxFileDropModule,
    FormsModule,
    FullyearcalendarLibComponent,
    InputMaskModule,
    Textarea,
    InputTextModule,
    MenubarModule,
    MenuModule,
    PanelModule,
    InputNumberModule,
    PasswordModule,
    ProgressBarModule,
    ReactiveFormsModule,
    routing,
    ScrollPanelModule,
    SelectModule,
    SharedModule,
    StepperModule,
    StepComponent,
    StepsComponent,
    UploadFileDialogComponent,
    UserTableComponent,
    UserEditComponent,
    UserChangeOwnerEntitiesComponent,
    UserEntityChangeLimitTableComponent,
    UserEntityChangeLimitEditComponent,
    GlobalSettingsTableComponent,
    GlobalSettingsEditComponent,
    ConnectorApiKeyTableComponent,
    ConnectorApiKeyEditComponent,
    MailForwardSettingEditComponent,
    MailForwardSettingTableComponent,
    MailForwardSettingTableEditComponent,
    SendRecvForwardTabMenuComponent,
    SendRecvTreetableComponent,
    SharedTabMenuComponent,
    UDFGeneralEditComponent,
    UDFMetadataSecurityEditComponent,
    UDFMetadataSecurityTableComponent,
    TaskDataChangeEditComponent,
    TaskDataChangeTableComponent,
    TradingPlatformPlanEditComponent,
    TradingPlatformPlanTableComponent,
    TabsModule,
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
    CommonModule,
    TransactionCashaccountEditDoubleComponent,
    TransactionCashaccountEditSingleComponent,
    TransactionSecurityEditComponent,
    PortfolioEditDynamicComponent,
  ],
  providers: [
    provideAnimations(),
    provideHttpClient(withInterceptorsFromDi()),
    ActivePanelService, ActuatorService, AlarmSetupService, AlgoAssetclassService, AlgoSecurityService, AlgoStrategyService,
    AlgoTopService, AssetclassService, CashaccountService, ChartDataService, ConfirmationService, ConnectorApiKeyService,
    CorrelationSetService, CurrencypairService, DataChangedService, DialogService, DividendService, GlobalparameterService,
    GlobalparameterGTService, GTNetMessageService, TabMenuService, ReleaseNoteService,
    GTNetMessageAnswerService, GTNetService, GtnetSecurityLookupService, GTNetSecurityImpHeadService, GTNetSecurityImpPosService,
    HistoryquotePeriodService, HistoryquoteService, HoldingService, ImportTransactionHeadService,
    ImportTransactionPlatformService, ImportTransactionPosService, ImportTransactionTemplateService, LoginService,
    MailSendRecvService, MailSendRecvService, MailSettingForwardService, MainDialogService, MessageToastService,
    MultipleRequestToOneService, ParentChildRegisterService, PortfolioService, ProductIconService, ProposeChangeEntityService,
    ProposeUserTaskService, SecurityaccountService, SecurityService, SecuritysplitService, StockexchangeService,
    TaskDataChangeService, TenantService, TimeSeriesQuotesService, TradingDaysMinusService, TradingDaysPlusService,
    TradingPlatformPlanService, TransactionService, UDFDataService, UDFMetadataGeneralService, UDFMetadataSecurityService,
    UDFSpecialTypeDisableUserService, UserAdminService, UserDataService, UserEntityChangeLimitService, UserSettingsService,
    ViewSizeChangedService, WatchlistService, {provide: TASK_EXTENDED_SERVICE, useClass: SecurityService},
    {provide: TASK_TYPE_ENUM, useValue: TaskType},
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
    {provide: DIALOG_HANDLER, useClass: AppDialogHandler},
    // After Login Handler for GT-specific initialization
    {provide: AfterLoginHandler, useClass: GtAfterLoginHandler},
    // Main Tree Contributors
    MainTreeContributorManager,
    MainTreeService,
    {provide: MAIN_TREE_CONTRIBUTOR, useClass: PortfolioMainTreeContributor, multi: true},
    {provide: MAIN_TREE_CONTRIBUTOR, useClass: AlgoMainTreeContributor, multi: true},
    {provide: MAIN_TREE_CONTRIBUTOR, useClass: WatchlistMainTreeContributor, multi: true},
    {provide: MAIN_TREE_CONTRIBUTOR, useClass: BaseDataMainTreeContributor, multi: true},
    {provide: MAIN_TREE_CONTRIBUTOR, useClass: AdminDataMainTreeContributor, multi: true}],
  bootstrap: [AppComponent]
})
export class AppModule {
  constructor() {
    // Register application-specific help IDs at startup
    registerHelpIds(AppHelpIds);
  }
}
