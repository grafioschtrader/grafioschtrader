import { AdvancedSearchCriterion, AdvancedSearchValueType } from '../../entities/search/advanced.search';
import { AdvancedSearchOperator } from '../../shared/types/advanced.search.operator';

/** Number of criteria slots of the search dialog; the backend refuses more (SecuritycurrencySearch.MAX_ADVANCED_CRITERIA). */
export const ADVANCED_SEARCH_MAX_CRITERIA = 5;

/** Longest text of a text criterion; the backend refuses a longer one. */
export const ADVANCED_SEARCH_MAX_TEXT_LENGTH = 100;

/** The inputs of a criteria slot besides its field select. */
export type AdvancedSlotControl = 'operator' | 'number' | 'numberTo' | 'date' | 'dateTo' | 'text' | 'flag';

/** All inputs of a slot, in the order of the form. */
export const ADVANCED_SLOT_CONTROLS: readonly AdvancedSlotControl[] = [
  'operator',
  'number',
  'numberTo',
  'date',
  'dateTo',
  'text',
  'flag'
];

/** The values of one criteria slot as the form holds them; dates are already 'YYYYMMDD' strings. */
export interface AdvancedSlotValues {
  field: string | null;
  operator: AdvancedSearchOperator | null;
  number: number | null;
  numberTo: number | null;
  date: string | null;
  dateTo: string | null;
  text: string | null;
  flag: boolean | null;
}

/** An empty slot: no field chosen, no value entered. */
export function emptySlotValues(): AdvancedSlotValues {
  return {
    field: null,
    operator: null,
    number: null,
    numberTo: null,
    date: null,
    dateTo: null,
    text: null,
    flag: null
  };
}

/**
 * The inputs a slot shows for the chosen field. A number or a date needs the operator and the comparison value, and a
 * second value only for BETWEEN; a text needs the text and a boolean the flag.
 *
 * @param valueType - Value type of the chosen field, undefined when no field is chosen
 * @param operator - The chosen operator, null as long as none is chosen
 * @returns The shown inputs, which are also the required ones
 */
export function visibleSlotControls(
  valueType: AdvancedSearchValueType | undefined,
  operator: AdvancedSearchOperator | null
): AdvancedSlotControl[] {
  const between = operator === AdvancedSearchOperator.BETWEEN;
  switch (valueType) {
    case 'NUMBER':
      return between ? ['operator', 'number', 'numberTo'] : ['operator', 'number'];
    case 'DATE':
      return between ? ['operator', 'date', 'dateTo'] : ['operator', 'date'];
    case 'TEXT':
      return ['text'];
    case 'BOOLEAN':
      return ['flag'];
    case undefined:
      return [];
    default: {
      const unreachable: never = valueType;
      return unreachable;
    }
  }
}

/** Whether a slot is untouched, started but not usable, or yields a criterion. */
export type AdvancedSlotState = 'EMPTY' | 'INCOMPLETE' | 'COMPLETE';

/** The state of a slot and, when it is complete, its criterion. */
export interface AdvancedSlotEvaluation {
  state: AdvancedSlotState;
  criterion?: AdvancedSearchCriterion;
}

const isNumber = (value: number | null): value is number => value !== null && Number.isFinite(value);
const isText = (value: string | null): value is string => value !== null && value.trim().length > 0;

/**
 * Builds the criterion of a slot. A slot without field is neutral; a slot with a field and a missing operator or value
 * is incomplete and must block the search instead of being dropped. Zero and false are values.
 *
 * @param values - The form values of the slot
 * @param valueType - Value type of the chosen field, undefined when the field is not in the catalogue
 * @returns The state of the slot, with its criterion when complete
 */
export function evaluateSlot(
  values: AdvancedSlotValues,
  valueType: AdvancedSearchValueType | undefined
): AdvancedSlotEvaluation {
  if (!values.field) {
    return { state: 'EMPTY' };
  }
  const between = values.operator === AdvancedSearchOperator.BETWEEN;
  const criterion: AdvancedSearchCriterion = { field: values.field };
  switch (valueType) {
    case 'NUMBER':
      if (!values.operator || !isNumber(values.number) || (between && !isNumber(values.numberTo))) {
        return { state: 'INCOMPLETE' };
      }
      criterion.operator = values.operator;
      criterion.number = values.number;
      criterion.numberTo = between ? values.numberTo : null;
      break;
    case 'DATE':
      if (!values.operator || !values.date || (between && !values.dateTo)) {
        return { state: 'INCOMPLETE' };
      }
      criterion.operator = values.operator;
      criterion.date = values.date;
      criterion.dateTo = between ? values.dateTo : null;
      break;
    case 'TEXT':
      if (!isText(values.text)) {
        return { state: 'INCOMPLETE' };
      }
      criterion.text = values.text;
      break;
    case 'BOOLEAN':
      if (values.flag === null) {
        return { state: 'INCOMPLETE' };
      }
      criterion.flag = values.flag;
      break;
    case undefined:
      return { state: 'INCOMPLETE' };
    default: {
      const unreachable: never = valueType;
      return unreachable;
    }
  }
  return { state: 'COMPLETE', criterion };
}

/** The criteria of all slots and whether any slot is incomplete. */
export interface AdvancedCriteriaOfSlots {
  criteria: AdvancedSearchCriterion[];
  incomplete: boolean;
}

/**
 * Collects the criteria of the slots in slot order.
 *
 * @param slots - The values of each slot with the value type of its chosen field
 * @returns The complete criteria, and true when a started slot is incomplete
 */
export function criteriaOfSlots(
  slots: { values: AdvancedSlotValues; valueType: AdvancedSearchValueType | undefined }[]
): AdvancedCriteriaOfSlots {
  const criteria: AdvancedSearchCriterion[] = [];
  let incomplete = false;
  for (const slot of slots) {
    const evaluation = evaluateSlot(slot.values, slot.valueType);
    if (evaluation.state === 'INCOMPLETE') {
      incomplete = true;
    } else if (evaluation.criterion) {
      criteria.push(evaluation.criterion);
    }
  }
  return { criteria, incomplete };
}
