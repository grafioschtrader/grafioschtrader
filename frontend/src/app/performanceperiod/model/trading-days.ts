import moment from 'moment';
import { BaseSettings } from '../../lib/base.settings';

/** Date-picker limits and unavailable sessions after conversion to local calendar days. */
export interface FirstAndMissingTradingDays {
  maxWeekLimit: number;
  minIncludeMonthLimit: number;
  firstEverTradingDay: Date | null;
  secondEverTradingDay: Date | null;
  lastTradingDayOfLastYear: Date | null;
  secondLatestTradingDay: Date | null;
  latestTradingDay: Date | null;
  holidayAndMissingQuoteDays: Date[];
}

type TradingDayKey =
  | 'firstEverTradingDay'
  | 'secondEverTradingDay'
  | 'lastTradingDayOfLastYear'
  | 'secondLatestTradingDay'
  | 'latestTradingDay';

/** The endpoint sends date-only ISO strings, without a time zone. */
export type FirstAndMissingTradingDaysResponse = Omit<
  FirstAndMissingTradingDays,
  TradingDayKey | 'holidayAndMissingQuoteDays'
> &
  Record<TradingDayKey, string | null> & { holidayAndMissingQuoteDays?: string[] | null };

/** UTC parsing would move the lower bound past local midnight and reject the first selectable day. */
export function parseTradingDays(response: FirstAndMissingTradingDaysResponse): FirstAndMissingTradingDays {
  const localDate = (day: string | null): Date | null =>
    day ? moment(day, BaseSettings.FORMAT_DATE_SHORT_NATIVE).toDate() : null;
  return {
    ...response,
    firstEverTradingDay: localDate(response.firstEverTradingDay),
    secondEverTradingDay: localDate(response.secondEverTradingDay),
    lastTradingDayOfLastYear: localDate(response.lastTradingDayOfLastYear),
    secondLatestTradingDay: localDate(response.secondLatestTradingDay),
    latestTradingDay: localDate(response.latestTradingDay),
    holidayAndMissingQuoteDays: (response.holidayAndMissingQuoteDays ?? []).map((day) =>
      moment(day, BaseSettings.FORMAT_DATE_SHORT_NATIVE).toDate()
    )
  };
}
