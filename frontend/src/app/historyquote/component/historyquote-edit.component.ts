import {Component, Input} from '@angular/core';
import {Historyquote} from '../../entities/historyquote';
import {Securitycurrency} from '../../entities/securitycurrency';
import {HistoryquoteService} from '../service/historyquote.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {HistoryquoteSecurityCurrency} from './historyquote-table.component';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {HelpIds} from '../../lib/help/help.ids';
import {FormHelper} from '../../lib/dynamic-form/components/FormHelper';
import {AppSettings} from '../../shared/app.settings';
import {DialogModule} from 'primeng/dialog';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';
import {HistoryquoteEditBase} from './historyquote-edit.base';

/**
 * Edit historical quotes. A new row enables the trading date; an existing row keeps it read-only.
 */
@Component({
    selector: 'historyquote-edit',
    template: `
    <p-dialog header="{{'HISTORY_QUOTE_FOR' | translate}} {{callParam.showName}}" [visible]="visibleDialog"
              [style]="{width: '400px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`,
    standalone: true,
    imports: [DialogModule, DynamicFormModule, TranslateModule]
})
export class HistoryquoteEditComponent extends HistoryquoteEditBase<Historyquote> {

  @Input() callParam: HistoryquoteSecurityCurrency;

  constructor(translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              historyquoteService: HistoryquoteService) {
    super(HelpIds.HELP_WATCHLIST_HISTORYQUOTES, AppSettings.HISTORYQUOTE_P_KEY.toUpperCase(), translateService, gps,
      messageToastService, historyquoteService);
  }

  protected getSecuritycurrency(): Securitycurrency {
    return this.callParam.securitycurrency;
  }

  protected getExistingEntity(): Historyquote {
    return this.callParam.historyquote;
  }

  protected createEntityInstance(): Historyquote {
    return new Historyquote();
  }

  protected transferAndToggleImmutableFields(): void {
    if (this.callParam.historyquote) {
      this.form.transferBusinessObjectToForm(this.callParam.historyquote);
      FormHelper.disableEnableFieldConfigs(true, [this.configObject.date]);
    } else {
      this.form.setDefaultValuesAndEnableSubmit();
      FormHelper.disableEnableFieldConfigs(false, [this.configObject.date]);
    }
  }

}
