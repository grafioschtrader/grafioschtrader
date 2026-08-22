import { Injectable } from '@angular/core';

/**
 * Remembers which accordion panels of an expanded watchlist row are open. The component of an expanded row is
 * destroyed as soon as the row is collapsed, and the watchlist table itself is recreated on every watchlist or tab
 * change, so this state cannot live in those components if the user's choice is to survive.
 *
 * <p>
 * This service is deliberately declared <b>without</b> {@code providedIn}. It is listed in the {@code providers} of
 * {@code WatchlistTabMenuComponent}, therefore the remembered panels are kept while the user stays within the
 * watchlist area and are forgotten as soon as the watchlist area is left.
 * </p>
 */
@Injectable()
export class WatchlistExpandedPanelStateService {
  /** Open panel values per instrument. */
  private readonly openPanels = new Map<number, string[]>();

  /**
   * Returns the open panels of an instrument, an empty array when the user has not opened a panel yet.
   *
   * @param idSecuritycurrency the security or currency pair of the expanded row
   * @returns the values of the currently open accordion panels
   */
  getOpenPanels(idSecuritycurrency: number): string[] {
    return this.openPanels.get(idSecuritycurrency) ?? [];
  }

  /**
   * Stores the panels the user has open for an instrument.
   *
   * @param idSecuritycurrency the security or currency pair of the expanded row
   * @param panels the values of the accordion panels which are now open
   */
  setOpenPanels(idSecuritycurrency: number, panels: string[]): void {
    this.openPanels.set(idSecuritycurrency, panels);
  }
}
