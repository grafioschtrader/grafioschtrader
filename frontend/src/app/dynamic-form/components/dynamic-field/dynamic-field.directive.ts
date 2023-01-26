import {
  ComponentFactoryResolver,
  ComponentRef,
  Directive,
  Input,
  OnChanges,
  OnInit,
  Type,
  ViewContainerRef
} from '@angular/core';
import {UntypedFormGroup} from '@angular/forms';

import {FormButtonComponent} from '../form-button/form-button.component';
import {FormInputComponent} from '../form-input/form-input.component';


import {FieldFormFormGroupConfig} from '../../models/field.form.form.group.config';
import {FieldConfig} from '../../models/field.config';
import {FormPCalendarComponent} from '../form-input/form-pcalendar.component';
import {FormPInputTextareaComponent} from '../form-input/form-pinputtextarea.component';
import {FormPButtonComponent} from '../form-button/form-pbutton.component';
import {InputType} from '../../models/input.type';
import {FormCheckboxComponent} from '../form-input/form-checkbox.component';
import {FormConfig} from '../../models/form.config';
import {FormFileUploadComponent} from '../form-input-file/form-file-upload.component';
import {FormInputSuggestionComponent} from '../form-input/form-input-suggestion.component';
import {FormInputSelectComponent} from '../form-input/form-input-select.component';
import {FormTriStateCheckboxComponent} from '../form-input/form-tri-state-checkbox.component';
import {FormInputButtonComponent} from '../form-input/form-input-button.component';
import {FormInputNumberComponent} from '../form-input/form-input-number.component';
import {FormInputCurrencyNumberComponent} from '../form-input/form-input-currency-number.component';
import {FormInputDropdownComponent} from '../form-input/form-input-dropdown.component';


const components: { [type: string]: Type<FieldFormFormGroupConfig> } = {
  [InputType.Button]: FormButtonComponent,
  [InputType.Pbutton]: FormPButtonComponent,
  [InputType.Checkbox]: FormCheckboxComponent,
  [InputType.TriStateCheckbox]: FormTriStateCheckboxComponent,
  [InputType.Input]: FormInputComponent,
  [InputType.InputButton]: FormInputButtonComponent,
  [InputType.InputNumber]: FormInputNumberComponent,
  [InputType.InputCurrencyNumber]: FormInputCurrencyNumberComponent,
  [InputType.InputSuggestion]: FormInputSuggestionComponent,
  [InputType.InputDropdown]: FormInputDropdownComponent,
  [InputType.Select]: FormInputSelectComponent,
  [InputType.Pcalendar]: FormPCalendarComponent,
  [InputType.Pinputtextarea]: FormPInputTextareaComponent,
  [InputType.FileUpload]: FormFileUploadComponent
};


@Directive({
  selector: 'dynamicField'
})
export class DynamicFieldDirective implements FieldFormFormGroupConfig, OnChanges, OnInit {
  @Input() config: FieldConfig;
  @Input() formConfig: FormConfig;
  @Input() group: UntypedFormGroup;

  component: ComponentRef<FieldFormFormGroupConfig>;

  constructor(private resolver: ComponentFactoryResolver,
              private viewContainerRef: ViewContainerRef) {
  }

  ngOnChanges() {
    if (this.component) {
      this.component.instance.config = this.config;
      this.component.instance.formConfig = this.formConfig;
      this.component.instance.group = this.group;
    }
  }

  ngOnInit() {
    if (!components[this.config.inputType]) {
      const supportedTypes = Object.keys(components).join(', ');
      throw new Error(
        `Trying to use an unsupported type (${this.config.inputType}).
        Supported types: ${supportedTypes}`
      );
    }
   // const component = this.resolver.resolveComponentFactory<FieldFormFormGroupConfig>(components[this.config.inputType]);
   // this.component = this.viewContainerRef.createComponent(component);

    this.component = this.viewContainerRef.createComponent(components[this.config.inputType]);


    this.component.instance.config = this.config;
    this.component.instance.formConfig = this.formConfig;
    this.component.instance.group = this.group;
  }
}
