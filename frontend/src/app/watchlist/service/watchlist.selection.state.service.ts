import { Service } from '@angular/core';

/**
 * Remembers which instruments are selected in a watchlist. The table of a watchlist tab is destroyed and recreated
 * whenever the user switches to another tab (performance, price feed, UDF, ...), so the selection cannot live in the
 * table component if the user expects the same instrument to stay selected on every tab of the same watchlist.
 *
 * <p>
 * This service is deliberately declared <b>without</b> {@code providedIn}. It is listed in the {@code providers} of
 * {@code WatchlistTabMenuComponent}, therefore the selection is kept while the user stays within the watchlist area
 * and is forgotten as soon as the watchlist area is left.
 * </p>
 */
@Service()
export class WatchlistSelectionStateService {
  /** Ids of the selected instruments per watchlist. */
  private readonly selectedIds = new Map<number, number[]>();

  /**
   * Returns the selected instruments of a watchlist.
   *
   * @param idWatchlist the watchlist whose selection is requested
   * @returns the ids of the selected securities and currency pairs, empty when nothing was selected
   */
  getSelectedIds(idWatchlist: number): number[] {
    return this.selectedIds.get(idWatchlist) ?? [];
  }

  /**
   * Stores the selection of a watchlist, replacing a previous one.
   *
   * @param idWatchlist the watchlist the selection belongs to
   * @param ids the ids of the selected securities and currency pairs, empty to clear the selection
   */
  setSelectedIds(idWatchlist: number, ids: number[]): void {
    if (ids.length === 0) {
      this.selectedIds.delete(idWatchlist);
    } else {
      this.selectedIds.set(idWatchlist, ids);
    }
  }
}
