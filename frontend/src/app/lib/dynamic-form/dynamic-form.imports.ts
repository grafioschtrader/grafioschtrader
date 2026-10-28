import { DynamicFormComponent } from './containers/dynamic-form/dynamic-form.component';
import { DisableControlDirective } from './components/disable.control.directive';
import { NgxErrorsDirective } from './error/ngxerrors.directive';
import { NgxErrorDirective } from './error/ngxerror.directive';

/** Standalone imports for dynamic forms and their companion control and validation directives. */
export const DYNAMIC_FORM_IMPORTS = [
  DynamicFormComponent,
  DisableControlDirective,
  NgxErrorsDirective,
  NgxErrorDirective
] as const;
