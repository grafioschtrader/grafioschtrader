import {FormConfig} from '../../dynamic-form/models/form.config';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {FormGroupDefinition} from '../../dynamic-form/models/form.group.definition';
import {ElementRef} from '@angular/core';


export abstract class FormBase {
  // Properties for the form
  config: FieldConfig[] = [];
  configObject: { [namekey: string]: FieldConfig };
  formConfig: FormConfig;
}
