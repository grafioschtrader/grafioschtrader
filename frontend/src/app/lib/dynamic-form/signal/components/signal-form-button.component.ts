import { Component, computed, Directive, inject, input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { ButtonModule } from '@openng/optimus-ui/button';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { AngularSvgIconModule } from 'angular-svg-icon';
import { FieldConfig } from '../../models/field.config';
import { FormConfig } from '../../models/form.config';
import { FilterOutPipe } from '../../pipe/FilterOutPipe';
import { SignalFormContext } from '../signal-form.model';

/**
 * Common state of the buttons of a signal dynamic form. A submit button, one without `buttonFN`, is enabled while the
 * form is valid and no submit is pending; the dialog re-enables it after a failed save through `enableSubmit()`. The
 * dialog changes the state of a button at runtime with `setButtonState()` of the form, otherwise the static
 * `disabled` and `groupItemUseOrLoading` of the FieldConfig apply.
 */
@Directive()
abstract class SignalButtonBase {
  protected readonly context = inject(SignalFormContext);

  readonly config = input.required<FieldConfig>();
  readonly formConfig = input.required<FormConfig>();

  private readonly state = computed(() => this.context.buttonState(this.config()));

  protected readonly submitDisabled = computed(
    () => !this.context.valid() || this.context.submitting() || !!this.state().disabled
  );

  protected readonly disabled = computed(() => this.state().disabled ?? !!this.config().disabled);

  protected readonly loading = computed(() => this.state().loading ?? !!this.config().groupItemUseOrLoading);

  /** Translation key of the label, changed at runtime through the button state. */
  protected readonly labelKey = computed(() => this.state().labelKey ?? this.config().labelKey);
}

/** Optimus button of a signal dynamic form. */
@Component({
  selector: 'signal-form-pbutton',
  template: `
    @let config = this.config();
    @if (!config.buttonFN) {
      <button
        pButton
        class="btn btn-primary ms-1"
        [loading]="loading()"
        type="submit"
        [label]="labelKey() | translate"
        [disabled]="submitDisabled()"
        pTooltip="{{ labelKey() + '_TOOLTIP' | translate | filterOut: labelKey() + '_TOOLTIP' }}">
        @if (config.icon) {
          <svg-icon [src]="config.icon" [svgStyle]="{ 'width.px': 16, 'height.px': 16 }" />
        }
      </button>
    } @else {
      <button
        pButton
        class="btn btn-primary ms-1"
        [class.button-no-label]="labelKey()?.endsWith('_')"
        [disabled]="disabled()"
        pTooltip="{{ labelKey() + '_TOOLTIP' | translate | filterOut: labelKey() + '_TOOLTIP' }}"
        type="button"
        [label]="labelKey()?.endsWith('_') ? '' : (labelKey() | translate)"
        (click)="config.buttonFN($event)">
        @if (config.icon) {
          <svg-icon [src]="config.icon" [svgStyle]="{ 'width.px': 16, 'height.px': 16 }" />
        }
      </button>
    }
  `,
  imports: [ButtonModule, TooltipModule, TranslateModule, FilterOutPipe, AngularSvgIconModule]
})
export class SignalFormPButtonComponent extends SignalButtonBase {}
