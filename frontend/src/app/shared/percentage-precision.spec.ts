import { afterEach, describe, expect, it, vi } from 'vitest';
import { BaseSettings } from '../lib/base.settings';
import { AppHelper } from '../lib/helper/app.helper';
import { DataType } from '../lib/dynamic-form/models/data.type';
import { GlobalparameterService } from '../lib/services/globalparameter.service';
import { AppSettings } from './app.settings';
import { BusinessHelper } from './helper/business.helper';

describe('percentage output precision', () => {
  const originalPrecision = AppSettings.FID_PERCENTAGE_FRACTION;

  afterEach(() => {
    AppSettings.FID_PERCENTAGE_FRACTION = originalPrecision;
    vi.unstubAllGlobals();
  });

  it('loads the application percentage precision through the existing login map', () => {
    AppSettings.FID_PERCENTAGE_FRACTION = 7;
    vi.stubGlobal('sessionStorage', { getItem: () => JSON.stringify({ FID_PERCENTAGE_FRACTION: 2 }) });
    BaseSettings.resetInterFractionLimit(AppSettings, 'precision');
    expect(AppSettings.FID_PERCENTAGE_FRACTION).toBe(2);
  });

  it.each(['en-US', 'de-DE'])('formats rounded percentages using the GT pipeline in %s', (locale) => {
    const gps = {
      getNumberFormat: () => new Intl.NumberFormat(locale, { minimumFractionDigits: 2, maximumFractionDigits: 8 }),
      getDecimalSymbol: () => (locale === 'de-DE' ? ',' : '.')
    } as GlobalparameterService;
    const field = {
      field: 'value',
      dataType: DataType.Numeric,
      maxFractionDigits: AppSettings.FID_PERCENTAGE_FRACTION
    };
    const value = BusinessHelper.roundNumber(0.6751 * 100, AppSettings.FID_PERCENTAGE_FRACTION);
    expect(AppHelper.getValueByPathWithField(gps, null, { value }, field, 'value')).toBe(
      locale === 'de-DE' ? '67,51' : '67.51'
    );
  });
});
