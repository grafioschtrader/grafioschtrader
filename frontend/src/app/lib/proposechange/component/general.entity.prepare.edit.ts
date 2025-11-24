import {BasePrepareEdit} from './base.prepare.edit';
import {ProposeTransientTransfer} from '../../entities/propose.transient.transfer';
import {BaseID} from '../../entities/base.id';
import {Type} from '@angular/core';
import {Observable, of} from 'rxjs';

/**
 * Generic implementation of entity preparation for edit dialogs in the propose change workflow.
 * This class provides a standard pattern for preparing any entity type that implements BaseID
 * for editing, creating a clone of the entity and making it available through the EntityMapping.
 *
 * The generic approach allows the library to handle any entity type without knowing specific
 * application entity classes, maintaining clean architectural separation.
 *
 * @template T - Entity type that implements BaseID interface
 */
export class GeneralEntityPrepareEdit<T extends BaseID> extends BasePrepareEdit<T> implements PrepareCallParam<T> {

  /**
   * Creates a general entity preparation handler.
   *
   * @param type - Constructor function for creating new instances of the entity type
   */
  constructor(private type: new() => T) {
    super();
  }

  /**
   * Prepares an entity for editing by creating a clone and configuring the entity mapping.
   * The clone is created using the entity constructor and Object.assign to copy all properties.
   *
   * @param entity - The entity to prepare for editing
   * @param entityMapping - Container for edit dialog state and parameters
   * @returns Observable that completes when preparation is done
   */
  prepareForEditEntity(entity: T, entityMapping: EntityMapping): Observable<void> {
    entityMapping.callParam = new this.type();
    Object.assign(entityMapping.callParam, entity);
    entityMapping.visibleDialog = true;
    return of(undefined);
  }
}

/**
 * Container class for managing entity edit dialog state and parameters.
 * Holds the dialog visibility state, entity-specific call parameters, edit component type, and additional options.
 */
export class EntityMapping {
  /** Whether the edit dialog should be visible */
  visibleDialog: boolean;

  /** Entity-specific parameters passed to the edit dialog */
  callParam: any = {};

  /** Additional options for customizing edit dialog behavior */
  option: any = {};

  /** Component type to use for editing this entity (dynamically loaded) */
  editComponentType?: Type<any>;

  /**
   * Creates an entity mapping.
   *
   * @param prepareCallParam - Handler for preparing entities for editing
   */
  constructor(public prepareCallParam: PrepareCallParam<any>) {
  }
}

/**
 * Interface for classes that prepare entities for editing in the propose change workflow.
 * Implementations handle entity-specific preparation logic including validation,
 * dependency loading, and dialog parameter configuration.
 *
 * @template T - Entity type that implements BaseID interface
 */
export interface PrepareCallParam<T extends BaseID> {
  /**
   * Determines if the proposed entity should use a different entity mapping for editing.
   *
   * @param proposeEntity - The entity being proposed for editing
   * @returns String identifier of alternative entity mapping, or null to use default
   */
  redirectEntityMapping(proposeEntity: T): string;

  /**
   * Prepares an entity for display in an edit dialog.
   * May load related data, validate permissions, or configure dialog-specific parameters.
   *
   * @param entity - The entity to prepare (can be the entity type T or ProposeTransientTransfer containing entity data)
   * @param entityMapping - Container for dialog state and parameters to be populated
   * @returns Observable that completes when preparation is done (allows for async data loading)
   */
  prepareForEditEntity(entity: T | ProposeTransientTransfer, entityMapping: EntityMapping): Observable<void>;
}
