/**
 * Interface representing a UDF (User-Defined Field) special type.
 * Special types are predefined UDF configurations that provide system-level functionality
 * which users can optionally enable or disable based on their needs.
 *
 * This interface enables a registry pattern where application-specific special types
 * can be registered dynamically without creating coupling between the library and
 * application-specific code.
 */
export interface IUDFSpecialType {
  /** Numeric value identifying this special type (stored in database) */
  value: number;

  /** Enum-style name identifier for this special type (e.g., "UDF_SPEC_INTERNAL_CALC_YIELD_TO_MATURITY") */
  name: string;
}
