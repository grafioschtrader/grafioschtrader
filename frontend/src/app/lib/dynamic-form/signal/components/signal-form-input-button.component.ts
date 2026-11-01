import { Component, computed } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { ButtonModule } from '@openng/optimus-ui/button';
import { InputGroup } from '@openng/optimus-ui/inputgroup';
import { InputGroupAddon } from '@openng/optimus-ui/inputgroupaddon';
import { SignalBaseInputComponent } from './signal-base-input.component';

/**
 * Read-only value with a button, whose callback sets the value. The input is not bound with `[formField]`: a
 * `readonly` rule would exclude the field from validation, and the FormField directive does not allow the readonly
 * attribute itself. The input therefore only shows the value and marks the field touched on blur.
 */
@Component({
  selector: 'signal-form-input-button',
  template: `
    @let config = this.view();
    <p-inputgroup>
      <input
        #input
        [style.background-color]="'white'"
        [class.required-input]="isRequired()"
        class="p-inputtext"
        type="text"
        [attr.placeholder]="config.placeholder"
        [value]="shownValue()"
        readonly
        [id]="fieldName"
        (blur)="field()().markAsTouched()" />
      @if (config.fieldSuffix) {
        <p-inputgroup-addon>{{ config.fieldSuffix | translate }}</p-inputgroup-addon>
      }
      <button pButton type="button" [disabled]="field()().disabled()" (click)="config.buttonFN?.(config)">
        <i [class]="config.icon || 'pi pi-search'" pButtonIcon></i>
      </button>
    </p-inputgroup>
  `,
  imports: [TranslateModule, ButtonModule, InputGroup, InputGroupAddon]
})
export class SignalFormInputButtonComponent extends SignalBaseInputComponent {
  protected readonly shownValue = computed(() => {
    const value = this.field()().value();
    return value === null || value === undefined ? '' : String(value);
  });
}
