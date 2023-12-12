import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {ProcessedAction} from '../../shared/types/processed.action';
import {MenuItem} from 'primeng/api';

/**
 * Test for Wizard - NOT used yet.
 */
@Component({
  selector: 'algo-strategy-create-wizard',
  template: `
    <p-dialog header="{{'STRATEGY_CREATE' | translate}}" [(visible)]="visibleDialog"
              [responsive]="true"  [style]="{width: '400px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <pe-steps [(activeIndex)]="activeIndex" styleClass="steps-custom">
        <pe-step label="First Step">
          Step 1
          <button pButton label="Go" (click)="next()"></button>
        </pe-step>
        <pe-step label="2nd Step">
          Step 2
          <button pButton label="Go" (click)="next()"></button>
        </pe-step>
        <pe-step label="3 Step">
          Step 3
          <button pButton label="Ok" (click)="ok()"></button>
        </pe-step>
      </pe-steps>


    </p-dialog>`
})
export class AlgoRuleStrategyCreateWizardComponent implements OnInit {
  // Input from parent view
  @Input() visibleDialog: boolean;
  @Input() callParam: CallParam;

  // Output for parent view
  @Output() closeDialog = new EventEmitter<ProcessedActionData>();

  items: MenuItem[];
  activeIndex = 0;

  ngOnInit(): void {

  }

  public onShow(event) {

  }

  onHide(event) {
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
  }

  next() {
    this.activeIndex++;
  }

  ok() {
    this.activeIndex = 0;
  }

}
