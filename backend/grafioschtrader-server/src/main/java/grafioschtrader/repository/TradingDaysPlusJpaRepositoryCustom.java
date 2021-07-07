package grafioschtrader.repository;

import java.time.LocalDate;

import grafioschtrader.dto.TradingDaysWithDateBoundaries;

public interface TradingDaysPlusJpaRepositoryCustom extends TradingDaysBase {

  /**
   * Every day, before the first login, it is expected that the previous day's
   * quote data has been loaded. This is the only way for this method to be
   * effective.
   *
   * @param tradingDay
   * @return
   */
  boolean hasTradingDayBetweenUntilYesterday(LocalDate tradingDay);

  TradingDaysWithDateBoundaries getTradingDaysByYear(int year);

  TradingDaysWithDateBoundaries save(SaveTradingDays saveTradingDays);

}
