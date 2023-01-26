package grafioschtrader.priceupdate.intraday;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.ThreadHelper;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.repository.GlobalparametersJpaRepository;

public abstract class BaseIntradayThru<S extends Securitycurrency<S>> implements IIntradayLoad<S> {

  protected final GlobalparametersJpaRepository globalparametersJpaRepository;

  protected BaseIntradayThru(GlobalparametersJpaRepository globalparametersJpaRepository) {
    this.globalparametersJpaRepository = globalparametersJpaRepository;
  }

  @Override
  @Transactional
  @Modifying
  public List<S> updateLastPriceOfSecuritycurrency(final List<S> securtycurrencies) {
    final short maxIntraRetry = globalparametersJpaRepository.getMaxIntraRetry();
    return this.updateLastPriceOfSecuritycurrency(securtycurrencies, maxIntraRetry);
  }

  @Override
  public List<S> updateLastPriceOfSecuritycurrency(final List<S> securtycurrencies, final short maxIntraRetry) {
    final int scIntradayUpdateTimeout = globalparametersJpaRepository.getSecurityCurrencyIntradayUpdateTimeout();
    if (securtycurrencies.size() > 1) {
      ThreadHelper.executeForkJoinPool(() -> securtycurrencies.parallelStream()
          .forEach(securitycurrency -> updateLastPriceSecurityCurrency(securitycurrency, maxIntraRetry,
              scIntradayUpdateTimeout)),
          GlobalConstants.FORK_JOIN_POOL_CORE_MULTIPLIER);
    } else if (securtycurrencies.size() == 1) {
      securtycurrencies.set(0,
          updateLastPriceSecurityCurrency(securtycurrencies.get(0), maxIntraRetry, scIntradayUpdateTimeout));
    }
    return securtycurrencies;
  }
}
