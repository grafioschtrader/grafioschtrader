import { BaseInputComponent } from '../base.input.component';
import { Component } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TriStateCheckboxComponent } from './tri-state-checkbox';

@Component({
  selector: 'form-tri-state-checkbox',
  template: `
    @let config = this.config();
    @let formConfig = this.formConfig();
    @let group = this.group();
    @let rev = notifier?.revision();
    <ng-container [formGroup]="group">
      <tri-state-checkbox [id]="fieldName" [formControlName]="fieldName" #input />
    </ng-container>
  `,
  imports: [ReactiveFormsModule, TriStateCheckboxComponent]
})
export class FormTriStateCheckboxComponent extends BaseInputComponent {}
