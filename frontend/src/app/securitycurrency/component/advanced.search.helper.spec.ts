import { describe, expect, it } from 'vitest';
import { AdvancedSearchOperator } from '../../shared/types/advanced.search.operator';
import {
  AdvancedSlotValues,
  criteriaOfSlots,
  emptySlotValues,
  evaluateSlot,
  visibleSlotControls
} from './advanced.search.helper';

const slot = (values: Partial<AdvancedSlotValues>): AdvancedSlotValues => ({ ...emptySlotValues(), ...values });

describe('visibleSlotControls', () => {
  it('shows the operator and the value of a number, and a second value only for between', () => {
    expect(visibleSlotControls('NUMBER', null)).toEqual(['operator', 'number']);
    expect(visibleSlotControls('NUMBER', AdvancedSearchOperator.LESS_THAN)).toEqual(['operator', 'number']);
    expect(visibleSlotControls('NUMBER', AdvancedSearchOperator.BETWEEN)).toEqual(['operator', 'number', 'numberTo']);
  });

  it('shows the same for a date with its own inputs, and one input for a text and for a flag', () => {
    expect(visibleSlotControls('DATE', AdvancedSearchOperator.BETWEEN)).toEqual(['operator', 'date', 'dateTo']);
    expect(visibleSlotControls('DATE', AdvancedSearchOperator.EQUAL)).toEqual(['operator', 'date']);
    expect(visibleSlotControls('TEXT', null)).toEqual(['text']);
    expect(visibleSlotControls('BOOLEAN', null)).toEqual(['flag']);
  });

  it('shows nothing without a chosen field', () => {
    expect(visibleSlotControls(undefined, null)).toEqual([]);
  });
});

describe('evaluateSlot', () => {
  it('treats a slot without a field as neutral, whatever else it holds', () => {
    expect(evaluateSlot(slot({ number: 5 }), undefined).state).toBe('EMPTY');
    expect(evaluateSlot(emptySlotValues(), 'NUMBER').state).toBe('EMPTY');
  });

  it('keeps a started slot incomplete instead of dropping it', () => {
    expect(evaluateSlot(slot({ field: 'U1' }), 'NUMBER').state).toBe('INCOMPLETE');
    expect(evaluateSlot(slot({ field: 'U1', number: 5 }), 'NUMBER').state).toBe('INCOMPLETE');
    expect(evaluateSlot(slot({ field: 'U1', operator: AdvancedSearchOperator.LESS_THAN }), 'NUMBER').state).toBe(
      'INCOMPLETE'
    );
    expect(
      evaluateSlot(slot({ field: 'U1', operator: AdvancedSearchOperator.BETWEEN, number: 1 }), 'NUMBER').state
    ).toBe('INCOMPLETE');
    expect(evaluateSlot(slot({ field: 'U2', operator: AdvancedSearchOperator.EQUAL }), 'DATE').state).toBe(
      'INCOMPLETE'
    );
    expect(evaluateSlot(slot({ field: 'U3', text: '  ' }), 'TEXT').state).toBe('INCOMPLETE');
    expect(evaluateSlot(slot({ field: 'U4' }), 'BOOLEAN').state).toBe('INCOMPLETE');
    expect(evaluateSlot(slot({ field: 'U9' }), undefined).state).toBe('INCOMPLETE');
  });

  it('accepts zero and false as values', () => {
    const zero = evaluateSlot(
      slot({ field: 'U1', operator: AdvancedSearchOperator.BETWEEN, number: 0, numberTo: 0 }),
      'NUMBER'
    );
    expect(zero.state).toBe('COMPLETE');
    expect(zero.criterion).toEqual({
      field: 'U1',
      operator: AdvancedSearchOperator.BETWEEN,
      number: 0,
      numberTo: 0
    });
    const flag = evaluateSlot(slot({ field: 'U4', flag: false }), 'BOOLEAN');
    expect(flag.state).toBe('COMPLETE');
    expect(flag.criterion).toEqual({ field: 'U4', flag: false });
  });

  it('does not send a value of the former type or a second value without between', () => {
    const evaluation = evaluateSlot(
      slot({
        field: 'U1',
        operator: AdvancedSearchOperator.LESS_THAN,
        number: 20,
        numberTo: 30,
        text: 'old',
        date: '20260101',
        flag: true
      }),
      'NUMBER'
    );
    expect(evaluation.criterion).toEqual({
      field: 'U1',
      operator: AdvancedSearchOperator.LESS_THAN,
      number: 20,
      numberTo: null
    });
  });

  it('builds date and text criteria', () => {
    expect(
      evaluateSlot(
        slot({ field: 'U2', operator: AdvancedSearchOperator.BETWEEN, date: '20260301', dateTo: '20260331' }),
        'DATE'
      ).criterion
    ).toEqual({ field: 'U2', operator: AdvancedSearchOperator.BETWEEN, date: '20260301', dateTo: '20260331' });
    expect(evaluateSlot(slot({ field: 'U3', text: '100%' }), 'TEXT').criterion).toEqual({ field: 'U3', text: '100%' });
  });
});

describe('criteriaOfSlots', () => {
  it('collects the complete criteria in slot order and reports an incomplete slot', () => {
    const result = criteriaOfSlots([
      { values: slot({ field: 'U4', flag: true }), valueType: 'BOOLEAN' },
      { values: emptySlotValues(), valueType: undefined },
      { values: slot({ field: 'U1' }), valueType: 'NUMBER' }
    ]);
    expect(result.criteria).toEqual([{ field: 'U4', flag: true }]);
    expect(result.incomplete).toBe(true);
  });

  it('reports no criteria and no incomplete slot for empty slots', () => {
    expect(criteriaOfSlots([{ values: emptySlotValues(), valueType: undefined }])).toEqual({
      criteria: [],
      incomplete: false
    });
  });
});
