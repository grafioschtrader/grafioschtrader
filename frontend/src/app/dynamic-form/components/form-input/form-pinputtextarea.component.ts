import {Component} from '@angular/core';
import {BaseInputComponent} from '../base.input.component';

@Component({
    selector: 'form-pinputtextarea',
  template: `
    <ng-container [formGroup]="group">
    <textarea pTextarea [rows]="config.textareaRows" [formControlName]="config.field" [id]="config.field"
              [class.required-input]="isRequired && !config.readonly"
              [readonly]="config.readonly"
              [disabled]="config.disabled"
              [maxlength]="config.maxLength"
              pTooltip="{{config.labelHelpText?.startsWith('*')? config.labelHelpText.slice(1):
              config.labelKey + '_TOOLTIP' | translate | filterOut:config.labelKey + '_TOOLTIP'}}"
              #input class="form-control">
    </textarea>

      @if (config.contextMenuItems) {
        <p-contextMenu [target]="input" [model]="config.contextMenuItems" />
      }
    </ng-container>
  `,
    standalone: false
})
export class FormPInputTextareaComponent extends BaseInputComponent {

}
