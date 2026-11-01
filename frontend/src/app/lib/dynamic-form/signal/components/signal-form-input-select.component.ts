import { Component } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { TranslateModule } from '@ngx-translate/core';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { FilterOutPipe } from '../../pipe/FilterOutPipe';
import { SignalBaseInputComponent } from './signal-base-input.component';

/**
 * Native select of a signal dynamic form; its options may come from a schema rule. The options are tracked by key: when
 * a rule publishes new option objects, for example after their labels were translated, the option elements stay. A
 * native select bound with `[formField]` otherwise loses its value as soon as all of its options are replaced.
 */
@Component({
  selector: 'signal-form-input-select',
  template: `
    @let config = this.view();
    <select
      #input
      [formField]="field()"
      [style.width]="config.inputWidth ? config.inputWidth + 1 + 'em' : null"
      class="form-select form-select-sm"
      [class.required-input]="isRequired()"
      [id]="fieldName"
      pTooltip="{{ config.labelKey + '_TOOLTIP' | translate | filterOut: config.labelKey + '_TOOLTIP' }}">
      @for (s of options(); track s.key) {
        <option [value]="s.key" [disabled]="s.disabled">
          {{ s.value }}
        </option>
      }
    </select>
  `,
  imports: [FormField, TooltipModule, TranslateModule, FilterOutPipe]
})
export class SignalFormInputSelectComponent extends SignalBaseInputComponent<string> {}
