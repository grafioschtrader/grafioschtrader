import { Component, OnInit, input, inject } from '@angular/core';
import { forkJoin } from 'rxjs';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';
import { TranslateModule } from '@ngx-translate/core';
import { TradingPlatformPlanService } from '../service/trading.platform.plan.service';
import { AppHelper } from '../../lib/helper/app.helper';
import { TradingPlatformPlan } from '../../entities/tradingplatformplan';
import { HelpIds } from '../../lib/help/help.ids';
import { TradingPlatformFeePlan } from '../../shared/types/trading.platform.fee.plan';
import { SimpleEntityEditBase } from '../../lib/edit/simple.entity.edit.base';
import { ImportTransactionPlatform } from '../../entities/import.transaction.platform';
import { ImportTransactionPlatformService } from '../../imptranstemplate/service/import.transaction.platform.service';
import { AuditHelper } from '../../lib/helper/audit.helper';
import { ProposeChangeEntityWithEntity } from '../../lib/proposechange/model/propose.change.entity.whit.entity';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { AppSettings } from '../../shared/app.settings';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';

@Component({
  selector: 'trading-platform-plan-edit',
  template: ` <p-dialog
    header="{{ 'TRADING_PLATFORM_PLAN' | translate }}"
    [visible]="visibleDialog()"
    [style]="{ width: '900px' }"
    (onShow)="onShow($event)"
    (onHide)="onHide($event)"
    [modal]="true">
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)" />
  </p-dialog>`,
  imports: [DialogModule, ...DYNAMIC_FORM_IMPORTS, TranslateModule]
})
export class TradingPlatformPlanEditComponent extends SimpleEntityEditBase<TradingPlatformPlan> implements OnInit {
  private importTransactionPlatformService: ImportTransactionPlatformService = inject(ImportTransactionPlatformService);
  private tradingPlatformPlanService: TradingPlatformPlanService = inject(TradingPlatformPlanService);
  private gpsGT: GlobalparameterGTService = inject(GlobalparameterGTService);

  readonly callParam = input<TradingPlatformPlan>(undefined);
  readonly proposeChangeEntityWithEntity = input<ProposeChangeEntityWithEntity>(undefined);

  private importTransactionPlatformList: ImportTransactionPlatform[] = [];

  constructor() {
    super(
      HelpIds.HELP_BASEDATA_TRADING_PLATFORM_PLAN,
      AppHelper.toUpperCaseWithUnderscore(AppSettings.TRADING_PLATFORM_PLAN),
      inject(TradingPlatformPlanService)
    );
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldInputString('en', 'PLATFORM_PLAN_NAME', 64, true, {
        labelSuffix: 'EN',
        dataproperty: 'platformPlanNameNLS.map.en'
      }),
      DynamicFieldHelper.createFieldInputString('de', 'PLATFORM_PLAN_NAME', 64, true, {
        labelSuffix: 'DE',
        dataproperty: 'platformPlanNameNLS.map.de'
      }),
      DynamicFieldHelper.createFieldSelectString('transactionFeePlan', 'TRANSACTION_FEE_PLAN', true),
      DynamicFieldHelper.createFieldSelectString('idTransactionImportPlatform', 'IMPORT_TRANSACTION_PLATFORM', false, {
        dataproperty: 'importTransactionPlatform.idTransactionImportPlatform'
      }),
      // The dealer country feeds the tax estimate (Swiss stamp duty) of the simulation and of the disposal costs. A plan
      // is shared by all tenants, so only the instance switch counts here, not the one of the current tenant.
      ...(this.gps.useAlgo() || this.gpsGT.isDisposalCostEstimateAvailable()
        ? [DynamicFieldHelper.createFieldDropdownString('countryCode', 'DEALER_COUNTRY', false, { filter: true })]
        : []),
      ...AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this)
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.form().setDefaultValuesAndEnableSubmit();
    AuditHelper.transferToFormAndChangeButtonForProposaleEdit(
      this.translateService,
      this.gps,
      this.callParam(),
      this.form(),
      this.configObject,
      this.proposeChangeEntityWithEntity()
    );
    // Options may arrive after the user starts typing. Only the options and submit state change in the callback.
    this.configObject.submit.disabled = true;
    this.configObject.en.baseInputComponent?.focus();
    const feeKeys = Object.values(TradingPlatformFeePlan).filter((value): value is string => typeof value === 'string');
    forkJoin([
      this.importTransactionPlatformService.getAllImportTransactionPlatforms(),
      this.gps.getCountriesForSelectBox(),
      this.translateService.get(feeKeys)
    ])
      .pipe(this.untilDialogClosed())
      .subscribe(([importTransactionPlatforms, countries, texts]) => {
        this.importTransactionPlatformList = importTransactionPlatforms;
        if (this.configObject.countryCode) {
          this.configObject.countryCode.groupItem = SelectOptionsHelper.createGroupItemsFromValueKeyHtmlSelectOptions(
            countries,
            true
          );
        }
        this.configObject.idTransactionImportPlatform.valueKeyHtmlOptions =
          SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray(
            'idTransactionImportPlatform',
            'name',
            importTransactionPlatforms,
            true
          );
        this.configObject.transactionFeePlan.valueKeyHtmlOptions = feeKeys
          .map((key) => new ValueKeyHtmlSelectOptions(key, texts[key]))
          .sort((a, b) => a.value.localeCompare(b.value));
        this.configObject.submit.disabled = false;
      });
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): TradingPlatformPlan {
    const tradingPlatformPlan = new TradingPlatformPlan();
    this.copyFormToPublicBusinessObject(tradingPlatformPlan, this.callParam(), this.proposeChangeEntityWithEntity());
    this.form().cleanMaskAndTransferValuesToBusinessObject(tradingPlatformPlan);
    if (tradingPlatformPlan.countryCode === '') {
      tradingPlatformPlan.countryCode = null;
    }
    tradingPlatformPlan.importTransactionPlatform = this.importTransactionPlatformList.find(
      (importTransactionPlatform) =>
        importTransactionPlatform.idTransactionImportPlatform === +value.idTransactionImportPlatform
    );
    const values: any = {};
    this.form().cleanMaskAndTransferValuesToBusinessObject(values, true);
    tradingPlatformPlan.platformPlanNameNLS.map.de = values.de;
    tradingPlatformPlan.platformPlanNameNLS.map.en = values.en;
    return tradingPlatformPlan;
  }
}
