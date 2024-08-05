package grafioschtrader.reports.udfalluserfields;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyUDFGroup;
import grafioschtrader.repository.MicProviderMapRepository;
import grafioschtrader.types.UDFSpecialType;

@Service
public class YahooStaticticsLink extends YahooUDFFields implements IUDFForEveryUser {

    @Autowired
    private MicProviderMapRepository micProviderMapRepository;

    @Override
    public void addUDFForEveryUser(SecuritycurrencyUDFGroup securitycurrencyUDFGroup) {
      createYahooFieldValue(securitycurrencyUDFGroup, getUDFSpecialType(), micProviderMapRepository);
    }

    @Override
    public UDFSpecialType getUDFSpecialType() {
      return UDFSpecialType.UDF_SPEC_INTERNAL_YAHOO_STATISTICS_LINK;
    }

    @Override
    public boolean mayRunInBackground() {
      return true;
    }

}
