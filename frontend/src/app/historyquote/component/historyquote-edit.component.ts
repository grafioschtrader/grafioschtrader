import { Component, input, inject } from '@angular/core';
import { Historyquote } from '../../entities/historyquote';
import { Securitycurrency } from '../../entities/securitycurrency';
import { HistoryquoteService } from '../service/historyquote.service';
import { TranslateModule } from '@ngx-translate/core';
import { HistoryquoteSecurityCurrency } from '../model/historyquote.security.currency';
import { HelpIds } from '../../lib/help/help.ids';
import { FormHelper } from '../../lib/dynamic-form/components/FormHelper';
import { AppSettings } from '../../shared/app.settings';
import { AppHelper } from '../../lib/helper/app.helper';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { HistoryquoteEditBase } from './historyquote-edit.base';

/**
 * Edit historical quotes. A new row enables the trading date; an existing row keeps it read-only.
 */
@Component({
  selector: 'historyquote-edit',
  template: ` <p-dialog
    header="{{ 'HISTORY_QUOTE_FOR' | translate }} {{ callParam().showName }}"
    [visible]="visibleDialog()"
    [style]="{ width: '400px' }"
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
export class HistoryquoteEditComponent extends HistoryquoteEditBase<Historyquote> {
  readonly callParam = input<HistoryquoteSecurityCurrency>(undefined);

  constructor() {
    super(
      HelpIds.HELP_WATCHLIST_HISTORYQUOTES,
      AppHelper.toUpperCaseWithUnderscore(AppSettings.HISTORYQUOTE_P_KEY),
      inject(HistoryquoteService)
    );
  }

  protected getSecuritycurrency(): Securitycurrency {
    return this.callParam().securitycurrency;
  }

  protected getExistingEntity(): Historyquote {
    return this.callParam().historyquote;
  }

  protected createEntityInstance(): Historyquote {
    return new Historyquote();
  }

  protected transferAndToggleImmutableFields(): void {
    const callParam = this.callParam();
    if (callParam.historyquote) {
      this.form().transferBusinessObjectToForm(callParam.historyquote);
      FormHelper.disableEnableFieldConfigs(true, [this.configObject.date]);
    } else {
      this.form().setDefaultValuesAndEnableSubmit();
      FormHelper.disableEnableFieldConfigs(false, [this.configObject.date]);
    }
  }
}
