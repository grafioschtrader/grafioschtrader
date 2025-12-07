import {
  AfterContentInit,
  Component,
  ContentChildren,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  QueryList,
  SimpleChanges
} from '@angular/core';

import {StepperModule} from 'primeng/stepper';
import {ButtonModule} from 'primeng/button';
import { StepComponent } from './step.component';

@Component({
  selector: 'pe-steps',
  template: `
    <p-stepper [value]="activeIndex + 1" [class]="styleClass">
      <p-step-list>
        @for (step of steps.toArray(); track step; let i = $index) {
          <p-step
            [value]="i + 1"
            [class]="step.styleClass || stepClass"
            (click)="onStepClick(i)">
            {{ step.label }}
          </p-step>
        }
      </p-step-list>
    </p-stepper>

    <!-- Content is rendered outside stepper -->
    <ng-content></ng-content>

    <!-- Navigation buttons -->
    <div class="flex pt-6 justify-between">
      @if (activeIndex > 0) {
        <p-button
          type="button"
          (click)="previous()"
          severity="secondary">
          <i class="pi pi-chevron-left mr-2"></i>
          Previous
        </p-button>
      }

      @if (activeIndex < steps.length - 1) {
        <p-button
          type="button"
          (click)="next()">
          Next
          <i class="pi pi-chevron-right ml-2"></i>
        </p-button>
      }
    </div>
  `,
  standalone: true,
  imports: [StepperModule, ButtonModule]
})
export class StepsComponent implements AfterContentInit, OnChanges {
  @Input() activeIndex = 0;
  @Input() styleClass: string;
  @Input() stepClass: string;
  @Output() activeIndexChange: EventEmitter<number> = new EventEmitter();
  @Output() activeLabelChange = new EventEmitter<string>();

  @ContentChildren(StepComponent) steps: QueryList<StepComponent>;

  ngAfterContentInit() {
    this.updateSteps();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (!this.steps) {
      return;
    }

    if (changes['activeIndex']) {
      this.updateSteps();
    }
  }

  private updateSteps() {
    this.steps.toArray().forEach((step: StepComponent, index: number) => {
      if (!step.styleClass) {
        step.styleClass = this.stepClass;
      }

      step.active = index === this.activeIndex;

      if (step.active) {
        this.activeLabelChange.emit(step.label);
      }
    });
  }

  onStepClick(index: number) {
    this.activeIndex = index;
    this.activeIndexChange.emit(this.activeIndex);
    this.updateSteps();
  }

  previous() {
    if (this.activeIndex > 0) {
      this.activeIndex--;
      this.activeIndexChange.emit(this.activeIndex);
      this.updateSteps();
    }
  }

  next() {
    if (this.activeIndex < this.steps.length - 1) {
      this.activeIndex++;
      this.activeIndexChange.emit(this.activeIndex);
      this.updateSteps();
    }
  }
}
