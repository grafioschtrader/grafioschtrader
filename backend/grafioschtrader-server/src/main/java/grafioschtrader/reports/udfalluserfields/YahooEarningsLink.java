package grafioschtrader.reports.udfalluserfields;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafiosch.types.IUDFSpecialType;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyUDFGroup;
import grafioschtrader.repository.MicProviderMapRepository;
import grafioschtrader.types.UDFSpecialGTType;

/**
 * Service class for generating Yahoo Finance earnings calendar links as user-defined fields for securities.
 * This class extends YahooUDFFields to provide automatic creation of direct links to Yahoo Finance's
 * earnings calendar pages for securities across all users in the system.
 * 
 * The service creates clickable URLs that direct users to Yahoo Finance's earnings calendar,
 * showing historical and upcoming earnings announcements, earnings dates, estimated vs. actual
 * earnings per share (EPS), revenue figures, and surprise percentages. This provides valuable
 * insights for fundamental analysis and investment timing decisions.
 * 
 * Key features include:</br>
 * - Automatic Yahoo symbol resolution for accurate link generation</br>
 * - Direct integration with Yahoo Finance earnings calendar pages</br>
 * - Background processing capability for improved user experience</br>
 * - Asset class filtering to ensure links are only created for equity securities</br>
 * - Persistent storage of generated links in the UDF system</br>
 * - Rate-limited access to Yahoo Finance services for reliable operation</br>
 * - Current date-based URL generation to display relevant earnings information</br>
 * 
 * The generated links follow the format: 
 * https://finance.yahoo.com/calendar/earnings?day={current_date}&symbol={symbol}
 * where {symbol} is the resolved Yahoo Finance symbol and {current_date} ensures
 * the calendar displays current and upcoming earnings events.
 * 
 * This service is particularly valuable for investors tracking earnings seasons,
 * analysts monitoring company performance, and portfolio managers making timing
 * decisions around earnings announcements. The links provide immediate access
 * to comprehensive earnings data without requiring manual navigation or symbol lookup.
 */
@Service
public class YahooEarningsLink extends YahooUDFFields implements IUDFForEveryUser {

  @Autowired
  private MicProviderMapRepository micProviderMapRepository;

  @Override
  public void addUDFForEveryUser(SecuritycurrencyUDFGroup securitycurrencyUDFGroup, boolean recreate) {
    createYahooFieldValue(securitycurrencyUDFGroup, getUDFSpecialType(), micProviderMapRepository, recreate);
  }

  @Override
  public IUDFSpecialType getUDFSpecialType() {
    return UDFSpecialGTType.UDF_SPEC_INTERNAL_YAHOO_EARNING_LINK;
  }

  @Override
  public boolean mayRunInBackground() {
    return true;
  }

}
