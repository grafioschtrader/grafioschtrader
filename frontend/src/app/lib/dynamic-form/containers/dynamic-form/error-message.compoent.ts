import {Component, Input} from '@angular/core';
import {BaseFieldFieldgroupConfig} from '../../models/base.field.fieldgroup.config';

/**
 * Support the validation of a single field with its error message.
 */
@Component({
  selector: 'error-message',
  template: `
    <div class="row" [ngxErrors]="baseFieldFieldgroupConfig.formControl">
      @for (error of baseFieldFieldgroupConfig.errors; track error) {
        <div class="col-12 alert alert-danger"
             [ngxError]="error.name"
             [when]="error.rules" [elementRef]="baseFieldFieldgroupConfig.elementRef">
          {{ error.text }}
        </div>
      }
    </div>
  `,
  standalone: false
})
export class ErrorMessageComponent {
  @Input() baseFieldFieldgroupConfig: BaseFieldFieldgroupConfig;
}
