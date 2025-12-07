import {Component} from '@angular/core';
import {BaseInputComponent} from '../base.input.component';
import {ReactiveFormsModule} from '@angular/forms';
import {CommonModule} from '@angular/common';
import {InputNumberModule} from 'primeng/inputnumber';
import {TooltipModule} from 'primeng/tooltip';
import {TranslateModule} from '@ngx-translate/core';
import {FilterOutPipe} from '../../pipe/FilterOutPipe';

/**
 * PrimeNG InputNumber wrapper component for dynamic forms.
 * Supports treating zero values as null/empty via the treatZeroAsNull setting.
 * *
 * WORKAROUND NOTE
 *
 * PrimeNG InputNumber has a known issue when using
 *   mode="currency" + locale="de-CH" + negative values.
 *
 * In this combination, entering a negative number (e.g. "-3")
 * causes incorrect cursor behavior and the value to be cleared
 * when the input loses focus (TAB / blur).
 *
 * The problem originates from PrimeNG's internal parsing logic,
 * which relies on Intl.NumberFormat. For Swiss locales (de-CH),
 * the currency format uses different separators and spacing,
 * which leads to parsing failures for negative currency values.
 *
 * To avoid this issue, the component switches to:
 *   - mode="decimal"
 *   - a manual currency prefix (e.g. "CHF ")
 *
 * The workaround is applied ONLY when:
 *   - locale is "de-CH"
 *   - negative values are allowed (min < 0)
 *   - the current value is negative (< 0)
 *
 * This preserves the expected visual format (e.g. "CHF -3.00")
 * while ensuring stable input behavior and correct value parsing.
 */
@Component({
  selector: 'form-inputnumber',
  template: `
    <ng-container [formGroup]="group">
      <p-inputNumber
        [inputStyleClass]="'text-end ' + (isRequired && !config.readonly ? 'required-input' : '')"
        [readonly]="config.readonly"
        [ngStyle]="{ width: config.inputWidth + 'em' }"
        [class.negativ-number]="currentValue < 0"
        pTooltip="{{ config.labelKey + '_TOOLTIP' | translate | filterOut: (config.labelKey + '_TOOLTIP') }}"
        #input
        [attr.placeholder]="config.placeholder"
        [formControlName]="config.field"
        [disabled]="config.readonly"
        [locale]="formConfig.locale"
        [id]="config.field"
        [min]="config.min"
        [max]="config.max"
        [maxFractionDigits]="config.inputNumberSettings?.maxFractionDigits"
        [allowEmpty]="config.inputNumberSettings?.allowEmpty !== false"
        [prefix]="inputPrefix"
        [currency]="inputCurrency"
        [mode]="inputMode"
        currencyDisplay="code">
      </p-inputNumber>
    </ng-container>
  `,
  imports: [
    ReactiveFormsModule,
    CommonModule,
    InputNumberModule,
    TooltipModule,
    TranslateModule,
    FilterOutPipe
  ],
  standalone: true
})
export class FormInputNumberComponent extends BaseInputComponent {

  /**
   * Current numeric value from the FormControl.
   * p-inputNumber writes a number (not the formatted string) to the model.
   */
  get currentValue(): number | undefined {
    const ctrl = this.group?.get(this.config.field);
    const value = ctrl?.value;
    return value === null || value === undefined ? undefined : Number(value);
  }

  /**
   * Determines whether the Swiss negative currency workaround should be applied.
   * Conditions:
   * - locale is de-CH
   * - negative values are allowed (min < 0)
   * - current value is negative
   */
  get useSwissNegativeWorkaround(): boolean {
    if (this.formConfig?.locale !== 'de-CH') {
      return false;
    }

    if (this.config.min == null || this.config.min >= 0) {
      // Negative values are not allowed
      return false;
    }

    const value = this.currentValue;
    return typeof value === 'number' && value < 0;
  }

  /** Configured currency code (e.g. "CHF", "EUR") */
  get currency(): string | undefined {
    return this.config.inputNumberSettings?.currency;
  }

  /**
   * Determines the InputNumber mode.
   * Uses 'decimal' for the Swiss negative workaround, otherwise 'currency' if a currency is defined.
   */
  get inputMode(): 'currency' | 'decimal' {
    if (this.useSwissNegativeWorkaround) {
      return 'decimal';
    }
    return this.currency ? 'currency' : 'decimal';
  }

  /**
   * Prefix used only for Swiss negative values
   * (e.g. "CHF -3.00" instead of PrimeNG currency formatting).
   */
  get inputPrefix(): string | undefined {
    if (this.useSwissNegativeWorkaround && this.currency) {
      return `${this.currency} `;
    }
    return undefined;
  }

  /**
   * Currency passed to PrimeNG.
   * Must be undefined (not null) when the workaround is active,
   * otherwise PrimeNG throws an error.
   */
  get inputCurrency(): string | undefined {
    if (this.useSwissNegativeWorkaround) {
      return undefined;
    }
    return this.currency;
  }
}

