import {Component, Input, OnInit} from '@angular/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {TranslateService} from '@ngx-translate/core';
import {TradingPlatformPlanService} from '../service/trading.platform.plan.service';
import {AppHelper} from '../../shared/helper/app.helper';
import {TradingPlatformPlan} from '../../entities/tradingplatformplan';
import {HelpIds} from '../../shared/help/help.ids';
import {TradingPlatformFeePlan} from '../../shared/types/trading.platform.fee.plan';
import {SimpleEntityEditBase} from '../../shared/edit/simple.entity.edit.base';
import {ImportTransactionPlatform} from '../../entities/import.transaction.platform';
import {ImportTransactionPlatformService} from '../../imptranstemplate/service/import.transaction.platform.service';
import {AuditHelper} from '../../shared/helper/audit.helper';
import {ProposeChangeEntityWithEntity} from '../../entities/proposechange/propose.change.entity.whit.entity';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {AppSettings} from '../../shared/app.settings';

@Component({
  selector: 'trading-platform-plan-edit',
  template: `
    <p-dialog header="{{'TRADINGPLATFORMPLAN' | translate}}" [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '600px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`
})
export class TradingPlatformPlanEditComponent extends SimpleEntityEditBase<TradingPlatformPlan> implements OnInit {
  @Input() callParam: TradingPlatformPlan;
  @Input() proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity;

  importTransactionPlatformList: ImportTransactionPlatform[];

  constructor(private importTransactionPlatformService: ImportTransactionPlatformService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              tradingPlatformPlanService: TradingPlatformPlanService) {
    super(HelpIds.HELP_BASEDATA_TRADING_PLATFORM_PLAN, AppSettings.TRADING_PLATFORM_PLAN.toUpperCase(), translateService, gps,
      messageToastService, tradingPlatformPlanService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldInputString('en', 'PLATFORM_PLAN_NAME', 64, true,
        {labelSuffix: 'EN', dataproperty: 'platformPlanNameNLS.map.en'}),
      DynamicFieldHelper.createFieldInputString('de', 'PLATFORM_PLAN_NAME', 64, true,
        {labelSuffix: 'DE', dataproperty: 'platformPlanNameNLS.map.de'}),
      DynamicFieldHelper.createFieldSelectString('transactionFeePlan', 'TRANSACTION_FEE_PLAN', true),
      DynamicFieldHelper.createFieldSelectString('idTransactionImportPlatform', 'IMPORTTRANSACTIONGROUP', true,
        {dataproperty: 'importTransactionPlatform.idTransactionImportPlatform'}),
      ...AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this)
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.importTransactionPlatformService.getAllImportTransactionPlatforms().subscribe(
      (importTransactionPlatforms: ImportTransactionPlatform[]) => {
        this.importTransactionPlatformList = importTransactionPlatforms;
        this.configObject.idTransactionImportPlatform.valueKeyHtmlOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray(
          'idTransactionImportPlatform', 'name', importTransactionPlatforms, true);
        this.form.setDefaultValuesAndEnableSubmit();
        this.configObject.transactionFeePlan.valueKeyHtmlOptions =
          SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService, TradingPlatformFeePlan);
        AuditHelper.transferToFormAndChangeButtonForProposaleEdit(this.translateService, this.gps, this.callParam,
          this.form, this.configObject, this.proposeChangeEntityWithEntity);

        this.configObject.en.elementRef.nativeElement.focus();
      });
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): TradingPlatformPlan {
    const tradingPlatformPlan = new TradingPlatformPlan();
    this.copyFormToPublicBusinessObject(tradingPlatformPlan, this.callParam, this.proposeChangeEntityWithEntity);
    /*
        if (this.callParam) {
          Object.assign(tradingPlatformPlan, this.callParam);
        }
    */
    this.form.cleanMaskAndTransferValuesToBusinessObject(tradingPlatformPlan);
    tradingPlatformPlan.importTransactionPlatform = this.importTransactionPlatformList.find(
      importTransactionPlatform => importTransactionPlatform.idTransactionImportPlatform === +value.idTransactionImportPlatform);
    const values: any = {};
    this.form.cleanMaskAndTransferValuesToBusinessObject(values, true);
    tradingPlatformPlan.platformPlanNameNLS.map.de = values.de;
    tradingPlatformPlan.platformPlanNameNLS.map.en = values.en;
    return tradingPlatformPlan;
  }

}
