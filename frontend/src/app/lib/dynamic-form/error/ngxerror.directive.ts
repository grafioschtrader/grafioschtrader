import {
  Directive,
  ElementRef,
  forwardRef,
  Inject,
  Renderer2,
  computed,
  effect,
  input,
  signal,
  untracked
} from '@angular/core';
import { NgxErrorsDirective } from './ngxerrors.directive';
import { ErrorOptions } from './ngxerrors';
import { toArray } from './toArray';
import { RuleEvent } from './error.message.rules';

/**
 * One error message of a control. It is shown when the control has one of the named errors and every rule is met,
 * for example 'touched' or 'dirty'. A message with the rule {@link RuleEvent.FOCUSOUT} shows only after the focus has
 * left the input element once.
 *
 * The visibility follows the control signal, its events and the focusout listener, never polling. Effects replace
 * the subscription and listener when their inputs change and dispose them with the directive.
 */
@Directive({
  selector: '[ngxError]',
  host: { '[hidden]': 'hidden()' }
})
export class NgxErrorDirective {
  readonly hidden = signal(true);
  /** Element of the input; it may arrive after the error message has rendered. */
  readonly elementRef = input<ElementRef>();
  readonly ngxError = input<ErrorOptions>();
  readonly when = input<ErrorOptions>();

  private readonly rules = computed(() => toArray(this.when()));
  private readonly errorNames = computed(() => toArray(this.ngxError()));
  private overrule = false;

  constructor(
    @Inject(forwardRef(() => NgxErrorsDirective)) private ngxErrors: NgxErrorsDirective,
    private renderer: Renderer2
  ) {
    effect((onCleanup) => {
      const control = this.ngxErrors.control();
      this.rules();
      this.errorNames();
      untracked(() => this.recompute());
      const subscription = control?.events.subscribe(() => this.recompute());
      onCleanup(() => subscription?.unsubscribe());
    });
    effect((onCleanup) => {
      const element = this.elementRef();
      if (element?.nativeElement && this.rules().includes(RuleEvent.FOCUSOUT)) {
        const unlisten = this.renderer.listen(element.nativeElement, RuleEvent.FOCUSOUT, () => {
          this.overrule = true;
          this.recompute();
        });
        onCleanup(unlisten);
      }
    });
  }

  private recompute(): void {
    const control = this.ngxErrors.control();
    const rulesMet = !!control && this.rules().every((rule) => !!(control as any)[rule]);
    const visible =
      !!control && (rulesMet || this.overrule) && this.errorNames().some((name) => control.hasError(name));
    untracked(() => this.hidden.set(!visible));
  }
}
