import moment from 'moment';
import { DataType } from '../dynamic-form/models/data.type';
import type { ColumnConfig } from '../datashowbase/column.config';
import type { ValueFormatSettings } from './locale.format';

/** The part of a column definition that decides how its value is formatted. */
export type ValueFormatField = Pick<
  ColumnConfig,
  | 'dataType'
  | 'maxFractionDigits'
  | 'minFractionDigits'
  | 'fractionDigitsField'
  | 'fixedFractionDigits'
  | 'currencyPrecisionField'
  | 'fixedCurrency'
>;

/**
 * Formats a value by its GT data type. Tables, record views and the mobile application all format through here, so a
 * figure reads the same wherever it is shown. It imports no user interface library.
 */
export class ValueFormatHelper {
  /**
   * Reads a value by a dotted path, such as 'cashaccount.name'.
   *
   * @param dataObject Object to read from
   * @param path Property names separated by dots
   * @returns The value, null when an intermediate object is missing, or the object itself when it is falsy
   */
  public static getValueByPath(dataObject: any, path: string): any {
    if (dataObject) {
      const iPath = path.split('.');
      const len = iPath.length;
      for (let i = 0; i < len; i++) {
        if (!dataObject) {
          return null;
        }
        dataObject = dataObject[iPath[i]];
      }
    }
    return dataObject;
  }

  /**
   * Formats data value based on field configuration and data type.
   * Handles various data types including numbers, dates, and strings with
   * appropriate locale-specific formatting.
   *
   * @param settings Locale and formatting settings
   * @param dataobject Source data object
   * @param field Column configuration specifying data type and formatting
   * @param valueField Path to the value within the data object
   * @param currencyFieldOverride Optional path to the currency code within the data object. Used by group/grand-total
   *                              rows whose object shape differs from detail rows; takes precedence over the
   *                              column's currencyPrecisionField.
   * @returns Formatted value string or undefined if no data
   */
  public static getValueByPathWithField(
    settings: ValueFormatSettings,
    dataobject: any,
    field: ValueFormatField,
    valueField: string,
    currencyFieldOverride?: string
  ) {
    const rowObject = dataobject;
    dataobject = ValueFormatHelper.getValueByPath(dataobject, valueField);
    if (dataobject || (field.dataType === DataType.NumericShowZero && dataobject === 0)) {
      switch (field.dataType) {
        case DataType.NumericInteger:
          return ValueFormatHelper.numberIntegerFormat(settings, dataobject);
        case DataType.Numeric:
        case DataType.NumericShowZero: {
          const fractionDigits = ValueFormatHelper.resolveFractionDigits(rowObject, field);
          if (fractionDigits != null) {
            return settings.getNumberFormatForPrecision(fractionDigits).format(dataobject);
          }
          const currency = ValueFormatHelper.resolvePrecisionCurrency(rowObject, field, currencyFieldOverride);
          if (currency) {
            return settings.getNumberFormatForPrecision(settings.getCurrencyPrecision(currency)).format(dataobject);
          }
          return ValueFormatHelper.numberFormat(settings, dataobject, field.maxFractionDigits, field.minFractionDigits);
        }
        case DataType.NumericRaw:
          return settings.getNumberFormatRaw().format(dataobject);
        case DataType.DateNumeric:
        case DataType.DateString:
          return ValueFormatHelper.getDateByFormat(settings, dataobject);
        case DataType.DateTimeNumeric:
          return moment(+dataobject).format(settings.getTimeDateFormatForTable());
        case DataType.DateTimeString:
          return moment(dataobject).format(settings.getTimeDateFormatForTable());
        case DataType.DateTimeSecondString:
          return moment.utc(dataobject).local().format(settings.getTimeSecondDateFormatForTable());
        default:
          return dataobject;
      }
    }
  }

  /**
   * Formats date string according to the short date format of the locale.
   * Uses moment.js for reliable date parsing and formatting.
   *
   * @param settings Settings providing the date format
   * @param dataobject Date string to format
   * @returns Formatted date string
   */
  public static getDateByFormat(settings: ValueFormatSettings, dataobject: string | number | Date): string {
    return moment(dataobject).format(settings.getDateFormat());
  }

  /**
   * Formats numeric value with locale-specific number formatting.
   * Handles precision, fraction digits, and decimal symbol based on locale.
   * Applies special formatting for very small numbers.
   *
   * @param settings Locale and formatting settings
   * @param value Numeric value to format
   * @param maxFractionDigits Maximum number of decimal places
   * @param minFractionDigits Minimum number of decimal places
   * @returns Formatted number string
   */
  public static numberFormat(
    settings: ValueFormatSettings,
    value: number,
    maxFractionDigits?: number | null,
    minFractionDigits?: number | null
  ) {
    if (maxFractionDigits != null) {
      if (maxFractionDigits > 0) {
        const n = Math.log(Math.abs(value)) / Math.LN10;
        if (n < 1) {
          // negative number means fractions or less than 0
          return value
            .toFixed(
              Math.min(
                maxFractionDigits,
                Math.max(minFractionDigits || 2, Math.max(2, Math.ceil(Math.abs(n)) + (n < 0 ? 4 : 2)))
              )
            )
            .split('.')
            .join(settings.getDecimalSymbol());
        }
      } else {
        return value.toFixed(0);
      }
    }
    return settings.getNumberFormat().format(value);
  }

  /**
   * Formats integer value with locale-specific formatting.
   * Uses browser's built-in locale formatting for integers.
   *
   * @param settings Settings providing the locale
   * @param value Integer value to format
   * @returns Formatted integer string
   */
  public static numberIntegerFormat(settings: ValueFormatSettings, value: number) {
    return value.toLocaleString(settings.getLocale());
  }

  /**
   * Resolves the exact number of fraction digits the backend decided on for a numeric column value, for example the
   * currency precision of an index level. The row path of fractionDigitsField is tried first, then the column's
   * fixedFractionDigits. Returns undefined when neither yields a number, which keeps the currency or standard path.
   *
   * @param rowObject The complete row data object
   * @param field Column configuration possibly carrying fractionDigitsField/fixedFractionDigits
   * @returns Number of fraction digits or undefined
   */
  private static resolveFractionDigits(rowObject: any, field: ValueFormatField): number | undefined {
    if (field.fractionDigitsField && rowObject) {
      const fractionDigits = ValueFormatHelper.getValueByPath(rowObject, field.fractionDigitsField);
      if (fractionDigits != null) {
        return fractionDigits;
      }
    }
    return field.fixedFractionDigits;
  }

  /**
   * Resolves the currency code that determines the display precision of a numeric column value. Detail rows resolve
   * the currency through the column's currencyPrecisionField path, group/grand-total rows through the override path
   * of their ColumnGroupConfig. When neither path resolves on the row object, the column's fixedCurrency applies
   * (typically the tenant/portfolio main currency set after data load). Returns undefined for columns that did not
   * opt in to currency-specific precision, which keeps the standard formatting path.
   *
   * @param rowObject The complete row data object (detail or group summary)
   * @param field Column configuration possibly carrying currencyPrecisionField/fixedCurrency
   * @param currencyFieldOverride Optional currency path of a group configuration
   * @returns Resolved currency code or undefined
   */
  private static resolvePrecisionCurrency(
    rowObject: any,
    field: ValueFormatField,
    currencyFieldOverride?: string
  ): string | undefined {
    const currencyPath = currencyFieldOverride || field.currencyPrecisionField;
    if (currencyPath && rowObject) {
      const currency = ValueFormatHelper.getValueByPath(rowObject, currencyPath);
      if (currency) {
        return currency;
      }
    }
    return field.fixedCurrency;
  }
}
