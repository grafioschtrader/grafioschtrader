import { Directive, input, model, output } from '@angular/core';
import { FormValueControl } from '@angular/forms/signals';

/**
 * Thin FormValueControl between a signal form field and an Optimus input component. The Optimus inputs derive from
 * BaseInput, whose `pattern` input is a string, while the FormField directive binds `pattern` as `RegExp[]`; with
 * strict templates `[formField]` can therefore not be placed on an Optimus element itself. The bridge carries
 * `[formField]` on a wrapping element instead, and the Optimus component binds the bridge with ngModel:
 *
 * ```html
 * <span signalValueBridge #bridge="signalValueBridge" [formField]="field()" style="display: contents">
 *   <p-inputNumber [ngModel]="bridge.value()" (ngModelChange)="bridge.value.set($event)"
 *     [disabled]="bridge.disabled()" (onBlur)="bridge.touch.emit()" />
 * </span>
 * ```
 *
 * The Optimus components stay unchanged.
 */
@Directive({
  selector: '[signalValueBridge]',
  exportAs: 'signalValueBridge'
})
export class SignalValueBridgeDirective implements FormValueControl<unknown> {
  /** Value of the field, written back by the Optimus component. */
  readonly value = model<unknown>(null);
  /** Disabled state of the field, from the schema. */
  readonly disabled = input(false);
  /** Readonly state of the field, from the schema. */
  readonly readonly = input(false);
  /** Maximum length of a text field, from the schema; undefined without a length rule. */
  readonly maxLength = input<number>();
  /** Marks the field touched; emitted on blur of the Optimus component. */
  readonly touch = output<void>();
}
