package grafioschtrader.repository;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.GTNetLastpriceCurrencypair;

public class GTNetLastpriceCurrencypairJpaRepositoryImpl
    extends GTNetLastpriceSecurityCurrencyService<GTNetLastpriceCurrencypair, Currencypair>
    implements GTNetLastpriceCurrencypairJpaRepositoryCustom {

  @Autowired
  private GTNetLastpriceCurrencypairJpaRepository gTNetLastpriceCurrencypairRepository;

  
}
