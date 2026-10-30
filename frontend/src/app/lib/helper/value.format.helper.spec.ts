import { describe, expect, it } from 'vitest';
import { DataType } from '../dynamic-form/models/data.type';
import { ValueFormatSettings } from './locale.format';
import { ValueFormatHelper } from './value.format.helper';

/** Swiss settings as the login stores them, with the currency precisions the backend sends. */
const settings: ValueFormatSettings = {
  getLocale: () => 'de-CH',
  getDecimalSymbol: () => '.',
  getCurrencyPrecision: (currency) => (currency == null ? 2 : ({ JPY: 0, BTC: 8 }[currency] ?? 2)),
  getNumberFormat: () => new Intl.NumberFormat('de-CH', { minimumFractionDigits: 2, maximumFractionDigits: 8 }),
  getNumberFormatForPrecision: (precision) =>
    new Intl.NumberFormat('de-CH', { minimumFractionDigits: precision, maximumFractionDigits: precision }),
  getNumberFormatRaw: () => new Intl.NumberFormat('de-CH', { maximumFractionDigits: 10 }),
  getDateFormat: () => 'DD.MM.YY',
  getTimeDateFormatForTable: () => 'HH:mm DD.MM.YY',
  getTimeSecondDateFormatForTable: () => 'HH:mm:ss DD.MM.YY'
};

const format = (value: unknown, dataType: DataType, fixedCurrency?: string) =>
  ValueFormatHelper.getValueByPathWithField(settings, { value }, { dataType, fixedCurrency }, 'value');

describe('ValueFormatHelper', () => {
  it('rounds an amount to the precision of its currency', () => {
    expect(format(1234.5678, DataType.Numeric, 'JPY')).toBe(new Intl.NumberFormat('de-CH').format(1235));
    expect(format(0.123456789, DataType.Numeric, 'BTC')).toBe('0.12345679');
    expect(format(1234.5, DataType.Numeric, 'CHF')).toBe(
      new Intl.NumberFormat('de-CH', { minimumFractionDigits: 2 }).format(1234.5)
    );
  });

  it('shows a zero only for NumericShowZero', () => {
    expect(format(0, DataType.Numeric, 'CHF')).toBeUndefined();
    expect(format(0, DataType.NumericShowZero, 'CHF')).toBe('0.00');
  });

  it('formats a date string in the short date format of the locale', () => {
    expect(format('2026-10-29', DataType.DateString)).toBe('29.10.26');
  });

  it('reads nested paths and stops at a missing intermediate object', () => {
    expect(ValueFormatHelper.getValueByPath({ a: { b: 5 } }, 'a.b')).toBe(5);
    expect(ValueFormatHelper.getValueByPath({ a: null }, 'a.b')).toBeNull();
  });
});
