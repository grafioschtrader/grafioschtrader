import { Component, input } from '@angular/core';
import { ReactiveFormsModule, UntypedFormGroup } from '@angular/forms';

import { FieldFormFormGroupConfig } from '../../models/field.form.form.group.config';
import { FieldConfig } from '../../models/field.config';
import { FormConfig } from '../../models/form.config';
import { FormNotifier } from '../../models/form.notifier';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'form-button',
  template: `
    @let config = this.config();
    @let formConfig = this.formConfig();
    @let group = this.group();
    @let rev = notifier?.revision();
    @if (!config.buttonFN) {
      <button class="btn btn-primary ms-1" type="submit" [disabled]="!group.valid || config.disabled">
        {{ config.labelKey | translate }}
      </button>
    } @else {
      <button class="btn btn-primary ms-1" [disabled]="config.disabled" type="button" (click)="config.buttonFN($event)">
        {{ config.labelKey | translate }}
      </button>
    }
  `,
  imports: [TranslateModule, ReactiveFormsModule]
})
export class FormButtonComponent implements FieldFormFormGroupConfig {
  readonly config = input.required<FieldConfig>();
  readonly formConfig = input.required<FormConfig>();
  readonly group = input.required<UntypedFormGroup>();

  /** Change notification of the form this field belongs to; the template reads its revision. */
  get notifier(): FormNotifier | undefined {
    return FormNotifier.of(this.config());
  }
}
