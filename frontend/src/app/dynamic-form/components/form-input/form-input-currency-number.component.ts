import {BaseInputComponent} from '../base.input.component';
import {Component} from '@angular/core';

@Component({
  selector: 'form-input-currency-number',
  template: `
    <ng-container [formGroup]="group">
      <input currencyMask [options]="config.currencyMaskConfig"  #input
             [id]="config.field"
             [formControlName]="config.field"
             [class.required-input]="isRequired && !config.readonly"
             [class.negativ-number]="input.value.startsWith('-')"
             [readonly]="config.readonly"
             [ngStyle]="{'width': (config.inputWidth) * 9 +'px'}"
             pTooltip="{{config.labelKey + '_TOOLTIP' | translate | filterOut:config.labelKey + '_TOOLTIP'}}"
             class="form-control"
             autocomplete="off"
             onfocus="this.select()"/>
    </ng-container>
  `
})
export class FormInputCurrencyNumberComponent extends BaseInputComponent {
}
