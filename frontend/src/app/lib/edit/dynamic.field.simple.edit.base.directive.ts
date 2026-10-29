import { SimpleFormEditBase } from './simple.form.edit.base';
import { Directive } from '@angular/core';

/**
 * Abstract base directive for Optimus UI editing dialogs with dynamic fields.
 * Extends SimpleEditBase to provide specialized functionality for dynamic field editing.
 */
@Directive()
export abstract class DynamicFieldSimpleEditBase extends SimpleFormEditBase {
  /**
   * Override of the onShow method to initialize the component when dialog becomes visible. Unlike SimpleEditBase it
   * initializes at once, without deferring to the next macrotask.
   *
   * @override
   * @param {any} event - The dialog show event
   * @returns {void}
   */
  public override onShow(event: unknown): void {
    this.renewDialogSession();
    this.initialize();
    this.viewChanged();
  }
}
