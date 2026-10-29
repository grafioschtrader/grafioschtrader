import { Directive } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

@Directive({
  selector: 'input[type=file]',
  host: {
    '(change)': 'onChange($any($event.target).files)',
    '(blur)': 'onTouched()'
  },
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: FileValueAccessorDirective, multi: true }]
})
export class FileValueAccessorDirective implements ControlValueAccessor {
  onChange = (_: FileList | null) => {};
  onTouched = () => {};

  writeValue(value: unknown) {}

  registerOnChange(fn: any) {
    this.onChange = fn;
  }

  registerOnTouched(fn: any) {
    this.onTouched = fn;
  }
}
