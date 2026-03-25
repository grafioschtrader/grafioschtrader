import {AfterViewInit, Component, OnInit, ViewChild} from '@angular/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {AppHelper, Comparison} from '../../lib/helper/app.helper';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {Portfolio} from '../../entities/portfolio';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {SecurityaccountService} from '../service/securityaccount.service';
import {Securityaccount} from '../../entities/securityaccount';
import {HelpIds} from '../../lib/help/help.ids';
import {TradingPlatformPlan} from '../../entities/tradingplatformplan';
import {TradingPlatformPlanService} from '../../tradingplatform/service/trading.platform.plan.service';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';
import {Helper} from '../../lib/helper/helper';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {SimpleDynamicEditBase} from '../../lib/edit/simple.dynamic.edit.base';
import {GlobalparameterGTService} from '../../gtservice/globalparameter.gt.service';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {BaseSettings} from '../../lib/base.settings';
import {DynamicFormComponent} from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import {SecaccountTradingPeriod} from '../../entities/secaccount.trading.period';
import {TradingPeriodTransactionSummary} from '../../entities/trading.period.transaction.summary';
import {AssetclassType} from '../../shared/types/assetclass.type';
import {SpecialInvestmentInstruments} from '../../shared/types/special.investment.instruments';
import {TradingPeriodTableComponent} from './trading-period-table.component';
import {GlobalSessionNames} from '../../lib/global.session.names';

/**
 * Edit security account with trading period table for defining which instrument types can be traded.
 */
@Component({
  selector: 'securityaccount-edit',
  template: `
    <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                  #form="dynamicForm"
                  (submitBt)="submit($event)">
    </dynamic-form>
    <trading-period-table #tradingPeriodTable [tradingPeriods]="tradingPeriods"
                          [transactionSummaries]="transactionSummaries"
                          [isNewSecurityAccount]="callParam.thisObject == null" />
  `,
  standalone: true,
  imports: [DynamicFormComponent, TradingPeriodTableComponent]
})
export class SecurityaccountEditDynamicComponent extends SimpleDynamicEditBase<Securityaccount> implements OnInit, AfterViewInit {
  static readonly DIALOG_WIDTH = 700;
  callParam: CallParam;

  @ViewChild('tradingPeriodTable') tradingPeriodTable: TradingPeriodTableComponent;

  tradingPeriods: SecaccountTradingPeriod[] = [];
  transactionSummaries: TradingPeriodTransactionSummary[] = [];

  constructor(private tradingPlatformPlanService: TradingPlatformPlanService,
    private gpsGT: GlobalparameterGTService,
    dynamicDialogConfig: DynamicDialogConfig,
    dynamicDialogRef: DynamicDialogRef,
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    securityaccountService: SecurityaccountService) {
    super(dynamicDialogConfig, dynamicDialogRef, HelpIds.HELP_PORTFOLIO_SECURITYACCOUNT, translateService, gps, messageToastService,
      securityaccountService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));
    this.callParam = this.dynamicDialogConfig.data.callParam;
    this.config = [
      DynamicFieldHelper.createFieldInputString('name', 'SECURITYACCOUNT_NAME', 25, true),
      DynamicFieldHelper.createFieldSelectStringHeqF('tradingPlatformPlan',
        true, {dataproperty: 'tradingPlatformPlan.idTradingPlatformPlan'}),
      DynamicFieldHelper.createFieldInputNumberHeqF('lowestTransactionCost', true,
        3, 2, false, {inputWidth: 10}),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('note', BaseSettings.FID_MAX_LETTERS, false),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  private tradingPlatformPlanCreateValueKeyHtmlSelectOptions(tradingPlatformPlans: TradingPlatformPlan[]): ValueKeyHtmlSelectOptions[] {
    const valueKeyHtmlSelectOptions: ValueKeyHtmlSelectOptions[] = [new ValueKeyHtmlSelectOptions('', '')];
    this.configObject.tradingPlatformPlan.referencedDataObject = tradingPlatformPlans;
    tradingPlatformPlans.forEach(tradingPlatformPlan => {
      this.translateService.get(tradingPlatformPlan.transactionFeePlan).subscribe(tp => {
        const valueKeyHtmlSelectOption = new ValueKeyHtmlSelectOptions(tradingPlatformPlan.idTradingPlatformPlan, null);
        valueKeyHtmlSelectOption.value = Helper.getValueByPath(tradingPlatformPlan, 'platformPlanNameNLS.map.'
          + this.gps.getUserLang()) + ' / ' + tp;
        const indexPos = AppHelper.binarySearch(valueKeyHtmlSelectOptions, valueKeyHtmlSelectOption.value, (option, value) =>
          option.value === value ? Comparison.EQ : option.value > value ? Comparison.GT : Comparison.LT);
        valueKeyHtmlSelectOptions.splice(Math.abs(indexPos), 0, valueKeyHtmlSelectOption);
      });
    });
    return valueKeyHtmlSelectOptions;
  }

  ngAfterViewInit(): void {
    this.tradingPlatformPlanService.getAllTradingPlatform().subscribe((tradingPlatformPlans: TradingPlatformPlan[]) => {
      this.configObject.tradingPlatformPlan.valueKeyHtmlOptions =
        this.tradingPlatformPlanCreateValueKeyHtmlSelectOptions(tradingPlatformPlans);
      DynamicFieldHelper.setCurrency(this.configObject.lowestTransactionCost,
        (<Portfolio>this.callParam.parentObject).currency);
      this.form.setDefaultValuesAndEnableSubmit();
      if (this.callParam.thisObject != null) {
        this.form.transferBusinessObjectToForm(this.callParam.thisObject);
        const secaccount = <Securityaccount>this.callParam.thisObject;
        this.tradingPeriods = [...(secaccount.tradingPeriods || [])];
        (<SecurityaccountService>this.serviceEntityUpdate).getTransactionSummaries(secaccount.idSecuritycashAccount)
          .subscribe(summaries => this.transactionSummaries = summaries);
      } else {
        this.tradingPeriods = this.getDefaultTradingPeriods();
      }
      setTimeout(() => {
        this.configObject.name.elementRef?.nativeElement?.focus();
      }, 50);
    });
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): Securityaccount {
    const securityaccount: Securityaccount = this.copyFormToPrivateBusinessObject(new Securityaccount(),
      <Securityaccount>this.callParam.thisObject);
    securityaccount.portfolio = <Portfolio>this.callParam.parentObject;
    securityaccount.tradingPeriods = this.tradingPeriodTable?.getData() || this.tradingPeriods;
    return securityaccount;
  }

  private getDefaultTradingPeriods(): SecaccountTradingPeriod[] {
    const defaultDateFrom = new Date((sessionStorage.getItem(GlobalSessionNames.OLDEST_TRADING_DAY) ?? BaseSettings.OLDEST_TRADING_DAY_FALLBACK) + 'T00:00:00');

    const p1 = new SecaccountTradingPeriod();
    p1.categoryType = AssetclassType[AssetclassType.EQUITIES];
    p1.specInvestInstrument = SpecialInvestmentInstruments[SpecialInvestmentInstruments.DIRECT_INVESTMENT];
    p1.dateFrom = defaultDateFrom;

    const p2 = new SecaccountTradingPeriod();
    p2.categoryType = AssetclassType[AssetclassType.EQUITIES];
    p2.specInvestInstrument = SpecialInvestmentInstruments[SpecialInvestmentInstruments.ETF];
    p2.dateFrom = defaultDateFrom;

    return [p1, p2];
  }
}
