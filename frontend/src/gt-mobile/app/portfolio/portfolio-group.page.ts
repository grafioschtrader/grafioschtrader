import { Component, computed, DestroyRef, inject, input, OnInit, signal } from '@angular/core';
import {
  IonBackButton,
  IonButtons,
  IonCard,
  IonCardContent,
  IonContent,
  IonHeader,
  IonRefresher,
  IonRefresherContent,
  IonSpinner,
  IonTitle,
  IonToolbar
} from '@ionic/angular';
import { TranslatePipe } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { MobileFormatService } from '../service/mobile.format.service';
import { MobilePortfolioService } from '../service/mobile.portfolio.service';
import { MobilePortfolioStore } from '../service/mobile.portfolio.store';
import { DetailSectionsComponent } from '../shared/detail-sections.component';
import {
  findMobilePortfolioGroup,
  mobileAccountSections,
  mobileGroupSection,
  MobilePortfolioGrouping
} from './mobile.portfolio.summary';

/**
 * One portfolio or currency of the account report: its value, its composition and results, and then each of its
 * accounts with the figures other than zero. It opens with the report the overview just showed; a reload of the page
 * reads it again.
 */
@Component({
  selector: 'mobile-portfolio-group-page',
  imports: [
    IonHeader,
    IonToolbar,
    IonButtons,
    IonBackButton,
    IonTitle,
    IonContent,
    IonRefresher,
    IonRefresherContent,
    IonCard,
    IonCardContent,
    IonSpinner,
    TranslatePipe,
    DetailSectionsComponent
  ],
  template: `
    <ion-header>
      <ion-toolbar color="primary">
        <ion-buttons slot="start">
          <ion-back-button defaultHref="/tabs/portfolios" />
        </ion-buttons>
        <ion-title>{{ groupName() }}</ion-title>
        @if (updating()) {
          <ion-spinner slot="end" name="dots" class="toolbar-spinner" />
        }
      </ion-toolbar>
    </ion-header>
    <ion-content>
      <ion-refresher slot="fixed" (ionRefresh)="refresh($event)">
        <ion-refresher-content />
      </ion-refresher>
      @let shown = group();
      @if (shown) {
        <ion-card>
          <ion-card-content>
            <div class="widget-note">{{ 'VALUE' | translate }}</div>
            <div class="group-total">{{ format.amount(shown.groupValueMC, mainCurrency()) }}</div>
          </ion-card-content>
        </ion-card>
        <mobile-detail-sections [sections]="sections()" />
      } @else if (groupMissing()) {
        <p class="widget-note ion-padding">{{ 'MOBILE_WIDGET_NO_DATA' | translate }}</p>
      } @else if (loadFailed()) {
        <p class="widget-note loss ion-padding">{{ 'DASHBOARD_LOAD_ERROR' | translate }}</p>
      } @else {
        <div class="widget-loading"><ion-spinner /></div>
      }
    </ion-content>
  `,
  styles: [
    `
      .group-total {
        font-size: 1.8em;
        font-weight: 600;
        color: var(--ion-text-color, #000);
        font-variant-numeric: tabular-nums;
        margin-top: 4px;
      }
      .toolbar-spinner {
        margin-right: 12px;
        color: var(--ion-color-primary-contrast);
      }
    `
  ]
})
export class PortfolioGroupPage implements OnInit {
  readonly grouping = input.required<MobilePortfolioGrouping, string>({
    transform: (value: string) => (value === 'currency' ? 'currency' : 'portfolio')
  });
  readonly groupName = input.required<string>();

  protected readonly format = inject(MobileFormatService);
  private readonly portfolioService = inject(MobilePortfolioService);
  private readonly store = inject(MobilePortfolioStore);

  /** True while the report is read. */
  protected readonly updating = signal(false);
  /** The report could not be read and none is shown. */
  protected readonly loadFailed = signal(false);
  private loadSubscription: Subscription | null = null;

  private readonly summary = computed(() => this.store.get(this.grouping()));
  protected readonly mainCurrency = computed(() => this.summary()?.mainCurrency ?? '');
  protected readonly group = computed(() => {
    const summary = this.summary();
    return summary ? findMobilePortfolioGroup(summary, this.groupName()) : null;
  });
  /** The report was read but no longer contains the group, for example after a portfolio was renamed. */
  protected readonly groupMissing = computed(() => this.summary() !== undefined && this.group() === null);
  protected readonly sections = computed(() => {
    const group = this.group();
    const mainCurrency = this.mainCurrency();
    return group ? [mobileGroupSection(group, mainCurrency), ...mobileAccountSections(group, mainCurrency)] : [];
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.loadSubscription?.unsubscribe());
  }

  /** A link opened directly, or a reload of the page, finds no report yet and reads it. */
  ngOnInit(): void {
    if (!this.summary()) {
      this.load();
    }
  }

  protected refresh(event: { target: { complete(): void } }): void {
    this.load(() => event.target.complete());
  }

  /** Reads the report into the store, which the overview shares. */
  private load(done?: () => void): void {
    const grouping = this.grouping();
    this.loadSubscription?.unsubscribe();
    this.updating.set(true);
    this.loadFailed.set(false);
    this.loadSubscription = this.portfolioService
      .getSummary(grouping)
      .pipe(
        finalize(() => {
          this.updating.set(false);
          done?.();
        })
      )
      .subscribe({
        next: (summary) => this.store.put(grouping, summary),
        error: () => this.loadFailed.set(this.store.get(grouping) === undefined)
      });
  }
}
