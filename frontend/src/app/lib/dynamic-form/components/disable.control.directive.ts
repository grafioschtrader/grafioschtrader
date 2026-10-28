import { NgControl } from '@angular/forms';
import { Directive, effect, input, untracked, inject } from '@angular/core';

@Directive({ selector: '[disableControl]' })
export class DisableControlDirective {
  private ngControl = inject(NgControl);

  readonly disableControl = input<boolean>();

  constructor() {
    effect(() => {
      const condition = this.disableControl();
      untracked(() => this.ngControl.control?.[condition ? 'disable' : 'enable']());
    });
  }
}
