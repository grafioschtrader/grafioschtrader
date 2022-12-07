package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.UserAccessHelper;
import grafioschtrader.dto.TenantLimit;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Globalparameters;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.User;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyLists;
import grafioschtrader.search.SecuritycurrencySearch;

public class WatchlistJpaRepositoryImpl extends BaseRepositoryImpl<Watchlist> implements WatchlistJapRepositoryCustom {

  @Autowired
  private WatchlistJpaRepository watchlistJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Override
  @Transactional
  @Modifying
  public Watchlist addSecuritycurrenciesToWatchlist(final Integer idWatchlist,
      final SecuritycurrencyLists securitycurrencyLists) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    final Watchlist watchlist = watchlistJpaRepository.findByIdWatchlistAndIdTenant(idWatchlist, user.getIdTenant());
    if (watchlist == null) {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    } else {
      var maxPositionForTenant = watchlistJpaRepository.countPostionsInAllWatchlistByIdTenant(user.getIdTenant())
          .intValue();

      if (watchlist.getWatchlistLength() + securitycurrencyLists.getLength() <= globalparametersJpaRepository
          .getMaxValueByKey(Globalparameters.GLOB_KEY_MAX_WATCHLIST_LENGTH)
          && maxPositionForTenant + securitycurrencyLists.getLength() <= TenantLimitsHelper
              .getMaxValueByKey(globalparametersJpaRepository, Globalparameters.GLOB_KEY_MAX_SECURITIES_CURRENCIES)) {
        securitycurrencyLists.currencypairList
            .forEach(currencypair -> watchlist.getSecuritycurrencyList().add(currencypair));
        securitycurrencyLists.securityList.forEach(security -> watchlist.getSecuritycurrencyList().add(security));
        return watchlistJpaRepository.save(watchlist);
      } else {
        throw new SecurityException(GlobalConstants.LIMIT_SECURITY_BREACH);
      }
    }
  }

  @Override
  @Transactional
  @Modifying
  public Watchlist removeAllSecurityCurrency(final Integer idWatchlist) {
    final Watchlist watchlist = watchlistJpaRepository.getReferenceById(idWatchlist);
    watchlist.setSecuritycurrencyList(null);
    return watchlistJpaRepository.save(watchlist);
  }

  @Override
  public SecuritycurrencyLists searchByCriteria(final Integer idWatchlist,
      final SecuritycurrencySearch securitycurrencySearch) {

    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Watchlist watchlist = watchlistJpaRepository.findByIdWatchlistAndIdTenant(idWatchlist, user.getIdTenant());
    if (watchlist == null) {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }

    return new SecuritycurrencyLists(
        securityJpaRepository.searchBuilderWithExclusion(idWatchlist, null, securitycurrencySearch, user.getIdTenant()),
        currencypairJpaRepository.searchBuilderWithExclusion(idWatchlist, null, securitycurrencySearch));
  }

  @Override
  @Transactional
  @Modifying
  public Watchlist saveOnlyAttributes(Watchlist watchlist, Watchlist existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    watchlist = RepositoryHelper.saveOnlyAttributes(watchlistJpaRepository, watchlist, existingEntity,
        updatePropertyLevelClasses);
    setPossiblePerformanceWatchlist(watchlist, existingEntity);
    return watchlist;
  }

  private void setPossiblePerformanceWatchlist(final Watchlist watchlist, Watchlist existingWatchlist) {
    if (existingWatchlist == null) {
      final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
      Tenant tenant = tenantJpaRepository.getReferenceById(user.getIdTenant());
      if (tenant.getIdWatchlistPerformance() == null) {
        tenant.setIdWatchlistPerformance(watchlist.getIdWatchlist());
        tenantJpaRepository.save(tenant);
      }
    }
  }

  private void removePossiblePerformanceWatchlist(final Integer idWatchlist, Integer idTenant) {
    Tenant tenant = tenantJpaRepository.getReferenceById(idTenant);
    if (idWatchlist.equals(tenant.getIdWatchlistPerformance())) {
      tenant.setIdWatchlistPerformance(null);
      tenantJpaRepository.save(tenant);
    }
  }

  @Override
  public Watchlist removeSecurityFromWatchlist(final Integer idWatchlist, final Integer idSecuritycurrency) {
    final Security security = securityJpaRepository.findById(idSecuritycurrency).orElse(null);
    return removeInstrumentFromWatchlist(idWatchlist, security);
  }

  @Override
  public Watchlist removeCurrencypairFromWatchlist(final Integer idWatchlist, final Integer idSecuritycurrency) {
    final Currencypair currencypair = currencypairJpaRepository.findById(idSecuritycurrency).orElse(null);
    return removeInstrumentFromWatchlist(idWatchlist, currencypair);
  }

  private Watchlist removeInstrumentFromWatchlist(final Integer idWatchlist,
      final Securitycurrency<?> securitycurrency) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();

    final Watchlist watchlist = watchlistJpaRepository.findByIdWatchlistAndIdTenant(idWatchlist, user.getIdTenant());
    if (watchlist == null) {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    } else {
      watchlist.getSecuritycurrencyList().remove(securitycurrency);
      return watchlistJpaRepository.save(watchlist);
    }
  }

  @Override
  public int removeMultipleFromWatchlist(Integer idWatchlist, final List<Integer> idsSecuritycurrency) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return watchlistJpaRepository.deleteByIdTenantAndWatchlistAndIds(user.getIdTenant(), idWatchlist,
        idsSecuritycurrency);
  }

  @Override
  public Watchlist removeSecurityFromWatchlistAndDelete(final Integer idWatchlist, final Integer idSecuritycurrency) {
    return removeSecuritycurrencyFromWatchlistAndDelete(idWatchlist, idSecuritycurrency, securityJpaRepository);
  }

  @Override
  public Watchlist removeCurrencypairFromWatchlistAndDelete(final Integer idWatchlist,
      final Integer idSecuritycurrency) {
    return removeSecuritycurrencyFromWatchlistAndDelete(idWatchlist, idSecuritycurrency, currencypairJpaRepository);
  }

  private <T extends Securitycurrency<T>> Watchlist removeSecuritycurrencyFromWatchlistAndDelete(
      final Integer idWatchlist, final Integer idSecuritycurrency,
      final JpaRepository<T, Integer> securityCurrencypairJpaRepository) {
    final T securitycurrencypair = securityCurrencypairJpaRepository.findById(idSecuritycurrency).orElse(null);
    final Watchlist watchlist = removeInstrumentFromWatchlist(idWatchlist, securitycurrencypair);
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();

    if (UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete(user, securitycurrencypair)) {
      historyquoteJpaRepository.removeAllSecurityHistoryquote(idSecuritycurrency);
      securityCurrencypairJpaRepository.deleteById(securitycurrencypair.getIdSecuritycurrency());
      // TODO may be not allowed action
    }
    return watchlist;
  }

  @Override
  public int delEntityWithTenant(Integer idWatchlist, Integer idTenant) {
    int rc = watchlistJpaRepository.deleteByIdWatchlistAndIdTenant(idWatchlist, idTenant);
    removePossiblePerformanceWatchlist(idWatchlist, idTenant);
    return rc;
  }

  @Override
  public Boolean moveSecuritycurrency(Integer idWatchlistSource, Integer idWatchlistTarget,
      Integer idSecuritycurrency) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();

    if (watchlistJpaRepository.getWatchlistByTenantAndWatchlistIds(user.getIdTenant(),
        new Integer[] { idWatchlistSource, idWatchlistTarget }) == 2) {
      watchlistJpaRepository.moveUpdateSecuritycurrency(idWatchlistSource, idWatchlistTarget, idSecuritycurrency);
      return true;
    } else {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }
  }

  @Override
  public SecuritycurrencyLists tryUpToIntradayDataWhenRetryIntraLoadGreaterThan0(Integer idWatchlist) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    List<Security> securityList = securityJpaRepository
        .tryUpToIntradayDataWhenRetryIntraLoadGreaterThan0(user.getIdTenant(), idWatchlist);
    List<Currencypair> currencypair = currencypairJpaRepository
        .tryUpToDateIntraDataWhenRetryIntraLoadGreaterThan0(user.getIdTenant(), idWatchlist);
    return new SecuritycurrencyLists(securityList, currencypair);
  }

  @Override
  public SecuritycurrencyLists tryUpToDateHistoricalDataWhenRetryHistoryLoadGreaterThan0(Integer idWatchlist) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    List<Security> securityList = securityJpaRepository
        .tryUpToDateHistoricalDataWhenRetryHistoryLoadGreaterThan0(user.getIdTenant(), idWatchlist);
    List<Currencypair> currencypairList = currencypairJpaRepository
        .tryUpToDateHistoricalDataWhenRetryHistoryLoadGreaterThan0(user.getIdTenant(), idWatchlist);
    return new SecuritycurrencyLists(securityList, currencypairList);
  }

  @Override
  public TenantLimit[] getSecuritiesCurrenciesWachlistLimits(Integer idWatchlist) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    TenantLimit[] tenantLimits = new TenantLimit[2];
    tenantLimits[0] = new TenantLimit(
        globalparametersJpaRepository.getMaxValueByKey(Globalparameters.GLOB_KEY_MAX_WATCHLIST_LENGTH),
        watchlistJpaRepository.countPostionsInWatchlist(user.getIdTenant(), idWatchlist).intValue(),
        Globalparameters.GLOB_KEY_MAX_WATCHLIST_LENGTH, Watchlist.class.getSimpleName());

    tenantLimits[1] = new TenantLimit(
        globalparametersJpaRepository.getMaxValueByKey(Globalparameters.GLOB_KEY_MAX_SECURITIES_CURRENCIES),
        watchlistJpaRepository.countPostionsInAllWatchlistByIdTenant(user.getIdTenant()).intValue(),
        Globalparameters.GLOB_KEY_MAX_SECURITIES_CURRENCIES, Watchlist.class.getSimpleName());

    return tenantLimits;
  }

}
