import {Component, Input, OnInit} from '@angular/core';
import {BaseFieldFieldgroupConfig} from '../../models/base.field.fieldgroup.config';

/**
 * Support the validation of a single field with its error message.
 */
@Component({
  selector: 'error-message',
  template: `
    <div class="row" [ngxErrors]="baseFieldFieldgroupConfig.formControl">
      <div class="col-12 alert alert-danger" *ngFor="let error of baseFieldFieldgroupConfig.errors"
           [ngxError]="error.name"
           [when]="error.rules" [elementRef]="baseFieldFieldgroupConfig.elementRef">
        {{ error.text }}
      </div>
    </div>
  `
})
export class ErrorMessageComponent {
  @Input() baseFieldFieldgroupConfig: BaseFieldFieldgroupConfig;
}
