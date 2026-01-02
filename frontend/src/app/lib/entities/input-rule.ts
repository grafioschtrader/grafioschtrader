import {ValidatorFn, Validators} from '@angular/forms';
import {range} from '../validator/validator';

/**
 * Parses and validates property values against dynamic validation rules stored as DSL strings.
 * Supports validation rules for numeric values (min, max, enum) and string patterns (regex).
 *
 * Supported Rule Types:
 * - min:N - Minimum numeric value (e.g., "min:1")
 * - max:N - Maximum numeric value (e.g., "max:99")
 * - enum:N1,N2,N3 - Value must be one of the specified numbers (e.g., "enum:1,7,12,365")
 * - pattern:REGEX - String must match the regex pattern (e.g., "pattern:^[A-Z]+$")
 *
 * Example Usage:
 * const rule = InputRule.parse("min:1,max:99");
 * const validators = rule.getValidators();
 * const description = rule.getDescription();
 */
export class InputRule {

  static readonly RULE_MIN = 'min';
  static readonly RULE_MAX = 'max';
  static readonly RULE_ENUM = 'enum';
  static readonly RULE_PATTERN = 'pattern';

  min: number = null;
  max: number = null;
  enumValues: number[] = null;
  pattern: string = null;

  private constructor() {
  }

  /**
   * Parses an input rule DSL string into an InputRule object.
   *
   * @param inputRule the DSL string (e.g., "min:1,max:99" or "enum:1,7,12,365")
   * @returns parsed InputRule object, or null if inputRule is null or empty
   */
  static parse(inputRule: string): InputRule | null {
    if (!inputRule || inputRule.trim() === '') {
      return null;
    }

    const rule = new InputRule();
    // Split on comma followed by rule name (to handle enum values with commas)
    const parts = inputRule.split(/,(?=\w+:)/);

    for (const part of parts) {
      const trimmedPart = part.trim();
      const colonIndex = trimmedPart.indexOf(':');
      if (colonIndex === -1) {
        continue;
      }

      const ruleName = trimmedPart.substring(0, colonIndex).toLowerCase();
      const ruleValue = trimmedPart.substring(colonIndex + 1);

      switch (ruleName) {
        case InputRule.RULE_MIN:
          rule.min = parseInt(ruleValue.trim(), 10);
          break;
        case InputRule.RULE_MAX:
          rule.max = parseInt(ruleValue.trim(), 10);
          break;
        case InputRule.RULE_ENUM:
          rule.enumValues = ruleValue.split(',').map(v => parseInt(v.trim(), 10));
          break;
        case InputRule.RULE_PATTERN:
          rule.pattern = ruleValue;
          break;
      }
    }

    return rule;
  }

  /**
   * Returns Angular validators based on the parsed rules.
   *
   * @returns array of ValidatorFn for use with Angular forms
   */
  getValidators(): ValidatorFn[] {
    const validators: ValidatorFn[] = [];

    if (this.min !== null && this.max !== null) {
      validators.push(range([this.min, this.max]));
    } else {
      if (this.min !== null) {
        validators.push(Validators.min(this.min));
      }
      if (this.max !== null) {
        validators.push(Validators.max(this.max));
      }
    }

    if (this.enumValues !== null && this.enumValues.length > 0) {
      validators.push(this.enumValidator(this.enumValues));
    }

    if (this.pattern !== null) {
      validators.push(Validators.pattern(this.pattern));
    }

    return validators;
  }

  /**
   * Returns a human-readable description of the validation rules for display purposes.
   *
   * @returns formatted rule description
   */
  getDescription(): string {
    const descriptions: string[] = [];

    if (this.min !== null) {
      descriptions.push(`${InputRule.RULE_MIN}: ${this.min}`);
    }
    if (this.max !== null) {
      descriptions.push(`${InputRule.RULE_MAX}: ${this.max}`);
    }
    if (this.enumValues !== null) {
      descriptions.push(`${InputRule.RULE_ENUM}: ${this.enumValues.sort((a, b) => a - b).join(', ')}`);
    }
    if (this.pattern !== null) {
      descriptions.push(`${InputRule.RULE_PATTERN}: ${this.pattern}`);
    }

    return descriptions.join(' | ');
  }

  /**
   * Creates a custom validator for enum values.
   */
  private enumValidator(allowedValues: number[]): ValidatorFn {
    return (control) => {
      if (control.value === null || control.value === undefined || control.value === '') {
        return null;
      }
      const value = +control.value;
      return allowedValues.includes(value) ? null : {enum: {allowedValues, actualValue: value}};
    };
  }
}
