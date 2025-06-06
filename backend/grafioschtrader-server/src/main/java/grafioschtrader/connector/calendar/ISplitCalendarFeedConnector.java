package grafioschtrader.connector.calendar;

import java.time.LocalDate;
import java.util.Map;

import grafioschtrader.entities.Securitysplit;

public interface ISplitCalendarFeedConnector {

  /**
   * Get split data for a certain day with all securities
   */
  Map<String, TickerSecuritysplit> getCalendarSplitForSingleDay(LocalDate forDate, String[] countyCodes)
      throws Exception;

  /**
   * Priority of the data provider, first means lower number
   */
  int getPriority();

  class TickerSecuritysplit {
    public TickerSecuritysplit(String companyName, Securitysplit securitysplit) {
      this.companyName = companyName;
      this.securitysplit = securitysplit;
    }

    public String companyName;
    public String countryCode;
    public Securitysplit securitysplit;

    @Override
    public String toString() {
      return "TickerSecuritysplit [companyName=" + companyName + ", countryCode=" + countryCode + ", securitysplit="
          + securitysplit + "]";
    }

  }

}
