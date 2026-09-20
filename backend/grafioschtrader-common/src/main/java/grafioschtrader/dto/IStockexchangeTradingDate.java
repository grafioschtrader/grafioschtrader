package grafioschtrader.dto;

import java.time.LocalDate;

/**
 * One trading session of one stock exchange, used to measure the age of a price in sessions instead of calendar days.
 */
public interface IStockexchangeTradingDate {

  Integer getIdStockexchange();

  LocalDate getTradingDate();
}
