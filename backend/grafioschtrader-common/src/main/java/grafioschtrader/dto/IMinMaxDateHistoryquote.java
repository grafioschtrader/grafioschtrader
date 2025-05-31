package grafioschtrader.dto;

import java.time.LocalDate;

/**
 * Contains the most recent and oldest closing prices of the instruments.
 */
public interface IMinMaxDateHistoryquote {
  /**
   * ID of security or currency pair
   */
  Integer getIdSecuritycurrency();

  /**
   * The oldest date of the closing price.
   */
  LocalDate getMinDate();

  /**
   * The date of the most recent closing price.
   */
  LocalDate getMaxDate();

}
