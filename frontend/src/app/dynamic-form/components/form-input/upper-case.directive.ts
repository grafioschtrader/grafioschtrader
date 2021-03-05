import {Directive, ElementRef, Input} from '@angular/core';

@Directive({
  selector: '[upperCase]',
  host: {
    '(input)': 'toUpperCase($event.target.value)',
  }

})
export class UpperCaseDirective {
  @Input('upperCase') allowUpperCase: boolean;

  constructor(private ref: ElementRef) {
  }

  toUpperCase(value: any) {
    if (this.allowUpperCase) {
      this.ref.nativeElement.value = value.toUpperCase();
    }
  }

}
