import { Directive } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';

@Directive({
  selector: '[requiredFile]',
  providers: [{ provide: NG_VALIDATORS, useExisting: FileRequiredValidator, multi: true }]
})
export class FileRequiredValidator implements Validator {
  static validate(c: AbstractControl): ValidationErrors | null {
    return c.value == null || c.value.length === 0 ? { required: true } : null;
  }

  validate(c: AbstractControl): ValidationErrors | null {
    return FileRequiredValidator.validate(c);
  }
}
