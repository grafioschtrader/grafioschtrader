import {
  AfterViewInit,
  Directive,
  DoCheck,
  ElementRef,
  forwardRef,
  HostBinding,
  Inject,
  Input,
  OnDestroy,
  OnInit,
  Renderer2
} from '@angular/core';
import {combineLatest, Observable, Subject, Subscription} from 'rxjs';
import {distinctUntilChanged, filter, map} from 'rxjs/operators';
import {NgxErrorsDirective} from './ngxerrors.directive';
import {ErrorOptions} from './ngxerrors';
import {toArray} from './toArray';
import {RuleEvent} from './error.message.rules';

@Directive({
  selector: '[ngxError]'
})
export class NgxErrorDirective implements OnInit, OnDestroy, DoCheck, AfterViewInit {


  @Input() elementRef: ElementRef;
  @HostBinding('hidden')
  hidden = true;
  overrule = false;
  rules: string[] = [];
  errorNames: string[] = [];
  subscription: Subscription;
  _states: Subject<string[]>;
  states: Observable<string[]>;
  focusOutListener: any;

  constructor(@Inject(forwardRef(() => NgxErrorsDirective))
              private ngxErrors: NgxErrorsDirective,
              private renderer: Renderer2) {
  }

  @Input()
  set ngxError(value: ErrorOptions) {
    this.errorNames = toArray(value);
  }

  @Input()
  set when(value: ErrorOptions) {
    this.rules = toArray(value);
  }

  ngOnInit() {
    this._states = new Subject<string[]>();
    this.states = this._states.asObservable().pipe(distinctUntilChanged());

    const errorsObservable = this.ngxErrors.subject.pipe(
      filter(Boolean), filter((obj: any) => !!~this.errorNames.indexOf(obj.errorName))); //eslint-disable-line no-bitwise

    const statesObserable: Observable<boolean> = this.states.pipe(
      map(states => this.rules.every(rule => !!~states.indexOf(rule)))); //eslint-disable-line no-bitwise

    this.subscription = combineLatest([statesObserable, errorsObservable])
      .subscribe(([states, errors]) => {
        this.hidden = !((states || this.overrule) && errors.control.hasError(errors.errorName));
      });
  }

  ngDoCheck() {
    this._states.next(
      this.rules.filter((rule) =>
        (this.ngxErrors.control as any)[rule]
      )
    );
  }

  ngAfterViewInit() {
    if (this.elementRef && this.rules.indexOf(RuleEvent.FOCUSOUT) >= 0) {

      this.focusOutListener = this.renderer.listen(this.elementRef.nativeElement, RuleEvent.FOCUSOUT, (event) => {
        this.overrule = true;
        this._states.next([RuleEvent.FOCUSOUT]);
      });

    }
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    this.focusOutListener && this.focusOutListener();
  }

}
