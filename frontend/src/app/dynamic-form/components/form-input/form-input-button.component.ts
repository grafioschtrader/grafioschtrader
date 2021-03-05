import {Component} from '@angular/core';
import {BaseInputComponent} from '../base.input.component';

/**
 * Creates a input field with a following button.
 */
@Component({
  selector: 'form-input-button',
  template: `
      <ng-container [formGroup]="group">
          <div class="p-inputgroup">
              <input
                      #input
                      [style.background-color]="'white'"
                      [class.required-input]="isRequired"
                      [ngClass]="config.fieldSuffix ? 'form-control-fluid input-sm' : 'form-control input-sm'"
                      [type]="(config.dataType === DataType.TimeString)? 'time':
            (config.dataType === DataType.Numeric  || config.dataType === DataType.NumericInteger)?
            'number': (config.dataType === DataType.Password)? 'password': (config.dataType === DataType.Email)? 'email' : 'text'"
                      [attr.placeholder]="config.placeholder"
                      [formControlName]="config.field"
                      readonly
                      [id]="config.field"
                      [min]="config.min"
                      [max]="config.max"
                      [upperCase]="config.upperCase">
              {{config.fieldSuffix|translate}}
              <button pButton type="button" icon="pi pi-search" [attr.disabled]="config.disabled"
                      (click)="config.buttonFN(config)">
              </button>
          </div>
      </ng-container>
  `
})

export class FormInputButtonComponent extends BaseInputComponent {


}
