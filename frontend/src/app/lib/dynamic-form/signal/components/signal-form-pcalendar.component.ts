import { Component, computed, inject, viewChild } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { FormsModule } from '@angular/forms';
import { SignalValueBridgeDirective } from './signal-value-bridge.directive';
import { TranslateModule } from '@ngx-translate/core';
import { DatePicker } from '@openng/optimus-ui/datepicker';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { FilterOutPipe } from '../../pipe/FilterOutPipe';
import { SignalFormContext } from '../signal-form.model';
import { SignalBaseInputComponent } from './signal-base-input.component';

/**
 * Optimus DatePicker of a signal dynamic form. A manually entered date the date picker does not accept is discarded
 * silently by Optimus; as in FormPCalendarComponent the entry is evaluated on blur with the parser of the date picker
 * and reported as an error of the field. The report lasts until the value of the field changes.
 *
 * The selectable period follows a `minDate` / `maxDate` rule of the schema, which also validates the value, otherwise
 * the `calendarConfig` of the FieldConfig or of FIELD_DISPLAY.
 */
@Component({
  selector: 'signal-form-pcalendar',
  template: `
    @let config = this.view();
    <span signalValueBridge #bridge="signalValueBridge" [formField]="field()" style="display: contents">
      <p-datepicker
        [ngModel]="bridge.value()"
        (ngModelChange)="bridge.value.set($event)"
        [ngModelOptions]="{ standalone: true }"
        [disabled]="bridge.disabled()"
        #input
        [id]="fieldName"
        [style]="{ 'max-width': '180px' }"
        [inputStyleClass]="'form-control ' + (isRequired() ? 'required-input' : '')"
        [showTime]="config.dataType === DataType.DateTimeNumeric"
        [dateFormat]="formConfig().dateFormat"
        dataType="date"
        [timeOnly]="config.dataType === DataType.TimeString"
        pTooltip="{{ config.labelKey + '_TOOLTIP' | translate | filterOut: config.labelKey + '_TOOLTIP' }}"
        [hideOnDateTimeSelect]="true"
        [minDate]="minDate()"
        [maxDate]="maxDate()"
        [disabledDates]="config.calendarConfig?.disabledDates ?? []"
        [disabledDays]="config.calendarConfig?.disabledDays ?? []"
        selectionMode="single"
        [showOnFocus]="false"
        [showIcon]="true"
        appendTo="body"
        (onBlur)="onCalendarBlur($event)" />
    </span>
  `,
  imports: [
    FormField,
    FormsModule,
    SignalValueBridgeDirective,
    DatePicker,
    TooltipModule,
    TranslateModule,
    FilterOutPipe
  ]
})
export class SignalFormPCalendarComponent extends SignalBaseInputComponent {
  private readonly context = inject(SignalFormContext);
  private readonly datePicker = viewChild(DatePicker);

  protected readonly minDate = computed(() => {
    const limit = this.field()().min?.();
    return limit instanceof Date ? limit : this.view().calendarConfig?.minDate;
  });

  protected readonly maxDate = computed(() => {
    const limit = this.field()().max?.();
    return limit instanceof Date ? limit : this.view().calendarConfig?.maxDate;
  });

  /**
   * Reports an entry the date picker rejects: text that does not match the date format, or a date outside the
   * permitted period or on an excluded day.
   *
   * @param event Blur event of the input field, it still carries the text which the user has entered
   */
  onCalendarBlur(event: Event): void {
    this.field()().markAsTouched();
    const datePicker = this.datePicker();
    if (!datePicker) {
      return;
    }
    const enteredText = (event.target as HTMLInputElement)?.value?.trim();
    let errorName: string | null = null;
    if (enteredText) {
      try {
        const parsedDate = datePicker.parseValueFromString(enteredText);
        errorName = parsedDate && !datePicker.isValidSelection(parsedDate) ? 'calendarDateNotSelectable' : null;
      } catch (ignored) {
        errorName = 'calendarDateInvalid';
      }
    }
    this.context.reportInputError(this.config(), errorName);
  }
}
