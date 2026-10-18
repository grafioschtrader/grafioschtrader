import { UDFMetadata, UDFMetadataParam } from '../../lib/udfmeta/model/udf.metadata';
import { AssetclassType } from '../../shared/types/assetclass.type';
import { SpecialInvestmentInstruments } from '../../shared/types/special.investment.instruments';
import { FieldDescriptorInputAndShowExtended } from '../../lib/dynamicfield/field.descriptor.input.and.show';
import { IUDFSpecialType } from '../../lib/udfmeta/model/udf.special.type.interface';

/**
 * Fill sources of a user-defined security field. The instance decides which ones it offers, see
 * {@link UDFFillSourceOption}; the edit form builds its selection from that answer only.
 */
export const UDFFillSource = {
  MANUAL: 'MANUAL',
  TEMPLATE: 'TEMPLATE',
  RESOLVER: 'RESOLVER',
  LLM: 'LLM'
} as const;

/** Fill states of one cell, as recorded by the server. */
export const UDFFillState = {
  APPLIED: 'APPLIED',
  NOT_FOUND: 'NOT_FOUND',
  LOCKED: 'LOCKED',
  PROPOSED: 'PROPOSED',
  REJECTED: 'REJECTED',
  DISCARDED: 'DISCARDED'
} as const;

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

  /** Where the value comes from; every source other than MANUAL computes it instead of the user typing it */
  fillSource: string = UDFFillSource.MANUAL;

  /** Rule of the fill source, for TEMPLATE one or more lines with placeholders; null for MANUAL */
  fillRule: string = null;

  /**
   * How often the instance fills the field by itself, for RESOLVER and LLM only. Null means on request for LLM and the
   * global refresh setting for RESOLVER.
   */
  fillPeriod: string = null;

  /** Maintained by the server, incremented whenever the fill source or the rule changes; never sent back */
  fillRuleVersion: number = null;

  /** Set by the server when listed: whether anything fills the field; a language model needs a key of the tenant */
  active?: boolean;
}

/** A fill source the instance offers, together with the data types of a field it can fill. */
export interface UDFFillSourceOption {
  fillSource: string;
  udfDataTypes: string[];
}

/** A resolver of the fill source RESOLVER; its ID is also the translation key of its name. */
export interface UDFFillResolverOption {
  resolverId: string;
  udfDataTypes: string[];
  /** Argument used when the rule names the resolver only */
  defaultArgument: string;
}

/** Everything the instance offers for the automatic filling; the edit form builds its selections from it only. */
export interface UDFFillSourcesOffer {
  fillSources: UDFFillSourceOption[];
  resolvers: UDFFillResolverOption[];
  /** True when a language model is configured, which can propose a template rule */
  ruleProposal: boolean;
  valueSuggestion: boolean;
  /** The fill periods a RESOLVER or LLM field may have, in order; no period is always possible as well */
  fillPeriods: string[];
}

/** One existing value that a rule proposal would send to the language model; the ID itself is not sent. */
export interface RuleProposalExample {
  idSecuritycurrency: number;
  isin: string;
  ticker: string;
  name: string;
  mic: string;
  currency: string;
  value: string;
}

/** What a rule proposal would send, shown to the user before the first request. */
export interface RuleProposalPreview {
  /** The configured language models with their model in order of priority; the first that answers gets the values */
  providers: { label: string; keySource: 'TENANT' | 'INSTANCE' }[];
  examples: RuleProposalExample[];
}

/** The rule written by the language model, with its parse error or its counters. */
export interface RuleProposalResult {
  rule: string;
  parseError: string;
  testResult: UDFFillTestResult;
}

/** Draft of a fill rule, evaluated against the tenant scope of the user without writing anything. */
export interface UDFFillTestRequest {
  /** The field being edited, or null for a field not yet saved */
  idUDFMetadata: number;
  udfDataType: string;
  fieldSize: string;
  categoryTypeEnums: AssetclassType[];
  specialInvestmentInstrumentEnums: SpecialInvestmentInstruments[];
  fillSource: string;
  fillRule: string;
}

/** An existing value which the rule does not reproduce. */
export interface UDFFillMismatch {
  name: string;
  existing: string;
  /** The computed value, null when the rule computes none */
  computed: string;
  /** Why no value was computed, null when one was */
  reason: string;
}

/** A value the rule computes for a security whose field is still empty. */
export interface UDFFillSample {
  name: string;
  computed: string;
}

/** Counters and examples of testing a fill rule; coverage and agreement are counted separately. */
export interface UDFFillTestResult {
  inScope: number;
  withValue: number;
  produced: number;
  equal: number;
  differing: number;
  noLine: number;
  invalid: number;
  mismatches: UDFFillMismatch[];
  samples: UDFFillSample[];
}

/** A security on which the description for the language model is tested; those with a value come first. */
export interface UDFSuggestTestCandidate {
  idSecuritycurrency: number;
  name: string;
  /** The value the field holds for this security, or null */
  existingValue: string;
}

/**
 * The answer of the language model for one tested security and how the check against the source judged it. Nothing of
 * it is stored. A discarded answer keeps the value, source and excerpt of the model, so the reason can be seen.
 */
export interface UDFSuggestTestResult {
  idSecuritycurrency: number;
  /** PROPOSED, DISCARDED, NOT_FOUND or FAILED */
  outcome: string;
  value: string;
  sourceUrl: string;
  evidence: string;
  asOfDate: string;
  /** Untranslated reason of a discarded, not found or failed answer */
  note: string;
  /** Whether a proposed value equals the existing one; null when there is nothing to compare */
  equalsExisting: boolean;
  /** One line per page the model fetched: URL and status, text length or why it could not be read; untranslated */
  fetches: string[];
}

/** Summary of the automatic filling of one field over the tenant scope. */
export interface UDFFillStatus {
  inScope: number;
  /** Number of in-scope cells per fill state */
  countByState: { [fillState: string]: number };
  manual: number;
  waiting: boolean;
  running: boolean;
}

/** Fill mark of one cell: an automatic value that still holds the filled value, or a cell the user took over. */
export interface UDFFillInfo {
  fillSource: string;
  fillState: string;
  /** UTC time of the last definitive outcome */
  checkedTime: string;
  sourceUrl: string;
  /** The proposed value of a PROPOSED cell, which is never part of the values themselves */
  proposedValue?: string;
  /** The excerpt of the source of a PROPOSED cell */
  evidence?: string;
  /** The fill rule version of a proposal; an accept or reject names it */
  ruleVersion?: number;
}

/** A value a language model proposed and the instance verified against its source; plausible, not proven. */
export interface UDFFillProposal {
  idSecuritycurrency: number;
  name: string;
  value: string;
  sourceUrl: string;
  evidence: string;
  asOfDate: string;
  ruleVersion: number;
}

/** The decision of the user about one proposal, naming the version and value seen. */
export interface UDFFillProposalDecision {
  idSecuritycurrency: number;
  ruleVersion: number;
  value: string;
}

/** What happened to one decided proposal: APPLIED, REJECTED, CONFLICT or STALE. */
export interface UDFFillProposalDecisionResult {
  idSecuritycurrency: number;
  outcome: string;
}

/**
 * Extended parameters for security UDF metadata editing.
 * Includes the UDF metadata entity being edited along with validation exclusion lists.
 */
export class UDFMetadataSecurityParam extends UDFMetadataParam {
  /** The security UDF metadata entity being edited, or null when creating a new entry */
  uDFMetadataSecurity: UDFMetadataSecurity;
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
  UDF_SPEC_INTERNAL_CALC_YIELD_TO_MATURITY: {
    value: 1,
    name: 'UDF_SPEC_INTERNAL_CALC_YIELD_TO_MATURITY'
  } as IUDFSpecialType,

  /** Internal field storing Yahoo Finance earnings report link URL */
  UDF_SPEC_INTERNAL_YAHOO_EARNING_LINK: { value: 2, name: 'UDF_SPEC_INTERNAL_YAHOO_EARNING_LINK' } as IUDFSpecialType,

  /** Internal field for next earnings announcement date from Yahoo Finance */
  UDF_SPEC_INTERNAL_YAHOO_EARNING_NEXT_DATE: {
    value: 3,
    name: 'UDF_SPEC_INTERNAL_YAHOO_EARNING_NEXT_DATE'
  } as IUDFSpecialType,

  /** Internal flag to hide Yahoo Finance symbol from display */
  UDF_SPEC_INTERNAL_YAHOO_SYMBOL_HIDE: { value: 4, name: 'UDF_SPEC_INTERNAL_YAHOO_SYMBOL_HIDE' } as IUDFSpecialType,

  /** Internal field storing Yahoo Finance statistics page link URL */
  UDF_SPEC_INTERNAL_YAHOO_STATISTICS_LINK: {
    value: 5,
    name: 'UDF_SPEC_INTERNAL_YAHOO_STATISTICS_LINK'
  } as IUDFSpecialType
} as const;

/**
 * Type alias for UDF special type values.
 * Allows type-safe usage of UDFSpecialType constants throughout the application.
 */
export type UDFSpecialTypeValue = (typeof UDFSpecialType)[keyof typeof UDFSpecialType];
