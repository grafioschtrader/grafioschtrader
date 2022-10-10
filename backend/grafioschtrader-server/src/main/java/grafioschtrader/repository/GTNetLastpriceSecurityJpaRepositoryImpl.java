package grafioschtrader.repository;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.entities.GTNetLastpriceSecurity;
import grafioschtrader.entities.Security;

public class GTNetLastpriceSecurityJpaRepositoryImpl
    extends GTNetLastpriceSecurityCurrencyService<GTNetLastpriceSecurity, Security>
    implements GTNetLastpriceSecurityJpaRepositoryCustom {

  @Autowired
  private GTNetLastpriceSecurityJpaRepository gtNetLastpriceSecurityJpaRepository;

 
  
  
  protected List<GTNetLastpriceSecurity> readUpdateGetLastpriceValues(List<Security> securities) {
    List<String> isins = securities.stream().map(Security::getIsin)
        .collect(Collectors.toList());
    List<String> currencies = securities.stream().map(Security::getCurrency).collect(Collectors.toList());

    List<GTNetLastpriceSecurity> gtNetLastpriceList = gtNetLastpriceSecurityJpaRepository.getLastpricesByListByIsinsAndCurrencies(
        isins, currencies);
    return gtNetLastpriceList;
  }



}
