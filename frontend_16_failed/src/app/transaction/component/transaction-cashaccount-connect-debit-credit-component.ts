import {Component, Input, OnInit} from '@angular/core';
import {TransactionCallParam} from './transaction.call.parm';
import {SimpleEditBase} from '../../shared/edit/simple.edit.base';
import {HelpIds} from '../../shared/help/help.ids';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {FormDefinitionHelper} from '../../shared/edit/form.definition.helper';

@Component({
  selector: 'transaction-cashaccount-connect-debit-credit',
  template: `
    <p-dialog header="{{'CHANGE_TO_ACCOUNT_TRANSFER' | translate}}" [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '450px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `
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
