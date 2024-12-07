package grafioschtrader.reports.udfalluserfields;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyUDFGroup;
import grafioschtrader.repository.MicProviderMapRepository;
import grafioschtrader.types.UDFSpecialType;

@Service
public class YahooEarningNextDate extends YahooUDFFields implements IUDFForEveryUser {

  @Autowired
  private MicProviderMapRepository micProviderMapRepository;
  
  @Override
  public void addUDFForEveryUser(SecuritycurrencyUDFGroup securitycurrencyUDFGroup, boolean recreate) {
    createYahooFieldValue(securitycurrencyUDFGroup, getUDFSpecialType(), micProviderMapRepository, recreate);
  }

  @Override
  public UDFSpecialType getUDFSpecialType() {
    return UDFSpecialType.UDF_SPEC_INTERNAL_YAHOO_EARNING_NEXT_DATE;
  }

  @Override
  public boolean mayRunInBackground() {
    return true;
  }

}
