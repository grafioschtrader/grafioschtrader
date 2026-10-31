import { ComponentRef, Directive, Type, ViewContainerRef, input, effect, untracked, inject } from '@angular/core';
import { UntypedFormGroup } from '@angular/forms';
import { FormButtonComponent } from '../form-button/form-button.component';
import { FormInputComponent } from '../form-input/form-input.component';
import { FieldFormFormGroupConfig } from '../../models/field.form.form.group.config';
import { FieldConfig } from '../../models/field.config';
import { FormPCalendarComponent } from '../form-input/form-pcalendar.component';
import { FormPInputTextareaComponent } from '../form-input/form-pinputtextarea.component';
import { FormPButtonComponent } from '../form-button/form-pbutton.component';
import { InputType } from '../../models/input.type';
import { FormCheckboxComponent } from '../form-input/form-checkbox.component';
import { FormConfig } from '../../models/form.config';
import { FormFileUploadComponent } from '../form-input-file/form-file-upload.component';
import { FormInputSuggestionComponent } from '../form-input/form-input-suggestion.component';
import { FormInputSelectComponent } from '../form-input/form-input-select.component';
import { FormTriStateCheckboxComponent } from '../form-input/form-tri-state-checkbox.component';
import { FormInputButtonComponent } from '../form-input/form-input-button.component';
import { FormInputNumberComponent } from '../form-input/form-input-number.component';
import { FormInputDropdownComponent } from '../form-input/form-input-dropdown.component';
import { FormInputMultiSelectComponent } from '../form-input/form-input-multi-select.component';
import { FormInputTreeSelectComponent } from '../form-input/form-input-tree-select.component';

const components: { [type: string]: Type<FieldFormFormGroupConfig> } = {
  [InputType.Button]: FormButtonComponent,
  [InputType.Pbutton]: FormPButtonComponent,
  [InputType.Checkbox]: FormCheckboxComponent,
  [InputType.TriStateCheckbox]: FormTriStateCheckboxComponent,
  [InputType.Input]: FormInputComponent,
  [InputType.InputButton]: FormInputButtonComponent,
  [InputType.InputNumber]: FormInputNumberComponent,
  [InputType.InputSuggestion]: FormInputSuggestionComponent,
  [InputType.InputDropdown]: FormInputDropdownComponent,
  [InputType.Select]: FormInputSelectComponent,
  [InputType.MultiSelect]: FormInputMultiSelectComponent,
  [InputType.TreeSelect]: FormInputTreeSelectComponent,
  [InputType.Pcalendar]: FormPCalendarComponent,
  [InputType.Pinputtextarea]: FormPInputTextareaComponent,
  [InputType.FileUpload]: FormFileUploadComponent
};

@Directive({
  selector: 'dynamicField'
})
export class DynamicFieldDirective {
  private viewContainerRef = inject(ViewContainerRef);

  readonly config = input.required<FieldConfig>();
  readonly formConfig = input.required<FormConfig>();
  readonly group = input.required<UntypedFormGroup>();

  component?: ComponentRef<FieldFormFormGroupConfig>;

  constructor() {
    effect(() => {
      this.config();
      this.formConfig();
      this.group();
      untracked(() => {
        const config = this.config();
        if (!this.component) {
          const componentType = components[config.inputType];
          if (!componentType)
            throw new Error(
              'Trying to use an unsupported type (' +
                config.inputType +
                '). Supported types: ' +
                Object.keys(components).join(', ')
            );
          this.component = this.viewContainerRef.createComponent(componentType);
        }
        this.component.setInput('config', config);
        this.component.setInput('formConfig', this.formConfig());
        this.component.setInput('group', this.group());
      });
    });
  }
}
