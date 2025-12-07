import {Component} from '@angular/core';
import {ReactiveFormsModule, UntypedFormGroup} from '@angular/forms';

import {FieldFormFormGroupConfig} from '../../models/field.form.form.group.config';
import {FieldConfig} from '../../models/field.config';
import {FormConfig} from '../../models/form.config';
import {TranslateModule} from '@ngx-translate/core';


@Component({
    selector: 'form-button',
  template: `
    @if (!config.buttonFN) {
      <button class="btn btn-primary ms-1"
              type="submit" [disabled]="!group.valid || config.disabled">
        {{config.labelKey | translate}}
      </button>
    } @else {
      <button class="btn btn-primary ms-1"
              [disabled]="config.disabled" type="button" (click)="config.buttonFN($event)">
        {{config.labelKey | translate}}
      </button>
    }
  `,
    imports: [
    TranslateModule,
    ReactiveFormsModule
],
    standalone: true
})

export class FormButtonComponent implements FieldFormFormGroupConfig {
  config: FieldConfig;
  formConfig: FormConfig;
  group: UntypedFormGroup;
}
