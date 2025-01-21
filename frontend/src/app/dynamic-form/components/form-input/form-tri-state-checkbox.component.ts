import {BaseInputComponent} from '../base.input.component';
import {Component} from '@angular/core';

@Component({
    selector: 'form-tri-state-checkbox',
    template: `
    <ng-container [formGroup]="group">
      <tri-state-checkbox [id]="config.field"  [formControlName]="config.field"  #input></tri-state-checkbox>
    </ng-container>
  `,
    standalone: false
})
export class FormTriStateCheckboxComponent extends BaseInputComponent {
}
