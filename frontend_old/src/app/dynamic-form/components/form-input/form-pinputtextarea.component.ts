import {Component} from '@angular/core';
import {BaseInputComponent} from '../base.input.component';

@Component({
  selector: 'form-pinputtextarea',
  template: `
    <ng-container [formGroup]="group">
      <textarea pInputTextarea [rows]="config.textareaRows" [formControlName]="config.field" [id]="config.field"
                [class.required-input]="isRequired && !config.readonly"
                pTooltip="{{config.labelKey + '_TOOLTIP' | translate | filterOut:config.labelKey + '_TOOLTIP'}}"
                [readonly]="config.readonly"
                [disabled]="config.disabled"
                [maxlength]="config.maxLength"
                #input class="form-control">
      </textarea>
    </ng-container>
  `
})
export class FormPInputTextareaComponent extends BaseInputComponent {

}
