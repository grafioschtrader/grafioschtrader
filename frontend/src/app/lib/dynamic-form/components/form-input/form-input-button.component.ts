import { Component } from '@angular/core';
import { BaseInputComponent } from '../base.input.component';

import { ReactiveFormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { ButtonModule } from '@openng/optimus-ui/button';
import { InputGroup } from '@openng/optimus-ui/inputgroup';
import { InputGroupAddon } from '@openng/optimus-ui/inputgroupaddon';
import { UpperCaseDirective } from './upper-case.directive';

/**
 * Creates an input field with a following button. The button is disabled only when `config.disabled` is true: an
 * attribute binding with `false` would still render `disabled="false"`, which the browser treats as disabled, so a
 * false or absent value removes the attribute.
 */
@Component({
  selector: 'form-input-button',
  template: `
    @let config = this.config();
    @let formConfig = this.formConfig();
    @let group = this.group();
    @let rev = notifier?.revision();
    <ng-container [formGroup]="group">
      <p-inputgroup>
        <input
          #input
          [style.background-color]="'white'"
          [class.required-input]="isRequired"
          class="p-inputtext"
          [type]="
            config.dataType === DataType.TimeString
              ? 'time'
              : config.dataType === DataType.Numeric || config.dataType === DataType.NumericInteger
                ? 'number'
                : config.dataType === DataType.Password
                  ? 'password'
                  : config.dataType === DataType.Email
                    ? 'email'
                    : 'text'
          "
          [attr.placeholder]="config.placeholder"
          [formControlName]="fieldName"
          readonly
          [id]="fieldName"
          [min]="config.min"
          [max]="config.max"
          [upperCase]="config.upperCase" />
        @if (config.fieldSuffix) {
          <p-inputgroup-addon>{{ config.fieldSuffix | translate }}</p-inputgroup-addon>
        }
        <button pButton type="button" [attr.disabled]="config.disabled ? '' : null" (click)="config.buttonFN?.(config)">
          <i [class]="config.icon || 'pi pi-search'" pButtonIcon></i>
        </button>
      </p-inputgroup>
    </ng-container>
  `,
  imports: [ReactiveFormsModule, TranslateModule, ButtonModule, InputGroup, InputGroupAddon, UpperCaseDirective]
})
export class FormInputButtonComponent extends BaseInputComponent {}
