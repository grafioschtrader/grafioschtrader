import {Component, Input, OnInit} from '@angular/core';

import {TransactionCallParam} from './transaction.call.parm';
import {SimpleEditBase} from '../../lib/edit/simple.edit.base';
import {HelpIds} from '../../lib/help/help.ids';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {FormDefinitionHelper} from '../../shared/edit/form.definition.helper';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';

@Component({
    selector: 'transaction-cashaccount-connect-debit-credit',
    template: `
    <p-dialog header="{{'CHANGE_TO_ACCOUNT_TRANSFER' | translate}}" [visible]="visibleDialog"
              [style]="{width: '450px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `,
    standalone: true,
    imports: [TranslateModule, DialogModule, DynamicFormModule]
})
export class TransactionCashaccountConnectDebitCreditComponent extends SimpleEditBase implements OnInit {

  @Input() transactionCallParam: TransactionCallParam;

  constructor(public translateService: TranslateService, gps: GlobalparameterService) {
    super(HelpIds.HELP_TRANSACTION_ACCOUNT, gps);
  }

  ngOnInit(): void {
    this.config = [
      FormDefinitionHelper.getTransactionTime()
    ];
  }

  initialize(): void {
  }

  submit(value: { [name: string]: any }): void {
  }

}
