import {Component, Input} from '@angular/core';
import {CommonModule} from '@angular/common';

@Component({
  selector: 'pe-step',
  styles: ['.pe-step-container {padding: 45px 25px 45px 25px; margin-bottom: 20px;}'],
  template: `
    @if (active) {
      <div [ngClass]="'ui-widget-content ui-corner-all pe-step-container'" [class]="styleClass">
        <ng-content></ng-content>
      </div>
    }
  `,
  standalone: true,
  imports: [CommonModule]
})
export class StepComponent {
  @Input() styleClass: string;
  @Input() label: string;
  active = false;
}
