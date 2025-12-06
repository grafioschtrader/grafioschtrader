import {Component, OnInit} from '@angular/core';
import {BaseInputComponent} from '../base.input.component';
import {Helper} from '../../../helper/helper';
import {ReactiveFormsModule} from '@angular/forms';
import {CommonModule} from '@angular/common';
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
                  [formControlName]="config.field">
      </p-datepicker>
    </ng-container>
  `,
    imports: [
        ReactiveFormsModule,
        CommonModule,
        DatePicker,
        TooltipModule,
        TranslateModule,
        FilterOutPipe
    ],
    standalone: true
})
export class FormPCalendarComponent extends BaseInputComponent implements OnInit {

  public language;

  override ngOnInit() {
    super.ngOnInit();
    this.language = Helper.CALENDAR_LANG[this.formConfig.language];
  }
}
