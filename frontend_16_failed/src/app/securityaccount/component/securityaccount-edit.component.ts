import {Component, Input, OnInit} from '@angular/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {AppHelper, Comparison} from '../../shared/helper/app.helper';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {Portfolio} from '../../entities/portfolio';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {SecurityaccountService} from '../service/securityaccount.service';
import {Securityaccount} from '../../entities/securityaccount';
import {HelpIds} from '../../shared/help/help.ids';
import {TradingPlatformPlan} from '../../entities/tradingplatformplan';
import {TradingPlatformPlanService} from '../../tradingplatform/service/trading.platform.plan.service';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {Helper} from '../../helper/helper';
import {SimpleEntityEditBase} from '../../shared/edit/simple.entity.edit.base';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {DataType} from '../../dynamic-form/models/data.type';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {AppSettings} from '../../shared/app.settings';

/**
 * Edit security account
 */
@Component({
  selector: 'securityaccount-edit',
  template: `
    <p-dialog header="{{i18nRecord | translate}}" [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '600px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`
})
export class SecurityaccountEditComponent extends SimpleEntityEditBase<Securityaccount> implements OnInit {

  @Input() callParam: CallParam;

  untilFields: UntilField[];

  constructor(private tradingPlatformPlanService: TradingPlatformPlanService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              securityaccountService: SecurityaccountService) {
    super(HelpIds.HELP_PORTFOLIO_SECURITYACCOUNT, AppSettings.SECURITYACCOUNT.toUpperCase(), translateService, gps,
      messageToastService, securityaccountService);

    this.untilFields = [new UntilField('shareUseUntil', true),
      new UntilField('bondUseUntil', true),
      new UntilField('etfUseUntil', true),
      new UntilField('fondUseUntil', false),
      new UntilField('forexUseUntil', false),
      new UntilField('cfdUseUntil', false)
    ];
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldInputString('name', 'SECURITYACCOUNT_NAME', 25, true),
      DynamicFieldHelper.createFieldSelectString('tradingPlatformPlan', AppSettings.TRADING_PLATFORM_PLAN.toUpperCase(),
        true, {dataproperty: 'tradingPlatformPlan.idTradingPlatformPlan'}),
      ...this.getUntilFieldDefinition(),
      DynamicFieldHelper.createFieldCurrencyNumberHeqF('lowestTransactionCost', true,
        3, 2, false,
        {
          ...this.gps.getNumberCurrencyMask(),
          prefix: AppHelper.addSpaceToCurrency((<Portfolio>this.callParam.parentObject).currency)
        }, true, {inputWidth: 10}),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('note', AppSettings.FID_MAX_LETTERS, false),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  tradingPlatformPlanCreateValueKeyHtmlSelectOptions(tradingPlatformPlans: TradingPlatformPlan[]): ValueKeyHtmlSelectOptions[] {
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

  protected override initialize(): void {
    this.tradingPlatformPlanService.getAllTradingPlatform().subscribe((tradingPlatformPlans: TradingPlatformPlan[]) => {
      this.configObject.tradingPlatformPlan.valueKeyHtmlOptions =
        this.tradingPlatformPlanCreateValueKeyHtmlSelectOptions(tradingPlatformPlans);
      this.form.setDefaultValuesAndEnableSubmit();
      if (this.callParam.thisObject != null) {
        this.form.transferBusinessObjectToForm(this.callParam.thisObject);
      } else {
        this.untilFields.forEach(uf => uf.active && this.configObject[uf.fieldName].formControl.setValue(new Date('2099-12-31')));
      }
      this.configObject.name.elementRef.nativeElement.focus();
    });
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): Securityaccount {
    const securityaccount: Securityaccount = this.copyFormToPrivateBusinessObject(new Securityaccount(),
      <Securityaccount>this.callParam.thisObject);
    securityaccount.portfolio = <Portfolio>this.callParam.parentObject;
    return securityaccount;
  }

  private getUntilFieldDefinition(): FieldConfig[] {
    const fieldConfigs: FieldConfig[] = [];
    this.untilFields.forEach(uf => fieldConfigs.push(DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString,
      uf.fieldName, false)));
    return fieldConfigs;
  }
}

class UntilField {
  constructor(public fieldName: string, public active: boolean) {
  }
}
