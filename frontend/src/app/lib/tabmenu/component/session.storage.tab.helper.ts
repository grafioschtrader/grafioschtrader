import { Injectable } from '@angular/core';
import { TabItem } from '../../types/tab.item';

/**
 * Helper service for managing tab-related session storage operations
 * Handles both legacy index-based and modern route-based storage
 */
@Injectable({
  providedIn: 'root'
})
export class SessionStorageTabHelper {

  /**
   * Get saved active route from session storage
   * Handles both index-based (legacy) and route-based storage
   *
   * @param sessionStorageKey - The key to use for session storage
   * @param tabs - Array of available tabs
   * @returns The saved route or null if not found
   */
  getSavedActiveRoute(sessionStorageKey: string, tabs: TabItem[]): string | null {
    if (!sessionStorageKey || !tabs || tabs.length === 0) {
      return null;
    }

    const savedValue = sessionStorage.getItem(sessionStorageKey);
    if (!savedValue) {
      return null;
    }

    // Handle both index-based (legacy) and route-based storage
    const savedIndex = +savedValue;
    if (!isNaN(savedIndex) && savedIndex >= 0 && savedIndex < tabs.length) {
      // Legacy: stored as index
      return tabs[savedIndex]?.route || null;
    } else {
      // New: stored as route
      return tabs.find(tab => tab.route === savedValue)?.route || null;
    }
  }

  /**
   * Save active route to session storage
   * Saves as index for backward compatibility
   *
   * @param sessionStorageKey - The key to use for session storage
   * @param route - The current active route
   * @param tabs - Array of available tabs
   */
  saveActiveRoute(sessionStorageKey: string, route: string, tabs: TabItem[]): void {
    if (!sessionStorageKey || !route || !tabs || tabs.length === 0) {
      return;
    }

    // Find the index of the current route (for backward compatibility)
    const tabIndex = tabs.findIndex(tab => tab.route === route);
    if (tabIndex >= 0) {
      sessionStorage.setItem(sessionStorageKey, tabIndex.toString());
    }
  }

  /**
   * Clear saved route from session storage
   *
   * @param sessionStorageKey - The key to clear from session storage
   */
  clearSavedRoute(sessionStorageKey: string): void {
    if (sessionStorageKey) {
      sessionStorage.removeItem(sessionStorageKey);
    }
  }

  /**
   * Check if a route exists in the tabs array
   *
   * @param route - The route to check
   * @param tabs - Array of available tabs
   * @returns True if the route exists, false otherwise
   */
  isValidRoute(route: string, tabs: TabItem[]): boolean {
    return tabs.some(tab => tab.route === route);
  }
}
