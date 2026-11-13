import { AdvancedSearchOperator } from '../../shared/types/advanced.search.operator';

/** The kind of value a field of the advanced search holds; it decides the operator and the inputs of a criterion. */
export type AdvancedSearchValueType = 'NUMBER' | 'DATE' | 'TEXT' | 'BOOLEAN';

/**
 * A user-defined field or an indicator column that a criterion of the advanced search can refer to. The backend
 * computes the list for the logged-in user and sends it with the base data of the search dialog.
 */
export interface AdvancedSearchField {
  /** Identifies the field in a criterion: 'U' and the metadata id of a user-defined field, or 'I' and an indicator id. */
  key: string;
  /** Text of the field in the language of the user. */
  label: string;
  valueType: AdvancedSearchValueType;
  /** True for an indicator column that needs a quote series, a benchmark or dividends; its search is limited. */
  candidateLimited: boolean;
}

/**
 * One criterion of the advanced search. Which attributes are set depends on the value type of the field: NUMBER and
 * DATE use the operator and the first comparison value, the second one only with BETWEEN; TEXT uses the text and
 * BOOLEAN the flag. Dates are 'YYYYMMDD' strings, as the backend expects them.
 */
export interface AdvancedSearchCriterion {
  field: string;
  operator?: AdvancedSearchOperator | null;
  number?: number | null;
  numberTo?: number | null;
  date?: string | null;
  dateTo?: string | null;
  text?: string | null;
  flag?: boolean | null;
}

/** The value that satisfied a criterion: a number, an ISO date or a text, or a flag. */
export type AdvancedSearchValue = number | string | boolean;
