import {Observable} from 'rxjs';

/**
 * Interface for services that provide entity update functionality.
 * Defines contract for updating entities with asynchronous operations.
 *
 * @template T - The type of entity to update
 */
export interface ServiceEntityUpdate<T> {
  /**
   * Updates an entity and returns the updated entity as an observable.
   *
   * @param {T} entity - The entity to update
   * @returns {Observable<T>} Observable containing the updated entity
   */
  update(entity: T): Observable<T>;
}
