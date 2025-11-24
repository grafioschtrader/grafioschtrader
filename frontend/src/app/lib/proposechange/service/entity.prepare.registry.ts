import {Injectable, Type} from '@angular/core';
import {PrepareCallParam} from '../component/general.entity.prepare.edit';

/**
 * Central registry for entity preparation handlers and edit components used in the propose change workflow.
 * This registry allows applications to register entity-specific behavior without the library needing to know
 * about specific entity types. Applications register handlers for each entity type they support, enabling
 * the lib code to remain entity-agnostic and reusable.
 *
 * The registry pattern provides a clean separation between the generic propose change workflow (in lib)
 * and application-specific entity implementations (in the main app).
 */
@Injectable({providedIn: 'root'})
export class EntityPrepareRegistry {
  /**
   * Map of entity type identifiers to their preparation handlers.
   * Each handler knows how to prepare a specific entity type for editing, including loading
   * related data and configuring dialog parameters.
   */
  private prepareHandlers = new Map<string, PrepareCallParam<any>>();

  /**
   * Map of entity type identifiers to their corresponding edit component types.
   * These components are dynamically loaded when an entity needs to be edited.
   */
  private editComponents = new Map<string, Type<any>>();

  /**
   * Registers a handler and edit component for a specific entity type.
   * Applications call this during initialization (typically via provideAppInitializer() or module constructor)
   * to configure which entities can be edited through the propose change workflow.
   *
   * @param entityType - Unique identifier for the entity type (e.g., "Assetclass", "Security")
   * @param prepareHandler - Handler that prepares entities of this type for editing
   * @param editComponent - Component type used to edit entities of this type
   */
  registerEntityHandler(entityType: string, prepareHandler: PrepareCallParam<any>, editComponent: Type<any>): void {
    this.prepareHandlers.set(entityType, prepareHandler);
    this.editComponents.set(entityType, editComponent);
  }

  /**
   * Retrieves the preparation handler for a specific entity type.
   *
   * @param entityType - The entity type identifier
   * @returns The registered preparation handler, or undefined if not registered
   */
  getPrepareHandler(entityType: string): PrepareCallParam<any> | undefined {
    return this.prepareHandlers.get(entityType);
  }

  /**
   * Retrieves the edit component type for a specific entity type.
   *
   * @param entityType - The entity type identifier
   * @returns The registered component type, or undefined if not registered
   */
  getEditComponent(entityType: string): Type<any> | undefined {
    return this.editComponents.get(entityType);
  }

  /**
   * Checks if a handler and component are registered for the given entity type.
   *
   * @param entityType - The entity type identifier
   * @returns True if both handler and component are registered, false otherwise
   */
  hasEntityHandler(entityType: string): boolean {
    return this.prepareHandlers.has(entityType) && this.editComponents.has(entityType);
  }

  /**
   * Removes the handler and component registration for a specific entity type.
   * Useful for dynamic scenarios or testing.
   *
   * @param entityType - The entity type identifier to unregister
   */
  unregisterEntityHandler(entityType: string): void {
    this.prepareHandlers.delete(entityType);
    this.editComponents.delete(entityType);
  }

  /**
   * Returns all registered entity type identifiers.
   *
   * @returns Array of entity type identifiers
   */
  getRegisteredEntityTypes(): string[] {
    return Array.from(this.prepareHandlers.keys());
  }
}
