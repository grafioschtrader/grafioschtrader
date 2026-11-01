import { afterNextRender, Component, OnInit, signal, viewChild, inject } from '@angular/core';
import { AppHelper } from '../../lib/helper/app.helper';
import { map, Observable, of, switchMap } from 'rxjs';
import { CallParam } from '../../shared/maintree/types/dialog.visible';
import { Portfolio } from '../../entities/portfolio';
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
import { BaseSettings } from '../../lib/base.settings';
import { SignalDynamicFormComponent } from '../../lib/dynamic-form/signal/signal-dynamic-form.component';
import { metadata, SchemaPathTree } from '@angular/forms/signals';
import { FIELD_DISPLAY } from '../../lib/dynamic-form/signal/signal-form.model';

import { SecaccountTradingPeriod } from '../../entities/secaccount.trading.period';
import type { TradingPeriodTransactionSummary } from '../../entities/trading.period.transaction.summary';
import { AssetclassType } from '../../shared/types/assetclass.type';
import { SpecialInvestmentInstruments } from '../../shared/types/special.investment.instruments';
import { TradingPeriodTableComponent } from './trading-period-table.component';
import { GlobalSessionNames } from '../../lib/global.session.names';

interface SecurityaccountFormModel {
  tradingPlatformPlan: number | string | null;
}

/**
 * Edit security account with trading period table for defining which instrument types can be traded.
 */
@Component({
  selector: 'securityaccount-edit',
  template: `
    <signal-dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [source]="callParam.thisObject"
      [rules]="rules"
      #form="signalDynamicForm"
      (submitBt)="submit($event)" />
    <trading-period-table
      #tradingPeriodTable
      [tradingPeriods]="tradingPeriods()"
      [transactionSummaries]="transactionSummaries()"
      [isNewSecurityAccount]="callParam.thisObject == null" />
  `,
  imports: [SignalDynamicFormComponent, TradingPeriodTableComponent]
})
export class SecurityaccountEditDynamicComponent
  extends SimpleDynamicEditBase<Securityaccount, SignalDynamicFormComponent<SecurityaccountFormModel>>
  implements OnInit
{
  private tradingPlatformPlanService: TradingPlatformPlanService = inject(TradingPlatformPlanService);
  private gpsGT: GlobalparameterGTService = inject(GlobalparameterGTService);

  private readonly planOptions = signal<ValueKeyHtmlSelectOptions[]>([]);
  readonly rules = (p: SchemaPathTree<SecurityaccountFormModel>): void => {
    metadata(p.tradingPlatformPlan, FIELD_DISPLAY, () => ({ valueKeyHtmlOptions: this.planOptions() }));
  };

  static readonly DIALOG_WIDTH = 850;
  readonly callParam: CallParam = this.dynamicDialogConfig.data.callParam;

  readonly tradingPeriodTable = viewChild<TradingPeriodTableComponent>('tradingPeriodTable');

  readonly tradingPeriods = signal<SecaccountTradingPeriod[]>([]);
  readonly transactionSummaries = signal<TradingPeriodTransactionSummary[]>([]);

  constructor() {
    super(HelpIds.HELP_PORTFOLIO_SECURITYACCOUNT, inject(SecurityaccountService));
    afterNextRender(() => this.initializeForm());
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
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
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config, false);
  }

  /** Publishes a complete translated list so no asynchronous callback mutates options already shown by the form. */
  private tradingPlatformPlanCreateValueKeyHtmlSelectOptions(
    tradingPlatformPlans: TradingPlatformPlan[]
  ): Observable<ValueKeyHtmlSelectOptions[]> {
    const selectablePlans = tradingPlatformPlans.filter(
      (plan): plan is TradingPlatformPlan & { idTradingPlatformPlan: number; transactionFeePlan: string } =>
        plan.idTradingPlatformPlan != null && plan.transactionFeePlan != null
    );
    this.configObject.tradingPlatformPlan.referencedDataObject = tradingPlatformPlans;
    if (!selectablePlans.length) {
      return of([new ValueKeyHtmlSelectOptions('', '')]);
    }
    return this.translateService
      .get(selectablePlans.map((plan) => plan.transactionFeePlan))
      .pipe(
        map((translations) => [
          new ValueKeyHtmlSelectOptions('', ''),
          ...selectablePlans
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
    if (this.callParam.thisObject != null) {
      const secaccount = <Securityaccount>this.callParam.thisObject;
      const id = secaccount.idSecuritycashAccount;
      if (id == null) throw new Error('Existing security account requires its persisted id');
      this.tradingPeriods.set([...(secaccount.tradingPeriods || [])]);
      (<SecurityaccountService>this.serviceEntityUpdate)
        .getTransactionSummaries(id)
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
      .subscribe((options) => this.planOptions.set(options));
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): Securityaccount {
    const securityaccount: Securityaccount = this.copyFormToPrivateBusinessObject(
      new Securityaccount(),
      <Securityaccount>this.callParam.thisObject
    );
    securityaccount.portfolio = <Portfolio>this.callParam.parentObject;
    securityaccount.tradingPeriods = this.tradingPeriodTable()?.getData() || this.tradingPeriods();
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
