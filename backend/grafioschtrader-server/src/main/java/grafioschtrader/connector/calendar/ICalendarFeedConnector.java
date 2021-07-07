package grafioschtrader.connector.calendar;

import java.time.LocalDate;
import java.util.Map;

import grafioschtrader.entities.Securitysplit;

public interface ICalendarFeedConnector {

  /**
   * Get split data for a certain day with all securities
   *
   * @param forDate
   * @param countyCodes
   * @return
   * @throws Exception
   */
  Map<String, TickerSecuritysplit> getCalendarSplitForSingleDay(LocalDate forDate, String[] countyCodes)
      throws Exception;

  /**
   * Priority of the data provider, first means lower number
   *
   * @return
   */
  int getPriority();

  class TickerSecuritysplit {
    public TickerSecuritysplit(String companyName, Securitysplit securitysplit) {
      this.companyName = companyName;
      this.securitysplit = securitysplit;
    }

    public String companyName;
    public String countyCode;
    public Securitysplit securitysplit;

    @Override
    public String toString() {
      return "TickerSecuritysplit [companyName=" + companyName + ", countyCode=" + countyCode + ", securitysplit="
          + securitysplit + "]";
    }

  }

}
