import {Component, Input, OnInit} from '@angular/core';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import moment from 'moment';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {SimpleEditBase} from '../../lib/edit/simple.edit.base';
import {HelpIds} from '../../lib/help/help.ids';
import {AppHelper} from '../../lib/helper/app.helper';
import {IHistoryquoteQuality} from '../../entities/view/ihistoryquote.quality';
import {Securitycurrency} from '../../entities/securitycurrency';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {SecurityService} from '../../securitycurrency/service/security.service';
import {
  HisotryqouteLinearFilledSummary,
  HistoryquoteFillGapsBounds
} from '../../securitycurrency/model/historyquote.quality.group';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {DialogModule} from '@openng/optimus-ui/dialog';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';
import {BaseSettings} from '../../lib/base.settings';
import {DataType} from '../../lib/dynamic-form/models/data.type';

/**
 * Dialog to fill gaps of history quotes.
 *
 * The end date of the filling is chosen by the user. It is preselected with the current day, while the selectable range
 * reaches as far as the trading calendar of the exchange and the lifetime of the instrument allow - commonly into the
 * future, up to the maturity of a bond for instance. Prices for future days are therefore reachable, but only when the
 * user moves the date there. When the trading calendar lags behind the last completed trading day, a warning explains
 * why the range stops short.
 */
@Component({
    selector: 'historyquote-quality-fill-gaps',
    template: `
    <p-dialog header="{{'HISTORYQUOTE_FILL_GAPS' | translate}}" [visible]="visibleDialog"
              [style]="{width: '500px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      @if (fillGapsBounds?.calendarOutdated) {
        <div class="alert alert-warning alert-dialog-wrap">{{ calendarLimitMessage }}</div>
      }

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `,
    standalone: true,
    imports: [DialogModule, DynamicFormModule, TranslateModule]
})
export class HistoryquoteQualityFillGapsComponent extends SimpleEditBase implements OnInit {
  @Input() historyquoteQuality: IHistoryquoteQuality;
  @Input() securitycurrency: Securitycurrency;
  /** Date boundaries loaded by the opener before this dialog is created. */
  @Input() fillGapsBounds: HistoryquoteFillGapsBounds;

  readonly moveWeekendToFriday = 'moveWeekendToFriday';
  readonly fillUpToDate = 'fillUpToDate';

  /** Translated warning shown when the trading calendar limits the proposed end date. */
  calendarLimitMessage = '';

  constructor(public translateService: TranslateService,
              private securityService: SecurityService,
              private messageToastService: MessageToastService,
              gps: GlobalparameterService) {
    super(HelpIds.HELP_HISTORYQUOTE_QUALITY, gps);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      7, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, this.fillUpToDate, true,
        {calendarConfig: {minDate: HistoryquoteQualityFillGapsComponent.toLocalDate(this.fillGapsBounds.minFillUpTo),
            maxDate: HistoryquoteQualityFillGapsComponent.toLocalDate(this.fillGapsBounds.maxFillUpTo)}}),
      DynamicFieldHelper.createFieldCheckboxHeqF(this.moveWeekendToFriday),
      // Angular -> Submit button can not be used because the checkbox may be the only disabled input field. The button
      // is therefore enabled and disabled from the state of the date field, see initialize().
      DynamicFieldHelper.createFunctionButtonFieldName('execute', 'EXECUTE',
        (e) => this.submit(null))
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  submit(value: { [name: string]: any }): void {
    this.configObject.execute.disabled = true;
    this.securityService.fillHistoryquoteGapsLinear(this.securitycurrency.idSecuritycurrency,
      {
        moveWeekendToFriday: this.configObject[this.moveWeekendToFriday].formControl.value,
        fillUpToDate: moment(this.configObject[this.fillUpToDate].formControl.value)
          .format(BaseSettings.FORMAT_DATE_SHORT_NATIVE)
      })
      .subscribe({next: (hlfs: HisotryqouteLinearFilledSummary) => {
        this.messageToastService.showMessageI18nEnableHtml(hlfs.warning ? InfoLevelType.WARNING : InfoLevelType.SUCCESS,
          'HISTORYQUOTE_FILL_RESULT', hlfs);
        this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED));
      }, error: () => this.configObject.execute.disabled = false});
  }

  protected override initialize(): void {
    this.form.setDefaultValuesAndEnableSubmit();
    this.configObject[this.fillUpToDate].formControl
      .setValue(HistoryquoteQualityFillGapsComponent.toLocalDate(this.fillGapsBounds.defaultFillUpTo));
    this.prepareCalendarLimitMessage();
    const hasWeekendDays: boolean = this.historyquoteQuality.quoteSaturday !== null && this.historyquoteQuality.quoteSaturday > 0
      || this.historyquoteQuality.quoteSunday !== null && this.historyquoteQuality.quoteSunday > 0;
    this.configObject[this.moveWeekendToFriday].formControl.setValue(hasWeekendDays);
    !hasWeekendDays && this.configObject[this.moveWeekendToFriday].formControl.disable();
    // The function button is outside the form and therefore not disabled by an invalid form on its own. Without this
    // the user could send a cleared or out of range date, which the server would only answer with a validation error.
    this.configObject[this.fillUpToDate].formControl.statusChanges.subscribe(
      status => this.configObject.execute.disabled = status !== 'VALID');
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

  /**
   * Builds the warning shown above the form when the trading calendar of the exchange is what holds the proposed end
   * date back. It names the calendar horizon and how many trading days up to today are not covered by it.
   */
  private prepareCalendarLimitMessage(): void {
    if (this.fillGapsBounds.calendarOutdated) {
      this.translateService.get('HISTORYQUOTE_FILL_CALENDAR_LIMIT', {
        calendarHorizon: moment(HistoryquoteQualityFillGapsComponent.toLocalDate(this.fillGapsBounds.calendarHorizon))
          .format(this.gps.getDateFormat()),
        missingTradingDays: this.fillGapsBounds.missingTradingDays
      }).subscribe(msg => this.calendarLimitMessage = msg);
    }
  }
}
