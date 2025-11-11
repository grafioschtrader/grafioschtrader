import {BaseID} from '../../entities/base.id';
import {Exclude} from 'class-transformer';
import {FieldDescriptorInputAndShowExtended} from '../../dynamicfield/field.descriptor.input.and.show';

/**
 * Abstract base class for User-Defined Field (UDF) metadata configuration.
 * This class stores the definition and structure of custom fields that users can add to various entities
 * in the system. Each UDF metadata entry defines a field's data type, size, display order, and help text.
 * UDF metadata can be owned by individual users or shared across all users (when idUser is 0).
 */
export abstract class UDFMetadata implements BaseID {
  /** Unique identifier for this UDF metadata entry */
  idUDFMetadata: number = null;

  /** Owner user ID. Value of 0 indicates a system-wide UDF available to all users */
  idUser: number = null;

  /**
   * Special type classification for UDFs with predefined purposes.
   * Numeric value corresponding to registered IUDFSpecialType entries in UDFSpecialTypeRegistry.
   * Use UDFSpecialTypeRegistry.getByValue() to resolve the full special type object.
   */
  udfSpecialType: number;

  /** User-visible field label or name */
  description: string = null;

  /** Optional help text displayed to guide users when filling out this field */
  descriptionHelp: string = null;

  /** Data type of the field (numeric, string, date, boolean, URL, etc.) */
  udfDataType: UDFDataType = null;

  /** Maximum field size or precision specification. Format varies by data type (e.g., "10" for max length, "10,2" for decimal precision) */
  fieldSize: string = null;

  /** Display order for the field in UI forms. Lower numbers appear first */
  uiOrder: number = null;

  /**
   * Returns the unique identifier of this UDF metadata entry.
   * Excluded from serialization by class-transformer.
   *
   * @returns The UDF metadata ID
   */
  @Exclude()
  getId(): number {
    return this.idUDFMetadata;
  }
}

/**
 * UDF metadata configuration for general entity types.
 * Extends base UDF metadata to support any entity in the system by storing the entity type name.
 * This allows users to add custom fields to entities like portfolios, watchlists, transactions, etc.
 */
export class UDFMetadataGeneral extends UDFMetadata {
  /** Entity type name that this UDF applies to (e.g., "Portfolio", "Watchlist", "Transaction") */
  entity: string = null;
}



/**
 * Parameters for UDF metadata editing operations.
 * Contains validation data to prevent duplicate field names and UI order conflicts.
 */
export class UDFMetadataParam {
  /** List of existing field names that should not be reused to prevent duplicates */
  excludeFieldNames: string[];

  /** List of existing UI order positions that should not be reused to prevent display conflicts */
  excludeUiOrders: number[];
}

/**
 * Extended parameters for general UDF metadata editing.
 * Includes the UDF metadata entity being edited along with validation exclusion lists.
 */
export class UDFMetadataGeneralParam extends UDFMetadataParam {
  /** The UDF metadata entity being edited, or null when creating a new entry */
  uDFMetadataGeneral: UDFMetadataGeneral
}

/**
 * Container for user-defined field data values.
 * Stores the actual user-entered data for UDF fields on a specific entity instance.
 * The jsonValues object contains key-value pairs where keys are UDF field names (prefixed with 'f' + field ID).
 */
export class UDFData {
  /**
   * Creates a new UDF data container.
   *
   * @param uDFDataKey - Composite key identifying the entity instance and user owning this data
   * @param jsonValues - Object containing the actual UDF field values as key-value pairs
   */
  constructor(public uDFDataKey: UDFDataKey, public jsonValues: any) {
  }
}

/**
 * Composite key identifying a specific UDF data record.
 * Uniquely identifies UDF data by user, entity type, and specific entity instance.
 */
export class UDFDataKey {
  /**
   * Creates a new UDF data key.
   *
   * @param idUser - User ID owning this UDF data
   * @param entity - Entity type name (e.g., "Portfolio", "Watchlist")
   * @param idEntity - Specific instance ID of the entity
   */
  constructor(public idUser: number, public entity: string, public idEntity: number) {
  }
}

/**
 * Extended field descriptor interface for general UDF fields.
 * Adds entity type information to the standard field descriptor for dynamic form generation.
 */
export interface FieldDescriptorInputAndShowExtendedGeneral extends FieldDescriptorInputAndShowExtended {
  /** Entity type name this field descriptor applies to */
  entity: string;
}

/**
 * Parameters for invoking general UDF data editing dialogs.
 * Contains all context needed to display and edit UDF fields for a specific entity instance.
 */
export class UDFGeneralCallParam {
  /**
   * Creates parameters for opening a UDF editing dialog.
   *
   * @param entityName - Entity type name (e.g., "Portfolio", "Watchlist")
   * @param selectedEntity - The entity instance being edited
   * @param udfData - Existing UDF data for this entity, or null when creating new data
   * @param titleKey - Translation key for the dialog title
   */
  constructor(public entityName: string, public selectedEntity: BaseID, public udfData: UDFData, public titleKey: string) {
  }
}

/**
 * Composite identifier for user-specific UDF special type disabling.
 * Used as the primary key for tracking which special UDF types a user has disabled.
 */
export class UDFSpecialTypeDisableUserId
{
  /** User ID who disabled this UDF special type */
  idUser: number;

  /**
   * The special UDF type that has been disabled (numeric value).
   * Use UDFSpecialTypeRegistry.getByValue() to resolve the full special type object.
   */
  udfSpecialType: number;
}

/**
 * Entity representing a user's disabled UDF special type.
 * Allows users to hide predefined UDF fields that they don't need in their workflow.
 */
export class UDFSpecialTypeDisableUser {
  /** Composite key identifying the user and disabled UDF special type */
  id: UDFSpecialTypeDisableUserId;
}

/**
 * Enumeration of supported data types for user-defined fields.
 * Each value represents a different input and validation type for UDF fields.
 */
export enum UDFDataType {
  /** Decimal numeric value with configurable precision */
  UDF_Numeric = 1,

  /** Integer numeric value without decimal places */
  UDF_NumericInteger = 4,

  /** Text string with configurable maximum length */
  UDF_String = 7,

  /** Date/time value stored as numeric timestamp */
  UDF_DateTimeNumeric = 8,

  /** Date value stored as formatted string */
  UDF_DateString = 10,

  /** Boolean true/false value displayed as checkbox */
  UDF_Boolean = 13,

  /** URL string with validation for proper URL format */
  UDF_URLString = 20
}
