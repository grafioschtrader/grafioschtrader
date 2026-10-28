import { Directive, ElementRef, input } from '@angular/core';

@Directive({
  selector: '[upperCase]',
  host: { '(input)': 'onInput($event)' }
})
export class UpperCaseDirective {
  readonly allowUpperCase = input<boolean>(undefined, { alias: 'upperCase' });
  constructor(public ref: ElementRef) {}

  onInput(event) {
    if (this.allowUpperCase()) {
      this.ref.nativeElement.value = event.target.value.toUpperCase();
    }
  }
}
