import {Directive, ElementRef, HostListener, Input} from '@angular/core';

@Directive({
  selector: '[upperCase]'
})
export class UpperCaseDirective {
  @Input('upperCase') allowUpperCase: boolean;
  constructor(public ref: ElementRef) { }

  @HostListener('input', ['$event']) onInput(event) {
    if(this.allowUpperCase) {
      this.ref.nativeElement.value = event.target.value.toUpperCase();
    }
  }

}
