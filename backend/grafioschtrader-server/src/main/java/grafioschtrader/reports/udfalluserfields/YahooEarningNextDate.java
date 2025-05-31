package grafioschtrader.reports.udfalluserfields;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafiosch.types.IUDFSpecialType;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyUDFGroup;
import grafioschtrader.repository.MicProviderMapRepository;
import grafioschtrader.types.UDFSpecialGTType;

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
