import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import {
  IonContent,
  IonHeader,
  IonItem,
  IonLabel,
  IonList,
  IonRefresher,
  IonRefresherContent,
  IonSegment,
  IonSegmentButton,
  IonSpinner,
  IonTitle,
  IonToolbar
} from '@ionic/angular';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { concat, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { MobileFormatService } from '../service/mobile.format.service';
import { MobileWatchlistService } from '../service/mobile.watchlist.service';
import { MobileWatchlist, MobileWatchlistRow, mobileWatchlistRows } from './mobile.watchlist.rows';

/** Remembers the chosen watchlist in this browser, so the next start opens it again. */
const STORAGE_KEY = 'gtMobile.idWatchlist';

/** Value of an Ionic segment change. */
interface SegmentChangeEvent {
  detail: { value?: string | number | null };
}

/**
 * The instruments of one watchlist as a price list: name and price, the kind of instrument with its currency and the
 * change against the previous day, and for a held position a third line with units, price gain and total position.
 * The prices last stored are shown at once; the server then updates them, which can take a few seconds.
 */
@Component({
  selector: 'mobile-watchlist-page',
  imports: [
    IonHeader,
    IonToolbar,
    IonTitle,
    IonContent,
    IonRefresher,
    IonRefresherContent,
    IonSegment,
    IonSegmentButton,
    IonList,
    IonItem,
    IonLabel,
    IonSpinner,
    TranslatePipe
  ],
  template: `
    <ion-header>
      <ion-toolbar color="primary">
        <ion-title>{{ 'MOBILE_WATCHLISTS' | translate }}</ion-title>
        @if (updating()) {
          <ion-spinner slot="end" name="dots" class="toolbar-spinner" />
        }
      </ion-toolbar>
      @if (watchlists().length > 1) {
        <ion-toolbar>
          <ion-segment [scrollable]="true" [value]="selectedId()" (ionChange)="onSelect($event)">
            @for (watchlist of watchlists(); track watchlist.idWatchlist) {
              <ion-segment-button [value]="watchlist.idWatchlist">
                <ion-label>{{ watchlist.name }}</ion-label>
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
      @if (watchlistsLoaded() && !watchlists().length) {
        <p class="widget-note ion-padding">{{ 'MOBILE_WATCHLIST_NONE' | translate }}</p>
      } @else if (loadFailed()) {
        <p class="widget-note loss ion-padding">{{ 'DASHBOARD_LOAD_ERROR' | translate }}</p>
      } @else if (rows(); as rows) {
        <ion-list lines="full">
          @for (row of rows; track row.idSecuritycurrency) {
            <ion-item>
              <ion-label class="watchlist-row">
                <div class="watchlist-line">
                  <span class="watchlist-name">{{ row.name }}</span>
                  <span class="figure watchlist-price">{{
                    format.price(row.price.value, row.price.priceFractionDigits)
                  }}</span>
                </div>
                <div class="watchlist-line watchlist-sub">
                  <span>{{ kindText(row) }}</span>
                  <span
                    class="figure"
                    [class.gain]="(row.dailyChangePercentage ?? 0) > 0"
                    [class.loss]="(row.dailyChangePercentage ?? 0) < 0">
                    {{ arrow(row.dailyChangePercentage) }} {{ format.percentage(row.dailyChangePercentage) }}
                  </span>
                </div>
                @if (row.holding; as holding) {
                  <div class="watchlist-line watchlist-sub">
                    <span>{{ format.units(holding.units) }}</span>
                    <span
                      class="figure"
                      [class.gain]="(holding.gainLossPercentage ?? 0) > 0"
                      [class.loss]="(holding.gainLossPercentage ?? 0) < 0">
                      {{ format.percentage(holding.gainLossPercentage) }}
                    </span>
                    <span class="figure">{{ format.amount(holding.value, row.currency) }}</span>
                  </div>
                }
              </ion-label>
            </ion-item>
          } @empty {
            <p class="widget-note ion-padding">{{ 'MOBILE_WATCHLIST_EMPTY' | translate }}</p>
          }
        </ion-list>
      } @else {
        <div class="widget-loading"><ion-spinner /></div>
      }
    </ion-content>
  `,
  styles: [
    `
      .watchlist-row {
        margin: 6px 0;
      }
      .watchlist-line {
        display: flex;
        justify-content: space-between;
        align-items: baseline;
        gap: 12px;
      }
      .watchlist-name {
        font-weight: 600;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .watchlist-price {
        font-weight: 600;
      }
      .watchlist-sub {
        font-size: 0.85em;
        color: var(--ion-color-medium);
        margin-top: 2px;
      }
      .watchlist-sub .gain {
        color: var(--gt-gain-color);
      }
      .watchlist-sub .loss {
        color: var(--gt-loss-color);
      }
      .toolbar-spinner {
        margin-right: 12px;
        color: var(--ion-color-primary-contrast);
      }
    `
  ]
})
export class WatchlistPage implements OnInit {
  protected readonly format = inject(MobileFormatService);
  private readonly watchlistService = inject(MobileWatchlistService);
  private readonly translateService = inject(TranslateService);

  protected readonly watchlists = signal<MobileWatchlist[]>([]);
  protected readonly watchlistsLoaded = signal(false);
  protected readonly selectedId = signal<number | null>(null);
  /** Lines of the selected watchlist, null until its content was first read. */
  protected readonly rows = signal<MobileWatchlistRow[] | null>(null);
  /** True while the server updates the prices of the selected watchlist. */
  protected readonly updating = signal(false);
  /** The content of the selected watchlist could not be read at all. */
  protected readonly loadFailed = signal(false);
  private loadSubscription: Subscription | null = null;

  /** Translated instrument kinds, rebuilt when the rows change. */
  private readonly kindTexts = computed(() => {
    const texts = new Map<string, string>();
    for (const row of this.rows() ?? []) {
      for (const key of row.kindKeys) {
        if (!texts.has(key)) {
          texts.set(key, this.translateService.instant(key));
        }
      }
    }
    return texts;
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.loadSubscription?.unsubscribe());
  }

  ngOnInit(): void {
    this.watchlistService.getWatchlists().subscribe({
      error: () => this.watchlistsLoaded.set(true),
      next: (watchlists) => {
        this.watchlists.set(watchlists);
        this.watchlistsLoaded.set(true);
        const stored = readStoredId();
        const chosen = watchlists.find((w) => w.idWatchlist === stored) ?? watchlists[0];
        if (chosen?.idWatchlist != null) {
          this.select(chosen.idWatchlist);
        }
      }
    });
  }

  protected onSelect(event: SegmentChangeEvent): void {
    const id = Number(event.detail.value);
    if (Number.isInteger(id) && id !== this.selectedId()) {
      this.select(id);
    }
  }

  /** Pulling down asks the server for current prices of the watchlist shown. */
  protected refresh(event: { target: { complete(): void } }): void {
    const id = this.selectedId();
    if (id == null) {
      event.target.complete();
      return;
    }
    this.load(id, false, () => event.target.complete());
  }

  protected kindText(row: MobileWatchlistRow): string {
    const texts = this.kindTexts();
    return [...row.kindKeys.map((key) => texts.get(key) ?? key), row.currency].filter((part) => !!part).join(' · ');
  }

  protected arrow(change: number | null): string {
    return change == null || change === 0 ? '' : change > 0 ? '▲' : '▼';
  }

  private select(idWatchlist: number): void {
    this.selectedId.set(idWatchlist);
    storeId(idWatchlist);
    this.rows.set(null);
    this.loadFailed.set(false);
    this.load(idWatchlist, true);
  }

  /**
   * Shows the stored prices first, when asked to, and then the updated ones. Choosing another watchlist cancels a read
   * still running, so the list never shows the prices of the previous one.
   */
  private load(idWatchlist: number, storedFirst: boolean, done?: () => void): void {
    this.loadSubscription?.unsubscribe();
    this.updating.set(true);
    const reads = storedFirst
      ? concat(
          this.watchlistService.getWatchlist(idWatchlist),
          this.watchlistService.getWatchlistWithUpdatedPrices(idWatchlist)
        )
      : this.watchlistService.getWatchlistWithUpdatedPrices(idWatchlist);
    this.loadSubscription = reads
      .pipe(
        finalize(() => {
          this.updating.set(false);
          done?.();
        })
      )
      .subscribe({
        next: (group) => this.rows.set(mobileWatchlistRows(group)),
        // A failed price update keeps the stored prices on screen; the toast of the shell reports it.
        error: () => this.loadFailed.set(this.rows() === null)
      });
  }
}

function readStoredId(): number | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    return value == null ? null : Number(value);
  } catch {
    return null;
  }
}

function storeId(idWatchlist: number): void {
  try {
    localStorage.setItem(STORAGE_KEY, String(idWatchlist));
  } catch {
    // Without storage the first watchlist opens next time.
  }
}
