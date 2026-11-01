import { Component } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { FormsModule } from '@angular/forms';
import { SignalValueBridgeDirective } from './signal-value-bridge.directive';
import { TranslateModule } from '@ngx-translate/core';
import { MultiSelectModule } from '@openng/optimus-ui/multiselect';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { FilterOutPipe } from '../../pipe/FilterOutPipe';
import { SignalBaseInputComponent } from './signal-base-input.component';

/** Optimus MultiSelect of a signal dynamic form; its options may come from a schema rule. */
@Component({
  selector: 'signal-form-input-multi-select',
  template: `
    @let config = this.view();
    <span signalValueBridge #bridge="signalValueBridge" [formField]="field()" style="display: contents">
      <p-multiSelect
        [ngModel]="bridge.value()"
        (ngModelChange)="bridge.value.set($event)"
        [ngModelOptions]="{ standalone: true }"
        [disabled]="bridge.disabled()"
        (onBlur)="bridge.touch.emit()"
        [style.width]="config.inputWidth == null ? null : config.inputWidth + 1 + 'em'"
        class="form-control input-sm"
        [class.required-input]="isRequired()"
        [id]="fieldName"
        optionValue="key"
        optionLabel="value"
        [options]="options()"
        pTooltip="{{ config.labelKey + '_TOOLTIP' | translate | filterOut: config.labelKey + '_TOOLTIP' }}" />
    </span>
  `,
  imports: [
    FormField,
    FormsModule,
    SignalValueBridgeDirective,
    MultiSelectModule,
    TooltipModule,
    TranslateModule,
    FilterOutPipe
  ]
})
export class SignalFormInputMultiSelectComponent extends SignalBaseInputComponent {}
