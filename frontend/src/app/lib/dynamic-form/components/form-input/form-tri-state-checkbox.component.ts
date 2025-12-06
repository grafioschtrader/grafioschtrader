import {BaseInputComponent} from '../base.input.component';
import {Component} from '@angular/core';
import {ReactiveFormsModule} from '@angular/forms';
import {TriStateCheckboxComponent} from './tri-state-checkbox';
import {CommonModule} from '@angular/common';

@Component({
    selector: 'form-tri-state-checkbox',
    template: `
    <ng-container [formGroup]="group">
      <tri-state-checkbox [id]="config.field"  [formControlName]="config.field"  #input></tri-state-checkbox>
    </ng-container>
  `,
    imports: [
        ReactiveFormsModule,
        TriStateCheckboxComponent,
        CommonModule
    ],
    standalone: true
})
export class FormTriStateCheckboxComponent extends BaseInputComponent {
}
