import { Component, computed, inject, input, signal } from '@angular/core';
import { IonItem, IonLabel, IonList, IonListHeader, IonNote, IonSegment, IonSegmentButton } from '@ionic/angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { DashboardResult } from '../../../app/lib/dashboard/dashboard.types';
import {
  HoldingMovers,
  HoldingMoversBranch,
  HoldingMoversRanking,
  HoldingMoversRow
} from '../../../app/dashboard/model/holding.movers';
import { MobileFormatService } from '../service/mobile.format.service';

/**
 * Winners or losers of the held instruments, one section per period. The two orderings of the desktop card are offered
 * as a segment, because a phone has room for one list at a time.
 */
@Component({
  selector: 'mobile-holding-movers-widget',
  imports: [IonSegment, IonSegmentButton, IonList, IonListHeader, IonItem, IonLabel, IonNote, TranslatePipe],
  template: `
    @if (movers(); as movers) {
      <ion-segment [value]="ranking()" (ionChange)="onRanking($event.detail.value)">
        <ion-segment-button value="byPercentage">
          <ion-label>{{ 'DASHBOARD_MOVERS_BY_PERCENTAGE' | translate }}</ion-label>
        </ion-segment-button>
        <ion-segment-button value="byAmount">
          <ion-label>{{ 'DASHBOARD_MOVERS_BY_AMOUNT' | translate }}</ion-label>
        </ion-segment-button>
      </ion-segment>
      @for (branch of movers.branches; track branch.branch) {
        <ion-list lines="inset">
          <ion-list-header>
            <ion-label>{{ branchLabel(branch) }}</ion-label>
          </ion-list-header>
          @for (row of branch[ranking()]; track row.idSecuritycurrency) {
            <ion-item>
              <ion-label class="ion-text-wrap">
                <h3>{{ row.name }}{{ row.substitute ? ' *' : '' }}</h3>
                @if (row.substitute) {
                  <p>{{ substituteText(row) }}</p>
                }
              </ion-label>
              <ion-note
                slot="end"
                class="figure"
                [class.gain]="row.changePercentage > 0"
                [class.loss]="row.changePercentage < 0">
                {{ format.percentage(row.changePercentage) }}<br />
                {{ format.amount(row.amountMC, movers.currency) }}
              </ion-note>
            </ion-item>
          } @empty {
            <ion-item>
              <ion-label class="ion-text-wrap">
                {{ branch.reasonKey ?? 'DASHBOARD_NO_ITEMS' | translate: { count: branch.omittedCount } }}
              </ion-label>
            </ion-item>
          }
        </ion-list>
      }
      @for (note of notes(); track note.key) {
        <p class="widget-note">{{ note.key | translate: note.params }}</p>
      }
    }
  `
})
export class HoldingMoversWidgetComponent {
  readonly result = input.required<DashboardResult>();
  protected readonly format = inject(MobileFormatService);
  private readonly translateService = inject(TranslateService);

  protected readonly movers = computed(() => this.result().payload?.custom as HoldingMovers | undefined);
  protected readonly ranking = signal<HoldingMoversRanking>('byPercentage');

  /**
   * What the card could not rank. The counts are the largest over the branches rather than their sum: the same
   * instrument is normally missing in every period.
   */
  protected readonly notes = computed(() => {
    const branches = this.movers()?.branches ?? [];
    const omitted = Math.max(0, ...branches.map((branch) => branch.omittedCount));
    const margin = Math.max(0, ...branches.map((branch) => branch.marginCount));
    const notes: { key: string; params: { count: number } }[] = [];
    if (omitted > 0) {
      notes.push({ key: 'DASHBOARD_MOVERS_NOTE_OMITTED', params: { count: omitted } });
    }
    if (margin > 0) {
      notes.push({ key: 'DASHBOARD_MOVERS_NOTE_MARGIN', params: { count: margin } });
    }
    return notes;
  });

  protected onRanking(value: unknown): void {
    if (value === 'byPercentage' || value === 'byAmount') {
      this.ranking.set(value);
    }
  }

  /** The period and, except for the intraday branch, the session it measures. */
  protected branchLabel(branch: HoldingMoversBranch): string {
    const period = this.translateService.instant(`DASHBOARD_MOVERS_${branch.branch}`);
    return branch.branch === 'INTRADAY' || !branch.date ? period : `${period} ${this.format.date(branch.date)}`;
  }

  /** Explains the marker of a row valued over other dates than its branch names. */
  protected substituteText(row: HoldingMoversRow): string {
    return row.usedPreviousDate
      ? this.translateService.instant('DASHBOARD_MOVERS_SUBSTITUTE', { from: row.usedPreviousDate, to: row.usedDate })
      : this.translateService.instant('DASHBOARD_MOVERS_STALE', { at: row.usedDate });
  }
}
