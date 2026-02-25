import {Component, OnInit} from '@angular/core';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {AppHelper} from '../../lib/helper/app.helper';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {StandingOrderCashaccount} from '../../entities/standing.order';
import {Cashaccount} from '../../entities/cashaccount';
import {StandingOrderService} from '../service/standing.order.service';
import {PortfolioService} from '../../portfolio/service/portfolio.service';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';
import {AppSettings} from '../../shared/app.settings';
import {TransactionType} from '../../shared/types/transaction.type';
import {StandingOrderEditBase} from './standing-order-edit-base';
import {AppHelpIds} from '../../shared/help/help.ids';

/**
 * Dialog component for creating and editing cashaccount standing orders (WITHDRAWAL/DEPOSIT).
 */
@Component({
  selector: 'standing-order-cashaccount-edit',
  template: `
    <p-dialog header="{{'STANDING_ORDER_CASHACCOUNT' | translate}}" [visible]="visibleDialog"
              [style]="{width: '550px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm" (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `,
  standalone: true,
  imports: [DialogModule, DynamicFormModule, TranslateModule]
})
export class StandingOrderCashaccountEditComponent extends StandingOrderEditBase implements OnInit {

  constructor(
    portfolioService: PortfolioService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    standingOrderService: StandingOrderService
  ) {
    super(portfolioService, AppHelpIds.HELP_STANDING_ORDER_CASH, translateService, gps, messageToastService, standingOrderService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    const fs = StandingOrderEditBase.FS_TRANSACTION;
    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF('transactionType', true, {fieldsetName: fs}),
      DynamicFieldHelper.createFieldSelectNumber('idCashaccount', AppSettings.CASHACCOUNT.toUpperCase(), true,
        {dataproperty: 'cashaccount.idSecuritycashAccount', fieldsetName: fs}),
      DynamicFieldHelper.createFieldInputNumberHeqF('cashaccountAmount', true,
        AppSettings.FID_STANDARD_INTEGER_DIGITS, AppSettings.FID_MAX_DIGITS - AppSettings.FID_STANDARD_INTEGER_DIGITS, false,
        {fieldsetName: fs}),
      DynamicFieldHelper.createFieldInputNumberHeqF('transactionCost', false,
        AppSettings.FID_STANDARD_INTEGER_DIGITS, AppSettings.FID_MAX_DIGITS - AppSettings.FID_STANDARD_INTEGER_DIGITS, false,
        {fieldsetName: fs}),
      ...StandingOrderEditBase.createSchedulingFields()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initializeTransactionTypeOptions(): void {
    this.configObject.transactionType.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
      this.translateService, TransactionType, [TransactionType.WITHDRAWAL, TransactionType.DEPOSIT]);
  }

  protected override setExistingValues(): void {
    const so = this.callParam?.standingOrder as StandingOrderCashaccount;
    if (so) {
      this.form.transferBusinessObjectToForm(so);
      this.disableTransactionFieldsIfExecuted(so);
    } else if (this.callParam?.transaction) {
      const t = this.callParam.transaction;
      this.configObject.transactionType.formControl.setValue(t.transactionType);
      if (t.cashaccount) {
        this.configObject.idCashaccount.formControl.setValue(t.cashaccount.idSecuritycashAccount);
      }
      if (t.cashaccountAmount != null) {
        this.configObject.cashaccountAmount.formControl.setValue(Math.abs(t.cashaccountAmount));
      }
    }
  }

  protected override getNewOrExistingInstanceBeforeSave(value: {[name: string]: any}): StandingOrderCashaccount {
    const so = new StandingOrderCashaccount();
    const existing = this.callParam?.standingOrder as StandingOrderCashaccount;
    this.copyFormToPrivateBusinessObject(so, existing);
    so.dtype = 'C';
    so.cashaccount = {idSecuritycashAccount: +this.configObject.idCashaccount.formControl.value} as Cashaccount;
    return so;
  }
}
