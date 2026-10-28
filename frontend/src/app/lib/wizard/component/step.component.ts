import { Component, Input, signal } from '@angular/core';

@Component({
  selector: 'pe-step',
  styles: ['.pe-step-container {padding: 45px 25px 45px 25px; margin-bottom: 20px;}'],
  template: `
    @if (active()) {
      <div class="ui-widget-content ui-corner-all pe-step-container" [class]="styleClass">
        <ng-content />
      </div>
    }
  `,
  imports: []
})
export class StepComponent {
  @Input() styleClass: string;
  @Input() label: string;
  /** Set by the surrounding StepsComponent */
  readonly active = signal(false);
}
