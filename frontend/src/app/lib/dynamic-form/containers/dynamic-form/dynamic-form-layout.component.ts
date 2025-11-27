import {Component, Input} from '@angular/core';
import {UntypedFormGroup} from '@angular/forms';


import {FieldConfig} from '../../models/field.config';
import {FormConfig} from '../../models/form.config';

/**
 * It handles one input with its label.
 */
@Component({
  selector: 'dynamic-form-layout',
  styles: [
    ':host { display: contents; }',
    `.dynamic-form-row {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
    }`,
    '.dynamic-form-row .dynamic-form-label, .dynamic-form-row .dynamic-form-control { flex: 0 0 100%; max-width: 100%; }',
    `.dynamic-form-row .dynamic-form-label {
      text-align: right;
      padding-right: 0.5rem;
      box-sizing: border-box;
    }`,
    `@media (min-width: 768px) {
      .dynamic-form-row .dynamic-form-label {
        flex: 0 0 var(--df-label-percent);
        max-width: var(--df-label-percent);
      }
      .dynamic-form-row .dynamic-form-control {
        flex: 0 0 var(--df-control-percent);
        max-width: var(--df-control-percent);
      }
    }`
  ],
  template: `
    @if (formConfig.fieldHeaders && formConfig.fieldHeaders[config.field]) {
      <div class="row">
        <div class="col-md-12">
          <h5 class="text-center">{{ formConfig.fieldHeaders[config.field] }}</h5>
        </div>
      </div>
    }

    <div [ngClass]="'small-padding col-md-' + (config.usedLayoutColumns? config.usedLayoutColumns: 12)">
      <div class="mb-3 dynamic-form-row"
           [style.--df-label-percent]="getLabelPercent(config)"
           [style.--df-control-percent]="getControlPercent(config)"
           [hidden]="config.invisible">
        @if (hasVisibleLabel(config)) {
          <label [title]="config.labelTitle !== undefined? config.labelTitle: ''"
                 [style.color]='config.labelTitle !== undefined? "red": null'
                 [for]="config.field"
                 class="small-padding form-label dynamic-form-label">
            {{ config.labelKey.startsWith('*') ? config.labelKey.slice(1) : config.labelKey | translate }} {{ config.labelSuffix }}
            @if (config.labelHelpText && !config.labelHelpText.startsWith('*')) {
              <i class="fa fa-question-circle-o" (click)="onHelpClick($event)"></i>
            }
          </label>
        } @else if (shouldReserveLabelSpace(config)) {
          <!-- Invisible placeholder to maintain vertical alignment -->
          <div class="dynamic-form-label"></div>
        }

        <div class="small-padding dynamic-form-control">
          <dynamicField
            [config]="config"
            [formConfig]="formConfig"
            [group]="group">
          </dynamicField>
        </div>
        @if (!config.buttonInForm) {
          <error-message [baseFieldFieldgroupConfig]="config"></error-message>
        }
        @if (config.labelShowText) {
          <div class="bg-info" [innerHTML]="config.labelShowText"></div>
        }
      </div>
    </div>
  `,
  standalone: false
})

export class DynamicFormLayoutComponent {
  @Input() config: FieldConfig;
  @Input() formConfig: FormConfig;
  @Input() group: UntypedFormGroup;

  onHelpClick(event) {
    this.config.labelShowText = (this.config.labelShowText) ? null : this.config.labelHelpText;
  }

  getLabelPercent(fieldConfig: FieldConfig): string {
    const usedColumns = fieldConfig.usedLayoutColumns || 12;
    if (!this.formConfig?.labelColumns) {
      return '0%';
    }
    if (!this.hasVisibleLabel(fieldConfig) && !this.shouldReserveLabelSpace(fieldConfig)) {
      return '0%';
    }
    const labelColumns = Math.min(this.formConfig.labelColumns, usedColumns);
    return this.formatPercent(labelColumns / usedColumns * 100);
  }

  getControlPercent(fieldConfig: FieldConfig): string {
    if (!this.formConfig?.labelColumns) {
      return '100%';
    }
    if (!this.hasVisibleLabel(fieldConfig) && !this.shouldReserveLabelSpace(fieldConfig)) {
      return '100%';
    }
    const usedColumns = fieldConfig.usedLayoutColumns || 12;
    const labelColumns = Math.min(this.formConfig.labelColumns, usedColumns);
    const controlPercent = 100 - (labelColumns / usedColumns * 100);
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
    return this.formConfig?.labelColumns > 0;
  }
}
