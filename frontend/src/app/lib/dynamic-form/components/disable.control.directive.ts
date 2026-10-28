import { NgControl } from '@angular/forms';
import { Directive, effect, input, untracked } from '@angular/core';

@Directive({ selector: '[disableControl]' })
export class DisableControlDirective {
  readonly disableControl = input<boolean>();

  constructor(private ngControl: NgControl) {
    effect(() => {
      const condition = this.disableControl();
      untracked(() => this.ngControl.control?.[condition ? 'disable' : 'enable']());
    });
  }
}
