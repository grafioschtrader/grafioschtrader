import {BaseInputComponent} from '../base.input.component';
import {Component} from '@angular/core';

@Component({
  selector: 'form-tri-state-checkbox',
  template: `
    <ng-container [formGroup]="group">
      <p-triStateCheckbox [id]="config.field" [formControlName]="config.field" #input></p-triStateCheckbox>
    </ng-container>
  `
})
export class FormTriStateCheckboxComponent extends BaseInputComponent {
}
