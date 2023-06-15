import {Component, OnInit} from '@angular/core';
import {BaseInputComponent} from '../base.input.component';

/**
 * It is not working als expected: https://github.com/primefaces/primeng/issues/9380
 */
@Component({
  selector: 'form-inputnumber',
  template: `
    <ng-container [formGroup]="group">
      <p-inputNumber
        [class.required-input]="isRequired && !config.readonly"
        inputStyleClass="text-right"
        [readonly]="config.readonly"
        [ngStyle]="{'width': config.inputWidth+'em'}"
        [class.negativ-number]="input.value < 0"
        pTooltip="{{config.labelKey + '_TOOLTIP' | translate | filterOut:config.labelKey + '_TOOLTIP'}}"
        #input
        [attr.placeholder]="config.placeholder"
        [formControlName]="config.field"
        [disabled]="config.readonly"
        [locale]="formConfig.locale"
        [id]="config.field"
        [min]="config.min"
        [max]="config.max"
        [maxFractionDigits]="config.inputNumberSettings.maxFractionDigits"
        [currency]="config.inputNumberSettings.currency"
        [mode]="config.inputNumberSettings.currency? 'currency': 'decimal'"
        currencyDisplay="code">
      </p-inputNumber>
    </ng-container>
  `
})

export class FormInputNumberComponent extends BaseInputComponent implements OnInit {
  ngOnInit() {
  }
}
