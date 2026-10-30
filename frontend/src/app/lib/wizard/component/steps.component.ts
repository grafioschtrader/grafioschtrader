import { Component, contentChildren, output, model, input, effect, untracked } from '@angular/core';

import { StepperModule } from '@openng/optimus-ui/stepper';
import { ButtonModule } from '@openng/optimus-ui/button';
import { StepComponent } from './step.component';

@Component({
  selector: 'pe-steps',
  template: `
    <p-stepper [value]="activeIndex() + 1" [class]="styleClass()">
      <p-step-list>
        @for (step of steps(); track step; let i = $index) {
          <p-step [value]="i + 1" [class]="step.styleClass() || stepClass()" (click)="onStepClick(i)">
            {{ step.label() }}
          </p-step>
        }
      </p-step-list>
    </p-stepper>

    <!-- Content is rendered outside stepper -->
    <ng-content />

    <!-- Navigation buttons -->
    <div class="flex pt-6 justify-between">
      @if (activeIndex() > 0) {
        <p-button type="button" (click)="previous()" severity="secondary">
          <i class="pi pi-chevron-left mr-2"></i>
          Previous
        </p-button>
      }

      @if (activeIndex() < steps().length - 1) {
        <p-button type="button" (click)="next()">
          Next
          <i class="pi pi-chevron-right ml-2"></i>
        </p-button>
      }
    </div>
  `,
  imports: [StepperModule, ButtonModule]
})
export class StepsComponent {
  readonly activeIndex = model(0);
  readonly styleClass = input<string>();
  readonly stepClass = input<string>();
  readonly activeLabelChange = output<string>();

  readonly steps = contentChildren(StepComponent);

  constructor() {
    effect(() => {
      this.steps();
      this.activeIndex();
      this.stepClass();
      untracked(() => {
        this.updateSteps();
      });
    });
  }

  private updateSteps() {
    this.steps().forEach((step: StepComponent, index: number) => {
      if (!step.styleClass()) {
        step.styleClass.set(this.stepClass());
      }

      step.active.set(index === this.activeIndex());

      if (step.active()) {
        const label = step.label();
        if (label != null) this.activeLabelChange.emit(label);
      }
    });
  }

  onStepClick(index: number) {
    this.activeIndex.set(index);
  }

  previous() {
    if (this.activeIndex() > 0) {
      this.activeIndex.update((value) => value - 1);
    }
  }

  next() {
    if (this.activeIndex() < this.steps().length - 1) {
      this.activeIndex.update((value) => value + 1);
    }
  }
}
