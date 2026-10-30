import moment from 'moment';
import 'moment/locale/de-ch.js';
import 'moment/locale/de.js';
import 'moment/locale/en-gb.js';
import 'moment/locale/en-au.js';
import 'moment/locale/en-nz.js';
import { BaseSettings } from '../base.settings';
import { GlobalSessionNames } from '../global.session.names';

/**
 * The locale settings value formatting reads. {@link LocaleFormat} implements it from the session, and the
 * GlobalparameterService satisfies it by delegating there, so formatting code accepts either.
 */
export interface ValueFormatSettings {
  getLocale(): string;
  getDecimalSymbol(): string;
  getCurrencyPrecision(currency: string): number;
  getNumberFormat(): Intl.NumberFormat;
  getNumberFormatForPrecision(precision: number): Intl.NumberFormat;
  getNumberFormatRaw(): Intl.NumberFormat;
  getDateFormat(): string;
  getTimeDateFormatForTable(): string;
  getTimeSecondDateFormatForTable(): string;
}

/**
 * Number and date formats of the signed-in user, derived from the locale and the separators the login stored in the
 * session. Each value is computed once and cached, so a new login needs a new instance.
 *
 * It depends on nothing but the session and moment, which lets the desktop application and the mobile application
 * format every figure in the same way.
 */
export class LocaleFormat implements ValueFormatSettings {
  private numberFormat: Intl.NumberFormat | null = null;
  private numberFormatRaw: Intl.NumberFormat | null = null;
  private readonly numberFormatPrecisionCache = new Map<number, Intl.NumberFormat>();
  private currencyPrecisionMap: { [currency: string]: number } | null = null;
  private thousandsSeparatorSymbol: string | null = null;
  private decimalSymbol: string | null = null;
  private timeDateFormat: string | null = null;
  private timeSecondDateFormat: string | null = null;
  private dateFormat: string | null = null;
  private dateFormatCalendar: string | null = null;
  private dateFormatCalendarTwoNumber: string | null = null;
  private dateFormatWithoutYear: string | null = null;

  public getLocale(): string {
    return sessionStorage.getItem(GlobalSessionNames.LOCALE) || 'de-CH';
  }

  public getThousandsSeparatorSymbol(): string {
    return (this.thousandsSeparatorSymbol ??= sessionStorage.getItem(GlobalSessionNames.THOUSANDS_SEPARATOR) ?? '');
  }

  public getDecimalSymbol(): string {
    return (this.decimalSymbol ??= sessionStorage.getItem(GlobalSessionNames.DECIMAL_SEPARATOR) ?? '.');
  }

  /**
   * Returns the number of decimal places for amounts in the given currency (e.g. BTC=8, JPY=0). The map is provided
   * by the backend at login. Currencies not contained in the map fall back to the standard fraction digits, mirroring
   * the backend's GlobalparametersService.getPrecisionForCurrency().
   *
   * @param currency ISO 4217 or crypto currency code
   * @returns Number of decimal places for amounts in this currency
   */
  public getCurrencyPrecision(currency: string): number {
    const precisionMap: { [currency: string]: number } = (this.currencyPrecisionMap ??=
      JSON.parse(sessionStorage.getItem(GlobalSessionNames.CURRENCY_PRECISION) ?? '{}') ?? {});
    const precision = precisionMap[currency];
    return precision != null ? precision : BaseSettings.FID_STANDARD_FRACTION_DIGITS;
  }

  public getNumberFormat(): Intl.NumberFormat {
    return (this.numberFormat ??= new Intl.NumberFormat(this.getLocale(), {
      minimumFractionDigits: BaseSettings.FID_STANDARD_FRACTION_DIGITS,
      maximumFractionDigits: BaseSettings.FID_MAX_FRACTION_DIGITS
    }));
  }

  /**
   * Returns a locale-aware number format with a fixed number of fraction digits (minimum = maximum = precision).
   * Instances are cached per precision. Used for displaying currency amounts with their currency-specific precision,
   * including precision 0 (e.g. JPY).
   *
   * @param precision Fixed number of fraction digits
   * @returns Cached Intl.NumberFormat for the user's locale and the given precision
   */
  public getNumberFormatForPrecision(precision: number): Intl.NumberFormat {
    let numberFormat = this.numberFormatPrecisionCache.get(precision);
    if (!numberFormat) {
      numberFormat = new Intl.NumberFormat(this.getLocale(), {
        minimumFractionDigits: precision,
        maximumFractionDigits: precision
      });
      this.numberFormatPrecisionCache.set(precision, numberFormat);
    }
    return numberFormat;
  }

  public getNumberFormatRaw(): Intl.NumberFormat {
    return (this.numberFormatRaw ??= new Intl.NumberFormat(this.getLocale(), { maximumFractionDigits: 10 }));
  }

  public getTimeDateFormatForTable(): string {
    return (this.timeDateFormat ??= this.getTimeAndShortDateFormat('LT'));
  }

  public getTimeSecondDateFormatForTable(): string {
    return (this.timeSecondDateFormat ??= this.getTimeAndShortDateFormat('LTS'));
  }

  public getDateFormatWithoutYear(): string {
    if (!this.dateFormatWithoutYear) {
      let format = this.getDateFormatYearCalendar('YYYY');
      if (format.match(/.YYYY/g)) {
        format = format.replace(/.YYYY/, '');
      }
      if (format.match(/YYYY./g)) {
        format = format.replace(/YYYY./, '');
      }
      this.dateFormatWithoutYear = format;
    }
    return this.dateFormatWithoutYear;
  }

  /**
   * Short Date format depending on locale. Year from 00-99, like MM-DD-YY or MM.DD.YY.
   */
  public getDateFormat(): string {
    return (this.dateFormat ??= this.getDateFormatYearCalendar('YY'));
  }

  public getDateFormatForCalendar(): string {
    return (this.dateFormatCalendar ??= this.getDateFormatYearCalendar('Y'));
  }

  public getCalendarTwoNumberDateFormat(): string {
    return (this.dateFormatCalendarTwoNumber ??= this.getDateFormatYearCalendar('Y'));
  }

  private getTimeAndShortDateFormat(timeFormat: 'LT' | 'LTS'): string {
    moment.locale(this.getTimeLocale());
    const formatYear = moment.localeData().longDateFormat('L');
    const formatTime = moment.localeData().longDateFormat(timeFormat);
    return formatTime + ' ' + formatYear.replace(/YYYY/g, 'YY');
  }

  private getDateFormatYearCalendar(yearReplace: string): string {
    moment.locale(this.getTimeLocale());
    const formatYear = moment.localeData().longDateFormat('L');
    return formatYear.replace(/YYYY/g, yearReplace);
  }

  private getTimeLocale(): string {
    if (['de', 'en-gb', 'en-au', 'en-nz'].indexOf(this.getLocale().toLowerCase()) != -1) {
      return this.getLocale().toLowerCase();
    }
    return this.getLocale().startsWith('de') ? 'de' : 'en';
  }
}
