import {Component, Input, OnInit} from '@angular/core';
import moment from 'moment';
import {SimpleEditBase} from '../../lib/edit/simple.edit.base';
import {AppHelper} from '../../lib/helper/app.helper';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {HelpIds} from '../../lib/help/help.ids';
import {IHistoryquoteQuality} from '../../entities/view/ihistoryquote.quality';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {HistoryquoteCreateType} from '../../entities/historyquote';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {HistoryquoteService} from '../service/historyquote.service';
import {DeleteHistoryquotesSuccess, HistoryquoteDeleteBounds} from '../../securitycurrency/model/historyquote.quality.group';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {DialogModule} from '@openng/optimus-ui/dialog';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';
import {BaseSettings} from '../../lib/base.settings';
import {DataType} from '../../lib/dynamic-form/models/data.type';

/**
 * Dialog to delete the linear filled and/or manually imported history quotes of an instrument.
 *
 * The deletion is restricted to a period chosen by the user. Both boundary dates are preselected with the oldest and
 * the most recent stored price, so the default withdraws everything of the selected create types, and the date pickers
 * cannot leave that range, because a date outside it could never delete anything. The boundaries are loaded by the
 * opener and not taken from the data quality figures: those stop at the last completed trading day, while a linear
 * filling may have written prices beyond it, even into the future.
 *
 * The period exists for the case where a linear filling has to be withdrawn only in part: a price level that is valid
 * from a certain date on falls inside an already generated linear range. The user deletes from that date on, enters
 * the new price manually and fills linearly into the future again.
 */
@Component({
    selector: 'historyquote-delete-dialog',
    template: `
      <p-dialog header="{{'DELETE_CREATE_TYPES_QUOTES' | translate}}" [visible]="visibleDialog"
                [style]="{width: '500px'}"
                (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

          <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                        (submitBt)="submit($event)">
          </dynamic-form>
      </p-dialog>
  `,
    standalone: true,
    imports: [DialogModule, DynamicFormModule, TranslateModule]
})
export class HistoryquoteDeleteDialogComponent extends SimpleEditBase implements OnInit {
  @Input() idSecuritycurrency: number;
  @Input() historyquoteQuality: IHistoryquoteQuality;
  /** Oldest and most recent stored price, loaded by the opener before this dialog is created. */
  @Input() deleteBounds: HistoryquoteDeleteBounds;

  readonly dateFrom = 'dateFrom';
  readonly dateTo = 'dateTo';

  readonly fieldCreatTypes: FieldCreateType[] = [new FieldCreateType('filledLinear',
    HistoryquoteCreateType.FILLED_CLOSED_LINEAR_TRADING_DAY),
    new FieldCreateType('manualImported', HistoryquoteCreateType.MANUAL_IMPORTED)];

  constructor(public translateService: TranslateService,
              private historyquoteService: HistoryquoteService,
              private messageToastService: MessageToastService,
              gps: GlobalparameterService) {
    super(HelpIds.HELP_HISTORYQUOTE_QUALITY, gps);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      7, this.helpLink.bind(this));

    const minDate = HistoryquoteDeleteDialogComponent.toLocalDate(this.deleteBounds.minDate);
    const maxDate = HistoryquoteDeleteDialogComponent.toLocalDate(this.deleteBounds.maxDate);
    this.config.push(
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, this.dateFrom, true,
        {calendarConfig: {minDate, maxDate}}),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, this.dateTo, true,
        {calendarConfig: {minDate, maxDate}}));

    this.fieldCreatTypes.forEach(fct => {
      if (this.historyquoteQuality[fct.fieldName]) {
        this.config.push(DynamicFieldHelper.createFieldCheckboxHeqF(fct.fieldName, {defaultValue: true}));
      }
    });
    // Angular -> Submit button can not be used because the checkboxes carry no validation. The button is therefore
    // enabled and disabled from the state of the two date fields, see initialize().
    this.config.push(DynamicFieldHelper.createFunctionButtonFieldName('execute', 'EXECUTE',
      (e) => this.submit(null)));
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  submit(value: { [name: string]: any }): void {
    this.configObject.execute.disabled = true;
    const hct: HistoryquoteCreateType[] = [];
    this.fieldCreatTypes.forEach(fct => {
      if (this.historyquoteQuality[fct.fieldName]) {
        if (this.configObject[fct.fieldName].formControl.value) {
          hct.push(fct.hct);
        }
      }
    });

    if (hct.length > 0) {
      this.historyquoteService.deleteHistoryquotesByCreateTypes(this.idSecuritycurrency, hct,
        this.getDateAsBackendFormat(this.dateFrom), this.getDateAsBackendFormat(this.dateTo)).subscribe(
        {next: (dhs: DeleteHistoryquotesSuccess) => {
          this.messageToastService.showMessageI18nEnableHtml(InfoLevelType.SUCCESS,
            'HISTORYQUOTE_DELETE_CREATE_TYPES', dhs);
          this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED));
        }, error: () => this.configObject.execute.disabled = false});
    } else {
      this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
    }
  }

  protected override initialize(): void {
    this.form.setDefaultValuesAndEnableSubmit();
    this.configObject[this.dateFrom].formControl
      .setValue(HistoryquoteDeleteDialogComponent.toLocalDate(this.deleteBounds.minDate));
    this.configObject[this.dateTo].formControl
      .setValue(HistoryquoteDeleteDialogComponent.toLocalDate(this.deleteBounds.maxDate));
    // Each picker limits the other, so a period whose start lies after its end can not be produced. A value that was
    // already chosen is not corrected by a changed boundary, therefore the opposite date is dragged along.
    this.configObject[this.dateFrom].formControl.valueChanges.subscribe(dateFrom => {
      this.configObject[this.dateTo].calendarConfig.minDate = dateFrom;
      const dateToControl = this.configObject[this.dateTo].formControl;
      if (dateFrom && dateToControl.value && dateToControl.value < dateFrom) {
        dateToControl.setValue(dateFrom);
      }
    });
    this.configObject[this.dateTo].formControl.valueChanges.subscribe(dateTo => {
      this.configObject[this.dateFrom].calendarConfig.maxDate = dateTo;
      const dateFromControl = this.configObject[this.dateFrom].formControl;
      if (dateTo && dateFromControl.value && dateFromControl.value > dateTo) {
        dateFromControl.setValue(dateTo);
      }
    });
    // The function button is outside the form and therefore not disabled by an invalid form on its own. Without this
    // the user could send a cleared or out of range date, which the server would only answer with a validation error.
    this.form.form.statusChanges.subscribe(
      status => this.configObject.execute.disabled = status !== 'VALID');
  }

  /**
   * Reads a date field of this dialog and returns it in the format the backend expects.
   *
   * @param fieldName - Name of the date field
   * @returns The chosen date as yyyy-MM-dd
   * @private
   */
  private getDateAsBackendFormat(fieldName: string): string {
    return moment(this.configObject[fieldName].formControl.value).format(BaseSettings.FORMAT_DATE_SHORT_NATIVE);
  }

  /**
   * Turns a backend date of the form yyyy-MM-dd into a Date at local midnight. The native Date constructor would read
   * such a string as UTC midnight, which shifts the day for every user west of Greenwich.
   *
   * @param dateString - Date delivered by the backend
   * @returns The same calendar day in the local time zone
   * @private
   */
  private static toLocalDate(dateString: string): Date {
    return moment(dateString, BaseSettings.FORMAT_DATE_SHORT_NATIVE).toDate();
  }
}

class FieldCreateType {
  constructor(public fieldName: string, public hct: HistoryquoteCreateType) {
  }
}
