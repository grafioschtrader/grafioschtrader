package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.common.UserAccessHelper;
import grafiosch.dto.TenantLimit;
import grafiosch.entities.User;
import grafiosch.repository.BaseRepositoryImpl;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.RepositoryHelper;
import grafiosch.repository.TenantLimitsHelper;
import grafioschtrader.GlobalParamKeyDefault;
import grafioschtrader.dto.IntraHistoricalWatchlistProblem;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyLists;
import grafioschtrader.search.SecuritycurrencySearch;
import grafioschtrader.service.GlobalparametersService;

public class WatchlistJpaRepositoryImpl extends BaseRepositoryImpl<Watchlist> implements WatchlistJpaRepositoryCustom {

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
  private GlobalparametersService globalparametersService;

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
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    } else {
      var maxPositionForTenant = watchlistJpaRepository.countPostionsInAllWatchlistByIdTenant(user.getIdTenant())
          .intValue();

      if (watchlist.getWatchlistLength() + securitycurrencyLists.getLength() <= globalparametersJpaRepository
          .getMaxValueByKey(GlobalParamKeyDefault.GLOB_KEY_MAX_WATCHLIST_LENGTH)
          && maxPositionForTenant + securitycurrencyLists.getLength() <= TenantLimitsHelper.getMaxValueByKey(
              globalparametersJpaRepository.getEntityManager(),
              GlobalParamKeyDefault.GLOB_KEY_MAX_SECURITIES_CURRENCIES)) {
        securitycurrencyLists.currencypairList
            .forEach(currencypair -> watchlist.getSecuritycurrencyList().add(currencypair));
        securitycurrencyLists.securityList.forEach(security -> watchlist.getSecuritycurrencyList().add(security));
        return watchlistJpaRepository.save(watchlist);
      } else {
        throw new SecurityException(BaseConstants.LIMIT_SECURITY_BREACH);
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
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
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

  /**
   * Removes a specific instrument (security or currencypair) object directly from the instrument list of the specified
   * watchlist.
   * <p>
   * This method assumes that the provided {@code securitycurrency} object is the actual instance present in the
   * watchlist's collection, allowing removal by object reference. It also ensures that the watchlist belongs to the
   * currently authenticated tenant.
   * </p>
   *
   * @param idWatchlist      The ID of the watchlist from which the instrument is to be removed.
   * @param securitycurrency The {@link Securitycurrency} object instance to remove from the watchlist.
   * @return The updated {@link Watchlist} entity after saving the changes.
   * @throws SecurityException if the watchlist with the given ID is not found or does not belong to the current tenant.
   */
  private Watchlist removeInstrumentFromWatchlist(final Integer idWatchlist,
      final Securitycurrency<?> securitycurrency) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();

    final Watchlist watchlist = watchlistJpaRepository.findByIdWatchlistAndIdTenant(idWatchlist, user.getIdTenant());
    if (watchlist == null) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
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

  /**
   * Removes a security or currency pair from the specified watchlist and, if the current user has sufficient
   * permissions, deletes the instrument and its associated historical quotes from the database.
   * <p>
   * The method first removes the instrument from the watchlist. Then, it checks if the user has editing or deletion
   * rights for the instrument. If so, all its history quotes are deleted, followed by the deletion of the instrument
   * entity itself.
   * </p>
   * <b>Note:</b> The deletion of the instrument and its history quotes is a critical operation and depends on the
   * user's privileges. 
   *
   * @param <T>                               The type of the security or currencypair, extending
   *                                          {@link Securitycurrency}.
   * @param idWatchlist                       The ID of the watchlist from which the instrument will be removed.
   * @param idSecuritycurrency                The ID of the security or currencypair to remove and potentially delete.
   * @param securityCurrencypairJpaRepository The JPA repository corresponding to the type {@code T}, used to find and
   *                                          delete the instrument.
   * @return The updated {@link Watchlist} after the instrument removal. Returns {@code null} if the underlying call to
   *         {@code removeInstrumentFromWatchlist} returns {@code null} (e.g. if watchlist not found).
   */
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
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
  }

  @Override
  public SecuritycurrencyLists tryUpToIntradayDataWhenRetryIntraLoadGreaterThan0(Integer idWatchlist) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (UserAccessHelper.hasHigherPrivileges(user)) {
      List<Security> securityList = securityJpaRepository
          .tryUpToIntradayDataWhenRetryIntraLoadGreaterThan0(user.getIdTenant(), idWatchlist);
      List<Currencypair> currencypair = currencypairJpaRepository
          .tryUpToDateIntraDataWhenRetryIntraLoadGreaterThan0(user.getIdTenant(), idWatchlist);
      return new SecuritycurrencyLists(securityList, currencypair);
    } else {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
  }

  @Override
  public SecuritycurrencyLists tryUpToDateHistoricalDataWhenRetryHistoryLoadGreaterThan0(Integer idWatchlist) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (UserAccessHelper.hasHigherPrivileges(user)) {
      List<Security> securityList = securityJpaRepository
          .tryUpToDateHistoricalDataWhenRetryHistoryLoadGreaterThan0(user.getIdTenant(), idWatchlist);
      List<Currencypair> currencypairList = currencypairJpaRepository
          .tryUpToDateHistoricalDataWhenRetryHistoryLoadGreaterThan0(user.getIdTenant(), idWatchlist);
      return new SecuritycurrencyLists(securityList, currencypairList);
    } else {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
  }

  @Override
  public TenantLimit[] getSecuritiesCurrenciesWachlistLimits(Integer idWatchlist) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    TenantLimit[] tenantLimits = new TenantLimit[2];
    tenantLimits[0] = new TenantLimit(
        globalparametersJpaRepository.getMaxValueByKey(GlobalParamKeyDefault.GLOB_KEY_MAX_WATCHLIST_LENGTH),
        watchlistJpaRepository.countPostionsInWatchlist(user.getIdTenant(), idWatchlist).intValue(),
        GlobalParamKeyDefault.GLOB_KEY_MAX_WATCHLIST_LENGTH, Watchlist.class.getSimpleName());

    tenantLimits[1] = new TenantLimit(
        globalparametersJpaRepository.getMaxValueByKey(GlobalParamKeyDefault.GLOB_KEY_MAX_SECURITIES_CURRENCIES),
        watchlistJpaRepository.countPostionsInAllWatchlistByIdTenant(user.getIdTenant()).intValue(),
        GlobalParamKeyDefault.GLOB_KEY_MAX_SECURITIES_CURRENCIES, Watchlist.class.getSimpleName());

    return tenantLimits;
  }

  @Override
  public Watchlist addInstrumentsWithPriceDataProblems(Integer idWatchlist, IntraHistoricalWatchlistProblem ihwp) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    final Watchlist watchlist = watchlistJpaRepository.findByIdWatchlistAndIdTenant(idWatchlist, user.getIdTenant());
    if (watchlist != null && watchlist.getSecuritycurrencyList().isEmpty()
        && UserAccessHelper.hasHigherPrivileges(user)) {
      if (ihwp.addHistorical) {
        watchlistJpaRepository.addInstrumentsWithHistoricalPriceDataTrouble(idWatchlist, ihwp.daysSinceLastWork,
            globalparametersService.getMaxHistoryRetry());
      }
      if (ihwp.addIntraday) {
        watchlistJpaRepository.addInstrumentsWithIntradayPriceDataTrouble(idWatchlist, ihwp.daysSinceLastWork,
            globalparametersService.getMaxIntraRetry());
      }
    } else {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    return watchlistJpaRepository.findByIdWatchlistAndIdTenant(idWatchlist, user.getIdTenant());
  }

  @Override
  public String getDataProviderResponseForUser(Integer idSecuritycurrency, boolean isIntraday, boolean isSecurity) {
    return isSecurity ? securityJpaRepository.getDataProviderResponseForUser(idSecuritycurrency, isIntraday)
        : currencypairJpaRepository.getDataProviderResponseForUser(idSecuritycurrency, isIntraday);
  }

  @Override
  public String getDataProviderLinkForUser(Integer idSecuritycurrency, boolean isIntraday, boolean isSecurity) {
    return isSecurity ? securityJpaRepository.getDataProviderLinkForUser(idSecuritycurrency, isIntraday)
        : currencypairJpaRepository.getDataProviderLinkForUser(idSecuritycurrency, isIntraday);
  }

  @Override
  public String getDataProviderDivSplitResponseForUser(Integer idSecuritycurrency, boolean isDiv) {
    return securityJpaRepository.getDivSplitProviderResponseForUser(idSecuritycurrency, isDiv);
  }

}
