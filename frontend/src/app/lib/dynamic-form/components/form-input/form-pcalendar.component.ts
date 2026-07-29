import {Component, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {BaseInputComponent} from '../base.input.component';
import {Helper} from '../../../helper/helper';
import {AbstractControl, ReactiveFormsModule} from '@angular/forms';
import {Subscription} from 'rxjs';

import {DatePicker} from 'primeng/datepicker';
import {TooltipModule} from 'primeng/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {FilterOutPipe} from '../../pipe/FilterOutPipe';


@Component({
    selector: 'form-pcalendar',
    template: `
    <ng-container [formGroup]="group">
      <p-datepicker [id]="config.field"
                  [style]="{'max-width': '180px'}"
                  [inputStyleClass]="'form-control ' + (isRequired? 'required-input': '')"
                  [showTime]="config.dataType === DataType.DateTimeNumeric"
                  [dateFormat]="formConfig.dateFormat"
                  dataType="date"
                  [timeOnly]="config.dataType === DataType.TimeString"
                  pTooltip="{{config.labelKey + '_TOOLTIP' | translate | filterOut:config.labelKey + '_TOOLTIP'}}"
                  #input
                  [hideOnDateTimeSelect]="true"
                  [minDate]="config.calendarConfig.minDate"
                  [maxDate]="config.calendarConfig.maxDate"
                  [disabledDates]="config.calendarConfig.disabledDates"
                  [disabledDays]="config.calendarConfig.disabledDays"
                  selectionMode="single"
                  [required]="isRequired"
                  [showOnFocus]="false"
                  [showIcon]="true"
                  appendTo="body"
                  (onBlur)="onCalendarBlur($event)"
                  [formControlName]="config.field">
      </p-datepicker>
    </ng-container>
  `,
    imports: [
    ReactiveFormsModule,
    DatePicker,
    TooltipModule,
    TranslateModule,
    FilterOutPipe
],
    standalone: true
})
export class FormPCalendarComponent extends BaseInputComponent implements OnInit, OnDestroy {

  /** Error names this component sets itself, they must be removed again before a new evaluation. */
  private static readonly OWN_ERRORS = ['calendarDateInvalid', 'calendarDateNotSelectable'];

  public language;

  @ViewChild(DatePicker) private datePicker: DatePicker;

  private valueChangedSub: Subscription;

  override ngOnInit() {
    super.ngOnInit();
    this.language = Helper.CALENDAR_LANG[this.formConfig.language];
    // Every accepted date, no matter if it was entered or chosen over the calendar, withdraws a former complaint.
    // Otherwise a rejected entry would keep the field invalid even after a correct date was selected.
    this.valueChangedSub = this.group.controls[this.config.field]?.valueChanges.subscribe(() =>
      this.setOwnError(this.group.controls[this.config.field], null));
  }

  ngOnDestroy(): void {
    this.valueChangedSub?.unsubscribe();
  }

  /**
   * Reports a manually entered date which the date picker does not accept. Such an entry is discarded silently by
   * PrimeNG: text which does not match the date format sets the model to null, and a date which is outside the
   * permitted period or on an excluded day leaves the model untouched while the input field is repainted with the
   * previous value. In both cases the user gets no explanation, therefore the entry is evaluated here once more and
   * turned into an error message of the field. Selection over the calendar overlay never reaches this state, because
   * it only offers dates which are selectable.
   *
   * The evaluation uses the parser and the selection check of the date picker itself, so it can not diverge from the
   * behaviour of the input field, for example over the interpretation of a two digit year.
   *
   * @param event Blur event of the input field, it still carries the text which the user has entered
   */
  onCalendarBlur(event: Event): void {
    const control = this.group.controls[this.config.field];
    if (!control || !this.datePicker) {
      return;
    }
    const enteredText = (event.target as HTMLInputElement)?.value?.trim();
    let errorName: string = null;
    if (enteredText) {
      try {
        const parsedDate = this.datePicker.parseValueFromString(enteredText);
        errorName = parsedDate && !this.datePicker.isValidSelection(parsedDate) ? 'calendarDateNotSelectable' : null;
      } catch (ignored) {
        errorName = 'calendarDateInvalid';
      }
    }
    this.setOwnError(control, errorName);
  }

  /**
   * Replaces the error of this component on the form control without touching errors of the registered validators,
   * for example the required error.
   *
   * @param control Form control of this input field
   * @param errorName Name of the error to set or null when the entry is accepted
   */
  private setOwnError(control: AbstractControl, errorName: string): void {
    const errors = {...(control.errors || {})};
    FormPCalendarComponent.OWN_ERRORS.forEach(name => delete errors[name]);
    if (errorName) {
      errors[errorName] = true;
    }
    control.setErrors(Object.keys(errors).length > 0 ? errors : null);
  }
}
