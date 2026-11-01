import { Component } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { TranslateModule } from '@ngx-translate/core';
import { KeyFilterModule } from '@openng/optimus-ui/keyfilter';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { UpperCaseDirective } from '../../components/form-input/upper-case.directive';
import { FilterOutPipe } from '../../pipe/FilterOutPipe';
import { SignalBaseInputComponent } from './signal-base-input.component';

/** Text, number, time, password and email input of a signal dynamic form. */
@Component({
  selector: 'signal-form-input',
  template: `
    @let config = this.view();
    <input
      #input
      [formField]="field()"
      [style.width]="config.inputWidth + 'em'"
      [class.required-input]="isRequired()"
      class="input-sm"
      [class.form-control-fluid]="config.fieldSuffix"
      [class.form-control]="!config.fieldSuffix"
      [class.text-end]="config.dataType === DataType.Numeric || config.dataType === DataType.NumericInteger"
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
      [pKeyFilter]="
        config.dataType === DataType.NumericInteger ? 'int' : config.dataType === DataType.Numeric ? 'num' : null
      "
      [attr.placeholder]="config.placeholder"
      [attr.size]="config.maxLength ? config.inputWidth || config.maxLength : null"
      pTooltip="{{
        config.maxLength && config.labelHelpText?.startsWith('*')
          ? config.labelHelpText.slice(1)
          : (config.labelKey + '_TOOLTIP' | translate | filterOut: config.labelKey + '_TOOLTIP')
      }}"
      [id]="fieldName"
      [upperCase]="config.upperCase" />
    {{ config.fieldSuffix | translate }}
  `,
  imports: [FormField, TranslateModule, FilterOutPipe, KeyFilterModule, TooltipModule, UpperCaseDirective]
})
export class SignalFormInputComponent extends SignalBaseInputComponent<string | number | null> {}
