import {Component} from '@angular/core';
import {BaseInputComponent} from '../base.input.component';

@Component({
    selector: 'form-checkbox',
    template: `
    <ng-container [formGroup]="group">
      <input class="form-check-input" type="checkbox" #input [id]="config.field" [formControlName]="config.field"
             pTooltip="{{config.labelHelpText?.startsWith('*')? config.labelHelpText.slice(1):
              config.labelKey + '_TOOLTIP' | translate | filterOut:config.labelKey + '_TOOLTIP'}}">
    </ng-container>
  `,
    standalone: false
})
export class FormCheckboxComponent extends BaseInputComponent {
}
