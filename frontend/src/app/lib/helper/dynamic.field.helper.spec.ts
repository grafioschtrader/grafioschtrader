import { FormControl, Validators } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { describe, expect, it } from 'vitest';
import { ShowRecordConfigBase } from '../datashowbase/show.record.config.base';
import { DataType } from '../dynamic-form/models/data.type';
import { requireFormControl } from '../dynamic-form/models/form-control-access';
import { FormNotifier } from '../dynamic-form/models/form.notifier';
import { GlobalparameterService } from '../services/globalparameter.service';
import { AppHelper } from './app.helper';
import { DynamicFieldHelper } from './dynamic.field.helper';
import { Helper } from './helper';

describe('nullable dynamic form configuration', () => {
  it('clears and restores required validation on the same control without replacing its empty value', () => {
    const field = DynamicFieldHelper.createFieldInputStringHeqF('name', 50, true);
    field.formControl = new FormControl(null, field.validation);
    const notifier = FormNotifier.instrument([field]);
    const revision = notifier.revision();
    expect(requireFormControl(field).errors).toEqual({ required: true });

    DynamicFieldHelper.resetValidator(field, null, null);
    expect(field.validation).toBeNull();
    expect(field.errors).toBeNull();
    expect(requireFormControl(field).value).toBeNull();
    expect(requireFormControl(field).valid).toBe(true);
    expect(notifier.revision()).toBeGreaterThan(revision);

    DynamicFieldHelper.resetValidator(field, [Validators.required], [DynamicFieldHelper.RULE_REQUIRED_TOUCHED]);
    expect(requireFormControl(field).errors).toEqual({ required: true });
    requireFormControl(field).setValue('entered');
    expect(requireFormControl(field).valid).toBe(true);
  });

  it('transfers zero and an empty date distinctly, and rejects an unnamed data field', () => {
    const amount = DynamicFieldHelper.createFieldInputNumberHeqF('amount', false, 10, 2, false);
    const date = DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'date', false);
    amount.formControl = new FormControl(0);
    date.formControl = new FormControl<Date | null>(null);
    const target: { amount: number; date: string | null } = { amount: 12, date: '2026-01-01' };
    for (const field of [amount, date]) {
      Helper.copyFormSingleFormConfigToBusinessObject({ labelColumns: 2 }, field, target);
    }
    expect(target).toEqual({ amount: 0, date: null });

    requireFormControl(date).setValue(new Date(2026, 9, 29));
    Helper.copyFormSingleFormConfigToBusinessObject({ labelColumns: 2 }, date, target);
    expect(target.date).toBe('2026-10-29');
    expect(() =>
      Helper.copyFormSingleFormConfigToBusinessObject({ labelColumns: 2 }, { ...amount, field: null }, target)
    ).toThrow('A data form control requires a field name');
    expect(target.amount).toBe(0);
  });

  it('keeps a missing amount blank and uses locale precision unless an explicit precision is configured', () => {
    const gps = {
      getNumberFormat: () => new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
      getDecimalSymbol: () => '.'
    } as unknown as GlobalparameterService;
    const translator = { get: (key: string) => of(key) } as unknown as TranslateService;
    const column = ShowRecordConfigBase.createColumnConfig(DataType.NumericShowZero, 'amount', 'AMOUNT');
    expect(AppHelper.getValueByPathWithField(gps, translator, { amount: null }, column, column.field)).toBeUndefined();
    expect(AppHelper.getValueByPathWithField(gps, translator, { amount: 0 }, column, column.field)).toBe('0.00');
    expect(AppHelper.numberFormat(gps, 1.234, null, null)).toBe('1.23');
    expect(AppHelper.numberFormat(gps, 1.234, 0)).toBe('1');
  });
});
