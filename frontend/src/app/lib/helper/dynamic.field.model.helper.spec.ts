import { describe, expect, it } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { FormControl, FormGroup } from '@angular/forms';
import { of } from 'rxjs';
import { DynamicFieldModelHelper } from './dynamic.field.model.helper';
import {
  ClassDescriptorInputAndShow,
  FieldDescriptorInputAndShow
} from '../dynamicfield/field.descriptor.input.and.show';
import { InputType } from '../dynamic-form/models/input.type';
import { Helper } from './helper';
import { FieldConfig } from '../dynamic-form/models/field.config';
import { FormGroupDefinition } from '../dynamic-form/models/form.group.definition';

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

describe('DynamicFieldModelHelper enum-backed string field', () => {
  const translateService = { get: (key: string) => of('T_' + key) } as unknown as TranslateService;
  const enumDescriptor = (required: boolean): FieldDescriptorInputAndShow => ({
    fieldName: 'crossDirection',
    dataType: 'String',
    required,
    min: null,
    max: null,
    enumType: 'CrossDirection',
    enumValues: ['ABOVE', 'BELOW'],
    dynamicFormPropertyHelps: null
  });

  it('renders the constants sent by the backend as a select with translated labels', () => {
    const [field] = DynamicFieldModelHelper.createConfigFieldsFromDescriptor(
      translateService,
      [enumDescriptor(true)],
      'ALGO_F_'
    );
    expect(field.inputType).toBe(InputType.Select);
    expect(field.valueKeyHtmlOptions.map((o) => o.key)).toEqual(['ABOVE', 'BELOW']);
    expect(field.valueKeyHtmlOptions.map((o) => o.value)).toEqual(['T_ABOVE', 'T_BELOW']);
  });

  it('offers an empty entry only for an optional field', () => {
    const [field] = DynamicFieldModelHelper.createConfigFieldsFromDescriptor(
      translateService,
      [enumDescriptor(false)],
      ''
    );
    expect(field.valueKeyHtmlOptions.map((o) => o.key)).toEqual(['', 'ABOVE', 'BELOW']);
  });
});

describe('DynamicFieldModelHelper string bounds', () => {
  it('rejects an overlong prefilled name and accepts its correction without truncating it', () => {
    const [field] = DynamicFieldModelHelper.createConfigFieldsFromDescriptor(
      {} as TranslateService,
      [
        {
          fieldName: 'tenantName',
          dataType: 'String',
          required: true,
          min: 1,
          max: 25,
          enumType: null,
          enumValues: null,
          dynamicFormPropertyHelps: null
        }
      ],
      ''
    );
    expect(field.maxLength).toBe(25);
    const control = new FormControl('', field.validation);
    expect(control.hasError('required')).toBe(true);
    control.setValue('A'.repeat(26));
    expect(control.hasError('rangeLength')).toBe(true);
    expect(control.value).toHaveLength(26);
    control.setValue('A'.repeat(25));
    expect(control.valid).toBe(true);
    control.setValue('A');
    expect(control.valid).toBe(true);
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

describe('DynamicFieldModelHelper class-level constraints', () => {
  const translateService = {} as TranslateService;
  const optionalNumber = (fieldName: string): FieldDescriptorInputAndShow => ({
    fieldName,
    dataType: 'NumericInteger',
    required: false,
    min: 0,
    max: 500,
    enumType: null,
    enumValues: null,
    dynamicFormPropertyHelps: []
  });

  /** The form definition of the holding gain/lose alert, as the backend serializes it. */
  const holdingAlert = (): ClassDescriptorInputAndShow => ({
    fieldDescriptorInputAndShows: ['gainPercentage', 'losePercentage', 'upperValue', 'lowerValue'].map(optionalNumber),
    constraintValidatorMap: {
      AtLeastOneNotNull: { fields: ['gainPercentage', 'losePercentage', 'upperValue', 'lowerValue'] },
      NumberRange: { lowerField: 'lowerValue', upperField: 'upperValue' }
    }
  });

  it('merges constraints sharing fields into one form group placed at the first field', () => {
    const config = DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(
      translateService,
      holdingAlert(),
      ''
    );
    expect(config).toHaveLength(1);
    const group = config[0] as FormGroupDefinition;
    expect(group.formGroupName).toBeDefined();
    expect(group.fieldConfig.map((f) => f.field)).toEqual([
      'gainPercentage',
      'losePercentage',
      'upperValue',
      'lowerValue'
    ]);
    expect(group.validation).toHaveLength(2);
    expect(group.errors.map((e) => e.keyi18n)).toEqual(expect.arrayContaining(['atLeastOneNotNull', 'numberRange']));
  });

  it('keeps unconstrained fields at their position around the group', () => {
    const config = DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(
      translateService,
      {
        fieldDescriptorInputAndShows: ['daysInPeriod', 'gainPercentage', 'losePercentage'].map(optionalNumber),
        constraintValidatorMap: { AtLeastOneNotNull: { fields: ['gainPercentage', 'losePercentage'] } }
      },
      ''
    );
    expect(config).toHaveLength(2);
    expect((config[0] as FieldConfig).field).toBe('daysInPeriod');
    expect((config[1] as FormGroupDefinition).fieldConfig.map((f) => f.field)).toEqual([
      'gainPercentage',
      'losePercentage'
    ]);
  });

  it('applies the backend rules to the form group', () => {
    const group = DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(
      translateService,
      holdingAlert(),
      ''
    )[0] as FormGroupDefinition;
    const formGroup = new FormGroup(
      Object.fromEntries(group.fieldConfig.map((f) => [f.field, new FormControl(null, f.validation)])),
      group.validation
    );
    expect(formGroup.hasError('atLeastOneNotNull')).toBe(true);
    formGroup.patchValue({ lowerValue: 20, upperValue: 10 });
    expect(formGroup.hasError('atLeastOneNotNull')).toBe(false);
    expect(formGroup.hasError('numberRange')).toBe(true);
    formGroup.patchValue({ lowerValue: 10, upperValue: 20 });
    expect(formGroup.valid).toBe(true);
  });

  it('generates plain fields when the descriptor has no constraints', () => {
    const config = DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(
      translateService,
      { fieldDescriptorInputAndShows: [optionalNumber('a')], constraintValidatorMap: {} },
      ''
    );
    expect((config[0] as FieldConfig).field).toBe('a');
  });
});
