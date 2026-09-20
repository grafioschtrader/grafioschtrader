package grafioschtrader.reports;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.entities.User;
import grafiosch.repository.UDFSpecialTypeDisableUserRepository;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.dto.IMinMaxDateHistoryquote;
import grafioschtrader.dto.ISecuritycurrencyIdDateClose;
import grafioschtrader.dto.ISecuritycurrencyIdDateCloseCreateType;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.instrument.SecurityCalcService;
import grafioschtrader.reports.udfalluserfields.IUDFForEveryUser;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyGroup;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyPosition;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyUDFGroup;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyUDFGroup.IUDFEntityValues;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.IPositionCloseOnLatestPrice;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.repository.WatchlistJpaRepository;
import grafioschtrader.service.GTNetLastpriceService;
import grafioschtrader.service.GlobalparametersService;
import grafioschtrader.service.PriceFreshnessService;
import grafioschtrader.types.LastpriceOrigin;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Prepares the Data for every kind of Watchlists.
 */
@Component
public class WatchlistReport {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private PriceFreshnessService priceFreshnessService;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Autowired
  private WatchlistJpaRepository watchlistJpaRepository;

  @Autowired
  private TransactionJpaRepository transactionJpaRepository;

  @Autowired
  private SecurityCalcService securityCalcService;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;

  @Autowired
  private GTNetLastpriceService gTNetLastpriceService;

  @Autowired(required = false)
  private List<IUDFForEveryUser> uDFForEveryUser;

  @Autowired
  private UDFSpecialTypeDisableUserRepository uDFSpecialTypeDisUserRep;

  @PersistenceContext
  private EntityManager entityManager;

  /**
   * Returns the watchlist with the oldest and the youngest date of history quote. The youngest date detects a
   * historical data feed that stopped delivering, the oldest one a feed that never delivered the beginning of the
   * history, which only shows when it is held against the active from date of the security.
   */
  public SecuritycurrencyGroup getWatchlistWithoutUpdateAndMaxHistoryquote(final Integer idWatchlist)
      throws InterruptedException, ExecutionException {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();

    final CompletableFuture<List<IMinMaxDateHistoryquote>> historyquoteCF = CompletableFuture
        .supplyAsync(() -> historyquoteJpaRepository.getMinMaxDateByWatchlist(idWatchlist, user.getIdTenant()));
    final CompletableFuture<int[]> securitiesIsUsedElsewhereCF = CompletableFuture
        .supplyAsync(() -> watchlistJpaRepository.watchlistSecuritiesHasTransactionOrOtherWatchlist(idWatchlist));
    final CompletableFuture<int[]> currencypairIsUsedElsewhereCF = CompletableFuture
        .supplyAsync(() -> watchlistJpaRepository.watchlistCurrencypairsHasReferencesButThisWatchlist(idWatchlist));

    return combineSecuritycurrencyGroupWithHistoryquote(getWatchlistWithoutUpdate(idWatchlist), historyquoteCF.get(),
        securitiesIsUsedElsewhereCF.get(), currencypairIsUsedElsewhereCF.get());
  }

  /**
   * Combines a {@link SecuritycurrencyGroup} with the date range of the stored historical prices and usage information.
   *
   * @param securitycurrencyGroup          The base group of watchlist instruments.
   * @param minMaxDates                    The oldest and the youngest date of the historical prices per instrument. An
   *                                       instrument without any historical price is not contained.
   * @param securitiesIsUsedElsewhereIds   An array of security IDs that are used elsewhere (transactions, other
   *                                       watchlists).
   * @param currencypairIsUsedElsewhereIds An array of currencypair IDs that are used elsewhere.
   * @return The enriched {@link SecuritycurrencyGroup}.
   */
  private SecuritycurrencyGroup combineSecuritycurrencyGroupWithHistoryquote(
      final SecuritycurrencyGroup securitycurrencyGroup, final List<IMinMaxDateHistoryquote> minMaxDates,
      final int[] securitiesIsUsedElsewhereIds, final int[] currencypairIsUsedElsewhereIds) {
    final Map<Integer, IMinMaxDateHistoryquote> minMaxDateMap = minMaxDates.stream()
        .collect(Collectors.toMap(IMinMaxDateHistoryquote::getIdSecuritycurrency, Function.identity()));
    securitycurrencyGroup.securityPositionList.forEach(
        securitycurrencyPosition -> combineSecuritycurrencyHistoryquote(securitycurrencyPosition, minMaxDateMap));
    securitycurrencyGroup.currencypairPositionList.forEach(
        securitycurrencyPosition -> combineSecuritycurrencyHistoryquote(securitycurrencyPosition, minMaxDateMap));

    // The price feed view is read to find an instrument whose data source stopped delivering, so it needs the age of
    // the intraday timestamp. No price is substituted on this path on purpose: here the timestamp has to say when the
    // intraday connector last succeeded, not how old the shown price is.
    setStaleTradingSessions(securitycurrencyGroup.securityPositionList, securitycurrencyGroup.currencypairPositionList);

    this.markForUsedSecurityCurrencypairs(securitycurrencyGroup, securitiesIsUsedElsewhereIds,
        currencypairIsUsedElsewhereIds);
    return securitycurrencyGroup;
  }

  private <T extends Securitycurrency<T>> void combineSecuritycurrencyHistoryquote(
      final SecuritycurrencyPosition<T> securitycurrencyPosition,
      final Map<Integer, IMinMaxDateHistoryquote> minMaxDateMap) {
    final IMinMaxDateHistoryquote minMaxDate = minMaxDateMap
        .get(securitycurrencyPosition.securitycurrency.getIdSecuritycurrency());
    if (minMaxDate != null) {
      securitycurrencyPosition.youngestHistoryDate = minMaxDate.getMaxDate();
      securitycurrencyPosition.oldestHistoryDate = minMaxDate.getMinDate();
    }
  }

  /**
   * Return of the split and dividend watchlist. Retrieves a watchlist and marks securities within it if they have ever
   * had a dividend or split event. This method does not fetch the actual dividend or split records, only an indication
   * of their existence. It also marks instruments if they are referenced in transactions or other watchlists.
   *
   * @param idWatchlist The ID of the watchlist.
   * @return A {@link SecuritycurrencyGroup} where securities are marked if they have dividend/split history and usage
   *         flags.
   */
  public SecuritycurrencyGroup getWatchlistForSplitAndDividend(final Integer idWatchlist)
      throws InterruptedException, ExecutionException {

    final CompletableFuture<Set<Integer>> securitiesIdsCF = CompletableFuture
        .supplyAsync(() -> watchlistJpaRepository.hasSplitOrDividendByWatchlist(idWatchlist));
    final CompletableFuture<int[]> securitiesIsUsedElsewhereCF = CompletableFuture
        .supplyAsync(() -> watchlistJpaRepository.watchlistSecuritiesHasTransactionOrOtherWatchlist(idWatchlist));
    final CompletableFuture<int[]> currencypairIsUsedElsewhereCF = CompletableFuture
        .supplyAsync(() -> watchlistJpaRepository.watchlistCurrencypairsHasReferencesButThisWatchlist(idWatchlist));
    return combineWatchlistWithDividendSplitMark(getWatchlistWithoutUpdate(idWatchlist), securitiesIdsCF.get(),
        securitiesIsUsedElsewhereCF.get(), currencypairIsUsedElsewhereCF.get());
  }

  private SecuritycurrencyGroup combineWatchlistWithDividendSplitMark(final SecuritycurrencyGroup securitycurrencyGroup,
      Set<Integer> securitiesIds, final int[] securitiesIsUsedElsewhereIds,
      final int[] currencypairIsUsedElsewhereIds) {
    markForUsedSecurityCurrencypairs(securitycurrencyGroup, securitiesIsUsedElsewhereIds,
        currencypairIsUsedElsewhereIds);
    securitycurrencyGroup.securityPositionList.forEach(
        spl -> spl.watchlistSecurityHasEver = securitiesIds.contains(spl.securitycurrency.getIdSecuritycurrency()));
    return securitycurrencyGroup;
  }

  /**
   * Retrieves a watchlist and enriches its instruments with User Defined Fields (UDF) data relevant to the currently
   * authenticated user.
   *
   * @param idWatchlist The ID of the watchlist.
   * @return A {@link SecuritycurrencyUDFGroup} containing the watchlist instruments along with their UDF data.
   */
  public SecuritycurrencyUDFGroup getWatchlistWithUDFData(final Integer idWatchlist)
      throws InterruptedException, ExecutionException {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    final CompletableFuture<List<IUDFEntityValues>> entityValuesCF = CompletableFuture
        .supplyAsync(() -> watchlistJpaRepository.getUDFByIdWatchlistAndIdUserAndEntity(idWatchlist, user.getIdUser(),
            new String[] { Currencypair.class.getSimpleName(), Security.class.getSimpleName() }));
    return combineWatchlistWithUDF(getWatchlistWithoutUpdate(idWatchlist), entityValuesCF.get(), user);
  }

  private SecuritycurrencyUDFGroup combineWatchlistWithUDF(SecuritycurrencyGroup sg,
      List<IUDFEntityValues> uDFEntityValues, User user) {
    Map<Integer, String> udfEntityValuesMap = uDFEntityValues.stream()
        .collect(Collectors.toMap(ud -> ud.getIdSecuritycurrency(), ud -> ud.getJsonValues()));

    Set<Byte> udfDisabledList = uDFSpecialTypeDisUserRep.findByIdIdUser(user.getIdUser());
    SecuritycurrencyUDFGroup sUDFGroup = new SecuritycurrencyUDFGroup(sg.securityPositionList,
        sg.currencypairPositionList, sg.lastTimestamp, sg.idWatchlist, udfEntityValuesMap);
    uDFForEveryUser.stream().filter(u -> !udfDisabledList.contains(u.getUDFSpecialType().getValue()))
        .forEach(u -> u.addUDFForEveryUser(sUDFGroup, false));
    return sUDFGroup;
  }

  /////////////////////////////////////////////////////////////
  // Get Watchlist - Report
  /////////////////////////////////////////////////////////////

  public SecuritycurrencyGroup getWatchlistWithoutUpdate(final Integer idWatchlist) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    final CompletableFuture<int[]> watchlistSecurtiesTransactionCF = CompletableFuture
        .supplyAsync(() -> watchlistJpaRepository
            .watchlistSecuritiesHasOpenOrClosedTransactionForThisTenant(idWatchlist, user.getIdTenant()));
    SecuritycurrencyGroup securitycurrencyGroup = createWatchlistWithoutUpdate(idWatchlist, user.getIdTenant());
    return combineSecuritycurrencyGroupWithSecurtiesTransaction(securitycurrencyGroup,
        watchlistSecurtiesTransactionCF.join());
  }

  /**
   * Retrieves a watchlist and calculates period-specific performance metrics for its instruments, including daily
   * change, year-to-date (YTD) change, and change over a specified time frame. This method may trigger an update of the
   * last prices for the instruments if the cached prices are considered stale.
   *
   * @param idWatchlist   The ID of the watchlist.
   * @param idTenant      The ID of the tenant to whom the watchlist belongs.
   * @param daysTimeFrame The number of days for the custom time frame performance calculation (e.g., 30 for 30-day
   *                      performance).
   * @return A {@link SecuritycurrencyGroup} enriched with performance data for each instrument.
   * @throws SecurityException if the watchlist does not belong to the specified tenant.
   */
  @Transactional
  @Modifying
  public SecuritycurrencyGroup getWatchlistwithPeriodPerformance(final Integer idWatchlist, final Integer idTenant,
      final Integer daysTimeFrame) {

    Watchlist watchlist = watchlistJpaRepository.getReferenceById(idWatchlist);
    if (!watchlist.getIdTenant().equals(idTenant)) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }

    final Map<Integer, List<Securitysplit>> securitysplitMap = securitysplitJpaRepository
        .getSecuritysplitMapByIdWatchlist(idWatchlist);

    // Currency conversion is not used since watchlist only calculates security
    // gains in the currency of the instrument
    final DateTransactionCurrencypairMap dateCurrencyMap = null;

    final LocalDate dateTimeFrame = LocalDate.now().minusDays(daysTimeFrame);
    Tenant tenant = tenantJpaRepository.getReferenceById(watchlist.getIdTenant());

    // Atomically claim the price update by updating last_timestamp only if it is stale.
    // This avoids the "Record has changed since last read" error when concurrent requests
    // both try to update the same watchlist row via saveAndFlush.
    final LocalDateTime now = LocalDateTime.now();
    final LocalDateTime threshold = now.minusSeconds(globalparametersService.getWatchlistIntradayUpdateTimeout());
    final boolean needsPriceUpdate = watchlistJpaRepository.updateLastTimestampIfStale(now, idWatchlist, threshold) > 0;
    if (needsPriceUpdate) {
      log.info("Intraday update for {}", watchlist.getName());
    }

    // Capture final reference for lambda
    final Watchlist finalWatchlist = watchlist;
    final CompletableFuture<GTNetLastpriceService.SecurityCurrency> securityCurrencyCF = CompletableFuture
        .supplyAsync(() -> executeLastPriceUpdate(tenant, finalWatchlist, needsPriceUpdate));
    final CompletableFuture<Map<Integer, ISecuritycurrencyIdDateCloseCreateType>> historyquoteMaxDayCF = CompletableFuture
        .supplyAsync(() -> getMaxDayHistoryquotesByIdWatchlist(idWatchlist));
    final CompletableFuture<Map<Integer, ISecuritycurrencyIdDateClose>> historyquoteLastDayYearCF = CompletableFuture
        .supplyAsync(() -> getLastDayOfLastYearHistoryquotesByIdWatchlist(idWatchlist));
    final CompletableFuture<Map<Integer, ISecuritycurrencyIdDateClose>> historyquoteTimeFrameCF = CompletableFuture
        .supplyAsync(() -> getTimeFrameHistoryquotesByIdWatchlistAndDate(idWatchlist, dateTimeFrame));
    final CompletableFuture<int[]> securitiesIsUsedElsewhereCF = CompletableFuture
        .supplyAsync(() -> this.watchlistJpaRepository.watchlistSecuritiesHasTransactionOrOtherWatchlist(idWatchlist));
    final CompletableFuture<int[]> currencypairIsUsedElsewhereCF = CompletableFuture.supplyAsync(
        () -> this.watchlistJpaRepository.watchlistCurrencypairsHasReferencesButThisWatchlist(idWatchlist));
    final CompletableFuture<int[]> watchlistSecurtiesTransactionCF = CompletableFuture
        .supplyAsync(() -> this.watchlistJpaRepository
            .watchlistSecuritiesHasOpenOrClosedTransactionForThisTenant(idWatchlist, idTenant));

    // Wait for all async operations to complete and collect their results
    final GTNetLastpriceService.SecurityCurrency securityCurrency = securityCurrencyCF.join();
    final Map<Integer, ISecuritycurrencyIdDateCloseCreateType> historyquoteMaxDay = historyquoteMaxDayCF.join();
    final Map<Integer, ISecuritycurrencyIdDateClose> historyquoteLastDayYear = historyquoteLastDayYearCF.join();
    final Map<Integer, ISecuritycurrencyIdDateClose> historyquoteTimeFrame = historyquoteTimeFrameCF.join();
    final int[] securitiesIsUsedElsewhere = securitiesIsUsedElsewhereCF.join();
    final int[] currencypairIsUsedElsewhere = currencypairIsUsedElsewhereCF.join();
    final int[] watchlistSecurtiesTransaction = watchlistSecurtiesTransactionCF.join();

    // Clear persistence context to avoid stale entity conflicts after async price updates.
    // The async operations may have modified Security/Currencypair entities in separate transactions,
    // causing version mismatches when this transaction's context tries to flush.
    entityManager.clear();

    // Reload entities after clearing context since they were detached
    final Watchlist reloadedWatchlist = watchlistJpaRepository.getReferenceById(idWatchlist);
    final Tenant reloadedTenant = tenantJpaRepository.getReferenceById(idTenant);

    final SecuritycurrencyGroup securitycurrencyGroup = combineLastPriceHistoryquote(reloadedTenant, securityCurrency,
        historyquoteMaxDay, historyquoteLastDayYear, historyquoteTimeFrame, securitiesIsUsedElsewhere,
        currencypairIsUsedElsewhere, watchlistSecurtiesTransaction, reloadedWatchlist, daysTimeFrame, securitysplitMap,
        dateCurrencyMap);
    securitycurrencyGroup.idWatchlist = idWatchlist;

    return securitycurrencyGroup;
  }

  private SecuritycurrencyGroup combineSecuritycurrencyGroupWithSecurtiesTransaction(
      SecuritycurrencyGroup securitycurrencyGroup, int[] watchlistSecuritesHasTransactionIds) {
    markWatchlistSecurityHasEverTransactionTenant(watchlistSecuritesHasTransactionIds,
        securitycurrencyGroup.securityPositionList);
    return securitycurrencyGroup;
  }

  /**
   * Creates a basic {@link SecuritycurrencyGroup} for a watchlist without updating prices or adding performance data.
   *
   * @param idWatchlist The ID of the watchlist.
   * @param idTenant    The ID of the tenant.
   * @return A new {@link SecuritycurrencyGroup}.
   * @throws SecurityException if the watchlist does not belong to the tenant.
   */
  private SecuritycurrencyGroup createWatchlistWithoutUpdate(final Integer idWatchlist, final Integer idTenant) {
    final Watchlist watchlist = watchlistJpaRepository.getReferenceById(idWatchlist);

    if (!watchlist.getIdTenant().equals(idTenant)) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    final List<SecuritycurrencyPosition<Security>> securityPositionList = createSecuritycurrencyPositionList(
        watchlist.getSecuritycurrencyListByType(Security.class));
    final List<SecuritycurrencyPosition<Currencypair>> currencypairPositionList = createSecuritycurrencyPositionList(
        watchlist.getSecuritycurrencyListByType(Currencypair.class));
    return new SecuritycurrencyGroup(securityPositionList, currencypairPositionList, watchlist.getLastTimestamp(),
        watchlist.getIdWatchlist());
  }

  /**
   * Executes the last price update for instruments in a watchlist. The decision whether to update and the watchlist
   * save are done in the calling method to avoid stale entity issues with async operations in MariaDB 11.x.
   *
   * @param tenant           The tenant owning the watchlist.
   * @param watchlist        The watchlist to process.
   * @param needsPriceUpdate Whether the price update should be performed.
   * @return A {@link GTNetLastpriceService.SecurityCurrency} object containing the lists of securities and
   *         currencypairs (some potentially updated, others not).
   */
  private GTNetLastpriceService.SecurityCurrency executeLastPriceUpdate(Tenant tenant, Watchlist watchlist,
      boolean needsPriceUpdate) {
    final List<Security> securities = watchlist.getSecuritycurrencyListByType(Security.class);
    final List<Currencypair> currencypairs = watchlist.getSecuritycurrencyListByType(Currencypair.class);

    if (needsPriceUpdate) {
      List<Currencypair> currenciesNotInList = updateDependingCurrencyWhenPerformanceWatchlist(tenant, watchlist,
          currencypairs);
      return gTNetLastpriceService.updateLastpriceIncludeSupplier(securities, currencypairs, currenciesNotInList);
    } else {
      log.info("No intraday update for {} because last update was at {}", watchlist.getName(),
          watchlist.getLastTimestamp());
      return new GTNetLastpriceService.SecurityCurrency(securities, currencypairs);
    }
  }

  /**
   * For a performance watchlist, identifies and returns currency pairs required for calculations that are not already
   * present in the watchlist's own list of currency pairs.
   *
   * @param tenant                   The tenant.
   * @param watchlist                The performance watchlist.
   * @param currencypairsInWatchlist List of currency pairs already in the watchlist.
   * @return A list of additional {@link Currencypair}s needed for performance calculation, or an empty list.
   */
  private List<Currencypair> updateDependingCurrencyWhenPerformanceWatchlist(final Tenant tenant, Watchlist watchlist,
      List<Currencypair> currencypairsInWatchlist) {
    if (watchlist.getIdWatchlist().equals(tenant.getIdWatchlistPerformance())) {
      List<Currencypair> currenciesNotInList = currencypairJpaRepository
          .getAllCurrencypairsForTenantByTenant(tenant.getIdTenant());
      currenciesNotInList.removeAll(currencypairsInWatchlist);
      return currenciesNotInList;
    }
    return Collections.emptyList();
  }

  private Map<Integer, ISecuritycurrencyIdDateClose> getLastDayOfLastYearHistoryquotesByIdWatchlist(
      final Integer idWatchlist) {
    final LocalDate lastDayLastYear = LocalDate.of(LocalDate.now().minusYears(1).getYear(), Month.DECEMBER, 31);
    return getTimeFrameHistoryquotesByIdWatchlistAndDate(idWatchlist, lastDayLastYear);
  }

  /**
   * The youngest history quote of every instrument of the watchlist that does not lie in the future. Linear gap filling
   * may reach beyond the current day, so an unbounded query would hand out a price for a day that has not happened yet.
   */
  private Map<Integer, ISecuritycurrencyIdDateCloseCreateType> getMaxDayHistoryquotesByIdWatchlist(
      final Integer idWatchlist) {
    final List<ISecuritycurrencyIdDateCloseCreateType> historyquotes = this.historyquoteJpaRepository
        .getYoungestHistorquoteForSecuritycurrencyByWatchlist(idWatchlist, LocalDate.now());
    return historyquotes.stream()
        .collect(Collectors.toMap(ISecuritycurrencyIdDateCloseCreateType::getIdSecuritycurrency, Function.identity()));
  }

  private Map<Integer, ISecuritycurrencyIdDateClose> getTimeFrameHistoryquotesByIdWatchlistAndDate(
      final Integer idWatchlist, final LocalDate localDateTimeFrame) {
    final List<ISecuritycurrencyIdDateClose> historyquotes = this.historyquoteJpaRepository
        .getCertainOrOlderDayInHistorquoteForSecuritycurrencyByWatchlist(idWatchlist, localDateTimeFrame);
    return historyquotes.stream()
        .collect(Collectors.toMap(ISecuritycurrencyIdDateClose::getIdSecuritycurrency, Function.identity()));
  }

  /**
   * Combines last price data and various historical price points to calculate performance metrics for instruments in a
   * watchlist.
   *
   * @param <S>                                 Type of the instrument.
   * @param tenant                              The tenant.
   * @param securityCurrency                    Contains lists of securities and currencypairs with their latest prices.
   * @param historyquoteMaxDateMap              Map of instrument ID to its most recent historical quote.
   * @param historyquoteLastDayPrevYear         Map of instrument ID to its historical quote from the end of the
   *                                            previous year.
   * @param historyquoteTimeFrame               Map of instrument ID to its historical quote for the specified time
   *                                            frame start.
   * @param securitiesIsUsedElsewhereIds        Array of security IDs used elsewhere.
   * @param currencypairIsUsedElsewhereIds      Array of currencypair IDs used elsewhere.
   * @param watchlistSecuritesHasTransactionIds Array of security IDs in the watchlist that have transactions.
   * @param watchlist                           The watchlist.
   * @param daysTimeFrame                       The number of days for the custom time frame.
   * @param securitysplitMap                    Map of security ID to its splits.
   * @param dateCurrencyMap                     Map for currency conversion (currently unused for watchlist
   *                                            performance).
   * @return An enriched {@link SecuritycurrencyGroup} with performance data.
   */
  private <S extends Securitycurrency<?>> SecuritycurrencyGroup combineLastPriceHistoryquote(final Tenant tenant,
      final GTNetLastpriceService.SecurityCurrency securityCurrency,
      final Map<Integer, ISecuritycurrencyIdDateCloseCreateType> historyquoteMaxDateMap,
      final Map<Integer, ISecuritycurrencyIdDateClose> historyquoteLastDayPrevYear,
      final Map<Integer, ISecuritycurrencyIdDateClose> historyquoteTimeFrame, final int[] securitiesIsUsedElsewhereIds,
      final int[] currencypairIsUsedElsewhereIds, final int[] watchlistSecuritesHasTransactionIds,
      final Watchlist watchlist, final Integer daysTimeFrame, final Map<Integer, List<Securitysplit>> securitysplitMap,
      final DateTransactionCurrencypairMap dateCurrencyMap) {

    final List<SecuritycurrencyPosition<Security>> securityPositions = setOpenPositions(tenant, watchlist,
        setDailyChangeAndTimeFrameChange(securityCurrency.securities, historyquoteMaxDateMap,
            historyquoteLastDayPrevYear, historyquoteTimeFrame, daysTimeFrame),
        securitysplitMap, dateCurrencyMap);
    final List<SecuritycurrencyPosition<Currencypair>> currencypairPositions = setDailyChangeAndTimeFrameChange(
        securityCurrency.currencypairs, historyquoteMaxDateMap, historyquoteLastDayPrevYear, historyquoteTimeFrame,
        daysTimeFrame);
    setStaleTradingSessions(securityPositions, currencypairPositions);

    final SecuritycurrencyGroup securitycurrencyGroup = new SecuritycurrencyGroup(securityPositions,
        currencypairPositions, watchlist.getLastTimestamp(), watchlist.getIdWatchlist());

    markForUsedSecurityCurrencypairs(securitycurrencyGroup, securitiesIsUsedElsewhereIds,
        currencypairIsUsedElsewhereIds);
    markWatchlistSecurityHasEverTransactionTenant(watchlistSecuritesHasTransactionIds,
        securitycurrencyGroup.securityPositionList);

    return securitycurrencyGroup;
  }

  private void markForUsedSecurityCurrencypairs(final SecuritycurrencyGroup securitycurrencyGroup,
      final int[] securitiesIsUsedElsewhereIds, final int[] currencypairIsUsedElsewhereIds) {
    markSecurityCurrencypairsIsUsedElsewhere(securitiesIsUsedElsewhereIds, securitycurrencyGroup.securityPositionList);
    markSecurityCurrencypairsIsUsedElsewhere(currencypairIsUsedElsewhereIds,
        securitycurrencyGroup.currencypairPositionList);
  }

  private <T extends Securitycurrency<T>> void markSecurityCurrencypairsIsUsedElsewhere(
      final int[] securitiesCurrencypairsIsUsedElsewhereIds,
      final List<SecuritycurrencyPosition<T>> securitycurrencyPositionList) {
    securitycurrencyPositionList.forEach(securitycurrencyPosition -> securitycurrencyPosition.isUsedElsewhere = Arrays
        .binarySearch(securitiesCurrencypairsIsUsedElsewhereIds,
            securitycurrencyPosition.securitycurrency.getIdSecuritycurrency().intValue()) >= 0);
  }

  private <T extends Securitycurrency<T>> void markWatchlistSecurityHasEverTransactionTenant(
      final int[] watchlistSecuritesHasTransactionIds,
      final List<SecuritycurrencyPosition<T>> securitycurrencyPositionList) {
    securitycurrencyPositionList
        .forEach(securitycurrencyPosition -> securitycurrencyPosition.watchlistSecurityHasEver = Arrays.binarySearch(
            watchlistSecuritesHasTransactionIds,
            securitycurrencyPosition.securitycurrency.getIdSecuritycurrency().intValue()) >= 0);
  }

  /**
   * Calculates and sets daily, YTD, and custom time frame percentage changes for a list of instruments.
   *
   * @param <S>                         Type of instrument.
   * @param securitycurrenyList         The list of instruments to process.
   * @param historyquoteMaxDateMap      Map of most recent historical quotes.
   * @param historyquoteLastDayPrevYear Map of end-of-previous-year historical quotes.
   * @param historyquoteTimeFrame       Map of historical quotes for the start of the custom time frame.
   * @param daysTimeFrame               Number of days for the custom time frame.
   * @return A list of {@link SecuritycurrencyPosition} objects with calculated performance data.
   */
  private <S extends Securitycurrency<S>> List<SecuritycurrencyPosition<S>> setDailyChangeAndTimeFrameChange(
      final List<S> securitycurrenyList,
      final Map<Integer, ISecuritycurrencyIdDateCloseCreateType> historyquoteMaxDateMap,
      final Map<Integer, ISecuritycurrencyIdDateClose> historyquoteLastDayPrevYear,
      final Map<Integer, ISecuritycurrencyIdDateClose> historyquoteTimeFrame, final Integer daysTimeFrame) {
    final List<SecuritycurrencyPosition<S>> securitycurrencyPositionList = createSecuritycurrencyPositionList(
        securitycurrenyList);
    // The substitution has to happen first, every return below is measured against the last price.
    securitycurrencyPositionList.forEach(position -> replaceLastPriceByHistoryquote(historyquoteMaxDateMap, position));
    securitycurrencyPositionList.stream()
        .filter(position -> position.lastpriceOrigin == LastpriceOrigin.INTRADAY
            && position.securitycurrency.getSChangePercentage() == null)
        .forEach(position -> setDailyChangeByUsingHistoryquote(historyquoteMaxDateMap, position));
    securitycurrencyPositionList.stream()
        .forEach(securitycurrency -> setYtdGainLoss(historyquoteLastDayPrevYear, securitycurrency));
    securitycurrencyPositionList.stream()
        .forEach(securitycurrency -> setTimeFrameGainLoss(historyquoteTimeFrame, securitycurrency, daysTimeFrame));
    return securitycurrencyPositionList;
  }

  //@formatter:off
  /**
   * Puts the newest historical closing price in the place of the last price whenever it is the younger of the two, and
   * records where the shown price then comes from.
   * <p>
   * An instrument whose intraday feed fell silent - a bankruptcy, a delisting, a connector that was switched off - keeps
   * the last price it ever received. That price is not what the instrument is worth, and the linear gap filling exists
   * precisely to carry such an instrument forward in the historical data. Taking the closing price makes the watchlist
   * agree with the valuation of the portfolio, which already prefers the younger history quote in
   * {@code SecuritycurrencyService.calcGainLossBasedOnDateOrNewestPrice}.
   * </p>
   * <p>
   * Everything describing a running session is dropped with it. Open, high, low, previous close, daily change and
   * volume belong to a trading day the instrument no longer has, and a closing price carries none of them.
   * </p>
   *
   * @param <S>                    type of instrument
   * @param historyquoteMaxDateMap youngest history quote per instrument, already bounded at the current day
   * @param position               the position whose price may be replaced
   */
  //@formatter:on
  private <S extends Securitycurrency<S>> void replaceLastPriceByHistoryquote(
      final Map<Integer, ISecuritycurrencyIdDateCloseCreateType> historyquoteMaxDateMap,
      final SecuritycurrencyPosition<S> position) {
    final Securitycurrency<S> securitycurrency = position.securitycurrency;
    final ISecuritycurrencyIdDateCloseCreateType historyquote = historyquoteMaxDateMap
        .get(securitycurrency.getIdSecuritycurrency());
    if (historyquote == null) {
      return;
    }
    position.youngestHistoryDate = historyquote.getDate();
    final LocalDate lastPriceDate = securitycurrency.getSTimestamp() == null ? null
        : securitycurrency.getSTimestamp().toLocalDate();
    if (securitycurrency.getSLast() != null && lastPriceDate != null
        && !historyquote.getDate().isAfter(lastPriceDate)) {
      return;
    }
    securitycurrency.setSLast(historyquote.getClose());
    // The timestamp has to follow the price it belongs to, otherwise the age shown next to a price is not its own.
    securitycurrency.setSTimestamp(historyquote.getDate().atStartOfDay());
    position.lastpriceOrigin = LastpriceOrigin.ofHistoryquoteCreateType(historyquote.getCreateType());
    securitycurrency.setSOpen(null);
    securitycurrency.setSHigh(null);
    securitycurrency.setSLow(null);
    securitycurrency.setSPrevClose(null);
    securitycurrency.setSChangePercentage(null);
    if (securitycurrency instanceof Security security) {
      security.setSVolume(null);
    }
  }

  /**
   * Determines for every position how many trading sessions of its own exchange went by without the shown price being
   * renewed. Securities are measured against the calendar of their stock exchange, currency pairs against the worldwide
   * calendar, because a foreign exchange rate belongs to no exchange.
   *
   * @param securityPositions     the securities of the watchlist
   * @param currencypairPositions the currency pairs of the watchlist
   */
  private void setStaleTradingSessions(final List<SecuritycurrencyPosition<Security>> securityPositions,
      final List<SecuritycurrencyPosition<Currencypair>> currencypairPositions) {
    final LocalDate today = LocalDate.now();
    final Set<Integer> idsStockexchange = new HashSet<>();
    securityPositions
        .forEach(position -> idsStockexchange.add(position.securitycurrency.getStockexchange().getIdStockexchange()));
    final Map<Integer, List<LocalDate>> sessionsByStockexchange = priceFreshnessService.loadSessions(idsStockexchange,
        today);
    securityPositions.forEach(position -> position.staleTradingSessions = priceFreshnessService.sessionsBehind(
        sessionsByStockexchange.get(position.securitycurrency.getStockexchange().getIdStockexchange()),
        priceDate(position), today));
    if (!currencypairPositions.isEmpty()) {
      final List<LocalDate> globalSessions = priceFreshnessService.loadGlobalSessions(today);
      currencypairPositions.forEach(position -> position.staleTradingSessions = priceFreshnessService
          .sessionsBehind(globalSessions, priceDate(position), today));
    }
  }

  private <S extends Securitycurrency<S>> LocalDate priceDate(final SecuritycurrencyPosition<S> position) {
    return position.securitycurrency.getSTimestamp() == null ? null
        : position.securitycurrency.getSTimestamp().toLocalDate();
  }

  private List<SecuritycurrencyPosition<Security>> setOpenPositions(final Tenant tenant, final Watchlist watchlist,
      final List<SecuritycurrencyPosition<Security>> securitycurrencyPositions,
      final Map<Integer, List<Securitysplit>> securitysplitMap, final DateTransactionCurrencypairMap dateCurrencyMap) {

    final List<Transaction> transactions = this.transactionJpaRepository.findByIdWatchlist(watchlist.getIdWatchlist());

    final boolean excludeDivTax = tenant.isExcludeDivTax();
    final Map<Security, SecurityPositionSummary> summarySecurityMap = new HashMap<>();

    // Calculate all positions closed or open
    transactions.forEach(transaction -> securityCalcService.calcSingleSecurityTransaction(transaction,
        summarySecurityMap, securitysplitMap, excludeDivTax, dateCurrencyMap));
    this.calcOpenSecurityPositons(securitycurrencyPositions, summarySecurityMap, securitysplitMap, dateCurrencyMap);
    return securitycurrencyPositions;
  }

  /**
   * Calculates the current value and gain/loss for open security positions based on their latest prices.
   *
   * @param securitycurrencyPositions List of security positions from the watchlist.
   * @param summarySecurityMap        Map containing summarized transaction data for securities.
   * @param securitysplitMap          Map of security splits.
   * @param dateCurrencyMap           Map for currency conversion (currently unused).
   */
  private void calcOpenSecurityPositons(final List<SecuritycurrencyPosition<Security>> securitycurrencyPositions,
      final Map<Security, SecurityPositionSummary> summarySecurityMap,
      final Map<Integer, List<Securitysplit>> securitysplitMap, final DateTransactionCurrencypairMap dateCurrencyMap) {

    final Map<SecurityPositionSummary, SecuritycurrencyPosition<Security>> securitycurrencyPositionMap = new HashMap<>();
    final List<SecurityPositionSummary> securityPostionSummaryList = new ArrayList<>();
    securitycurrencyPositions.forEach(securitycurrencyPosition -> {
      final SecurityPositionSummary securityPositionSummary = summarySecurityMap
          .get(securitycurrencyPosition.securitycurrency);
      if (securityPositionSummary != null && securityPositionSummary.units != 0) {
        securitycurrencyPositionMap.put(securityPositionSummary, securitycurrencyPosition);
        securityPostionSummaryList.add(securityPositionSummary);

      }
    });
    if (!securityPostionSummaryList.isEmpty()) {
      securityJpaRepository.calcGainLossBasedOnDateOrNewestPrice(securityPostionSummaryList,
          new IPositionCloseOnLatestPrice<Security, SecurityPositionSummary>() {

            @Override
            public void calculatePositionClose(final SecurityPositionSummary securityPositionSummary,
                final Double lastPrice) {
              final SecuritycurrencyPosition<Security> securitycurrencyPosition = securitycurrencyPositionMap
                  .get(securityPositionSummary);
              securityPositionSummary.reCalculateOpenPosition = true;
              securityCalcService.createHypotheticalSellTransaction(securityPositionSummary, lastPrice,
                  securitysplitMap, dateCurrencyMap, null);
              securitycurrencyPosition.valueSecurity = securityPositionSummary.getValueSecurity();
              securitycurrencyPosition.units = securityPositionSummary.getUnits();
              securitycurrencyPosition.positionGainLossPercentage = securityPositionSummary
                  .getPositionGainLossPercentage();
            }
          }, LocalDate.now());
    }
  }

  @SuppressWarnings("unchecked")
  private <S extends Securitycurrency<S>> List<SecuritycurrencyPosition<S>> createSecuritycurrencyPositionList(
      final List<S> securitycurrencyList) {
    final List<SecuritycurrencyPosition<S>> securityPositionList = new ArrayList<>();
    securitycurrencyList.stream().forEach(securitycurrency -> {
      final SecuritycurrencyPosition<S> securitycurrencyPosition = new SecuritycurrencyPosition<>(securitycurrency);
      if (securitycurrency instanceof Security) {
        this.securityJpaRepository
            .setSecuritycurrencyHistoricalDownloadLink((SecuritycurrencyPosition<Security>) securitycurrencyPosition);
        this.securityJpaRepository
            .setSecuritycurrencyIntradayDownloadLink((SecuritycurrencyPosition<Security>) securitycurrencyPosition);
        this.securityJpaRepository
            .setDividendDownloadLink((SecuritycurrencyPosition<Security>) securitycurrencyPosition);
        this.securityJpaRepository.setSplitDownloadLink((SecuritycurrencyPosition<Security>) securitycurrencyPosition);
      } else {
        this.currencypairJpaRepository.setSecuritycurrencyHistoricalDownloadLink(
            (SecuritycurrencyPosition<Currencypair>) securitycurrencyPosition);
        this.currencypairJpaRepository
            .setSecuritycurrencyIntradayDownloadLink((SecuritycurrencyPosition<Currencypair>) securitycurrencyPosition);
      }
      securityPositionList.add(securitycurrencyPosition);

    });
    return securityPositionList;
  }

  /**
   * Sets the daily percentage change for an instrument using its last price and the previous day's close from
   * historical data.
   *
   * @param <S>                      Type of instrument.
   * @param historyquoteMaxDateMap   Map of instrument ID to its most recent historical quote.
   * @param securitycurrencyPosition The position to update.
   */
  private <S extends Securitycurrency<S>> void setDailyChangeByUsingHistoryquote(
      final Map<Integer, ISecuritycurrencyIdDateCloseCreateType> historyquoteMaxDateMap,
      final SecuritycurrencyPosition<S> securitycurrencyPosition) {
    final ISecuritycurrencyIdDateClose historyquote = historyquoteMaxDateMap
        .get(securitycurrencyPosition.securitycurrency.getIdSecuritycurrency());
    if (historyquote != null && securitycurrencyPosition.securitycurrency.getSLast() != null) {
      securitycurrencyPosition.securitycurrency.setSPrevClose(historyquote.getClose());
      securitycurrencyPosition.securitycurrency
          .setSChangePercentage((securitycurrencyPosition.securitycurrency.getSLast() - historyquote.getClose())
              / historyquote.getClose() * 100);
    }
  }

  /**
   * Calculates and sets the Year-to-Date (YTD) percentage change for an instrument.
   *
   * @param <S>                      Type of instrument.
   * @param historyquoteTimeFrame    Map of instrument ID to its historical quote from the end of the previous year.
   * @param securitycurrencyPosition The position to update.
   */
  private <S extends Securitycurrency<S>> void setYtdGainLoss(
      final Map<Integer, ISecuritycurrencyIdDateClose> historyquoteTimeFrame,
      final SecuritycurrencyPosition<S> securitycurrencyPosition) {
    final ISecuritycurrencyIdDateClose historyquote = historyquoteTimeFrame
        .get(securitycurrencyPosition.securitycurrency.getIdSecuritycurrency());
    if (historyquote != null && securitycurrencyPosition.securitycurrency.getSLast() != null) {
      final double histroyClose = historyquote.getClose();
      securitycurrencyPosition.ytdChangePercentage = DataBusinessHelper
          .roundPercentage((securitycurrencyPosition.securitycurrency.getSLast() - histroyClose) / histroyClose * 100);
    }
  }

  /**
   * Calculates and sets the percentage change over a custom time frame and its annualized equivalent.
   *
   * @param <S>                        Type of instrument.
   * @param historyquoteTimeFrameStart Map of instrument ID to its historical quote from the start of the time frame.
   * @param securitycurrencyPosition   The position to update.
   * @param daysTimeFrame              The number of days in the custom time frame.
   */
  private <S extends Securitycurrency<S>> void setTimeFrameGainLoss(
      final Map<Integer, ISecuritycurrencyIdDateClose> historyquoteLastDayPrevYear,
      final SecuritycurrencyPosition<S> securitycurrencyPosition, final Integer daysTimeFrame) {
    final ISecuritycurrencyIdDateClose historyquote = historyquoteLastDayPrevYear
        .get(securitycurrencyPosition.securitycurrency.getIdSecuritycurrency());
    if (historyquote != null && securitycurrencyPosition.securitycurrency.getSLast() != null) {
      final double histroyClose = historyquote.getClose();
      final int years = daysTimeFrame / 365;
      securitycurrencyPosition.timeFrameChangePercentage = DataBusinessHelper
          .roundPercentage((securitycurrencyPosition.securitycurrency.getSLast() - histroyClose) / histroyClose * 100);
      if (years >= 1) {
        securitycurrencyPosition.timeFrameAnnualChangePercentage = DataBusinessHelper.roundPercentage(
            (Math.pow(securitycurrencyPosition.timeFrameChangePercentage / 100 + 1, 1.0 / years) - 1.0) * 100);
      }
    }
  }

}
