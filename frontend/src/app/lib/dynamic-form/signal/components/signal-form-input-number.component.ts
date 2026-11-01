import { Component, computed } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { FormsModule } from '@angular/forms';
import { SignalValueBridgeDirective } from './signal-value-bridge.directive';
import { TranslateModule } from '@ngx-translate/core';
import { InputNumberModule } from '@openng/optimus-ui/inputnumber';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { FilterOutPipe } from '../../pipe/FilterOutPipe';
import { SignalBaseInputComponent } from './signal-base-input.component';

/**
 * Optimus InputNumber of a signal dynamic form, bound through the SignalValueBridgeDirective. It keeps the Swiss
 * negative currency workaround of FormInputNumberComponent: with locale de-CH, a negative minimum and a negative value
 * the field switches to decimal mode with a currency prefix, because Optimus fails to parse negative de-CH currency
 * values.
 */
@Component({
  selector: 'signal-form-inputnumber',
  template: `
    @let config = this.view();
    <span signalValueBridge #bridge="signalValueBridge" [formField]="field()" style="display: contents">
      <p-inputNumber
        [ngModel]="bridge.value()"
        (ngModelChange)="bridge.value.set($event)"
        [ngModelOptions]="{ standalone: true }"
        [disabled]="bridge.disabled()"
        [readonly]="bridge.readonly()"
        (onBlur)="bridge.touch.emit()"
        #input
        [inputStyleClass]="'text-end ' + (isRequired() ? 'required-input' : '')"
        [style.width]="config.inputWidth + 'em'"
        [class.negativ-number]="isNegative()"
        pTooltip="{{ config.labelKey + '_TOOLTIP' | translate | filterOut: config.labelKey + '_TOOLTIP' }}"
        [attr.placeholder]="config.placeholder"
        [locale]="formConfig().locale"
        [id]="fieldName"
        [maxFractionDigits]="config.inputNumberSettings?.maxFractionDigits"
        [allowEmpty]="config.inputNumberSettings?.allowEmpty !== false"
        [prefix]="useSwissNegativeWorkaround() && currency() ? currency() + ' ' : undefined"
        [currency]="useSwissNegativeWorkaround() ? undefined : currency()"
        [mode]="useSwissNegativeWorkaround() || !currency() ? 'decimal' : 'currency'"
        currencyDisplay="code" />
    </span>
  `,
  imports: [
    FormField,
    FormsModule,
    SignalValueBridgeDirective,
    InputNumberModule,
    TooltipModule,
    TranslateModule,
    FilterOutPipe
  ]
})
export class SignalFormInputNumberComponent extends SignalBaseInputComponent {
  /** Current numeric value; p-inputNumber writes a number, not the formatted string. */
  private readonly currentValue = computed(() => {
    const value = this.field()().value();
    return value === null || value === undefined ? undefined : Number(value);
  });

  protected readonly isNegative = computed(() => {
    const value = this.currentValue();
    return value != null && value < 0;
  });

  /** Configured currency code (e.g. "CHF", "EUR"). */
  protected readonly currency = computed(() => this.view().inputNumberSettings?.currency);

  protected readonly useSwissNegativeWorkaround = computed(() => {
    const min = this.view().min;
    return this.formConfig().locale === 'de-CH' && min != null && min < 0 && this.isNegative();
  });
}
