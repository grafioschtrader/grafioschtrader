package grafioschtrader.service;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections4.map.MultiKeyMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetLastpriceCurrencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.gtnet.GTNetServerStateTypes;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.GTNetJpaRepository;
import grafioschtrader.repository.GTNetLastpriceCurrencypairJpaRepository;
import grafioschtrader.repository.GTNetLastpriceSecurityJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

@Service
public class GTNetLastpriceService {

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;
  
  @Autowired
  private GTNetLastpriceSecurityJpaRepository gtNetLastpriceSecurityJpaRepository;
  
  @Autowired
  private GTNetLastpriceCurrencypairJpaRepository gtTNetLastpriceCurrencypairJpaRepository;

 

  public GTNetLastpriceService.SecurityCurrency updateLastpriceIncludeSupplier(List<Security> securyties,
      List<Currencypair> currencypairs, List<Currencypair> currenciesNotInList) {
  
    List<GTNet> gtNetsOpen = gtNetJpaRepository.findByLastpriceConsumerUsageAndLastpriceServerState(
        GTNetServerStateTypes.SS_OPEN.getValue(), GTNetServerStateTypes.SS_OPEN.getValue());
    currencypairJpaRepository.updateLastPriceByList(currenciesNotInList);

    return new GTNetLastpriceService.SecurityCurrency(securityJpaRepository.updateLastPriceByList(securyties),
        currencypairJpaRepository.updateLastPriceByList(currencypairs));
  }

  
  private List<GTNetLastpriceCurrencypair> readUpdateGetLastpriceValues(List<Currencypair> currencypairs) {
    List<String> fromCurrencies = currencypairs.stream().map(Currencypair::getFromCurrency)
        .collect(Collectors.toList());
    List<String> toCurrencies = currencypairs.stream().map(Currencypair::getToCurrency).collect(Collectors.toList());

    List<GTNetLastpriceCurrencypair> gtNetLastpriceList = gtTNetLastpriceCurrencypairJpaRepository
        .getLastpricesByListByFromAndToCurrencies(fromCurrencies, toCurrencies);
    MultiKeyMap<String, GTNetLastpriceCurrencypair> lastCurrencyPriceMap = new MultiKeyMap<>();
    
    
    return gtNetLastpriceList;
  }
  
  
  
  public static class SecurityCurrency {
    public List<Security> securities;
    public List<Currencypair> currencypairs;

    public SecurityCurrency(final List<Security> securities, final List<Currencypair> currencypairs) {
      super();
      this.securities = securities;
      this.currencypairs = currencypairs;
    }
  }
}
