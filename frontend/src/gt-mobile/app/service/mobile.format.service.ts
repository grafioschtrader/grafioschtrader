import { Service } from '@angular/core';
import { BaseSettings } from '../../../app/lib/base.settings';
import { DataType } from '../../../app/lib/dynamic-form/models/data.type';
import { LocaleFormat } from '../../../app/lib/helper/locale.format';
import { ValueFormatField, ValueFormatHelper } from '../../../app/lib/helper/value.format.helper';

/**
 * Formats figures of the mobile application with GT's data types and the locale of the signed-in user, exactly as the
 * desktop tables do.
 */
@Service()
export class MobileFormatService {
  private localeFormat = new LocaleFormat();

  /** Forgets the cached formats; called when a user signs in or out. */
  reset(): void {
    this.localeFormat = new LocaleFormat();
  }

  /**
   * Formats one property of an object by its data type.
   *
   * @param dataObject Object holding the value
   * @param path Dotted path of the value
   * @param dataType GT data type of the value
   * @param currency Currency whose precision applies to a numeric value; omitted for the standard precision
   * @returns Formatted text, empty when there is no value
   */
  format(dataObject: unknown, path: string, dataType: DataType, currency?: string | null): string {
    const field: ValueFormatField = { dataType, fixedCurrency: currency ?? undefined };
    return ValueFormatHelper.getValueByPathWithField(this.localeFormat, dataObject, field, path) ?? '';
  }

  /** Formats an amount with the precision of its currency, followed by the currency code. */
  amount(value: number | null | undefined, currency: string): string {
    const figure = this.amountFigure(value, currency);
    return figure && figure + ' ' + currency;
  }

  /** Formats an amount with the precision of its currency, without the code, where the line names the currency. */
  amountFigure(value: number | null | undefined, currency: string): string {
    return value == null ? '' : this.format({ value }, 'value', DataType.NumericShowZero, currency);
  }

  /** Formats a percentage with two fraction digits and the percent sign. */
  percentage(value: number | null | undefined): string {
    if (value == null) {
      return '';
    }
    const field: ValueFormatField = { dataType: DataType.NumericShowZero, fixedFractionDigits: 2 };
    return ValueFormatHelper.getValueByPathWithField(this.localeFormat, { value }, field, 'value') + ' %';
  }

  /**
   * Formats a price like the price column of the desktop watchlist: with the fraction digits the backend decided on for
   * the instrument, otherwise with the standard precision and up to the maximum for very small prices.
   */
  price(value: number | null | undefined, priceFractionDigits: number | null | undefined): string {
    if (value == null) {
      return '';
    }
    const field: ValueFormatField = {
      dataType: DataType.NumericShowZero,
      maxFractionDigits: BaseSettings.FID_MAX_FRACTION_DIGITS,
      fixedFractionDigits: priceFractionDigits ?? undefined
    };
    return ValueFormatHelper.getValueByPathWithField(this.localeFormat, { value }, field, 'value') ?? '';
  }

  /** Formats a number of units without padding fraction digits, so 150 shares read as 150. */
  units(value: number | null | undefined): string {
    return value == null ? '' : this.format({ value }, 'value', DataType.NumericRaw);
  }

  /** Formats a date given as ISO string in the short date format of the user. */
  date(value: string | null | undefined): string {
    return value ? this.format({ value }, 'value', DataType.DateString) : '';
  }

  /** Formats a point in time, given as ISO string or epoch milliseconds, with date and time of the user's locale. */
  dateTime(value: string | number | null | undefined): string {
    return value == null || value === '' ? '' : this.format({ value }, 'value', DataType.DateTimeString);
  }

  /** Formats a whole number with the thousands separator of the user's locale, such as a trading volume. */
  integer(value: number | null | undefined): string {
    return value == null ? '' : this.format({ value }, 'value', DataType.NumericInteger);
  }

  /** Decimal symbol followed by the thousands separator, as Plotly expects it. */
  plotlySeparators(): string {
    return this.localeFormat.getDecimalSymbol() + this.localeFormat.getThousandsSeparatorSymbol();
  }

  getLocale(): string {
    return this.localeFormat.getLocale();
  }
}
