import {Component} from '@angular/core';
import {BaseInputComponent} from '../base.input.component';

@Component({
  selector: 'form-input-select',
  template: `
    <ng-container [formGroup]="group">
      <select #input
              [ngStyle]="{'width': (config.inputWidth+1) + 'em'}"
              class="form-control input-sm"
              [class.required-input]="isRequired"
              [id]="config.field"
              [formControlName]="config.field"
              pTooltip="{{config.labelKey + '_TOOLTIP' | translate | filterOut:config.labelKey + '_TOOLTIP'}}">
        <option *ngFor="let s of config.valueKeyHtmlOptions" [value]="s.key" [disabled]="s.disabled">
          {{ s.value }}
        </option>
      </select>
    </ng-container>
  `
})

export class FormInputSelectComponent extends BaseInputComponent {
}
