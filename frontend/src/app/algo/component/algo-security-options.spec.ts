import { describe, expect, it } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { AppHelper } from '../../lib/helper/app.helper';
import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { AlgoSecurityOptionData, createAlgoSecurityOptions } from './algo-security-options';

const labels = { activeFrom: 'From', activeTo: 'Until', startsAfterReference: 'Starts later', ended: 'Ended' };
const security: AlgoSecurityOptionData = {
  idSecuritycurrency: 42,
  name: 'Example',
  currency: 'CHF',
  activeFromDate: '2020-02-06',
  activeToDate: '2026-09-16'
};

function options(reference: string | Date = '2020-02-05', dates = security, pattern = 'DD.MM.YYYY') {
  const gps = { getDateFormat: () => pattern } as GlobalparameterService;
  return createAlgoSecurityOptions(
    [dates],
    reference,
    (row, field) => {
      const column = ShowRecordConfigBase.createColumnConfig(DataType.DateString, field, '');
      return AppHelper.getValueByPathWithField(gps, {} as TranslateService, row, column, field);
    },
    labels,
    '2026-09-17'
  )[0];
}

describe('strategy security date options', () => {
  it('preserves numeric IDs and formats searchable labels using the configured GT date format', () => {
    const option = options();
    expect(option.key).toBe(42);
    expect(option.value).toBe('Example / CHF — 06.02.2020 – 16.09.2026');
    expect(option.optionsText).toBe(option.value);
    expect(options('2020-02-05', security, 'MM/DD/YYYY').value).toBe('Example / CHF — 02/06/2020 – 09/16/2026');
  });

  it('colors only the individual dates, independently, and explains each warning', () => {
    const option = options();
    expect(option.textSegments.map((segment) => segment.cssClass)).toEqual([
      undefined,
      'text-danger',
      undefined,
      'text-danger'
    ]);
    expect(option.textSegments[1].title).toBe('From: Starts later');
    expect(option.textSegments[3].title).toBe('Until: Ended');
    expect(option.disabled).toBeUndefined();
    const current = options('2020-02-05', { ...security, activeToDate: '2099-12-31' });
    expect(current.textSegments[1].cssClass).toBe('text-danger');
    expect(current.textSegments[3].cssClass).toBeUndefined();
  });

  it('does not highlight equality with reference date or today', () => {
    const option = options(new Date(2020, 1, 6), { ...security, activeToDate: '2026-09-17' });
    expect(option.textSegments.every((segment) => !segment.cssClass)).toBe(true);
  });

  it('does not highlight an earlier start or a future end', () => {
    const option = options('2021-01-01', { ...security, activeToDate: '2099-12-31' });
    expect(option.textSegments.every((segment) => !segment.cssClass)).toBe(true);
  });

  it('still highlights an expired end without a strategy reference date', () => {
    const option = options(null);
    expect(option.textSegments[1].cssClass).toBeUndefined();
    expect(option.textSegments[3].cssClass).toBe('text-danger');
  });

  it('keeps missing dates readable without false warnings', () => {
    const option = options(null, { ...security, activeFromDate: null, activeToDate: null });
    expect(option.value).toBe('Example / CHF — — – —');
    expect(option.textSegments.every((segment) => !segment.cssClass)).toBe(true);
  });
});
