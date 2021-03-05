import {Component, OnInit} from '@angular/core';
import {BaseInputComponent} from '../base.input.component';
import {Helper} from '../../../helper/helper';


@Component({
  selector: 'form-pcalendar',

  template: `
    <ng-container [formGroup]="group">
      <p-calendar [id]="config.field"
                  [style]="{'max-width': '180px'}"
                  [inputStyleClass]="'form-control ' + (isRequired? 'required-input': '')"
                  [showTime]="config.dataType === DataType.DateTimeNumeric"
                  [dateFormat]="formConfig.dateFormat"
                  monthNavigator="true" yearNavigator="true" yearRange="2000:2099"
                  dataType="date"
                  [timeOnly]="config.dataType === DataType.TimeString"
                  pTooltip="{{config.labelKey + '_TOOLTIP' | translate | filterOut:config.labelKey + '_TOOLTIP'}}"
                  #input
                  [hideOnDateTimeSelect]="true"
                  [locale]="language"
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
      </p-calendar>
    </ng-container>
  `
})
export class FormPCalendarComponent extends BaseInputComponent implements OnInit {

  public language;

  ngOnInit() {
    super.ngOnInit();
    this.language = Helper.CALENDAR_LANG[this.formConfig.language];
  }
}
