import {UDFMetadata, UDFMetadataParam} from '../../lib/udfmeta/model/udf.metadata';
import {AssetclassType} from '../../shared/types/assetclass.type';
import {SpecialInvestmentInstruments} from '../../shared/types/special.investment.instruments';
import {FieldDescriptorInputAndShowExtended} from '../../lib/dynamicfield/field.descriptor.input.and.show';
import {IUDFSpecialType} from '../../lib/udfmeta/model/udf.special.type.interface';

/**
 * UDF metadata configuration specific to securities and financial instruments.
 * Extends base UDF metadata with asset class and special instrument type filtering, allowing
 * custom fields to be applicable only to specific types of securities (e.g., bonds, stocks, ETFs).
 * This enables context-specific UDF fields that appear only for relevant security types.
 */
export class UDFMetadataSecurity extends UDFMetadata {
  /** Asset class types this UDF field applies to (e.g., EQUITY, FIXED_INCOME, ETF) */
  categoryTypeEnums: AssetclassType[] = [];

  /** Special investment instrument types this UDF field applies to (e.g., BOND, STOCK, OPTION) */
  specialInvestmentInstrumentEnums: SpecialInvestmentInstruments[] = [];
}

/**
 * Extended parameters for security UDF metadata editing.
 * Includes the UDF metadata entity being edited along with validation exclusion lists.
 */
export class UDFMetadataSecurityParam extends UDFMetadataParam {
  /** The security UDF metadata entity being edited, or null when creating a new entry */
  uDFMetadataSecurity: UDFMetadataSecurity
}

/**
 * Extended field descriptor interface for security-specific UDF fields.
 * Adds asset class and special instrument type information to the standard field descriptor
 * for dynamic form generation with security type filtering.
 */
export interface FieldDescriptorInputAndShowExtendedSecurity extends FieldDescriptorInputAndShowExtended {
  /** Asset class types this field descriptor applies to */
  categoryTypeEnums: AssetclassType[];

  /** Special investment instrument types this field descriptor applies to */
  specialInvestmentInstrumentEnums: SpecialInvestmentInstruments[];
}

/**
 * Predefined special UDF types for securities with specific internal purposes.
 * These special types provide system-level UDF fields that users can optionally disable if not needed.
 *
 * This const object provides enum-like usage while implementing the IUDFSpecialType interface
 * for compatibility with the UDFSpecialTypeRegistry pattern.
 */
export const UDFSpecialType = {
  /** Internal field for calculating yield to maturity for bonds */
  UDF_SPEC_INTERNAL_CALC_YIELD_TO_MATURITY: { value: 1, name: 'UDF_SPEC_INTERNAL_CALC_YIELD_TO_MATURITY' } as IUDFSpecialType,

  /** Internal field storing Yahoo Finance earnings report link URL */
  UDF_SPEC_INTERNAL_YAHOO_EARNING_LINK: { value: 2, name: 'UDF_SPEC_INTERNAL_YAHOO_EARNING_LINK' } as IUDFSpecialType,

  /** Internal field for next earnings announcement date from Yahoo Finance */
  UDF_SPEC_INTERNAL_YAHOO_EARNING_NEXT_DATE: { value: 3, name: 'UDF_SPEC_INTERNAL_YAHOO_EARNING_NEXT_DATE' } as IUDFSpecialType,

  /** Internal flag to hide Yahoo Finance symbol from display */
  UDF_SPEC_INTERNAL_YAHOO_SYMBOL_HIDE: { value: 4, name: 'UDF_SPEC_INTERNAL_YAHOO_SYMBOL_HIDE' } as IUDFSpecialType,

  /** Internal field storing Yahoo Finance statistics page link URL */
  UDF_SPEC_INTERNAL_YAHOO_STATISTICS_LINK: { value: 5, name: 'UDF_SPEC_INTERNAL_YAHOO_STATISTICS_LINK' } as IUDFSpecialType
} as const;

/**
 * Type alias for UDF special type values.
 * Allows type-safe usage of UDFSpecialType constants throughout the application.
 */
export type UDFSpecialTypeValue = typeof UDFSpecialType[keyof typeof UDFSpecialType];
