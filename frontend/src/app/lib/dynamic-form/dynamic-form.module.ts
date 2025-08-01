import {ModuleWithProviders, NgModule} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';

import {DynamicFieldDirective} from './components/dynamic-field/dynamic-field.directive';
import {DynamicFormComponent} from './containers/dynamic-form/dynamic-form.component';
import {FormButtonComponent} from './components/form-button/form-button.component';
import {FormInputComponent} from './components/form-input/form-input.component';
import {FormPCalendarComponent} from './components/form-input/form-pcalendar.component';

import {FormPInputTextareaComponent} from './components/form-input/form-pinputtextarea.component';
import {FormPButtonComponent} from './components/form-button/form-pbutton.component';
import {NgxErrorsDirective} from './error/ngxerrors.directive';
import {NgxErrorDirective} from './error/ngxerror.directive';
import {ErrorMessageComponent} from './containers/dynamic-form/error-message.compoent';
import {FormCheckboxComponent} from './components/form-input/form-checkbox.component';
import {TranslateModule} from '@ngx-translate/core';

import {DynamicFormLayoutComponent} from './containers/dynamic-form/dynamic-form-layout.component';
import {FormInputSuggestionComponent} from './components/form-input/form-input-suggestion.component';
import {FormFileUploadComponent} from './components/form-input-file/form-file-upload.component';
import {FormInputSelectComponent} from './components/form-input/form-input-select.component';
import {FileRequiredValidator} from './components/form-input-file/file-input.validator';
import {FileValueAccessorDirective} from './components/form-input-file/file-control-value-accessor';
import {UpperCaseDirective} from './components/form-input/upper-case.directive';
import {FormTriStateCheckboxComponent} from './components/form-input/form-tri-state-checkbox.component';
import {AutoCompleteModule} from 'primeng/autocomplete';
import {DialogModule} from 'primeng/dialog';
import {ButtonModule} from 'primeng/button';
import {CheckboxModule} from 'primeng/checkbox';

import {FieldsetModule} from 'primeng/fieldset';
import {TooltipModule} from 'primeng/tooltip';
import {FilterOutPipe} from './pipe/FilterOutPipe';
import {FormInputButtonComponent} from './components/form-input/form-input-button.component';
import {DisableControlDirective} from './components/disable.control.directive';
import {FormInputNumberComponent} from './components/form-input/form-input-number.component';
import {FormInputCurrencyNumberComponent} from './components/form-input/form-input-currency-number.component';
import {AngularSvgIconModule} from 'angular-svg-icon';
import {HttpClientModule} from '@angular/common/http';
import {FormInputDropdownComponent} from './components/form-input/form-input-dropdown.component';
import {InputNumberModule} from 'primeng/inputnumber';
import {NgxCurrencyDirective} from 'ngx-currency';
import {FormInputMultiSelectComponent} from './components/form-input/form-input-multi-select.component';
import {MultiSelectModule} from 'primeng/multiselect';
import {ContextMenuModule} from 'primeng/contextmenu';
import {KeyFilterModule} from 'primeng/keyfilter';
import {DatePicker} from 'primeng/datepicker';
import {SelectModule} from 'primeng/select';
import {PopoverModule} from 'primeng/popover';
import {TriStateCheckboxComponent} from './components/form-input/tri-state-checkbox';


@NgModule({
  imports: [
    AutoCompleteModule,
    DatePicker,
    InputNumberModule,
    KeyFilterModule,
    CommonModule,
    SelectModule,
    ReactiveFormsModule,
    CheckboxModule,
    ButtonModule,
    MultiSelectModule,
    DialogModule,
    FieldsetModule,
    FormsModule,
    PopoverModule,
    TooltipModule,
    TranslateModule,
    TriStateCheckboxComponent,
    HttpClientModule, AngularSvgIconModule.forRoot(), NgxCurrencyDirective, ContextMenuModule, TriStateCheckboxComponent
  ],
  declarations: [
    ErrorMessageComponent,
    FilterOutPipe,
    DynamicFormLayoutComponent,
    DynamicFieldDirective,
    DynamicFormComponent,
    FileValueAccessorDirective,
    FileRequiredValidator,
    FormButtonComponent,
    FormPButtonComponent,
    FormCheckboxComponent,
    FormInputNumberComponent,
    FormInputCurrencyNumberComponent,
    FormTriStateCheckboxComponent,
    FormFileUploadComponent,
    FormInputComponent,
    FormInputButtonComponent,
    FormInputDropdownComponent,
    FormInputSuggestionComponent,
    FormInputSelectComponent,
    FormInputMultiSelectComponent,
    FormPCalendarComponent,
    FormPInputTextareaComponent,
    DisableControlDirective,
    NgxErrorsDirective,
    NgxErrorDirective,
    UpperCaseDirective
  ],
  exports: [
    DynamicFormComponent,
    DisableControlDirective,
    NgxErrorsDirective,
    NgxErrorDirective
  ]
  /*,
  // Needed for generation of a ComponentFactory for referenced Components.
  entryComponents: [
    FormButtonComponent,
    FormPButtonComponent,
    FormCheckboxComponent,
    FormInputComponent,
    FormInputSuggestionComponent,
    FormSelectComponent,
    FormPCalendarComponent,
    FormPInputTextareaComponent
  ]*/
})
export class DynamicFormModule {
  static withComponents(components: any[]): ModuleWithProviders<DynamicFormModule> {
    return {
      ngModule: DynamicFormModule,
      providers: [
        //  {provide: ANALYZE_FOR_ENTRY_COMPONENTS, useValue: components, multi: true}
      ]
    };
  }
}
