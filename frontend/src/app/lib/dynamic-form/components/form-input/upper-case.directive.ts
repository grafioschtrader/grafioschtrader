import { Directive, ElementRef, Input } from '@angular/core';

@Directive({
  selector: '[upperCase]',
  host: { '(input)': 'onInput($event)' }
})
export class UpperCaseDirective {
  @Input('upperCase') allowUpperCase: boolean;
  constructor(public ref: ElementRef) {}

  onInput(event) {
    if (this.allowUpperCase) {
      this.ref.nativeElement.value = event.target.value.toUpperCase();
    }
  }
}
