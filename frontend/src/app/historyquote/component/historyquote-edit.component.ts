import {Component, Input, OnInit} from '@angular/core';
import {Historyquote} from '../../entities/historyquote';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {AppHelper} from '../../lib/helper/app.helper';
import {HistoryquoteService} from '../service/historyquote.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {TranslateService} from '@ngx-translate/core';
import {HistoryquoteSecurityCurrency} from './historyquote-table.component';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {HelpIds} from '../../lib/help/help.ids';
import {SimpleEntityEditBase} from '../../lib/edit/simple.entity.edit.base';
import {AuditHelper} from '../../lib/helper/audit.helper';
import {ProposeChangeEntityWithEntity} from '../../lib/proposechange/model/propose.change.entity.whit.entity';
import {FormHelper} from '../../lib/dynamic-form/components/FormHelper';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import moment from 'moment';
import {AppSettings} from '../../shared/app.settings';

/**
 * Edit historical quotes.
 */
@Component({
    selector: 'historyquote-edit',
    template: `
    <p-dialog header="{{'HISTORY_QUOTE_FOR' | translate}} {{callParam.showName}}" [(visible)]="visibleDialog"
              [style]="{width: '400px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`,
    standalone: false
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
        AppSettings.FID_MAX_INTEGER_DIGITS, 0, false, this.gps.getNumberCurrencyMask(), false),
      DynamicFieldHelper.createFieldCurrencyNumberHeqF('open', false,
        AppSettings.FID_MAX_INT_REAL_DOUBLE, this.gps.getMaxFractionDigits(), false,
        this.gps.getNumberCurrencyMask(), false),
      DynamicFieldHelper.createFieldCurrencyNumberHeqF('high', false,
        AppSettings.FID_MAX_INT_REAL_DOUBLE, this.gps.getMaxFractionDigits(), false,
        this.gps.getNumberCurrencyMask(), false),
      DynamicFieldHelper.createFieldCurrencyNumberHeqF('low', false,
        AppSettings.FID_MAX_INT_REAL_DOUBLE, this.gps.getMaxFractionDigits(), false,
        this.gps.getNumberCurrencyMask(), false),
      DynamicFieldHelper.createFieldCurrencyNumberHeqF('close', true,
        AppSettings.FID_MAX_INT_REAL_DOUBLE, this.gps.getMaxFractionDigits(), false,
        this.gps.getNumberCurrencyMask(), false),
      ...AuditHelper.getFullNoteRequestInputDefinition(this.closeDialog, this)
    ];
    this.configObject = this.config.reduce((acc, d) => ({
      ...acc, [d.field]: d }), {});
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
