import {Component, Input, OnInit} from '@angular/core';
import {Historyquote} from '../../entities/historyquote';
import {DataType} from '../../dynamic-form/models/data.type';
import {AppHelper} from '../../shared/helper/app.helper';
import {HistoryquoteService} from '../service/historyquote.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {TranslateService} from '@ngx-translate/core';
import {HistoryquoteSecurityCurrency} from './historyquote-table.component';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {HelpIds} from '../../shared/help/help.ids';
import {SimpleEntityEditBase} from '../../shared/edit/simple.entity.edit.base';
import {AuditHelper} from '../../shared/helper/audit.helper';
import {ProposeChangeEntityWithEntity} from '../../entities/proposechange/propose.change.entity.whit.entity';
import {FormHelper} from '../../dynamic-form/components/FormHelper';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import * as moment from 'moment';
import {AppSettings} from '../../shared/app.settings';

/**
 * Edit historical quotes.
 */
@Component({
  selector: 'historyquote-edit',
  template: `
    <p-dialog header="{{'HISTORY_QUOTE_FOR' | translate}} {{callParam.showName}}" [(visible)]="visibleDialog"
              [responsive]="true" [style]="{width: '400px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`
})
export class HistoryquoteEditComponent extends SimpleEntityEditBase<Historyquote> implements OnInit {

  @Input() callParam: HistoryquoteSecurityCurrency;
  @Input() proposeChangeEntityWithEntity: ProposeChangeEntityWithEntity;

  constructor(translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              historyquoteService: HistoryquoteService) {
    super(HelpIds.HELP_WATCHLIST_HISTORYQUOTES, AppSettings.HISTORYQUOTE_P_KEY.toUpperCase(), translateService, gps,
      messageToastService, historyquoteService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateNumeric, 'date', true,
        {
          calendarConfig: {
            maxDate: moment().subtract(1, 'days').toDate(),
            disabledDays: [0, 6]
          }
        }),
      DynamicFieldHelper.createFieldCurrencyNumberHeqF('volume', false,
        10, 2, false, this.gps.getNumberCurrencyMask(), false),
      DynamicFieldHelper.createFieldCurrencyNumberHeqF('open', false,
        6, 8, false, this.gps.getNumberCurrencyMask(), false),
      DynamicFieldHelper.createFieldCurrencyNumberHeqF('high', false,
        6, 8, false, this.gps.getNumberCurrencyMask(), false),
      DynamicFieldHelper.createFieldCurrencyNumberHeqF('low', false,
        6, 8, false, this.gps.getNumberCurrencyMask(), false),
      DynamicFieldHelper.createFieldCurrencyNumberHeqF('close', true,
        6, 8, false, this.gps.getNumberCurrencyMask(), false),
      ...AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this)
    ];
    this.configObject = Object.assign({}, ...this.config.map(d => ({[d.field]: d})));
    TranslateHelper.translateMessageErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    if (this.callParam.historyquote) {
      this.form.transferBusinessObjectToForm(this.callParam.historyquote);
      FormHelper.disableEnableFieldConfigs(true, [this.configObject.date]);
    } else {
      this.form.setDefaultValuesAndEnableSubmit();
      FormHelper.disableEnableFieldConfigs(false, [this.configObject.date]);
    }
    AuditHelper.configureFormFromAuditableRights(this.translateService, this.gps,
      this.callParam.securitycurrency, this.form, this.configObject, this.proposeChangeEntityWithEntity, false);
  }

  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): Historyquote {
    const historyquote = new Historyquote();
    this.copyFormToPublicBusinessObject(historyquote, this.callParam.historyquote, this.proposeChangeEntityWithEntity);
    historyquote.idSecuritycurrency = this.callParam.securitycurrency.idSecuritycurrency;
    return historyquote;
  }

}
