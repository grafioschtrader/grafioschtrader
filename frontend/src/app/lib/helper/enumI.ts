/**
 * Interface representing the structure of TypeScript numeric enums.
 * Defines the contract for enum objects that map numeric indices to string values,
 * enabling type-safe operations on enums in utility functions.
 *
 * TypeScript numeric enums automatically implement this interface structure,
 * creating bidirectional mappings where numeric values map to string names
 * and string names map back to numeric values.
 *
 * This interface is primarily used by SelectOptionsHelper and other utility
 * classes to ensure type safety when working with enum objects for dropdown
 * generation, translation, and filtering operations.
 */
export interface EnumI {
  /** Maps numeric enum values to their string representations */
  [id: number]: string;
}
