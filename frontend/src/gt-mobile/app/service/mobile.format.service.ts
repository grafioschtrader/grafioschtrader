import { Service } from '@angular/core';
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
    return value == null ? '' : this.format({ value }, 'value', DataType.NumericShowZero, currency) + ' ' + currency;
  }

  /** Formats a percentage with two fraction digits and the percent sign. */
  percentage(value: number | null | undefined): string {
    return value == null ? '' : this.format({ value }, 'value', DataType.NumericShowZero, null) + ' %';
  }

  /** Formats a date given as ISO string in the short date format of the user. */
  date(value: string | null | undefined): string {
    return value ? this.format({ value }, 'value', DataType.DateString) : '';
  }

  /** Decimal symbol followed by the thousands separator, as Plotly expects it. */
  plotlySeparators(): string {
    return this.localeFormat.getDecimalSymbol() + this.localeFormat.getThousandsSeparatorSymbol();
  }

  getLocale(): string {
    return this.localeFormat.getLocale();
  }
}
