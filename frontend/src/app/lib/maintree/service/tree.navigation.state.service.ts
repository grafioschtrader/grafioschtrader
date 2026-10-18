import { Injectable } from '@angular/core';

/**
 * Shared state service for passing entity data between tree navigation and routed components.
 * Avoids serializing large entity objects into route URLs, which degrades performance because
 * Angular's router processes the full URL on every change detection cycle.
 *
 * Usage: The main tree stores entities here before navigation; target components read them by key.
 * Keys use the format "route_id" (e.g., "securityaccountTabMenu_189").
 *
 * Every entity is also mirrored into the session storage of the browser tab. A page reload, whether by the user or
 * by the development server after a recompilation, clears the in-memory map while the URL still points to the routed
 * component; without the mirror that component would find no entity and render nothing useful. The session storage is
 * per tab and is cleared on logout.
 */
@Injectable()
export class TreeNavigationStateService {
  /** Prefix of the session storage keys, keeps them apart from the other entries of the application. */
  private static readonly STORAGE_PREFIX = 'treeNav_';

  private entityMap = new Map<string, any>();

  /**
   * Stores an entity associated with a navigation. Entities are stored as parsed objects (not JSON strings).
   *
   * @param route The route path used for navigation
   * @param id The entity ID
   * @param entity The entity object (already parsed, not JSON)
   */
  setEntity(route: string, id: number, entity: any): void {
    const key = this.buildKey(route, id);
    this.entityMap.set(key, entity);
    try {
      sessionStorage.setItem(TreeNavigationStateService.STORAGE_PREFIX + key, JSON.stringify(entity));
    } catch {
      // Storage full or not available: the entity still lives in memory until the next reload
    }
  }

  /**
   * Retrieves a previously stored entity for the given route and ID. After a page reload it comes from the session
   * storage of the tab.
   *
   * @param route The route path
   * @param id The entity ID
   * @returns The stored entity, or null if it was never stored in this tab
   */
  getEntity<T>(route: string, id: number): T | null {
    const key = this.buildKey(route, id);
    let entity = this.entityMap.get(key);
    if (entity === undefined) {
      entity = this.readFromSessionStorage(key);
      if (entity != null) {
        this.entityMap.set(key, entity);
      }
    }
    return (entity as T) ?? null;
  }

  private readFromSessionStorage(key: string): any {
    try {
      const json = sessionStorage.getItem(TreeNavigationStateService.STORAGE_PREFIX + key);
      return json ? JSON.parse(json) : null;
    } catch {
      return null;
    }
  }

  private buildKey(route: string, id: number): string {
    return `${route}_${id}`;
  }
}
