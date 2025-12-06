import {ModuleWithProviders, NgModule} from '@angular/core';

import {DynamicFormComponent} from './containers/dynamic-form/dynamic-form.component';
import {NgxErrorsDirective} from './error/ngxerrors.directive';
import {NgxErrorDirective} from './error/ngxerror.directive';
import {DisableControlDirective} from './components/disable.control.directive';


@NgModule({
  imports: [
    DynamicFormComponent,
    DisableControlDirective,
    NgxErrorsDirective,
    NgxErrorDirective
  ],
  exports: [
    DynamicFormComponent,
    DisableControlDirective,
    NgxErrorsDirective,
    NgxErrorDirective
  ]
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
