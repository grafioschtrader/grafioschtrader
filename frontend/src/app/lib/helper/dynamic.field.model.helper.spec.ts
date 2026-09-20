import { describe, expect, it } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { FormControl } from '@angular/forms';
import { DynamicFieldModelHelper } from './dynamic.field.model.helper';
import {
  ClassDescriptorInputAndShow,
  FieldDescriptorInputAndShow
} from '../dynamicfield/field.descriptor.input.and.show';
import { InputType } from '../dynamic-form/models/input.type';
import { Helper } from './helper';

function descriptor(max: number | null): ClassDescriptorInputAndShow {
  return {
    fieldDescriptorInputAndShows: [
      {
        fieldName: 'initializationMode',
        dataType: 'String',
        required: true,
        min: null,
        max,
        enumType: 'SimulationInitializationMode',
        dynamicFormPropertyHelps: ['SELECT_OPTIONS'],
        enumValues: null
      }
    ],
    constraintValidatorMap: {}
  };
}

describe('DynamicFieldModelHelper SELECT_OPTIONS width', () => {
  const translateService = {} as TranslateService;

  it('does not set inputWidth when the descriptor has no max', () => {
    const config = DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(
      translateService,
      descriptor(null),
      ''
    );
    expect(config[0].inputType).toBe(InputType.Select);
    expect(config[0].inputWidth).toBeUndefined();
  });

  it('uses max as inputWidth when the descriptor has a max', () => {
    const config = DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(
      translateService,
      descriptor(5),
      ''
    );
    expect(config[0].inputType).toBe(InputType.Select);
    expect(config[0].inputWidth).toBe(5);
  });
});

describe('DynamicFieldModelHelper numeric bounds', () => {
  const translateService = {} as TranslateService;
  const numericDescriptor = (
    fieldName: string,
    min: number | null,
    max: number | null,
    required = true
  ): FieldDescriptorInputAndShow => ({
    fieldName,
    dataType: 'NumericInteger',
    required,
    min,
    max,
    enumType: null,
    enumValues: null,
    dynamicFormPropertyHelps: []
  });

  it.each([
    ['maxRows', 1, 20, 5, '3'],
    ['topN', 1, 5, 3, '5'],
    ['days', 2, 10, 5, '10']
  ])('transfers an edited dashboard %s as a JSON number', (name, min, max, initial, edited) => {
    const [field] = DynamicFieldModelHelper.createConfigFieldsFromDescriptor(
      translateService,
      [numericDescriptor(name, min, max)],
      ''
    );
    field.formControl = new FormControl(edited, field.validation);
    expect(field.formControl.valid).toBe(true);
    const settings = { [name]: initial };
    Helper.copyFormSingleFormConfigToBusinessObject({}, field, settings);
    expect(JSON.parse(JSON.stringify(settings))).toEqual({ [name]: Number(edited) });
  });

  it('generates all rebalancing parameters when the trade count has no upper bound', () => {
    const fields = DynamicFieldModelHelper.createConfigFieldsFromDescriptor(
      translateService,
      [
        numericDescriptor('timePeriodPerYear', 1, 53),
        numericDescriptor('thresholdPercentage', 1, 49),
        {
          ...numericDescriptor('securityDeviationPercentage', 0, 100),
          dataType: 'Numeric',
          dynamicFormPropertyHelps: ['PERCENTAGE']
        },
        numericDescriptor('maxTradedSecuritiesPerAssetclass', 1, null)
      ],
      'ALGO_F_'
    );
    expect(fields.map((field) => field.field)).toEqual([
      'timePeriodPerYear',
      'thresholdPercentage',
      'securityDeviationPercentage',
      'maxTradedSecuritiesPerAssetclass'
    ]);
    const count = fields[3];
    expect(count.maxLength).toBeUndefined();
    expect(Number.isFinite(count.inputWidth)).toBe(true);
    const control = new FormControl(null, count.validation);
    expect(control.hasError('required')).toBe(true);
    control.setValue(0);
    expect(control.hasError('min')).toBe(true);
    control.setValue(1);
    expect(control.valid).toBe(true);
    control.setValue(1000);
    expect(control.valid).toBe(true);
  });

  it('allows a blank asset-class override but rejects a count below one', () => {
    const [field] = DynamicFieldModelHelper.createConfigFieldsFromDescriptor(
      translateService,
      [numericDescriptor('maxTradedSecuritiesPerAssetclass', 1, null, false)],
      ''
    );
    const control = new FormControl(null, field.validation);
    expect(control.valid).toBe(true);
    control.setValue(0);
    expect(control.hasError('min')).toBe(true);
  });

  it.each([null, -10])('enforces a zero upper bound with minimum %s', (min) => {
    const [field] = DynamicFieldModelHelper.createConfigFieldsFromDescriptor(
      translateService,
      [numericDescriptor('amount', min, 0)],
      ''
    );
    const control = new FormControl(0, field.validation);
    expect(control.valid).toBe(true);
    control.setValue(1);
    expect(control.invalid).toBe(true);
  });

  it('supports a numeric descriptor with neither bound', () => {
    const [field] = DynamicFieldModelHelper.createConfigFieldsFromDescriptor(
      translateService,
      [numericDescriptor('amount', null, null, false)],
      ''
    );
    expect(field.maxLength).toBeUndefined();
    expect(Number.isFinite(field.inputWidth)).toBe(true);
    const control = new FormControl(-1000, field.validation);
    expect(control.valid).toBe(true);
  });
});
