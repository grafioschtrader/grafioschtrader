package grafioschtrader.reports.udfalluserfields;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafiosch.types.IUDFSpecialType;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyUDFGroup;
import grafioschtrader.repository.MicProviderMapRepository;
import grafioschtrader.types.UDFSpecialGTType;

/**
 * Service class for generating Yahoo Finance key statistics links as user-defined fields for securities. This class
 * extends YahooUDFFields to provide automatic creation of direct links to Yahoo Finance's key statistics pages for
 * securities across all users in the system.
 * 
 * The service creates clickable URLs that direct users to comprehensive financial statistics and fundamental analysis
 * data on Yahoo Finance, including metrics such as market capitalization, price-to-earnings ratios, dividend yields,
 * financial ratios, and other key performance indicators.
 * 
 * Key features include:</br>
 * - Automatic Yahoo symbol resolution for accurate link generation</br>
 * - Direct integration with Yahoo Finance key statistics pages</br>
 * - Background processing capability for improved user experience</br>
 * - Asset class filtering to ensure links are only created for appropriate securities</br>
 * - Persistent storage of generated links in the UDF system</br>
 * - Rate-limited access to Yahoo Finance services for reliable operation</br>
 * 
 * The generated links follow the format: https://finance.yahoo.com/quote/{symbol}/key-statistics/ where {symbol} is the
 * resolved Yahoo Finance symbol for the security.
 * 
 * This service is particularly valuable for providing users with quick access to detailed fundamental analysis data
 * without requiring manual symbol lookup or navigation to Yahoo Finance. The links remain valid as long as the security
 * is actively traded and available on Yahoo Finance.
 */
@Service
public class YahooStaticticsLink extends YahooUDFFields implements IUDFForEveryUser {

  @Autowired
  private MicProviderMapRepository micProviderMapRepository;

  @Override
  public void addUDFForEveryUser(SecuritycurrencyUDFGroup securitycurrencyUDFGroup, boolean recreate) {
    createYahooFieldValue(securitycurrencyUDFGroup, getUDFSpecialType(), micProviderMapRepository, recreate);
  }

  @Override
  public IUDFSpecialType getUDFSpecialType() {
    return UDFSpecialGTType.UDF_SPEC_INTERNAL_YAHOO_STATISTICS_LINK;
  }

  @Override
  public boolean mayRunInBackground() {
    return true;
  }

}
