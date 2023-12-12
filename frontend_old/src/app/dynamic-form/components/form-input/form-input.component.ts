import {Component} from '@angular/core';

import {BaseInputComponent} from '../base.input.component';

/**
 * Input for normal text, password email and number with minimal and maximal value
 */
@Component({
  selector: 'form-input',
  template: `
    <ng-container [formGroup]="group">
      <div *ngIf="config.maxLength; then withSize else withoutSize"></div>
      <ng-template #withSize>
        <input
          #input
          [ngStyle]="{'width': config.inputWidth+'em'}"
          [class.required-input]="isRequired && !config.readonly"
          [ngClass]="config.fieldSuffix ? 'form-control-fluid input-sm' : 'form-control input-sm'"
          [type]="(config.dataType === DataType.TimeString)? 'time':
            (config.dataType === DataType.Numeric  || config.dataType === DataType.NumericInteger)?
            'number': (config.dataType === DataType.Password)? 'password': (config.dataType === DataType.Email)? 'email' : 'text'"
          [attr.placeholder]="config.placeholder"
          [formControlName]="config.field"
          pTooltip="{{config.labelKey + '_TOOLTIP' | translate | filterOut:config.labelKey + '_TOOLTIP'}}"
          [maxlength]="config.maxLength"
          [size]="config.inputWidth || config.maxLength"
          [readonly]="config.readonly"
          [id]="config.field"
          [min]="config.min"
          [max]="config.max"
          [upperCase]="config.upperCase">
        {{config.fieldSuffix|translate}}
      </ng-template>
      <ng-template #withoutSize>
        <input
          #input
          [ngStyle]="{'width': config.inputWidth+'em'}"
          [class.required-input]="isRequired && !config.readonly"
          [ngClass]="config.fieldSuffix ? 'form-control-fluid input-sm' : 'form-control input-sm'"
          [type]="(config.dataType === DataType.TimeString)? 'time':
            (config.dataType === DataType.Numeric  || config.dataType === DataType.NumericInteger)?
            'number': (config.dataType === DataType.Password)? 'password': (config.dataType === DataType.Email)? 'email' : 'text'"
          [attr.placeholder]="config.placeholder"
          [formControlName]="config.field"
          pTooltip="{{config.labelKey + '_TOOLTIP' | translate | filterOut:config.labelKey + '_TOOLTIP'}}"
          [readonly]="config.readonly"
          [id]="config.field"
          [min]="config.min"
          [max]="config.max"
          [upperCase]="config.upperCase">
        {{config.fieldSuffix|translate}}
      </ng-template>
    </ng-container>
  `
})

export class FormInputComponent extends BaseInputComponent {


}
