import {BaseInputComponent} from '../base.input.component';
import {Component} from '@angular/core';


/**
 * A html multi select component
 */
@Component({
  selector: 'form-input-multi-select',
  template: `
    <ng-container [formGroup]="group">
      <p-multiSelect
              [ngStyle]="{'width': (config.inputWidth+1) + 'em'}"
              class="form-control input-sm"
              [class.required-input]="isRequired"
              [id]="config.field"
              optionValue="key"
              optionLabel="value"
              [options]="config.valueKeyHtmlOptions"
              [formControlName]="config.field"
              pTooltip="{{config.labelKey + '_TOOLTIP' | translate | filterOut:config.labelKey + '_TOOLTIP'}}"/>
    </ng-container>
  `
})
export class FormInputMultiSelectComponent extends BaseInputComponent {
}
