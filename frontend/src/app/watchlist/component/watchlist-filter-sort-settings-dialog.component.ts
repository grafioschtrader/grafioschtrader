import { Component, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { SelectItem } from '@openng/optimus-ui/api';
import { SelectModule } from '@openng/optimus-ui/select';
import { ButtonModule } from '@openng/optimus-ui/button';
import { DynamicDialogConfig } from '@openng/optimus-ui/dynamicdialog';
import {
  FilterSortEntryView,
  FilterSortScope,
  WatchlistFilterSortStateService
} from '../service/watchlist.filter.sort.state.service';

/** Data the opening watchlist table passes to the settings dialog. */
export interface WatchlistFilterSortSettingsData {
  /** The watchlist which is currently displayed, it is the local scope. */
  idWatchlist: number;
}

/**
 * Settings of the filtering and the sorting of the watchlist tables.
 *
 * <p>
 * Offers the scope for both, which decides whether a newly entered filter or sort order applies only to the current
 * watchlist or to all of them, lists what is currently set and allows to remove it, either one entry at a time or all
 * at once. Every change takes effect immediately, the open table is informed through the service.
 * </p>
 */
@Component({
  selector: 'watchlist-filter-sort-settings-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, SelectModule, ButtonModule],
  template: `
    <div class="settings-section">
      <label for="filterScope">{{ 'FILTER_SCOPE' | translate }}</label>
      <p-select
        inputId="filterScope"
        [options]="scopeOptions"
        [ngModel]="filterScope"
        (onChange)="changeFilterScope($event.value)"
        appendTo="body"></p-select>
    </div>

    <div class="settings-section">
      <label for="sortScope">{{ 'SORT_SCOPE' | translate }}</label>
      <p-select
        inputId="sortScope"
        [options]="scopeOptions"
        [ngModel]="sortScope"
        (onChange)="changeSortScope($event.value)"
        appendTo="body"></p-select>
    </div>

    <h5>{{ 'ACTIVE_FILTERS' | translate }}</h5>
    @if (filters.length === 0) {
      <div class="entry-empty">{{ 'NO_FILTER_SORT_ENTRY' | translate }}</div>
    } @else {
      @for (entry of filters; track entry.scope + entry.key) {
        <div class="entry-row">
          <span class="entry-scope">{{ scopeLabel(entry) | translate }}</span>
          <span class="entry-label">{{ entry.label }}</span>
          <span class="entry-value">{{ entry.valueText }}</span>
          <p-button severity="secondary" text="true" (click)="removeFilter(entry)">
            <i class="pi pi-times" pButtonIcon></i>
          </p-button>
        </div>
      }
    }
    <div class="entry-buttons">
      <p-button [label]="'CLEAR_FILTER_WATCHLIST' | translate" severity="secondary" (click)="clearFilters()"></p-button>
      <p-button
        [label]="'CLEAR_FILTER_ALL_WATCHLISTS' | translate"
        severity="secondary"
        (click)="clearAllFilters()"></p-button>
    </div>

    <h5>{{ 'ACTIVE_SORTING' | translate }}</h5>
    @if (sorts.length === 0) {
      <div class="entry-empty">{{ 'NO_FILTER_SORT_ENTRY' | translate }}</div>
    } @else {
      @for (entry of sorts; track entry.scope + entry.key) {
        <div class="entry-row">
          <span class="entry-scope">{{ scopeLabel(entry) | translate }}</span>
          <span class="entry-label">{{ entry.label }}</span>
          <span class="entry-value">{{ (entry.order === 1 ? 'ASCENDING' : 'DESCENDING') | translate }}</span>
          <p-button severity="secondary" text="true" (click)="removeSort(entry)">
            <i class="pi pi-times" pButtonIcon></i>
          </p-button>
        </div>
      }
    }
    <div class="entry-buttons">
      <p-button [label]="'CLEAR_SORT_WATCHLIST' | translate" severity="secondary" (click)="clearSorts()"></p-button>
      <p-button
        [label]="'CLEAR_SORT_ALL_WATCHLISTS' | translate"
        severity="secondary"
        (click)="clearAllSorts()"></p-button>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
  styles: [
    `
      .settings-section {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding-bottom: 0.5rem;
      }

      .settings-section label {
        flex: 0 0 12rem;
      }

      h5 {
        margin: 1rem 0 0.25rem 0;
      }

      .entry-row {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.15rem 0;
        border-bottom: 1px solid var(--surface-border);
      }

      .entry-scope {
        flex: 0 0 8rem;
        font-size: 0.85em;
        opacity: 0.75;
      }

      .entry-label {
        flex: 1 1 auto;
        font-weight: 700;
      }

      .entry-value {
        flex: 1 1 auto;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .entry-empty {
        opacity: 0.75;
        padding: 0.15rem 0;
      }

      .entry-buttons {
        display: flex;
        gap: 0.5rem;
        padding-top: 0.5rem;
      }
    `
  ]
})
export class WatchlistFilterSortSettingsDialogComponent {
  /** The two scopes as dropdown options. */
  scopeOptions: SelectItem[];

  /** Scope a newly entered filter is stored in. */
  filterScope: FilterSortScope;

  /** Scope a newly chosen sort order is stored in. */
  sortScope: FilterSortScope;

  /** Currently stored filters. */
  filters: FilterSortEntryView[];

  /** Currently stored sort criteria. */
  sorts: FilterSortEntryView[];

  private readonly idWatchlist: number;

  constructor(
    dynamicDialogConfig: DynamicDialogConfig,
    translateService: TranslateService,
    private filterSortStateService: WatchlistFilterSortStateService
  ) {
    const data: WatchlistFilterSortSettingsData = dynamicDialogConfig.data;
    this.idWatchlist = data.idWatchlist;
    this.scopeOptions = [
      {
        value: FilterSortScope.LOCAL,
        label: translateService.instant('SCOPE_WATCHLIST')
      },
      {
        value: FilterSortScope.GLOBAL,
        label: translateService.instant('SCOPE_ALL_WATCHLISTS')
      }
    ];
    this.readState();
  }

  /**
   * Returns the translation key which names the scope of an entry.
   *
   * @param entry the listed filter or sort criterion
   * @returns the translation key of its scope
   */
  scopeLabel(entry: FilterSortEntryView): string {
    return entry.scope === FilterSortScope.GLOBAL ? 'SCOPE_ALL_WATCHLISTS' : 'SCOPE_WATCHLIST';
  }

  /**
   * Changes the scope newly entered filters are stored in.
   *
   * @param scope the chosen scope
   */
  changeFilterScope(scope: FilterSortScope): void {
    this.filterSortStateService.setFilterScope(scope);
    this.readState();
  }

  /**
   * Changes the scope newly chosen sort orders are stored in.
   *
   * @param scope the chosen scope
   */
  changeSortScope(scope: FilterSortScope): void {
    this.filterSortStateService.setSortScope(scope);
    this.readState();
  }

  /**
   * Removes a single filter.
   *
   * @param entry the listed filter
   */
  removeFilter(entry: FilterSortEntryView): void {
    this.filterSortStateService.removeFilter(this.idWatchlist, entry.key, entry.scope);
    this.readState();
  }

  /**
   * Removes a single sort criterion.
   *
   * @param entry the listed sort criterion
   */
  removeSort(entry: FilterSortEntryView): void {
    this.filterSortStateService.removeSort(this.idWatchlist, entry.key, entry.scope);
    this.readState();
  }

  /** Removes the filters of the displayed watchlist, the global ones stay. */
  clearFilters(): void {
    this.filterSortStateService.clearFilters(this.idWatchlist);
    this.readState();
  }

  /** Removes the filters of every watchlist including the global ones. */
  clearAllFilters(): void {
    this.filterSortStateService.clearAllFilters();
    this.readState();
  }

  /** Removes the sort criteria of the displayed watchlist, the global ones stay. */
  clearSorts(): void {
    this.filterSortStateService.clearSorts(this.idWatchlist);
    this.readState();
  }

  /** Removes the sort criteria of every watchlist including the global ones. */
  clearAllSorts(): void {
    this.filterSortStateService.clearAllSorts();
    this.readState();
  }

  /** Takes over what the service holds after every change, the table is informed by the service itself. */
  private readState(): void {
    this.filterScope = this.filterSortStateService.getFilterScope();
    this.sortScope = this.filterSortStateService.getSortScope();
    this.filters = this.filterSortStateService.listFilters(this.idWatchlist);
    this.sorts = this.filterSortStateService.listSorts(this.idWatchlist);
  }
}
