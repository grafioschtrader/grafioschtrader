import { Component } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { TranslateModule } from '@ngx-translate/core';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { TriStateCheckboxComponent } from '../../components/form-input/tri-state-checkbox';
import { FilterOutPipe } from '../../pipe/FilterOutPipe';
import { SignalBaseInputComponent } from './signal-base-input.component';

/** Native checkbox of a signal dynamic form. */
@Component({
  selector: 'signal-form-checkbox',
  template: `
    @let config = this.view();
    <input
      class="form-check-input"
      type="checkbox"
      #input
      [formField]="field()"
      [id]="fieldName"
      pTooltip="{{
        config.labelHelpText?.startsWith('*')
          ? config.labelHelpText.slice(1)
          : (config.labelKey + '_TOOLTIP' | translate | filterOut: config.labelKey + '_TOOLTIP')
      }}" />
  `,
  imports: [FormField, TooltipModule, TranslateModule, FilterOutPipe]
})
export class SignalFormCheckboxComponent extends SignalBaseInputComponent<boolean> {}

/** Three-state checkbox of a signal dynamic form, bound through the ControlValueAccessor of the existing control. */
@Component({
  selector: 'signal-form-tri-state-checkbox',
  template: `<tri-state-checkbox #input [formField]="field()" [id]="fieldName" />`,
  imports: [FormField, TriStateCheckboxComponent]
})
export class SignalFormTriStateCheckboxComponent extends SignalBaseInputComponent {}
