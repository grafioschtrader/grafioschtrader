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

import {StepComponent} from './step.component';
import {MenuItem} from 'primeng/api';

@Component({
  selector: 'pe-steps',
  template: `
    <p-steps [model]="items" [(activeIndex)]="activeIndex"
             [styleClass]="styleClass" [readonly]="true"></p-steps>
    <ng-content></ng-content>
    <button pButton type="text" *ngIf="activeIndex > 0"
            (click)="previous()" icon="pi pi-chevron-left" label="Previous"></button>
    <button pButton type="text" *ngIf="activeIndex < items.length - 1"
            (click)="next()" icon="pi pi-chevron-right" iconPos="right" label="Next"></button>
  `
})
export class StepsComponent implements AfterContentInit, OnChanges {
  @Input() activeIndex = 0;
  @Input() styleClass: string;
  @Input() stepClass: string;
  @Output() activeIndexChange: EventEmitter<any> = new EventEmitter();
  @Output() activeLabelChange = new EventEmitter();

  items: MenuItem[] = [];

  @ContentChildren(StepComponent) steps: QueryList<StepComponent>;

  ngAfterContentInit() {
    this.steps.toArray().forEach((step: StepComponent, index: number) => {
      if (!step.styleClass) {
        // set style class if it was not set on step component directly
        step.styleClass = this.stepClass;
      }

      if (index === this.activeIndex) {
        // show this step on init
        step.active = true;
      }

      this.items[index] = {
        label: step.label,
        command: (event: any) => {
          // hide all steps
          this.steps.toArray().forEach((s: StepComponent) => s.active = false);

          // show the step the user has clicked on.
          step.active = true;
          this.activeIndex = index;

          // emit currently selected index (two-way binding)
          this.activeIndexChange.emit(index);
          // emit currently selected label
          this.activeLabelChange.next(step.label);
        }
      };
    });
  }

  ngOnChanges(changes: SimpleChanges) {
    if (!this.steps) {
      // we could also check changes['activeIndex'].isFirstChange()
      return;
    }

    for (const prop in changes) {
      if (prop === 'activeIndex') {
        const curIndex = changes[prop].currentValue;
        this.steps.toArray().forEach((step: StepComponent, index: number) => {
          // show / hide the step
          const selected = index === curIndex;
          step.active = selected;

          if (selected) {
            // emit currently selected label
            this.activeLabelChange.next(step.label);
          }
        });
      }
    }
  }

  private previous() {
    this.activeIndex--;
    // emit currently selected index (two-way binding)
    this.activeIndexChange.emit(this.activeIndex);
    // show / hide steps and emit selected label
    this.ngOnChanges({
      activeIndex: {
        currentValue: this.activeIndex,
        previousValue: this.activeIndex + 1,
        firstChange: false,
        isFirstChange: () => false
      }
    });
  }

  private next() {
    this.activeIndex++;
    // emit currently selected index (two-way binding)
    this.activeIndexChange.emit(this.activeIndex);
    // show / hide steps and emit selected label
    this.ngOnChanges({
      activeIndex: {
        currentValue: this.activeIndex,
        previousValue: this.activeIndex - 1,
        firstChange: false,
        isFirstChange: () => false
      }
    });
  }
}
