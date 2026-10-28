import { afterNextRender, Component, OnInit, ViewChild, signal } from '@angular/core';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { TranslateService } from '@ngx-translate/core';
import { AppHelper } from '../../lib/helper/app.helper';
import { map, Observable, of, switchMap } from 'rxjs';
import { CallParam } from '../../shared/maintree/types/dialog.visible';
import { Portfolio } from '../../entities/portfolio';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { SecurityaccountService } from '../service/securityaccount.service';
import { Securityaccount } from '../../entities/securityaccount';
import { HelpIds } from '../../lib/help/help.ids';
import { TradingPlatformPlan } from '../../entities/tradingplatformplan';
import { TradingPlatformPlanService } from '../../tradingplatform/service/trading.platform.plan.service';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { Helper } from '../../lib/helper/helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { SimpleDynamicEditBase } from '../../lib/edit/simple.dynamic.edit.base';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';
import { DynamicDialogConfig, DynamicDialogRef } from '@openng/optimus-ui/dynamicdialog';
import { BaseSettings } from '../../lib/base.settings';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { SecaccountTradingPeriod } from '../../entities/secaccount.trading.period';
import { TradingPeriodTransactionSummary } from '../../entities/trading.period.transaction.summary';
import { AssetclassType } from '../../shared/types/assetclass.type';
import { SpecialInvestmentInstruments } from '../../shared/types/special.investment.instruments';
import { TradingPeriodTableComponent } from './trading-period-table.component';
import { GlobalSessionNames } from '../../lib/global.session.names';

/**
 * Edit security account with trading period table for defining which instrument types can be traded.
 */
@Component({
  selector: 'securityaccount-edit',
  template: `
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)" />
    <trading-period-table
      #tradingPeriodTable
      [tradingPeriods]="tradingPeriods()"
      [transactionSummaries]="transactionSummaries()"
      [isNewSecurityAccount]="callParam.thisObject == null" />
  `,
  imports: [DynamicFormComponent, TradingPeriodTableComponent]
})
export class SecurityaccountEditDynamicComponent extends SimpleDynamicEditBase<Securityaccount> implements OnInit {
  static readonly DIALOG_WIDTH = 850;
  callParam: CallParam;

  @ViewChild('tradingPeriodTable')
  tradingPeriodTable: TradingPeriodTableComponent;

  readonly tradingPeriods = signal<SecaccountTradingPeriod[]>([]);
  readonly transactionSummaries = signal<TradingPeriodTransactionSummary[]>([]);

  constructor(
    private tradingPlatformPlanService: TradingPlatformPlanService,
    private gpsGT: GlobalparameterGTService,
    dynamicDialogConfig: DynamicDialogConfig,
    dynamicDialogRef: DynamicDialogRef,
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    securityaccountService: SecurityaccountService
  ) {
    super(
      dynamicDialogConfig,
      dynamicDialogRef,
      HelpIds.HELP_PORTFOLIO_SECURITYACCOUNT,
      translateService,
      gps,
      messageToastService,
      securityaccountService
    );
    afterNextRender(() => this.initializeForm());
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    this.callParam = this.dynamicDialogConfig.data.callParam;
    this.config = [
      DynamicFieldHelper.createFieldInputString('name', 'SECURITYACCOUNT_NAME', 25, true),
      DynamicFieldHelper.createFieldSelectStringHeqF('tradingPlatformPlan', true, {
        dataproperty: 'tradingPlatformPlan.idTradingPlatformPlan'
      }),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateNumeric, 'activeToDate', false),
      // The exemption feeds the tax estimate of the simulation (rule-based trading) and of the disposal costs
      ...(this.gps.useAlgo() || this.gpsGT.useDisposalCostEstimate()
        ? [DynamicFieldHelper.createFieldTriStateCheckboxHeqF('taxExemptInvestor')]
        : []),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('note', BaseSettings.FID_MAX_LETTERS, false),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  /** Publishes a complete translated list so no asynchronous callback mutates options already shown by the form. */
  private tradingPlatformPlanCreateValueKeyHtmlSelectOptions(
    tradingPlatformPlans: TradingPlatformPlan[]
  ): Observable<ValueKeyHtmlSelectOptions[]> {
    this.configObject.tradingPlatformPlan.referencedDataObject = tradingPlatformPlans;
    if (!tradingPlatformPlans.length) {
      return of([new ValueKeyHtmlSelectOptions('', '')]);
    }
    return this.translateService
      .get(tradingPlatformPlans.map((plan) => plan.transactionFeePlan))
      .pipe(
        map((translations) => [
          new ValueKeyHtmlSelectOptions('', ''),
          ...tradingPlatformPlans
            .map(
              (plan) =>
                new ValueKeyHtmlSelectOptions(
                  plan.idTradingPlatformPlan,
                  Helper.getValueByPath(plan, 'platformPlanNameNLS.map.' + this.gps.getUserLang()) +
                    ' / ' +
                    translations[plan.transactionFeePlan]
                )
            )
            .sort((a, b) => a.value.localeCompare(b.value))
        ])
      );
  }

  /** Loads options and transaction constraints independently after the dynamic form has rendered. */
  private initializeForm(): void {
    this.form.setDefaultValuesAndEnableSubmit();
    if (this.callParam.thisObject != null) {
      this.form.transferBusinessObjectToForm(this.callParam.thisObject);
      const secaccount = <Securityaccount>this.callParam.thisObject;
      this.tradingPeriods.set([...(secaccount.tradingPeriods || [])]);
      (<SecurityaccountService>this.serviceEntityUpdate)
        .getTransactionSummaries(secaccount.idSecuritycashAccount)
        .pipe(this.untilDialogClosed())
        .subscribe((summaries) => this.transactionSummaries.set(summaries));
    } else {
      this.tradingPeriods.set(this.getDefaultTradingPeriods());
    }
    this.configObject.name.elementRef?.nativeElement?.focus();
    this.tradingPlatformPlanService
      .getAllTradingPlatform()
      .pipe(
        switchMap((plans) => this.tradingPlatformPlanCreateValueKeyHtmlSelectOptions(plans)),
        this.untilDialogClosed()
      )
      .subscribe((options) => (this.configObject.tradingPlatformPlan.valueKeyHtmlOptions = options));
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): Securityaccount {
    const securityaccount: Securityaccount = this.copyFormToPrivateBusinessObject(
      new Securityaccount(),
      <Securityaccount>this.callParam.thisObject
    );
    securityaccount.portfolio = <Portfolio>this.callParam.parentObject;
    securityaccount.tradingPeriods = this.tradingPeriodTable?.getData() || this.tradingPeriods();
    return securityaccount;
  }

  private getDefaultTradingPeriods(): SecaccountTradingPeriod[] {
    const defaultDateFrom = new Date(
      (sessionStorage.getItem(GlobalSessionNames.OLDEST_TRADING_DAY) ?? BaseSettings.OLDEST_TRADING_DAY_FALLBACK) +
        'T00:00:00'
    );

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
