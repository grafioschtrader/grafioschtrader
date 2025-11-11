import {BaseID} from '../../entities/base.id';

/**
 * Abstract base class for preparing entities for edit dialogs in the propose change workflow.
 * Uses generic type parameter to support any entity type that implements BaseID, allowing the
 * library code to remain application-agnostic.
 *
 * Subclasses can override redirectEntityMapping to provide custom entity routing logic
 * when dealing with related or derived entities.
 *
 * @template T - Entity type that implements BaseID interface
 */
export abstract class BasePrepareEdit<T extends BaseID = BaseID> {
  /**
   * Determines if the proposed entity should be redirected to a different entity mapping.
   * Override this method in subclasses to handle special cases where an entity should
   * be edited using a different entity type's editor (e.g., derived securities).
   *
   * @param proposedEntity - The entity being proposed for editing
   * @returns String identifier of the alternative entity mapping, or null to use default mapping
   */
  redirectEntityMapping(proposedEntity: T): string {
    return null;
  }
}
