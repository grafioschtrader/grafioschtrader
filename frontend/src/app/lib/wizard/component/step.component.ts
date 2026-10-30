import { Component, signal, input, linkedSignal } from '@angular/core';

@Component({
  selector: 'pe-step',
  styles: ['.pe-step-container {padding: 45px 25px 45px 25px; margin-bottom: 20px;}'],
  template: `
    @if (active()) {
      <div class="ui-widget-content ui-corner-all pe-step-container" [class]="styleClass()">
        <ng-content />
      </div>
    }
  `,
  imports: []
})
export class StepComponent {
  readonly styleClassInput = input<string>(undefined, { alias: 'styleClass' });
  readonly styleClass = linkedSignal(() => this.styleClassInput());
  readonly label = input<string>();
  /** Set by the surrounding StepsComponent */
  readonly active = signal(false);
}
