export interface TradingDaysWithDateBoundaries {
  oldestTradingCalendarDay: Date | string;
  youngestTradingCalendarDay: Date | string;
  dates: Date[];
}
