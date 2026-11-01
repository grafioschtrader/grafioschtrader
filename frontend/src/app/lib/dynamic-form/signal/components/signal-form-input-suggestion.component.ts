import { Component } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { FormsModule } from '@angular/forms';
import { SignalValueBridgeDirective } from './signal-value-bridge.directive';
import { TranslateModule } from '@ngx-translate/core';
import { AutoCompleteModule } from '@openng/optimus-ui/autocomplete';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { FilterOutPipe } from '../../pipe/FilterOutPipe';
import { SignalBaseInputComponent } from './signal-base-input.component';

/**
 * Optimus AutoComplete of a signal dynamic form.
 *
 * Suggestion callbacks publish their results into a signal of the dialog. A FIELD_DISPLAY rule exposes that signal
 * as `suggestions`, so asynchronously loaded and filtered choices refresh the control without FormNotifier.
 */
@Component({
  selector: 'signal-form-input-suggestion',
  template: `
    @let config = this.view();
    <span signalValueBridge #bridge="signalValueBridge" [formField]="field()" style="display: contents">
      <p-autoComplete
        [ngModel]="bridge.value()"
        (ngModelChange)="bridge.value.set($event)"
        [ngModelOptions]="{ standalone: true }"
        [disabled]="bridge.disabled()"
        (onBlur)="bridge.touch.emit()"
        #input
        [id]="fieldName"
        [inputStyleClass]="'form-control ' + (isRequired() ? 'required-input' : '')"
        class="p-autocomplete"
        [style]="{ width: '100%' }"
        [inputStyle]="{ width: '100%' }"
        [suggestions]="config.suggestions ?? []"
        (completeMethod)="config.suggestionsFN?.($event)"
        pTooltip="{{ config.labelKey + '_TOOLTIP' | translate | filterOut: config.labelKey + '_TOOLTIP' }}" />
    </span>
  `,
  imports: [
    FormField,
    FormsModule,
    SignalValueBridgeDirective,
    AutoCompleteModule,
    TooltipModule,
    TranslateModule,
    FilterOutPipe
  ]
})
export class SignalFormInputSuggestionComponent extends SignalBaseInputComponent {}
