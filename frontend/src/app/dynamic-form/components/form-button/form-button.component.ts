import {Component} from '@angular/core';
import {UntypedFormGroup} from '@angular/forms';

import {FieldFormFormGroupConfig} from '../../models/field.form.form.group.config';
import {FieldConfig} from '../../models/field.config';
import {FormConfig} from '../../models/form.config';

@Component({
  selector: 'form-button',
  template: `
      <button *ngIf="!config.buttonFN" class="btn ml-1"
              type="submit" [disabled]="!group.valid || config.disabled">
          {{config.labelKey | translate}}
      </button>
      <button *ngIf="config.buttonFN" class="btn ml-1"
              [disabled]="config.disabled" type="button" (click)="config.buttonFN($event)">
          {{config.labelKey | translate}}
      </button>

  `
})

export class FormButtonComponent implements FieldFormFormGroupConfig {
  config: FieldConfig;
  formConfig: FormConfig;
  group: UntypedFormGroup;
}
