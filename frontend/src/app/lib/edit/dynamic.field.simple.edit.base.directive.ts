import {SimpleEditBase} from './simple.edit.base';
import {Directive} from '@angular/core';

/**
 * Abstract base directive for PrimeNG editing dialogs with dynamic fields.
 * Extends SimpleEditBase to provide specialized functionality for dynamic field editing.
 */
 @Directive()
export abstract class DynamicFieldSimpleEditBase extends SimpleEditBase {
  /**
   * Override of the onShow method to initialize the component when dialog becomes visible.
   *
   * @override
   * @param {any} event - The dialog show event
   * @returns {void}
   */
  public override onShow(event): void {
    this.initialize();
  }
}
