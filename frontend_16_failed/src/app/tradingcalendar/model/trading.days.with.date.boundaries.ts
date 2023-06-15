import {CreateType} from '../../entities/dividend.split';

export interface TradingDaysWithDateBoundaries {
  oldestTradingCalendarDay: Date | string;
  youngestTradingCalendarDay: Date | string;
  dates: Date[];
  createTypes: CreateType[];
}
