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
          [ngClass]="{'form-control-fluid input-sm': config.fieldSuffix,
                    'form-control input-sm': !config.fieldSuffix,
                     'text-right': config.dataType === DataType.Numeric || config.dataType === DataType.NumericInteger }"
          [type]="(config.dataType === DataType.TimeString)? 'time':
            (config.dataType === DataType.Numeric  || config.dataType === DataType.NumericInteger)?
            'number': (config.dataType === DataType.Password)? 'password': (config.dataType === DataType.Email)? 'email' : 'text'"
          [pKeyFilter]="config.dataType === DataType.NumericInteger? 'int': config.dataType === DataType.Numeric? 'num': null"
          [attr.placeholder]="config.placeholder"
          [formControlName]="config.field"
          pTooltip="{{config.labelHelpText?.startsWith('*')? config.labelHelpText.slice(1):
              config.labelKey + '_TOOLTIP' | translate | filterOut:config.labelKey + '_TOOLTIP'}}"
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
          [ngClass]="{'form-control-fluid input-sm': config.fieldSuffix,
                    'form-control input-sm': !config.fieldSuffix,
                     'text-right': config.dataType === DataType.Numeric || config.dataType === DataType.NumericInteger }"
          [type]="(config.dataType === DataType.TimeString)? 'time':
            (config.dataType === DataType.Numeric  || config.dataType === DataType.NumericInteger)?
            'number': (config.dataType === DataType.Password)? 'password': (config.dataType === DataType.Email)? 'email' : 'text'"
          [pKeyFilter]="config.dataType === DataType.NumericInteger? 'int': config.dataType === DataType.Numeric? 'num': null"
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
  `,
    standalone: false
})

export class FormInputComponent extends BaseInputComponent {


}
