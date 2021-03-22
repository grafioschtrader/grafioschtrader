import {Component} from '@angular/core';
import {BaseInputComponent} from '../base.input.component';

@Component({
  selector: 'form-checkbox',
  template: `
    <ng-container [formGroup]="group">
      <input class="form-control" type="checkbox" #input [id]="config.field" [formControlName]="config.field"
             pTooltip="{{config.labelKey + '_TOOLTIP' | translate | filterOut:config.labelKey + '_TOOLTIP'}}">
    </ng-container>
  `
})
export class FormCheckboxComponent extends BaseInputComponent {
}
