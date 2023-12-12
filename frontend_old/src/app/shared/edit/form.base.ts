import {FormConfig} from '../../dynamic-form/models/form.config';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {FieldFormGroup, FormGroupDefinition} from '../../dynamic-form/models/form.group.definition';

export abstract class FormBase {
  // Properties for the form
  config: FieldConfig[] = [];
  configObject: { [name: string]: FieldConfig };
  formConfig: FormConfig;
}
