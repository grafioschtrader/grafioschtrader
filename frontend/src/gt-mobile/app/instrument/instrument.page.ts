import { Component, computed, DestroyRef, inject, input, numberAttribute, OnInit, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import {
  IonBackButton,
  IonButtons,
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
import { GlobalSessionNames } from '../../../app/lib/global.session.names';
import { MobileFormatService } from '../service/mobile.format.service';
import { MobileInstrumentService } from '../service/mobile.instrument.service';
import { MobileWatchlistService } from '../service/mobile.watchlist.service';
import { MobileWatchlistStore } from '../service/mobile.watchlist.store';
import { DetailSectionsComponent } from '../shared/detail-sections.component';
import { InstrumentChartComponent } from './instrument-chart.component';
import {
  findMobileWatchlistInstrument,
  mobileInstrumentCurrency,
  mobileInstrumentName,
  mobileInstrumentSections
} from './mobile.instrument.details';
import { mobileTransactionRows, mobileTransactionSummarySection } from './mobile.instrument.transactions';

/** The two views of an instrument. */
type InstrumentView = 'details' | 'chart' | 'transactions';

/** Value of an Ionic segment change. */
interface SegmentChangeEvent {
  detail: { value?: string | number | null };
}

/**
 * One instrument of a watchlist: its position, quotation data and base data, its price chart, and for a security the
 * tenant ever traded its transactions across all portfolios. The page opens with the figures the watchlist just showed; a reload of the
 * page reads the stored content of the watchlist. Pulling down updates the prices of the watchlist, or reads the
 * transactions again.
 */
@Component({
  selector: 'mobile-instrument-page',
  imports: [
    IonHeader,
    IonToolbar,
    IonButtons,
    IonBackButton,
    IonTitle,
    IonContent,
    IonRefresher,
    IonRefresherContent,
    IonSegment,
    IonSegmentButton,
    IonList,
    IonListHeader,
    IonItem,
    IonLabel,
    IonSpinner,
    TranslatePipe,
    DetailSectionsComponent,
    InstrumentChartComponent
  ],
  template: `
    @let shown = instrument();
    <ion-header>
      <ion-toolbar color="primary">
        <ion-buttons slot="start">
          <ion-back-button defaultHref="/tabs/watchlists" />
        </ion-buttons>
        <ion-title>{{ shown ? name() : ('INSTRUMENT' | translate) }}</ion-title>
        @if (updating()) {
          <ion-spinner slot="end" name="dots" class="toolbar-spinner" />
        }
      </ion-toolbar>
      @if (instrument()) {
        <ion-toolbar>
          <ion-segment [scrollable]="true" [value]="view()" (ionChange)="onView($event)">
            <ion-segment-button value="details">
              <ion-label>{{ 'INSTRUMENT' | translate }}</ion-label>
            </ion-segment-button>
            <ion-segment-button value="chart">
              <ion-label>{{ 'MOBILE_CHART' | translate }}</ion-label>
            </ion-segment-button>
            @if (hasTransactions()) {
              <ion-segment-button value="transactions">
                <ion-label>{{ 'TRANSACTIONS' | translate }}</ion-label>
              </ion-segment-button>
            }
          </ion-segment>
        </ion-toolbar>
      }
    </ion-header>
    <ion-content>
      <ion-refresher slot="fixed" (ionRefresh)="refresh($event)">
        <ion-refresher-content />
      </ion-refresher>
      @if (!shown) {
        @if (groupMissing()) {
          <p class="widget-note ion-padding">{{ 'MOBILE_INSTRUMENT_NOT_FOUND' | translate }}</p>
        } @else if (loadFailed()) {
          <p class="widget-note loss ion-padding">{{ 'DASHBOARD_LOAD_ERROR' | translate }}</p>
        } @else {
          <div class="widget-loading"><ion-spinner /></div>
        }
      } @else {
        @let currentView = view();
        @switch (currentView) {
          @case ('details') {
            <div class="instrument-price ion-padding">
              <span class="figure instrument-last">{{
                format.price(shown.position.securitycurrency.sLast, shown.position.securitycurrency.priceFractionDigits)
              }}</span>
              <span class="instrument-currency">{{ currency() }}</span>
            </div>
            <mobile-detail-sections [sections]="sections()" />
          }
          @case ('chart') {
            <div class="ion-padding">
              <mobile-instrument-chart
                [idSecuritycurrency]="idSecuritycurrency()"
                [currency]="currency()"
                [isSecurity]="shown.kind === 'security'"
                [withTransactions]="hasTransactions()"
                [priceFractionDigits]="shown.position.securitycurrency.priceFractionDigits ?? null" />
            </div>
          }
          @case ('transactions') {
            @if (transactionsResource.hasValue()) {
              <mobile-detail-sections [sections]="transactionSummary()" />
              <ion-list lines="full">
                <ion-list-header>
                  <ion-label>{{ 'TRANSACTIONS' | translate }}</ion-label>
                </ion-list-header>
                @for (row of transactionRows(); track row.idTransaction) {
                  <ion-item>
                    <ion-label class="transaction-row">
                      <div class="detail-line">
                        <span class="transaction-head">
                          {{ format.date(row.transactionTime) }} · {{ row.typeKey | translate }}
                        </span>
                        <span
                          class="figure transaction-amount"
                          [class.gain]="(row.cashaccountAmount ?? 0) > 0"
                          [class.loss]="(row.cashaccountAmount ?? 0) < 0"
                          >{{ format.amount(row.cashaccountAmount, row.cashaccountCurrency) }}</span
                        >
                      </div>
                      <div class="detail-line transaction-sub">
                        <span>
                          @if (row.units != null) {
                            {{ format.units(row.units) }} ×
                            {{ format.price(row.quotation, shown.position.securitycurrency.priceFractionDigits) }} ·
                          }
                          {{ row.cashaccountName }}
                        </span>
                        @if (row.gainLossPercentage != null) {
                          <span
                            class="figure"
                            [class.gain]="row.gainLossPercentage > 0"
                            [class.loss]="row.gainLossPercentage < 0"
                            >{{ format.percentage(row.gainLossPercentage) }}</span
                          >
                        }
                      </div>
                      @if (row.transactionCost != null || row.taxCost != null) {
                        <div class="transaction-sub">
                          @if (row.transactionCost != null) {
                            {{ 'TRANSACTION_COST' | translate }} {{ format.amount(row.transactionCost, currency()) }}
                          }
                          @if (row.transactionCost != null && row.taxCost != null) {
                            ·
                          }
                          @if (row.taxCost != null) {
                            {{ 'TAX_COST' | translate }} {{ format.amount(row.taxCost, currency()) }}
                          }
                        </div>
                      }
                    </ion-label>
                  </ion-item>
                } @empty {
                  <p class="widget-note ion-padding">{{ 'MOBILE_TRANSACTIONS_NONE' | translate }}</p>
                }
              </ion-list>
            } @else if (transactionsResource.error()) {
              <p class="widget-note loss ion-padding">{{ 'DASHBOARD_LOAD_ERROR' | translate }}</p>
            } @else {
              <div class="widget-loading"><ion-spinner /></div>
            }
          }
          @default never;
        }
      }
    </ion-content>
  `,
  styles: [
    `
      .instrument-price {
        display: flex;
        align-items: baseline;
        gap: 8px;
      }
      .instrument-last {
        font-size: 1.8em;
        font-weight: 600;
      }
      .instrument-currency {
        color: var(--ion-color-medium);
      }
      .detail-line {
        display: flex;
        justify-content: space-between;
        align-items: baseline;
        gap: 12px;
      }
      .transaction-row {
        margin: 6px 0;
      }
      .transaction-head,
      .transaction-amount {
        font-weight: 600;
      }
      .transaction-sub {
        font-size: 0.85em;
        color: var(--ion-color-medium);
        margin-top: 2px;
      }
      .toolbar-spinner {
        margin-right: 12px;
        color: var(--ion-color-primary-contrast);
      }
    `
  ]
})
export class InstrumentPage implements OnInit {
  readonly idWatchlist = input.required({ transform: numberAttribute });
  readonly idSecuritycurrency = input.required({ transform: numberAttribute });

  protected readonly format = inject(MobileFormatService);
  private readonly store = inject(MobileWatchlistStore);
  private readonly watchlistService = inject(MobileWatchlistService);
  private readonly instrumentService = inject(MobileInstrumentService);
  private readonly language = sessionStorage.getItem(GlobalSessionNames.LANGUAGE) ?? 'en';

  protected readonly view = signal<InstrumentView>('details');
  /** True while the server updates the prices of the watchlist. */
  protected readonly updating = signal(false);
  /** The content of the watchlist could not be read at all. */
  protected readonly loadFailed = signal(false);
  private loadSubscription: Subscription | null = null;

  private readonly group = computed(() => this.store.get(this.idWatchlist()));
  protected readonly instrument = computed(() => {
    const group = this.group();
    return group ? findMobileWatchlistInstrument(group, this.idSecuritycurrency()) : null;
  });
  /** The watchlist was read but no longer contains the instrument. */
  protected readonly groupMissing = computed(() => this.group() !== undefined && this.instrument() === null);
  protected readonly name = computed(() => {
    const instrument = this.instrument();
    return instrument ? mobileInstrumentName(instrument) : '';
  });
  protected readonly currency = computed(() => {
    const instrument = this.instrument();
    return instrument ? mobileInstrumentCurrency(instrument) : '';
  });
  protected readonly sections = computed(() => {
    const instrument = this.instrument();
    return instrument ? mobileInstrumentSections(instrument, this.language) : [];
  });
  /** Only a security the tenant ever traded has transactions to show. */
  protected readonly hasTransactions = computed(() => {
    const instrument = this.instrument();
    return instrument?.kind === 'security' && !!instrument.position.watchlistSecurityHasEver;
  });

  /** Set when the transactions are first shown; they are read once and kept while the page lives. */
  private readonly transactionsRequested = signal(false);
  protected readonly transactionsResource = rxResource({
    params: () => (this.transactionsRequested() ? this.idSecuritycurrency() : undefined),
    stream: ({ params }) => this.instrumentService.getTransactions(params)
  });
  protected readonly transactionRows = computed(() =>
    this.transactionsResource.hasValue() ? mobileTransactionRows(this.transactionsResource.value()) : []
  );
  protected readonly transactionSummary = computed(() =>
    this.transactionsResource.hasValue()
      ? [mobileTransactionSummarySection(this.transactionsResource.value(), this.currency())]
      : []
  );

  constructor() {
    inject(DestroyRef).onDestroy(() => this.loadSubscription?.unsubscribe());
  }

  /** A link opened directly, or a reload of the page, finds no content yet and reads the stored one. */
  ngOnInit(): void {
    if (!this.group()) {
      this.loadWatchlist(false);
    }
  }

  protected onView(event: SegmentChangeEvent): void {
    const view = event.detail.value;
    if (view === 'details' || view === 'chart' || view === 'transactions') {
      this.view.set(view);
      if (view === 'transactions') {
        this.transactionsRequested.set(true);
      }
    }
  }

  /** Pulling down updates the prices of the watchlist, or reads the transactions again. */
  protected refresh(event: { target: { complete(): void } }): void {
    if (this.view() === 'transactions') {
      this.transactionsResource.reload();
      event.target.complete();
    } else {
      this.loadWatchlist(true, () => event.target.complete());
    }
  }

  /** Reads the content of the watchlist into the store, which the watchlist page shares. */
  private loadWatchlist(updatePrices: boolean, done?: () => void): void {
    const idWatchlist = this.idWatchlist();
    this.loadSubscription?.unsubscribe();
    this.updating.set(true);
    const read = updatePrices
      ? this.watchlistService.getWatchlistWithUpdatedPrices(idWatchlist)
      : this.watchlistService.getWatchlist(idWatchlist);
    this.loadSubscription = read
      .pipe(
        finalize(() => {
          this.updating.set(false);
          done?.();
        })
      )
      .subscribe({
        next: (group) => this.store.put(idWatchlist, group),
        // A failed price update keeps the figures on screen; the toast of the shell reports it.
        error: () => this.loadFailed.set(this.group() === undefined)
      });
  }
}
