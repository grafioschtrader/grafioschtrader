package grafioschtrader.priceupdate.intraday;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.ThreadHelper;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.priceupdate.BaseQuoteThru;
import grafioschtrader.service.GlobalparametersService;

public abstract class BaseIntradayThru<S extends Securitycurrency<S>> extends BaseQuoteThru
    implements IIntradayLoad<S> {

  protected final GlobalparametersService globalparametersService;

  protected BaseIntradayThru(GlobalparametersService globalparametersService) {
    this.globalparametersService = globalparametersService;
  }

  @Override
  @Transactional
  @Modifying
  public List<S> updateLastPriceOfSecuritycurrency(final List<S> securtycurrencies, boolean singleThread) {
    final short maxIntraRetry = globalparametersService.getMaxIntraRetry();
    return updateLastPriceOfSecuritycurrency(securtycurrencies, maxIntraRetry, singleThread);
  }

  @Override
  public List<S> updateLastPriceOfSecuritycurrency(final List<S> securtycurrencies, final short maxIntraRetry, boolean singleThread) {
    final int scIntradayUpdateTimeout = globalparametersService.getSecurityCurrencyIntradayUpdateTimeout();
    final List<S> securtycurrenciesUpd = new ArrayList<>();
    if (securtycurrencies.size() > 1) {
      if(singleThread) {
        securtycurrencies.forEach(securitycurrency -> {
          securtycurrenciesUpd
              .add(updateLastPriceSecurityCurrency(securitycurrency, maxIntraRetry, scIntradayUpdateTimeout));
        });
      } else {
        ThreadHelper.executeForkJoinPool(() -> securtycurrencies.parallelStream().forEach(securitycurrency -> {
          securtycurrenciesUpd
              .add(updateLastPriceSecurityCurrency(securitycurrency, maxIntraRetry, scIntradayUpdateTimeout));
        }), GlobalConstants.FORK_JOIN_POOL_CORE_MULTIPLIER);
      }
    } else if (securtycurrencies.size() == 1) {
      securtycurrenciesUpd
          .add(updateLastPriceSecurityCurrency(securtycurrencies.getFirst(), maxIntraRetry, scIntradayUpdateTimeout));
    }
    return securtycurrenciesUpd;
  }
}
