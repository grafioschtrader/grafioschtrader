import { Component, computed, input, signal, TemplateRef } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { UntypedFormGroup } from '@angular/forms';

import { FieldConfig } from '../../models/field.config';
import { FormConfig } from '../../models/form.config';
import { TranslateModule } from '@ngx-translate/core';

import { DynamicFieldDirective } from '../../components/dynamic-field/dynamic-field.directive';
import { ErrorMessageComponent } from './error-message.compoent';
import { FormNotifier } from '../../models/form.notifier';
import { displayConfig, SignalFieldDisplay } from '../../signal/signal-form.model';

/** Context of the field and error templates of a layout: the FieldConfig of the field. */
export interface FieldTemplateContext {
  $implicit: FieldConfig;
}

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
    @let configValue = view();
    @let formConfigValue = formConfig();

    @let rev = notifier?.revision();
    @if (configValue.fieldHeader) {
      <div class="row">
        <div class="col-md-12">
          <h5 class="text-center">
            {{ configValue.fieldHeader | translate }}
          </h5>
        </div>
      </div>
    }

    <div [class]="'small-padding col-md-' + (configValue.usedLayoutColumns ? configValue.usedLayoutColumns : 12)">
      <div
        class="mb-3 dynamic-form-row"
        [style.--df-label-percent]="getLabelPercent(configValue)"
        [style.--df-control-percent]="getControlPercent(configValue)"
        [hidden]="configValue.invisible || fieldHidden()">
        @if (hasVisibleLabel(configValue)) {
          <label
            [title]="configValue.labelTitle !== undefined ? configValue.labelTitle : ''"
            [style.color]="configValue.labelTitle !== undefined ? 'red' : null"
            [for]="configValue.field"
            class="small-padding form-label dynamic-form-label">
            {{
              configValue.labelKey?.startsWith('*') ? configValue.labelKey.slice(1) : (configValue.labelKey | translate)
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
          @if (fieldTemplate(); as fieldTemplate) {
            <ng-container [ngTemplateOutlet]="fieldTemplate" [ngTemplateOutletContext]="{ $implicit: config() }" />
          } @else {
            @let group = this.group();
            @if (group) {
              <dynamicField [config]="config()" [formConfig]="formConfigValue" [group]="group" />
            }
          }
        </div>
        @if (!configValue.buttonInForm) {
          @if (errorTemplate(); as errorTemplate) {
            <ng-container [ngTemplateOutlet]="errorTemplate" [ngTemplateOutletContext]="{ $implicit: config() }" />
          } @else {
            <error-message [baseFieldFieldgroupConfig]="config()" />
          }
        }
        @if (configValue.labelShowText) {
          <div class="bg-info" [innerHTML]="configValue.labelShowText"></div>
        }
      </div>
    </div>
  `,
  imports: [TranslateModule, NgTemplateOutlet, DynamicFieldDirective, ErrorMessageComponent]
})
export class DynamicFormLayoutComponent {
  readonly config = input.required<FieldConfig>();
  readonly formConfig = input.required<FormConfig>();
  /** Group of the reactive form; absent when the form supplies its own field through {@link fieldTemplate}. */
  readonly group = input<UntypedFormGroup>();
  /** Renders the input of the field instead of the reactive DynamicFieldDirective, for the signal form. */
  readonly fieldTemplate = input<TemplateRef<FieldTemplateContext>>();
  /** Renders the error messages of the field instead of the reactive ErrorMessageComponent, for the signal form. */
  readonly errorTemplate = input<TemplateRef<FieldTemplateContext>>();
  /** Hides the field in addition to FieldConfig.invisible, for a hidden rule of the signal form. */
  readonly fieldHidden = input(false);
  /** Display properties a rule of the signal form lays over the FieldConfig, for example a changed label. */
  readonly display = input<SignalFieldDisplay>();

  /** Raised by the help toggle, so the label help text re-renders without a FormNotifier. */
  protected readonly helpRevision = signal(0);

  /** The FieldConfig the labels are read from; the field and error templates keep the FieldConfig itself. */
  protected readonly view = computed(() => {
    this.helpRevision();
    return displayConfig(this.config(), this.display());
  });

  /** Change notification of the form this field belongs to; the template reads its revision. */
  get notifier(): FormNotifier | undefined {
    return FormNotifier.of(this.config());
  }

  onHelpClick(event: unknown) {
    this.config().labelShowText = this.config().labelShowText ? null : this.config().labelHelpText;
    this.helpRevision.update((revision) => revision + 1);
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
