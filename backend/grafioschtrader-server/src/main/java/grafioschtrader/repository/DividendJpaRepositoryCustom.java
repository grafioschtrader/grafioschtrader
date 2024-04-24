package grafioschtrader.repository;

import java.util.List;

import grafioschtrader.entities.Security;

public interface DividendJpaRepositoryCustom {

  /**
   * Determines from the dividend calendar possible securities that have received
   * a dividend.
   */
  public void appendThruDividendCalendar();

  /**
   * The following algorithm is used to determine possible missing dividend income
   * in the dividend entity for securities. This is based on the date of the last
   * dividend payment and the periodicity of the expected payments. In addition,
   * the dividend payments of the transactions are also taken into account if the
   * dividend payment is more recent than the date in the dividend entity.
   * 
   * TODO If the dividend calendars cover enough securities, this periodic update
   * no longer needs to be supported.
   */
  List<String> periodicallyUpdate();

  List<String> loadAllDividendDataFromConnector(Security security);
}
