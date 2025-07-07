package grafioschtrader.reports.udfalluserfields;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafiosch.types.IUDFSpecialType;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyUDFGroup;
import grafioschtrader.repository.MicProviderMapRepository;
import grafioschtrader.types.UDFSpecialGTType;

/**
 * Service class for extracting and providing the next earnings announcement dates from Yahoo Finance
 * as user-defined fields for securities across all users in the system.
 * This class extends YahooUDFFields to provide automatic extraction of upcoming earnings dates
 * from Yahoo Finance's earnings calendar pages.
 * 
 * The service performs web scraping of Yahoo Finance earnings calendar data to extract the specific
 * date and time of the next scheduled earnings announcement for each security. This information is
 * crucial for investment timing, portfolio management, and risk assessment around earnings events.
 * 
 * Key features include:<br>
 * - Automatic Yahoo symbol resolution for accurate data retrieval<br>
 * - Web scraping of Yahoo Finance earnings calendar pages with HTML parsing<br>
 * - Extraction of precise date and time information for next earnings announcements<br>
 * - Intelligent date validation to ensure only future earnings dates are captured<br>
 * - Background processing capability for improved user experience<br>
 * - Asset class filtering to ensure dates are only extracted for equity securities<br>
 * - Automatic expiration and refresh of past earnings dates<br>
 * - Rate-limited access to Yahoo Finance services for reliable operation<br>
 * - Persistent storage of extracted dates in the UDF system<br>
 * 
 * Unlike static link generation, this service performs active data extraction and provides
 * actionable date/time information that can be used for automated decision-making and alerts.
 */
@Service
public class YahooEarningNextDate extends YahooUDFFields implements IUDFForEveryUser {

  @Autowired
  private MicProviderMapRepository micProviderMapRepository;

  @Override
  public void addUDFForEveryUser(SecuritycurrencyUDFGroup securitycurrencyUDFGroup, boolean recreate) {
    createYahooFieldValue(securitycurrencyUDFGroup, getUDFSpecialType(), micProviderMapRepository, recreate);
  }

  @Override
  public IUDFSpecialType getUDFSpecialType() {
    return UDFSpecialGTType.UDF_SPEC_INTERNAL_YAHOO_EARNING_NEXT_DATE;
  }

  @Override
  public boolean mayRunInBackground() {
    return true;
  }

}
