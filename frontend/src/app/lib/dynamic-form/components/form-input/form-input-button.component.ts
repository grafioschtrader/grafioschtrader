import {Component} from '@angular/core';
import {BaseInputComponent} from '../base.input.component';

/**
 * Creates an input field with a following button.
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
          class="p-inputtext"
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
        <span *ngIf="config.fieldSuffix" class="p-inputgroup-addon">{{config.fieldSuffix|translate}}</span>
        <button pButton type="button" icon="pi pi-search" [attr.disabled]="config.disabled"
                (click)="config.buttonFN(config)">
        </button>
      </div>
    </ng-container>
  `,
    standalone: false
})

export class FormInputButtonComponent extends BaseInputComponent {


}
