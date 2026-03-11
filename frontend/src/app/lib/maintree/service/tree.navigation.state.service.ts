import {Injectable} from '@angular/core';

/**
 * Shared state service for passing entity data between tree navigation and routed components.
 * Avoids serializing large entity objects into route URLs, which degrades performance because
 * Angular's router processes the full URL on every change detection cycle.
 *
 * Usage: The main tree stores entities here before navigation; target components read them by key.
 * Keys use the format "route_id" (e.g., "securityaccountTabMenu_189").
 */
@Injectable()
export class TreeNavigationStateService {

  private entityMap = new Map<string, any>();

  /**
   * Stores an entity associated with a navigation. Entities are stored as parsed objects (not JSON strings).
   *
   * @param route The route path used for navigation
   * @param id The entity ID
   * @param entity The entity object (already parsed, not JSON)
   */
  setEntity(route: string, id: number, entity: any): void {
    this.entityMap.set(this.buildKey(route, id), entity);
  }

  /**
   * Retrieves a previously stored entity for the given route and ID.
   *
   * @param route The route path
   * @param id The entity ID
   * @returns The stored entity, or null if not found (e.g., after a page refresh)
   */
  getEntity<T>(route: string, id: number): T | null {
    return this.entityMap.get(this.buildKey(route, id)) as T ?? null;
  }

  private buildKey(route: string, id: number): string {
    return `${route}_${id}`;
  }
}
