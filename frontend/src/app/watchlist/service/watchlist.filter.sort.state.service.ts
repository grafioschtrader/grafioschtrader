import {Injectable} from '@angular/core';
import {Observable, Subject} from 'rxjs';
import {SortMeta} from 'primeng/api';
import {ColumnConfig, getFilterKey} from '../../lib/datashowbase/column.config';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {AppSettings} from '../../shared/app.settings';

/** Whether a filter or a sort order belongs to a single watchlist or to all of them. */
export enum FilterSortScope {
  /** Applies only to the watchlist it was entered in. */
  LOCAL = 'LOCAL',
  /** Applies to every watchlist. */
  GLOBAL = 'GLOBAL'
}

/**
 * A single stored filter. The metadata is whatever PrimeNG put into its filter map and is kept verbatim, because its
 * shape differs by control: a column filter menu writes an array of constraints, the dropdown and the date/decimal
 * helpers write a single object. Rebuilding it by hand would break one of the two.
 */
export interface StoredFilter {
  /** Translated column header, so a filter can be named even in a watchlist type without that column. */
  label: string;
  /** PrimeNG filter metadata, single object or array of constraints. */
  meta: any;
}

/** A single stored sort criterion. */
export interface StoredSort {
  /** Translated column header, see {@link StoredFilter.label}. */
  label: string;
  /** Field the table sorts on. */
  field: string;
  /** 1 for ascending, -1 for descending. */
  order: number;
}

/** Filters and sort criteria of one scope. */
export interface WatchlistFilterSortEntries {
  /** Filters keyed by the field name the table filters on. */
  filters: { [filterKey: string]: StoredFilter };
  /** Sort criteria in the order they are applied. */
  sorts: StoredSort[];
}

/** Everything this service persists. */
export interface WatchlistFilterSortState {
  /** Scope new or changed filters are stored in. */
  filterScope: FilterSortScope;
  /** Scope new or changed sort criteria are stored in. */
  sortScope: FilterSortScope;
  /** Entries which apply to every watchlist. */
  global: WatchlistFilterSortEntries;
  /** Entries per watchlist. */
  byWatchlist: { [idWatchlist: number]: WatchlistFilterSortEntries };
}

/** One row of the settings dialog, for either a filter or a sort criterion. */
export interface FilterSortEntryView {
  /** Filter key or sort field, identifies the entry for removal. */
  key: string;
  /** Translated column header. */
  label: string;
  /** Readable representation of what is filtered, empty for a sort criterion. */
  valueText: string;
  /** Sort direction, 1 for ascending and -1 for descending; undefined for a filter. */
  order?: number;
  /** Scope the entry is stored in. */
  scope: FilterSortScope;
}

/** Matches an ISO date as JSON.stringify writes it, used to turn dates back into Date objects on read. */
const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,3})?Z$/;

/**
 * Keeps the column filters and the sort order of the watchlist tables.
 *
 * <p>
 * Each of the four watchlist types is its own component which is destroyed and recreated on every tab and every
 * watchlist change, so neither PrimeNG's filter map nor the sort metadata survive on their own. This service holds
 * both outside of the components and persists them in the local storage, which is why a filter entered on one tab is
 * immediately in effect on the others.
 * </p>
 *
 * <p>
 * An entry belongs either to a single watchlist or to all of them, see {@link FilterSortScope}. What a watchlist type
 * actually applies is the union of both, with the global entry winning when the same column is in both scopes.
 * Entries whose column does not exist in the current watchlist type are kept but not applied, so a filter of the
 * performance view is not lost while the user looks at the price feed view.
 * </p>
 */
@Injectable()
export class WatchlistFilterSortStateService {

  /** Emits after every change, so an open table can re-apply the settings without being recreated. */
  public readonly changed$: Observable<void>;

  private readonly changedSubject = new Subject<void>();
  private state: WatchlistFilterSortState;

  constructor(private usersettingsService: UserSettingsService) {
    this.changed$ = this.changedSubject.asObservable();
    this.state = this.readState();
  }

  /** @returns the scope new or changed filters are stored in */
  getFilterScope(): FilterSortScope {
    return this.state.filterScope;
  }

  /**
   * Sets the scope new or changed filters are stored in. Already stored filters keep their scope.
   *
   * @param scope the new scope
   */
  setFilterScope(scope: FilterSortScope): void {
    this.state.filterScope = scope;
    this.writeState();
  }

  /** @returns the scope new or changed sort criteria are stored in */
  getSortScope(): FilterSortScope {
    return this.state.sortScope;
  }

  /**
   * Sets the scope new or changed sort criteria are stored in. Already stored criteria keep their scope.
   *
   * @param scope the new scope
   */
  setSortScope(scope: FilterSortScope): void {
    this.state.sortScope = scope;
    this.writeState();
  }

  /**
   * Builds the filter map a watchlist type has to apply: the global filters first, then the local ones which are not
   * already covered by a global filter of the same column, reduced to the columns of that watchlist type.
   *
   * @param idWatchlist the watchlist which is displayed
   * @param fields the columns of the displaying table
   * @returns the filter map for PrimeNG, empty when nothing is filtered
   */
  getEffectiveFilters(idWatchlist: number, fields: ColumnConfig[]): { [filterKey: string]: any } {
    const applicable = this.applicableFilterKeys(fields);
    const local = this.entriesOf(idWatchlist).filters;
    const global = this.state.global.filters;
    const effective: { [filterKey: string]: any } = {};
    Object.keys(local).filter(key => applicable.has(key) && !global[key])
      .forEach(key => effective[key] = this.clone(local[key].meta));
    Object.keys(global).filter(key => applicable.has(key))
      .forEach(key => effective[key] = this.clone(global[key].meta));
    return effective;
  }

  /**
   * Builds the sort metadata a watchlist type has to apply. The global criteria come first, therefore they also have
   * the higher sort priority; local criteria of a column which is already sorted globally are dropped, and criteria of
   * columns the watchlist type does not have are skipped.
   *
   * @param idWatchlist the watchlist which is displayed
   * @param fields the columns of the displaying table
   * @returns the sort metadata for PrimeNG, empty when nothing is sorted
   */
  getEffectiveSorts(idWatchlist: number, fields: ColumnConfig[]): SortMeta[] {
    const applicable = this.applicableSortFields(fields);
    const global = this.state.global.sorts.filter(sort => applicable.has(sort.field));
    const local = this.entriesOf(idWatchlist).sorts.filter(sort => applicable.has(sort.field)
      && !global.some(globalSort => globalSort.field === sort.field));
    return [...global, ...local].map(sort => ({field: sort.field, order: sort.order}));
  }

  /**
   * Takes over the filters the user has entered. Only the columns of the displaying table are considered, so filters
   * of other watchlist types stay untouched. An unchanged filter keeps its scope, a new or changed one is stored in
   * the scope of {@link getFilterScope} and removed from the other, a cleared one is removed from both.
   *
   * @param idWatchlist the watchlist which is displayed
   * @param fields the columns of the displaying table
   * @param currentFilters PrimeNG's filter map of the displaying table
   */
  captureFilters(idWatchlist: number, fields: ColumnConfig[], currentFilters: { [filterKey: string]: any }): void {
    const local = this.entriesOf(idWatchlist).filters;
    const global = this.state.global.filters;
    let changed = false;
    this.applicableFilterKeys(fields).forEach((label, key) => {
      const meta = currentFilters ? currentFilters[key] : null;
      if (this.isFilterBlank(meta)) {
        changed = this.deleteKey(global, key) || changed;
        changed = this.deleteKey(local, key) || changed;
      } else if (!this.isSameMeta(global[key], meta) && !this.isSameMeta(local[key], meta)) {
        const target = this.state.filterScope === FilterSortScope.GLOBAL ? global : local;
        const other = target === global ? local : global;
        target[key] = {label, meta: this.clone(meta)};
        this.deleteKey(other, key);
        changed = true;
      }
    });
    if (changed) {
      this.writeState();
    }
  }

  /**
   * Takes over the sort order the user has chosen, following the same rules as {@link captureFilters}.
   *
   * @param idWatchlist the watchlist which is displayed
   * @param fields the columns of the displaying table
   * @param sortMeta the sort metadata of the displaying table
   */
  captureSorts(idWatchlist: number, fields: ColumnConfig[], sortMeta: SortMeta[]): void {
    const applicable = this.applicableSortFields(fields);
    const localEntries = this.entriesOf(idWatchlist);
    const current = (sortMeta ?? []).filter(sort => applicable.has(sort.field));
    let changed = this.removeDroppedSorts(this.state.global.sorts, applicable, current);
    changed = this.removeDroppedSorts(localEntries.sorts, applicable, current) || changed;
    current.forEach(sort => {
      const inGlobal = this.state.global.sorts.find(stored => stored.field === sort.field);
      const inLocal = localEntries.sorts.find(stored => stored.field === sort.field);
      if (inGlobal?.order !== sort.order && inLocal?.order !== sort.order) {
        const target = this.state.sortScope === FilterSortScope.GLOBAL ? this.state.global.sorts : localEntries.sorts;
        const other = target === this.state.global.sorts ? localEntries.sorts : this.state.global.sorts;
        this.removeSortField(other, sort.field);
        const existing = target.find(stored => stored.field === sort.field);
        if (existing) {
          // Only the direction was toggled, keep the position because it is the sort priority.
          existing.order = sort.order;
        } else {
          target.push({label: applicable.get(sort.field), field: sort.field, order: sort.order});
        }
        changed = true;
      }
    });
    if (changed) {
      this.writeState();
    }
  }

  /**
   * Lists all stored filters for the settings dialog, the global ones first.
   *
   * @param idWatchlist the watchlist which is displayed
   * @returns one row per stored filter
   */
  listFilters(idWatchlist: number): FilterSortEntryView[] {
    const toView = (entries: { [filterKey: string]: StoredFilter }, scope: FilterSortScope) =>
      Object.keys(entries).map(key => ({
        key, label: entries[key].label, valueText: this.filterValueText(entries[key].meta), scope
      }));
    return [...toView(this.state.global.filters, FilterSortScope.GLOBAL),
      ...toView(this.entriesOf(idWatchlist).filters, FilterSortScope.LOCAL)];
  }

  /**
   * Lists all stored sort criteria for the settings dialog, the global ones first.
   *
   * @param idWatchlist the watchlist which is displayed
   * @returns one row per stored sort criterion
   */
  listSorts(idWatchlist: number): FilterSortEntryView[] {
    const toView = (sorts: StoredSort[], scope: FilterSortScope) => sorts.map(sort => ({
      key: sort.field, label: sort.label, valueText: '', order: sort.order, scope
    }));
    return [...toView(this.state.global.sorts, FilterSortScope.GLOBAL),
      ...toView(this.entriesOf(idWatchlist).sorts, FilterSortScope.LOCAL)];
  }

  /**
   * Removes a single filter.
   *
   * @param idWatchlist the watchlist which is displayed
   * @param key the filter key of the entry
   * @param scope the scope the entry is stored in
   */
  removeFilter(idWatchlist: number, key: string, scope: FilterSortScope): void {
    const entries = scope === FilterSortScope.GLOBAL ? this.state.global : this.entriesOf(idWatchlist);
    this.deleteKey(entries.filters, key);
    this.writeState();
  }

  /**
   * Removes a single sort criterion.
   *
   * @param idWatchlist the watchlist which is displayed
   * @param field the sort field of the entry
   * @param scope the scope the entry is stored in
   */
  removeSort(idWatchlist: number, field: string, scope: FilterSortScope): void {
    const entries = scope === FilterSortScope.GLOBAL ? this.state.global : this.entriesOf(idWatchlist);
    this.removeSortField(entries.sorts, field);
    this.writeState();
  }

  /**
   * Removes all filters of one watchlist. Global filters are not touched, they are removed with
   * {@link clearAllFilters} or one by one.
   *
   * @param idWatchlist the watchlist whose filters are removed
   */
  clearFilters(idWatchlist: number): void {
    this.entriesOf(idWatchlist).filters = {};
    this.writeState();
  }

  /** Removes the global filters and the filters of every watchlist. */
  clearAllFilters(): void {
    this.state.global.filters = {};
    Object.values(this.state.byWatchlist).forEach(entries => entries.filters = {});
    this.writeState();
  }

  /**
   * Removes all sort criteria of one watchlist. Global criteria are not touched, they are removed with
   * {@link clearAllSorts} or one by one.
   *
   * @param idWatchlist the watchlist whose sort criteria are removed
   */
  clearSorts(idWatchlist: number): void {
    this.entriesOf(idWatchlist).sorts = [];
    this.writeState();
  }

  /** Removes the global sort criteria and those of every watchlist. */
  clearAllSorts(): void {
    this.state.global.sorts = [];
    Object.values(this.state.byWatchlist).forEach(entries => entries.sorts = []);
    this.writeState();
  }

  /**
   * Returns the entries of a watchlist, creating them on first use.
   *
   * @param idWatchlist the watchlist
   * @returns the filters and sort criteria of that watchlist
   */
  private entriesOf(idWatchlist: number): WatchlistFilterSortEntries {
    if (!this.state.byWatchlist[idWatchlist]) {
      this.state.byWatchlist[idWatchlist] = {filters: {}, sorts: []};
    }
    return this.state.byWatchlist[idWatchlist];
  }

  /**
   * Determines the filter keys a table can apply. The key is the field PrimeNG filters on, which for a column with a
   * transformed or translated value is the shadow field and not the field itself.
   *
   * @param fields the columns of the table
   * @returns filter key to translated column header
   */
  private applicableFilterKeys(fields: ColumnConfig[]): Map<string, string> {
    const keys = new Map<string, string>();
    fields.filter(field => field.filterType).forEach(field =>
      keys.set(getFilterKey(field), field.headerTranslated || field.headerKey));
    return keys;
  }

  /**
   * Determines the fields a table can sort on.
   *
   * @param fields the columns of the table
   * @returns sort field to translated column header
   */
  private applicableSortFields(fields: ColumnConfig[]): Map<string, string> {
    const sortFields = new Map<string, string>();
    fields.forEach(field => sortFields.set(field.field, field.headerTranslated || field.headerKey));
    return sortFields;
  }

  /**
   * Removes the stored sort criteria of columns the table has but the user no longer sorts by.
   *
   * @param sorts the stored criteria of one scope
   * @param applicable the fields the table can sort on
   * @param current the criteria the table sorts by now
   * @returns true when something was removed
   */
  private removeDroppedSorts(sorts: StoredSort[], applicable: Map<string, string>, current: SortMeta[]): boolean {
    const dropped = sorts.filter(stored => applicable.has(stored.field)
      && !current.some(sort => sort.field === stored.field));
    dropped.forEach(stored => this.removeSortField(sorts, stored.field));
    return dropped.length > 0;
  }

  /**
   * Removes a sort criterion from a list.
   *
   * @param sorts the list to change
   * @param field the sort field to remove
   */
  private removeSortField(sorts: StoredSort[], field: string): void {
    const index = sorts.findIndex(sort => sort.field === field);
    if (index >= 0) {
      sorts.splice(index, 1);
    }
  }

  /**
   * Removes a key from a filter map.
   *
   * @param filters the map to change
   * @param key the filter key to remove
   * @returns true when the key existed
   */
  private deleteKey(filters: { [filterKey: string]: StoredFilter }, key: string): boolean {
    if (filters[key]) {
      delete filters[key];
      return true;
    }
    return false;
  }

  /**
   * Decides whether a filter holds a value at all. Follows PrimeNG, which treats null, undefined and the empty string
   * as no filter, and considers a constraint list blank when none of its constraints has a value. A multi select
   * filter carries an array of values, which is blank while nothing is selected.
   *
   * @param meta the filter metadata to check
   * @returns true when nothing is filtered
   */
  private isFilterBlank(meta: any): boolean {
    if (meta == null) {
      return true;
    }
    if (Array.isArray(meta)) {
      return meta.every(constraint => this.isFilterBlank(constraint));
    }
    return Array.isArray(meta.value) ? meta.value.length === 0
      : meta.value === null || meta.value === undefined || meta.value === '';
  }

  /**
   * Compares a stored filter with the current one. The comparison runs over the serialized form, which also levels out
   * that a restored date is a Date while a freshly entered one may still be a string.
   *
   * @param stored the stored filter, may be undefined
   * @param meta the current filter metadata
   * @returns true when both filter for the same thing
   */
  private isSameMeta(stored: StoredFilter, meta: any): boolean {
    return !!stored && JSON.stringify(stored.meta) === JSON.stringify(meta);
  }

  /**
   * Builds the text the settings dialog shows for a filter.
   *
   * @param meta the filter metadata
   * @returns the filtered values, separated by a comma
   */
  private filterValueText(meta: any): string {
    const constraints = Array.isArray(meta) ? meta : [meta];
    return constraints.filter(constraint => !this.isFilterBlank(constraint))
      .map(constraint => Array.isArray(constraint.value) ? constraint.value.join(', ')
        : constraint.value instanceof Date ? constraint.value.toLocaleDateString()
          : String(constraint.value)).join(', ');
  }

  /**
   * Copies filter metadata, so the stored state and the map handed to the table cannot influence each other.
   *
   * @param meta the metadata to copy
   * @returns an independent copy with dates preserved
   */
  private clone(meta: any): any {
    return this.reviveDates(JSON.parse(JSON.stringify(meta)));
  }

  /** @returns the persisted state, or an empty one when nothing was stored yet */
  private readState(): WatchlistFilterSortState {
    const stored: WatchlistFilterSortState = this.usersettingsService.retrieveObject(
      AppSettings.WATCHLIST_FILTER_SORT_STORE);
    if (!stored) {
      return {
        filterScope: FilterSortScope.LOCAL, sortScope: FilterSortScope.LOCAL,
        global: {filters: {}, sorts: []}, byWatchlist: {}
      };
    }
    stored.global = stored.global ?? {filters: {}, sorts: []};
    stored.byWatchlist = stored.byWatchlist ?? {};
    return this.reviveDates(stored);
  }

  /** Persists the state and notifies the open table. */
  private writeState(): void {
    this.usersettingsService.saveObject(AppSettings.WATCHLIST_FILTER_SORT_STORE, this.state);
    this.changedSubject.next();
  }

  /**
   * Turns the ISO strings JSON leaves behind back into dates, otherwise a date filter would compare a string against a
   * Date and never match.
   *
   * @param value the value to walk through
   * @returns the value with all ISO date strings replaced by Date objects
   */
  private reviveDates(value: any): any {
    if (typeof value === 'string') {
      return ISO_DATE_PATTERN.test(value) ? new Date(value) : value;
    }
    if (Array.isArray(value)) {
      return value.map(entry => this.reviveDates(entry));
    }
    if (value !== null && typeof value === 'object') {
      Object.keys(value).forEach(key => value[key] = this.reviveDates(value[key]));
    }
    return value;
  }
}
