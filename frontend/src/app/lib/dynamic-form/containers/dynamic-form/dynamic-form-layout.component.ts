import { Component, input } from '@angular/core';
import { UntypedFormGroup } from '@angular/forms';

import { FieldConfig } from '../../models/field.config';
import { FormConfig } from '../../models/form.config';
import { TranslateModule } from '@ngx-translate/core';

import { DynamicFieldDirective } from '../../components/dynamic-field/dynamic-field.directive';
import { ErrorMessageComponent } from './error-message.compoent';
import { FormNotifier } from '../../models/form.notifier';

/**
 * It handles one input with its label.
 */
@Component({
  selector: 'dynamic-form-layout',
  styles: [
    ':host { display: contents; }',
    `
      .dynamic-form-row {
        display: flex;
        flex-wrap: wrap;
        align-items: baseline;
      }
    `,
    '.dynamic-form-row .dynamic-form-label, .dynamic-form-row .dynamic-form-control { flex: 0 0 100%; max-width: 100%; }',
    `
      .dynamic-form-row .dynamic-form-label {
        text-align: right;
        padding-right: 0.5rem;
        box-sizing: border-box;
      }
    `,
    `
      @media (min-width: 768px) {
        .dynamic-form-row .dynamic-form-label {
          flex: 0 0 var(--df-label-percent);
          max-width: var(--df-label-percent);
        }
        .dynamic-form-row .dynamic-form-control {
          flex: 0 0 var(--df-control-percent);
          max-width: var(--df-control-percent);
        }
      }
    `
  ],
  template: `
    @let configValue = config();
    @let formConfigValue = formConfig();

    @let rev = notifier?.revision();
    @if (formConfigValue.fieldHeaders && formConfigValue.fieldHeaders[configValue.field]) {
      <div class="row">
        <div class="col-md-12">
          <h5 class="text-center">
            {{ formConfigValue.fieldHeaders[configValue.field] }}
          </h5>
        </div>
      </div>
    }

    <div [class]="'small-padding col-md-' + (configValue.usedLayoutColumns ? configValue.usedLayoutColumns : 12)">
      <div
        class="mb-3 dynamic-form-row"
        [style.--df-label-percent]="getLabelPercent(configValue)"
        [style.--df-control-percent]="getControlPercent(configValue)"
        [hidden]="configValue.invisible">
        @if (hasVisibleLabel(configValue)) {
          <label
            [title]="configValue.labelTitle !== undefined ? configValue.labelTitle : ''"
            [style.color]="configValue.labelTitle !== undefined ? 'red' : null"
            [for]="configValue.field"
            class="small-padding form-label dynamic-form-label">
            {{
              configValue.labelKey.startsWith('*') ? configValue.labelKey.slice(1) : (configValue.labelKey | translate)
            }}
            {{ configValue.labelSuffix }}
            @if (configValue.labelHelpText && !configValue.labelHelpText.startsWith('*')) {
              <i class="fa fa-question-circle-o" (click)="onHelpClick($event)"></i>
            }
          </label>
        } @else if (shouldReserveLabelSpace(configValue)) {
          <!-- Invisible placeholder to maintain vertical alignment -->
          <div class="dynamic-form-label"></div>
        }

        <div class="small-padding dynamic-form-control">
          <dynamicField [config]="configValue" [formConfig]="formConfigValue" [group]="group()" />
        </div>
        @if (!configValue.buttonInForm) {
          <error-message [baseFieldFieldgroupConfig]="configValue" />
        }
        @if (configValue.labelShowText) {
          <div class="bg-info" [innerHTML]="configValue.labelShowText"></div>
        }
      </div>
    </div>
  `,
  imports: [TranslateModule, DynamicFieldDirective, ErrorMessageComponent]
})
export class DynamicFormLayoutComponent {
  readonly config = input.required<FieldConfig>();
  readonly formConfig = input.required<FormConfig>();
  readonly group = input.required<UntypedFormGroup>();

  /** Change notification of the form this field belongs to; the template reads its revision. */
  get notifier(): FormNotifier | undefined {
    return FormNotifier.of(this.config());
  }

  onHelpClick(event: unknown) {
    this.config().labelShowText = this.config().labelShowText ? null : this.config().labelHelpText;
  }

  getLabelPercent(fieldConfig: FieldConfig): string {
    const usedColumns = fieldConfig.usedLayoutColumns || 12;
    if (!this.formConfig()?.labelColumns) {
      return '0%';
    }
    if (!this.hasVisibleLabel(fieldConfig) && !this.shouldReserveLabelSpace(fieldConfig)) {
      return '0%';
    }
    const labelColumns = Math.min(this.formConfig().labelColumns, usedColumns);
    return this.formatPercent((labelColumns / usedColumns) * 100);
  }

  getControlPercent(fieldConfig: FieldConfig): string {
    if (!this.formConfig()?.labelColumns) {
      return '100%';
    }
    if (!this.hasVisibleLabel(fieldConfig) && !this.shouldReserveLabelSpace(fieldConfig)) {
      return '100%';
    }
    const usedColumns = fieldConfig.usedLayoutColumns || 12;
    const labelColumns = Math.min(this.formConfig().labelColumns, usedColumns);
    const controlPercent = 100 - (labelColumns / usedColumns) * 100;
    return this.formatPercent(controlPercent);
  }

  private formatPercent(value: number): string {
    const rounded = Math.max(0, Math.min(100, Math.round(value * 1000) / 1000));
    return `${rounded}%`;
  }

  hasVisibleLabel(fieldConfig: FieldConfig): boolean {
    return !!fieldConfig.labelKey && !fieldConfig.buttonInForm && !fieldConfig.labelKey.startsWith('_');
  }

  shouldReserveLabelSpace(fieldConfig: FieldConfig): boolean {
    if (!fieldConfig.labelKey) {
      return false;
    }
    if (fieldConfig.labelKey.startsWith('_')) {
      return false;
    }
    if (fieldConfig.buttonInForm) {
      return false;
    }
    return this.formConfig()?.labelColumns > 0;
  }
}
