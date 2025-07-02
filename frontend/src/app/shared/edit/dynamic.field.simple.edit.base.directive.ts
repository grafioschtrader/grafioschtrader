import {SimpleEditBase} from './simple.edit.base';
import {Directive} from '@angular/core';

/**
 * Intended for an editing PrimeNG dialog with dynamic fields.
 */
@Directive()
export abstract class DynamicFieldSimpleEditBase extends SimpleEditBase {
  public override onShow(event) {
    this.initialize();
  }
}
