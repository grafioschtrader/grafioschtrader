import { Component, input } from '@angular/core';
import { BaseFieldFieldgroupConfig } from '../../models/base.field.fieldgroup.config';
import { NgxErrorsDirective } from '../../error/ngxerrors.directive';
import { NgxErrorDirective } from '../../error/ngxerror.directive';
import { FormNotifier } from '../../models/form.notifier';

/**
 * Support the validation of a single field with its error message.
 */
@Component({
  selector: 'error-message',
  template: `
    @let rev = notifier?.revision();
    <div class="row" [ngxErrors]="baseFieldFieldgroupConfig().formControl">
      @for (error of baseFieldFieldgroupConfig().errors; track error) {
        <div
          class="col-12 alert alert-danger"
          [ngxError]="error.name"
          [when]="error.rules"
          [elementRef]="baseFieldFieldgroupConfig().elementRef">
          {{ error.text }}
        </div>
      }
    </div>
  `,
  imports: [NgxErrorsDirective, NgxErrorDirective]
})
export class ErrorMessageComponent {
  readonly baseFieldFieldgroupConfig = input<BaseFieldFieldgroupConfig>(undefined);

  /** Change notification of the form this field or group belongs to; the template reads its revision. */
  get notifier(): FormNotifier {
    return FormNotifier.of(this.baseFieldFieldgroupConfig());
  }
}
