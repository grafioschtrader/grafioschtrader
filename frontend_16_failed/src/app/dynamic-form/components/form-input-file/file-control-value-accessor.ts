import {Directive, HostListener} from '@angular/core';
import {ControlValueAccessor, NG_VALUE_ACCESSOR} from '@angular/forms';

@Directive({
  selector: 'input[type=file]',
  providers: [
    {provide: NG_VALUE_ACCESSOR, useExisting: FileValueAccessorDirective, multi: true}
  ]
})
export class FileValueAccessorDirective implements ControlValueAccessor {
  @HostListener('change', ['$event.target.files']) onChange = (_) => {
  };
  @HostListener('blur') onTouched = () => {
  };

  writeValue(value) {
  }

  registerOnChange(fn: any) {
    this.onChange = fn;
  }

  registerOnTouched(fn: any) {
    this.onTouched = fn;
  }
}
