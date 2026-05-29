import {Component, Input} from '@angular/core';
import {DialogModule} from 'primeng/dialog';
import {TranslateModule, TranslateService} from '@ngx-translate/core';

import {HistoryquoteLegacy} from '../../entities/historyquote.legacy';
import {Securitycurrency} from '../../entities/securitycurrency';
import {Security} from '../../entities/security';
import {Currencypair} from '../../entities/currencypair';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {FieldConfig} from '../../lib/dynamic-form/models/field.config';
import {HistoryquoteLegacyService} from '../service/historyquote.legacy.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {HelpIds} from '../../lib/help/help.ids';
import {FormHelper} from '../../lib/dynamic-form/components/FormHelper';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';
import {HistoryquoteEditBase} from './historyquote-edit.base';

/**
 * Call parameter for {@link HistoryquoteLegacyEditComponent}: the archived row being edited together with its owning
 * security or currency pair (the auditable parent used for rights/propose-change decisions).
 */
export class HistoryquoteLegacySecurityCurrency {
  constructor(public historyquoteLegacy: HistoryquoteLegacy, public securitycurrency: Securitycurrency) {
  }

  get showName(): string {
    return this.securitycurrency.hasOwnProperty('name') ? (<Security>this.securitycurrency).name :
      (<Currencypair>this.securitycurrency).fromCurrency + '/' + (<Currencypair>this.securitycurrency).toCurrency;
  }
}

/**
 * Edit an archived {@code historyquote_legacy} row. The trading date and the archival transferDate are read-only;
 * only the OHLCV values can change. Persisted through the same propose-change approval flow as the live history quote.
 */
@Component({
  selector: 'historyquote-legacy-edit',
  template: `
    <p-dialog header="{{'HISTORYQUOTE_LEGACY' | translate}} {{callParam.showName}}" [visible]="visibleDialog"
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
export class HistoryquoteLegacyEditComponent extends HistoryquoteEditBase<HistoryquoteLegacy> {

  @Input() callParam: HistoryquoteLegacySecurityCurrency;

  constructor(translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              historyquoteLegacyService: HistoryquoteLegacyService) {
    super(HelpIds.HELP_WATCHLIST_HISTORYQUOTES_LEGACY, 'HISTORYQUOTE_LEGACY', translateService, gps,
      messageToastService, historyquoteLegacyService);
  }

  protected getSecuritycurrency(): Securitycurrency {
    return this.callParam.securitycurrency;
  }

  protected getExistingEntity(): HistoryquoteLegacy {
    return this.callParam.historyquoteLegacy;
  }

  protected createEntityInstance(): HistoryquoteLegacy {
    return new HistoryquoteLegacy();
  }

  protected override getAdditionalLeadingFields(): FieldConfig[] {
    return [DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'transferDate', false, {disabled: true})];
  }

  protected transferAndToggleImmutableFields(): void {
    this.form.transferBusinessObjectToForm(this.callParam.historyquoteLegacy);
    // Trading date and archival transferDate are immutable; only prices may change.
    FormHelper.disableEnableFieldConfigs(true, [this.configObject.date, this.configObject.transferDate]);
  }

}
