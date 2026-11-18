import moment from 'moment';
import { BaseSettings } from '../../lib/base.settings';
import type { CalendarConfig } from '../../lib/dynamic-form/models/field.config';
import { Weekday } from '../../shared/helper/weekday';
import type { FirstAndMissingTradingDays } from './trading-days';

/**
 * Date range shared by the period performance and the period performance per security. Both views take the bounds,
 * the defaults and the stored values through these pure functions, so a range chosen in one view is preset in the
 * other. Reading and writing the session storage is left to the views.
 */

/** Selectable bounds of the two date fields of one scope. */
export interface PerformanceRangeBounds {
  /** First selectable start date, the excluded base of the longest possible period. */
  fromMin: Date;
  /** Last selectable start date; the end date must still lie after it. */
  fromMax: Date;
  /** Last selectable end date, the newest valuation day of the scope. */
  toMax: Date;
  /** Days without a price for a held security or a holiday, never selectable. */
  disabledDates: Date[];
}

/** A start and end date of a period. */
export interface PerformanceRange {
  dateFrom: Date;
  dateTo: Date;
}

/**
 * Returns the bounds of the date fields, or null when the scope has no period to offer.
 *
 * @param days - Trading day metadata of the client or portfolio
 * @returns the bounds, null when one of the required days is missing
 */
export function performanceRangeBounds(days: FirstAndMissingTradingDays): PerformanceRangeBounds | null {
  if (!days.firstEverTradingDay || !days.secondLatestTradingDay || !days.latestTradingDay) {
    return null;
  }
  return {
    fromMin: days.firstEverTradingDay,
    fromMax: days.secondLatestTradingDay,
    toMax: days.latestTradingDay,
    disabledDates: days.holidayAndMissingQuoteDays
  };
}

/**
 * Whether a date can be picked: within the bounds, a weekday and not a disabled date.
 *
 * @param date - The date to test
 * @param min - First allowed day
 * @param max - Last allowed day
 * @param disabledDates - Days that are never selectable
 * @returns true when the calendar would accept the date
 */
export function isSelectableDay(date: Date, min: Date, max: Date, disabledDates: Date[]): boolean {
  const day = moment(date);
  return (
    !day.isBefore(min, 'day') &&
    !day.isAfter(max, 'day') &&
    day.isoWeekday() < 6 &&
    !disabledDates.some((disabled) => day.isSame(disabled, 'day'))
  );
}

/**
 * First selectable end date after a start date, the lower bound of the end date field. When no day up to the newest
 * valuation day qualifies, that newest day is returned.
 *
 * @param dateFrom - The chosen start date
 * @param bounds - Bounds of the scope
 * @returns the first day after the start date that the end date field accepts
 */
export function firstSelectableDateTo(dateFrom: Date, bounds: PerformanceRangeBounds): Date {
  let day = moment(dateFrom).startOf('day').add(1, 'd');
  while (
    day.isBefore(bounds.toMax, 'day') &&
    !isSelectableDay(day.toDate(), day.toDate(), bounds.toMax, bounds.disabledDates)
  ) {
    day = day.add(1, 'd');
  }
  return day.toDate();
}

/**
 * Parses a date stored in the session storage.
 *
 * @param value - The stored text, null when nothing is stored
 * @returns the local calendar day, null for an absent or unparsable value
 */
export function parseStoredDate(value: string | null): Date | null {
  if (!value) {
    return null;
  }
  const day = moment(value, BaseSettings.FORMAT_DATE_SHORT_NATIVE, true);
  return day.isValid() ? day.toDate() : null;
}

/**
 * Builds the range a view starts with. A stored date is used only when the calendar of the scope can select it, and
 * the end date only when it also lies after the start date; otherwise the default applies: the last trading day of
 * the previous year, or the first trading day of the scope when that lies later, and the newest valuation day.
 *
 * @param days - Trading day metadata of the scope
 * @param storedFrom - The stored start date, may be null or unparsable
 * @param storedTo - The stored end date, may be null or unparsable; absent means the newest valuation day
 * @returns the range, null when the scope has no period to offer
 */
export function initialPerformanceRange(
  days: FirstAndMissingTradingDays,
  storedFrom: string | null,
  storedTo: string | null
): PerformanceRange | null {
  const bounds = performanceRangeBounds(days);
  if (!bounds) {
    return null;
  }
  const lastOfLastYear = days.lastTradingDayOfLastYear;
  let defaultFrom = !lastOfLastYear || bounds.fromMin > lastOfLastYear ? bounds.fromMin : lastOfLastYear;
  defaultFrom = defaultFrom > bounds.fromMax ? bounds.fromMax : defaultFrom;
  const from = parseStoredDate(storedFrom);
  const dateFrom =
    from && isSelectableDay(from, bounds.fromMin, bounds.fromMax, bounds.disabledDates) ? from : defaultFrom;
  const to = parseStoredDate(storedTo);
  const toMin = firstSelectableDateTo(dateFrom, bounds);
  const dateTo = to && isSelectableDay(to, toMin, bounds.toMax, bounds.disabledDates) ? to : bounds.toMax;
  return { dateFrom, dateTo };
}

/**
 * The value to store for an end date. The newest valuation day is not stored, so a range that ends there follows
 * newer valuation days when the view is opened again.
 *
 * @param dateTo - The chosen end date, null when the field is empty
 * @param days - Trading day metadata of the scope
 * @returns the text to store, or null when the stored entry must be removed
 */
export function storedDateTo(dateTo: Date | null, days: FirstAndMissingTradingDays): string | null {
  if (!dateTo || (days.latestTradingDay && moment(dateTo).isSame(days.latestTradingDay, 'day'))) {
    return null;
  }
  return moment(dateTo).format(BaseSettings.FORMAT_DATE_SHORT_NATIVE);
}

/**
 * Writes or removes the stored end date according to {@link storedDateTo}.
 *
 * @param storage - The session storage, or a stand-in of it
 * @param key - Storage key of the end date
 * @param dateTo - The chosen end date, null when the field is empty
 * @param days - Trading day metadata of the scope
 */
export function syncStoredDateTo(
  storage: Pick<Storage, 'setItem' | 'removeItem'>,
  key: string,
  dateTo: Date | null,
  days: FirstAndMissingTradingDays
): void {
  const value = storedDateTo(dateTo, days);
  if (value) {
    storage.setItem(key, value);
  } else {
    storage.removeItem(key);
  }
}

/**
 * Calendar settings of the two date fields: weekends and the disabled dates are never selectable, the end date starts
 * after the start date.
 *
 * @param bounds - Bounds of the scope
 * @param dateFrom - The chosen start date, null while the field is empty
 * @returns the settings of the start and the end date field
 */
export function performanceRangeCalendars(
  bounds: PerformanceRangeBounds,
  dateFrom: Date | null
): { from: CalendarConfig; to: CalendarConfig } {
  const disabledDays = [Weekday.Saturday, Weekday.Sunday];
  return {
    from: { minDate: bounds.fromMin, maxDate: bounds.fromMax, disabledDays, disabledDates: bounds.disabledDates },
    to: {
      minDate: dateFrom ? firstSelectableDateTo(dateFrom, bounds) : bounds.fromMin,
      maxDate: bounds.toMax,
      disabledDays,
      disabledDates: bounds.disabledDates
    }
  };
}
