import {SimpleEditBase} from './simple.edit.base';
import {Directive} from '@angular/core';

@Directive()
export abstract class DynamicSimpleEditBase extends SimpleEditBase {
  public onShow(event) {
    this.initialize();
  }
}
