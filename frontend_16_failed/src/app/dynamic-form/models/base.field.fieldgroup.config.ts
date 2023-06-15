import {AbstractControl, ValidatorFn} from '@angular/forms';
import {ErrorMessageRules} from '../error/error.message.rules';
import {ElementRef} from '@angular/core';

/**
 * Definition for a single form input field.
 */
export interface BaseFieldFieldgroupConfig extends ValidationErrorRule {
  formControl?: AbstractControl;
  elementRef?: ElementRef;
}

export interface ValidationErrorRule {
  validation?: ValidatorFn[];
  errors?: ErrorMessageRules[];
}
