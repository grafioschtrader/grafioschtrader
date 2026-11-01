import { ComponentRef, Directive, effect, inject, input, Type, untracked, ViewContainerRef } from '@angular/core';
import { FieldConfig } from '../models/field.config';
import { FormConfig } from '../models/form.config';
import { InputType } from '../models/input.type';
import { SignalFormContext } from './signal-form.model';
import { SignalFormPButtonComponent } from './components/signal-form-button.component';
import {
  SignalFormCheckboxComponent,
  SignalFormTriStateCheckboxComponent
} from './components/signal-form-checkbox.component';
import { SignalFormFileUploadComponent } from './components/signal-form-file-upload.component';
import { SignalFormInputButtonComponent } from './components/signal-form-input-button.component';
import { SignalFormInputDropdownComponent } from './components/signal-form-input-dropdown.component';
import { SignalFormInputMultiSelectComponent } from './components/signal-form-input-multi-select.component';
import { SignalFormInputNumberComponent } from './components/signal-form-input-number.component';
import { SignalFormInputSelectComponent } from './components/signal-form-input-select.component';
import { SignalFormInputSuggestionComponent } from './components/signal-form-input-suggestion.component';
import { SignalFormInputTreeSelectComponent } from './components/signal-form-input-tree-select.component';
import { SignalFormInputComponent } from './components/signal-form-input.component';
import { SignalFormPCalendarComponent } from './components/signal-form-pcalendar.component';
import { SignalFormPInputTextareaComponent } from './components/signal-form-pinputtextarea.component';

/** Input and button components of the signal dynamic form, by input type of the FieldConfig. */
const components: Partial<Record<InputType, Type<unknown>>> = {
  [InputType.Pbutton]: SignalFormPButtonComponent,
  [InputType.Checkbox]: SignalFormCheckboxComponent,
  [InputType.TriStateCheckbox]: SignalFormTriStateCheckboxComponent,
  [InputType.Input]: SignalFormInputComponent,
  [InputType.InputButton]: SignalFormInputButtonComponent,
  [InputType.InputNumber]: SignalFormInputNumberComponent,
  [InputType.InputSuggestion]: SignalFormInputSuggestionComponent,
  [InputType.InputDropdown]: SignalFormInputDropdownComponent,
  [InputType.Select]: SignalFormInputSelectComponent,
  [InputType.MultiSelect]: SignalFormInputMultiSelectComponent,
  [InputType.TreeSelect]: SignalFormInputTreeSelectComponent,
  [InputType.Pcalendar]: SignalFormPCalendarComponent,
  [InputType.Pinputtextarea]: SignalFormPInputTextareaComponent,
  [InputType.FileUpload]: SignalFormFileUploadComponent
};

/**
 * Creates the component of one field or button of a signal dynamic form, the counterpart of DynamicFieldDirective.
 * A data field receives its field tree from the enclosing form instead of a FormGroup.
 */
@Directive({
  selector: 'signalDynamicField'
})
export class SignalDynamicFieldDirective {
  private readonly viewContainerRef = inject(ViewContainerRef);
  private readonly context = inject(SignalFormContext);

  readonly config = input.required<FieldConfig>();
  readonly formConfig = input.required<FormConfig>();

  private component?: ComponentRef<unknown>;

  constructor() {
    effect(() => {
      const config = this.config();
      const formConfig = this.formConfig();
      const field = this.context.fieldOf(config);
      untracked(() => {
        if (!this.component) {
          const componentType = components[config.inputType];
          if (!componentType) throw new Error(`Unsupported input type "${config.inputType}" for "${config.field}".`);
          this.component = this.viewContainerRef.createComponent(componentType);
        }
        this.component.setInput('config', config);
        this.component.setInput('formConfig', formConfig);
        if (config.inputType !== InputType.Pbutton) {
          if (!field) throw new Error(`Signal form has no field for "${config.field}".`);
          this.component.setInput('field', field);
        }
      });
    });
  }
}
