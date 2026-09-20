import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';

/**
 * Landing page of the rule-based trading root node.
 *
 * The node above all strategies has no data of its own to show: the strategies are the children of the tree node, and
 * each of them has its own view. Until an overview across strategies is defined, this page explains what the branch
 * contains, so that the root node does not open an empty main area.
 */
@Component({
  selector: 'algo-overview',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [TranslateModule],
  template: `
    <h4>{{ 'ALGO_OVERVIEW' | translate }}</h4>
    <p>{{ 'ALGO_OVERVIEW_EXPLANATION' | translate }}</p>
  `
})
export class AlgoOverviewComponent {}
