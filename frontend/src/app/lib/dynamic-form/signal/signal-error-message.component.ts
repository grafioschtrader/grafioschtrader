import { Component, computed, input } from '@angular/core';
import { FieldTree } from '@angular/forms/signals';
import { TranslateModule } from '@ngx-translate/core';
import { ValidationErrorRule } from '../models/base.field.fieldgroup.config';
import { ErrorMessageRules, RuleEvent } from '../error/error.message.rules';

/** A message to show: the error rule of the FieldConfig, or the text a server or control error carries. */
interface ShownError {
  rule?: ErrorMessageRules;
  message?: string;
}

/**
 * The error messages of one signal form field or field group. A rule of the FieldConfig shows when the field has an
 * error of the rule's name and every condition of the rule is met. 'touched' and 'focusout' both mean that the focus
 * has left the field once: signal forms mark a field touched on blur. An error without a rule, such as one reported
 * by the server, shows its own message.
 */
@Component({
  selector: 'signal-error-message',
  template: `
    <div class="row">
      @for (error of shownErrors(); track $index) {
        <div class="col-12 alert alert-danger">
          @if (error.rule; as rule) {
            {{ rule.text ?? (rule.keyi18n ?? '' | translate: { param1: rule.param1, param2: rule.param2 }) }}
          } @else {
            {{ error.message }}
          }
        </div>
      }
    </div>
  `,
  imports: [TranslateModule]
})
export class SignalErrorMessageComponent {
  /** Error rules of the field or group. */
  readonly rules = input.required<ValidationErrorRule>();
  /** The field or group; undefined renders no message. */
  readonly field = input<FieldTree<unknown>>();

  protected readonly shownErrors = computed<ShownError[]>(() => {
    const field = this.field();
    if (!field) {
      return [];
    }
    const state = field();
    const errors = state.errors();
    const rules = this.rules().errors ?? [];
    const shown: ShownError[] = rules
      .filter((rule) => errors.some((error) => error.kind === rule.name))
      .filter((rule) =>
        rule.rules.every((condition) =>
          condition === RuleEvent.DIRTY
            ? state.dirty()
            : condition === RuleEvent.TOUCHED || condition === RuleEvent.FOCUSOUT
              ? state.touched()
              : true
        )
      )
      .map((rule) => ({ rule }));
    errors
      .filter((error) => !!error.message && !rules.some((rule) => rule.name === error.kind))
      .forEach((error) => shown.push({ message: error.message }));
    return shown;
  });
}
