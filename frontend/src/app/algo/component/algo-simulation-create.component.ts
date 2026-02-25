import {AfterViewInit, Component, OnInit} from '@angular/core';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {SimpleDynamicEditBase} from '../../lib/edit/simple.dynamic.edit.base';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {TenantService} from '../../tenant/service/tenant.service';
import {AlgoTop} from '../model/algo.top';
import {Tenant} from '../../entities/tenant';
import {Cashaccount} from '../../entities/cashaccount';
import {SimulationTenantCreateDTO} from '../model/simulation.tenant';
import {AppHelper} from '../../lib/helper/app.helper';
import {AppSettings} from '../../shared/app.settings';
import {BaseSettings} from '../../lib/base.settings';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {HelpIds} from '../../lib/help/help.ids';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {ProcessedAction} from '../../lib/types/processed.action';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';

/**
 * Dialog for creating a simulation tenant from an AlgoTop strategy.
 * Shows strategy name, copy transactions checkbox, and editable cash balance fields per cash account.
 * Cash accounts are pre-loaded and passed via callParam.parentObject.cashAccounts.
 */
@Component({
  template: `
    <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                  #form="dynamicForm" (submitBt)="submit($event)">
    </dynamic-form>
  `,
  standalone: true,
  imports: [
    DynamicFormModule,
    TranslateModule
  ]
})
export class AlgoSimulationCreateDynamicComponent extends SimpleDynamicEditBase<Tenant> implements OnInit, AfterViewInit {
  callParam: CallParam;
  private algoTop: AlgoTop;
  private cashAccounts: Cashaccount[] = [];

  constructor(
    private tenantService: TenantService,
    dynamicDialogConfig: DynamicDialogConfig,
    dynamicDialogRef: DynamicDialogRef,
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService
  ) {
    super(dynamicDialogConfig, dynamicDialogRef, HelpIds.HELP_ALGO_STRATEGY, translateService, gps,
      messageToastService, tenantService);
  }

  ngOnInit(): void {
    this.callParam = this.dynamicDialogConfig.data.callParam;
    this.algoTop = this.callParam.thisObject as AlgoTop;
    this.cashAccounts = (this.callParam.parentObject as any)?.cashAccounts || [];

    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldInputStringHeqF('name', 40, true),
      DynamicFieldHelper.createFieldCheckboxHeqF('copyTransactions'),
    ];

    for (const ca of this.cashAccounts) {
      this.config.push(
        DynamicFieldHelper.createFieldInputNumber(
          `cashBalance_${ca.idSecuritycashAccount}`,
          `*${ca.name} (${ca.currency})`,
          false,
          AppSettings.FID_STANDARD_INTEGER_DIGITS,
          BaseSettings.FID_STANDARD_FRACTION_DIGITS,
          false
        )
      );
    }

    this.config.push(DynamicFieldHelper.createSubmitButton());
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  ngAfterViewInit(): void {
    setTimeout(() => {
      this.form.setValue('name', this.algoTop.name);

      // Enable copy transactions only if referenceDate exists
      const hasRefDate = this.algoTop.referenceDate != null;
      if (!hasRefDate) {
        this.form.setValue('copyTransactions', false);
        this.configObject.copyTransactions.disabled = true;
      }

      // Listen for checkbox changes to toggle cash balance field visibility
      this.form.form?.get('copyTransactions')?.valueChanges.subscribe(checked => {
        this.toggleCashBalanceFields(!checked);
      });

      // Initial state: if no ref date or unchecked, show cash balance fields
      this.toggleCashBalanceFields(!this.form.form?.get('copyTransactions')?.value);
    });
  }

  override submit(value: { [name: string]: any }): void {
    const copyTransactions = value.copyTransactions === true;

    const dto: SimulationTenantCreateDTO = {
      idAlgoTop: this.algoTop.idAlgoAssetclassSecurity,
      tenantName: value.name,
      copyTransactions: copyTransactions
    };

    if (!copyTransactions) {
      dto.cashBalances = {};
      for (const ca of this.cashAccounts) {
        const amount = value[`cashBalance_${ca.idSecuritycashAccount}`] || 0;
        if (amount > 0) {
          dto.cashBalances[ca.idSecuritycashAccount] = amount;
        }
      }
    }

    this.tenantService.createSimulationTenant(dto).subscribe({
      next: returnEntity => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'SIMULATION_CREATED');
        this.dynamicDialogRef.close(new ProcessedActionData(ProcessedAction.CREATED, returnEntity));
      },
      error: () => this.configObject.submit.disabled = false
    });
  }

  protected getNewOrExistingInstanceBeforeSave(value: { [p: string]: any }): Tenant {
    return undefined;
  }

  private toggleCashBalanceFields(enabled: boolean): void {
    for (const ca of this.cashAccounts) {
      const fieldName = `cashBalance_${ca.idSecuritycashAccount}`;
      if (this.configObject[fieldName]) {
        this.configObject[fieldName].disabled = !enabled;
      }
    }
  }
}
