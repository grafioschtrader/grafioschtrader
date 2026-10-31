import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  IonCard,
  IonCardContent,
  IonContent,
  IonHeader,
  IonItem,
  IonLabel,
  IonList,
  IonListHeader,
  IonRefresher,
  IonRefresherContent,
  IonSegment,
  IonSegmentButton,
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
  mobileGrandSection,
  mobileMissingExchangeRates,
  MobilePortfolioGroupCard,
  mobilePortfolioGroupCards,
  MobilePortfolioGrouping
} from './mobile.portfolio.summary';

/** Remembers the chosen grouping in this browser. */
const GROUPING_STORAGE_KEY = 'gtMobile.portfolioGrouping';

/** Value of an Ionic segment change. */
interface SegmentChangeEvent {
  detail: { value?: string | number | null };
}

/**
 * The accounts of the tenant as the phone shows them, in three steps instead of the wide table of the desktop: the
 * total value at the top, then one line per portfolio or currency with its value and share, and below the composition
 * and results of the whole tenant. A tap on a group opens its accounts.
 */
@Component({
  selector: 'mobile-portfolio-page',
  imports: [
    IonHeader,
    IonToolbar,
    IonTitle,
    IonContent,
    IonRefresher,
    IonRefresherContent,
    IonSegment,
    IonSegmentButton,
    IonCard,
    IonCardContent,
    IonList,
    IonListHeader,
    IonItem,
    IonLabel,
    IonSpinner,
    TranslatePipe,
    DetailSectionsComponent
  ],
  template: `
    <ion-header>
      <ion-toolbar color="primary">
        <ion-title>{{ 'PORTFOLIOS' | translate }}</ion-title>
        @if (updating()) {
          <ion-spinner slot="end" name="dots" class="toolbar-spinner" />
        }
      </ion-toolbar>
      <ion-toolbar>
        <ion-segment [value]="grouping()" (ionChange)="onGrouping($event)">
          <ion-segment-button value="portfolio">
            <ion-label>{{ 'PORTFOLIO' | translate }}</ion-label>
          </ion-segment-button>
          <ion-segment-button value="currency">
            <ion-label>{{ 'CURRENCY' | translate }}</ion-label>
          </ion-segment-button>
        </ion-segment>
      </ion-toolbar>
    </ion-header>
    <ion-content>
      <ion-refresher slot="fixed" (ionRefresh)="refresh($event)">
        <ion-refresher-content />
      </ion-refresher>
      @let report = summary();
      @if (report) {
        @let missing = missingRates();
        @if (missing) {
          <p class="widget-note loss ion-padding">
            {{ 'MISSING_EXCHANGE_RATE_WARNING' | translate: { currencies: missing } }}
          </p>
        }
        <ion-card>
          <ion-card-content>
            <div class="widget-note">{{ 'VALUE' | translate }}</div>
            <div class="portfolio-total figure">{{ format.amount(report.grandValueMC, report.mainCurrency) }}</div>
            <div class="portfolio-split">
              <span
                >{{ 'SECURITY' | translate }}
                {{ format.amount(report.grandValueSecuritiesMC, report.mainCurrency) }}</span
              >
              <span
                >{{ 'CASH_BALANCE' | translate }}
                {{ format.amount(report.grandCashBalanceMC, report.mainCurrency) }}</span
              >
            </div>
          </ion-card-content>
        </ion-card>
        <ion-list lines="full">
          <ion-list-header>
            <ion-label>{{ (grouping() === 'portfolio' ? 'PORTFOLIOS' : 'CURRENCY') | translate }}</ion-label>
          </ion-list-header>
          @for (card of groupCards(); track card.groupName) {
            <ion-item [button]="true" [detail]="true" (click)="open(card)">
              <ion-label class="group-row">
                <div class="group-line">
                  <span class="group-name">{{ card.groupName }}</span>
                  <span class="figure group-value">{{ format.amount(card.valueMC, report.mainCurrency) }}</span>
                </div>
                @if (card.sharePercentage != null) {
                  <div class="share-bar">
                    <div class="share-fill" [style.width.%]="clampShare(card.sharePercentage)"></div>
                  </div>
                }
                <div class="group-line group-sub">
                  <span>
                    @if (card.sharePercentage != null) {
                      {{ format.percentage(card.sharePercentage) }} ·
                    }
                    {{ 'SECURITY' | translate }} {{ format.amountFigure(card.valueSecuritiesMC, report.mainCurrency) }}
                  </span>
                  @if (card.gainLossSecuritiesMC) {
                    <span
                      class="figure"
                      [class.gain]="card.gainLossSecuritiesMC > 0"
                      [class.loss]="card.gainLossSecuritiesMC < 0"
                      >{{ format.amountFigure(card.gainLossSecuritiesMC, report.mainCurrency) }}</span
                    >
                  }
                </div>
              </ion-label>
            </ion-item>
          }
        </ion-list>
        <mobile-detail-sections [sections]="grandSections()" />
      } @else if (loadFailed()) {
        <p class="widget-note loss ion-padding">{{ 'DASHBOARD_LOAD_ERROR' | translate }}</p>
      } @else {
        <div class="widget-loading"><ion-spinner /></div>
      }
    </ion-content>
  `,
  styles: [
    `
      .portfolio-total {
        text-align: left;
        font-size: 1.8em;
        font-weight: 600;
        color: var(--ion-text-color, #000);
        margin: 4px 0 8px;
      }
      .portfolio-split {
        display: flex;
        flex-wrap: wrap;
        justify-content: space-between;
        gap: 4px 12px;
      }
      .group-row {
        margin: 6px 0;
      }
      .group-line {
        display: flex;
        justify-content: space-between;
        align-items: baseline;
        gap: 12px;
      }
      .group-name,
      .group-value {
        font-weight: 600;
      }
      .group-name {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .group-sub {
        font-size: 0.85em;
        color: var(--ion-color-medium);
        margin-top: 2px;
      }
      .share-bar {
        height: 4px;
        border-radius: 2px;
        background: var(--ion-color-light);
        margin-top: 6px;
        overflow: hidden;
      }
      .share-fill {
        height: 100%;
        background: var(--ion-color-primary);
      }
      .toolbar-spinner {
        margin-right: 12px;
        color: var(--ion-color-primary-contrast);
      }
    `
  ]
})
export class PortfolioPage implements OnInit {
  protected readonly format = inject(MobileFormatService);
  private readonly portfolioService = inject(MobilePortfolioService);
  private readonly store = inject(MobilePortfolioStore);
  private readonly router = inject(Router);

  protected readonly grouping = signal<MobilePortfolioGrouping>(readStoredGrouping());
  /** True while the report is read. */
  protected readonly updating = signal(false);
  /** The report could not be read and none is shown. */
  protected readonly loadFailed = signal(false);
  private loadSubscription: Subscription | null = null;

  protected readonly summary = computed(() => this.store.get(this.grouping()) ?? null);
  protected readonly groupCards = computed(() => {
    const summary = this.summary();
    return summary ? mobilePortfolioGroupCards(summary) : [];
  });
  protected readonly grandSections = computed(() => {
    const summary = this.summary();
    return summary ? [mobileGrandSection(summary)] : [];
  });
  protected readonly missingRates = computed(() => {
    const summary = this.summary();
    return summary ? mobileMissingExchangeRates(summary) : null;
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.loadSubscription?.unsubscribe());
  }

  ngOnInit(): void {
    this.load();
  }

  protected onGrouping(event: SegmentChangeEvent): void {
    const grouping = event.detail.value;
    if ((grouping === 'portfolio' || grouping === 'currency') && grouping !== this.grouping()) {
      this.grouping.set(grouping);
      storeGrouping(grouping);
      this.load();
    }
  }

  protected refresh(event: { target: { complete(): void } }): void {
    this.load(() => event.target.complete());
  }

  /** Opens the accounts of a group. */
  protected open(card: MobilePortfolioGroupCard): void {
    this.router.navigate(['/portfolios', this.grouping(), card.groupName]);
  }

  /** A share outside 0 to 100, from a negative balance somewhere, still draws a bar that fits. */
  protected clampShare(share: number): number {
    return Math.min(100, Math.max(0, share));
  }

  /** Reads the report of the chosen grouping; a report already read stays on screen meanwhile. */
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

function readStoredGrouping(): MobilePortfolioGrouping {
  try {
    return localStorage.getItem(GROUPING_STORAGE_KEY) === 'currency' ? 'currency' : 'portfolio';
  } catch {
    return 'portfolio';
  }
}

function storeGrouping(grouping: MobilePortfolioGrouping): void {
  try {
    localStorage.setItem(GROUPING_STORAGE_KEY, grouping);
  } catch {
    // Without storage the next start groups by portfolio.
  }
}
