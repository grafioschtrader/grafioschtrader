import { Component, OnInit, input, inject } from '@angular/core';

import { TransactionCallParam } from './transaction.call.parm';
import { SimpleEditBase } from '../../lib/edit/simple.edit.base';
import { HelpIds } from '../../lib/help/help.ids';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { FormDefinitionHelper } from '../../shared/edit/form.definition.helper';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';

@Component({
  selector: 'transaction-cashaccount-connect-debit-credit',
  template: `
    <p-dialog
      header="{{ 'CHANGE_TO_ACCOUNT_TRANSFER' | translate }}"
      [visible]="visibleDialog()"
      [style]="{ width: '450px' }"
      (onShow)="onShow($event)"
      (onHide)="onHide($event)"
      [modal]="true">
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm"
        (submitBt)="submit($event)" />
    </p-dialog>
  `,
  imports: [TranslateModule, DialogModule, ...DYNAMIC_FORM_IMPORTS]
})
export class TransactionCashaccountConnectDebitCreditDialogComponent extends SimpleEditBase implements OnInit {
  public translateService: TranslateService = inject(TranslateService);

  readonly transactionCallParam = input<TransactionCallParam>(undefined);

  constructor() {
    super(HelpIds.HELP_TRANSACTION_ACCOUNT);
  }

  ngOnInit(): void {
    this.config = [FormDefinitionHelper.getTransactionTime()];
  }

  initialize(): void {}

  submit(value: { [name: string]: any }): void {}
}
