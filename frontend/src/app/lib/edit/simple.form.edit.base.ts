import { Directive, viewChild } from '@angular/core';
import { DynamicFormComponent } from '../dynamic-form/containers/dynamic-form/dynamic-form.component';
import { DynamicFormApi, dynamicFormToken } from '../dynamic-form/models/dynamic-form-api';
import { SimpleEditBase } from './simple.edit.base';

/** Base for editors whose template always contains the form. Access it after the view has initialized. */
@Directive()
export abstract class SimpleFormEditBase<F extends DynamicFormApi = DynamicFormComponent> extends SimpleEditBase<F> {
  override readonly form = viewChild.required(dynamicFormToken<F>());
}
