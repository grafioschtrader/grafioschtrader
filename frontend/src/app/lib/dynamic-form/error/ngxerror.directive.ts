import {
  Directive,
  ElementRef,
  forwardRef,
  Inject,
  Input,
  OnDestroy,
  OnInit,
  Renderer2,
  signal,
  untracked
} from '@angular/core';
import { of, startWith, Subscription, switchMap } from 'rxjs';
import { NgxErrorsDirective } from './ngxerrors.directive';
import { ErrorOptions } from './ngxerrors';
import { toArray } from './toArray';
import { RuleEvent } from './error.message.rules';

/**
 * One error message of a control. It is shown when the control has one of the named errors and every rule is met,
 * for example 'touched' or 'dirty'. A message with the rule {@link RuleEvent.FOCUSOUT} shows only after the focus has
 * left the input element once.
 *
 * The visibility is recomputed from the events of the control and from the focusout listener, never by polling, so a
 * programmatic updateValueAndValidity() shows or hides the message as well.
 */
@Directive({
  selector: '[ngxError]',
  standalone: true,
  host: { '[hidden]': 'hidden()' }
})
export class NgxErrorDirective implements OnInit, OnDestroy {
  /** Whether the message is hidden; bound to the hidden property of the host element */
  readonly hidden = signal(true);

  private overrule = false;
  private rules: string[] = [];
  private errorNames: string[] = [];
  private element: ElementRef;
  private subscription: Subscription;
  private focusOutListener: () => void;

  constructor(
    @Inject(forwardRef(() => NgxErrorsDirective))
    private ngxErrors: NgxErrorsDirective,
    private renderer: Renderer2
  ) {}

  /** Element of the input; set once the input component is rendered, which may be after this directive */
  @Input()
  set elementRef(value: ElementRef) {
    if (value !== this.element) {
      this.removeFocusOutListener();
      this.element = value;
      this.addFocusOutListener();
    }
  }

  @Input()
  set ngxError(value: ErrorOptions) {
    this.errorNames = toArray(value);
    this.recompute();
  }

  @Input()
  set when(value: ErrorOptions) {
    this.rules = toArray(value);
    this.removeFocusOutListener();
    this.addFocusOutListener();
    this.recompute();
  }

  ngOnInit(): void {
    this.subscription = this.ngxErrors.control$
      .pipe(switchMap((control) => (control ? control.events.pipe(startWith(null)) : of(null))))
      .subscribe(() => this.recompute());
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
    this.removeFocusOutListener();
  }

  private recompute(): void {
    const control = this.ngxErrors.control;
    const rulesMet = !!control && this.rules.every((rule) => !!(control as any)[rule]);
    const visible =
      !!control && (rulesMet || this.overrule) && this.errorNames.some((errorName) => control.hasError(errorName));
    untracked(() => this.hidden.set(!visible));
  }

  private addFocusOutListener(): void {
    if (!this.focusOutListener && this.element?.nativeElement && this.rules.indexOf(RuleEvent.FOCUSOUT) >= 0) {
      this.focusOutListener = this.renderer.listen(this.element.nativeElement, RuleEvent.FOCUSOUT, () => {
        this.overrule = true;
        this.recompute();
      });
    }
  }

  private removeFocusOutListener(): void {
    this.focusOutListener?.();
    this.focusOutListener = null;
  }
}
