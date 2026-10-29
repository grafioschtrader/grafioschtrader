import { Directive, ElementRef, input, inject } from '@angular/core';

@Directive({
  selector: '[upperCase]',
  host: { '(input)': 'onInput($event)' }
})
export class UpperCaseDirective {
  ref = inject(ElementRef);

  readonly allowUpperCase = input<boolean>(undefined, { alias: 'upperCase' });

  onInput(event: Event) {
    if (this.allowUpperCase()) {
      this.ref.nativeElement.value = (event.target as HTMLInputElement).value.toUpperCase();
    }
  }
}
