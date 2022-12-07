package grafioschtrader.reports;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
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

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataHelper;
import grafioschtrader.dto.ISecuritycurrencyIdDateClose;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitycurrency;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.entities.User;
import grafioschtrader.entities.Watchlist;
import grafioschtrader.instrument.SecurityCalcService;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyGroup;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyPosition;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.GlobalparametersJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.IPositionCloseOnLatestPrice;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecuritysplitJpaRepository;
import grafioschtrader.repository.TenantJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.repository.WatchlistJpaRepository;
import grafioschtrader.service.GTNetLastpriceService;

/**
 * Prepares the Data for every kind of Watchlists.
 *
 * @author Hugo Graf
 *
 */
@Component
public class WatchlistReport {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

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
  
  
  /**
   * Returns the watchlist with the youngest date of history quote. This should
   * help to detect non working historical data feeds.
   *
   * @param idWatchlist
   * @return
   * @throws InterruptedException
   * @throws ExecutionException
   */
  public SecuritycurrencyGroup getWatchlistWithoutUpdateAndMaxHistoryquote(final Integer idWatchlist)
      throws InterruptedException, ExecutionException {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();

    final CompletableFuture<List<Historyquote>> historyquoteCF = CompletableFuture
        .supplyAsync(() -> historyquoteJpaRepository
            .getYoungestFeedHistorquoteForSecuritycurrencyByWatchlist(idWatchlist, user.getIdTenant()));
    final CompletableFuture<int[]> securitiesIsUsedElsewhereCF = CompletableFuture
        .supplyAsync(() -> watchlistJpaRepository.watchlistSecuritiesHasTransactionOrOtherWatchlist(idWatchlist));
    final CompletableFuture<int[]> currencypairIsUsedElsewhereCF = CompletableFuture
        .supplyAsync(() -> watchlistJpaRepository.watchlistCurrencypairsHasReferencesButThisWatchlist(idWatchlist));

    return combineSecuritycurrencyGroupWithHistoryquote(getWatchlistWithoutUpdate(idWatchlist), historyquoteCF.get(),
        securitiesIsUsedElsewhereCF.get(), currencypairIsUsedElsewhereCF.get());
  }

  private SecuritycurrencyGroup combineSecuritycurrencyGroupWithHistoryquote(
      final SecuritycurrencyGroup securitycurrencyGroup, final List<Historyquote> historyquotes,
      final int[] securitiesIsUsedElsewhereIds, final int[] currencypairIsUsedElsewhereIds) {
    final Comparator<Historyquote> historyquoteComparator = (h1, h2) -> h1.getIdSecuritycurrency()
        .compareTo(h2.getIdSecuritycurrency());
    final Historyquote searchHistoryquote = new Historyquote();
    securitycurrencyGroup.securityPositionList
        .forEach(securitycurrencyPosition -> combineSecuritycurrencyHistoryquote(securitycurrencyPosition,
            searchHistoryquote, historyquotes, historyquoteComparator));
    securitycurrencyGroup.currencypairPositionList
        .forEach(securitycurrencyPosition -> combineSecuritycurrencyHistoryquote(securitycurrencyPosition,
            searchHistoryquote, historyquotes, historyquoteComparator));

    this.markForUsedSecurityCurrencypairs(securitycurrencyGroup, securitiesIsUsedElsewhereIds,
        currencypairIsUsedElsewhereIds);
    return securitycurrencyGroup;
  }

  private <T extends Securitycurrency<T>> void combineSecuritycurrencyHistoryquote(
      final SecuritycurrencyPosition<T> securitycurrencyPosition, final Historyquote searchHistoryquote,
      final List<Historyquote> historyquotes, final Comparator<Historyquote> historyquoteComparator) {
    searchHistoryquote.setIdSecuritycurrency(securitycurrencyPosition.securitycurrency.getIdSecuritycurrency());
    final int index = Collections.binarySearch(historyquotes, searchHistoryquote, historyquoteComparator);
    if (index >= 0) {
      securitycurrencyPosition.youngestHistoryDate = historyquotes.get(index).getDate();
    }
  }

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

  @Transactional
  @Modifying
  public SecuritycurrencyGroup getWatchlistwithPeriodPerformance(final Integer idWatchlist, final Integer idTenant,
      final Integer daysTimeFrame) {

    final Watchlist watchlist = watchlistJpaRepository.getReferenceById(idWatchlist);
    if (!watchlist.getIdTenant().equals(idTenant)) {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }

    final Map<Integer, List<Securitysplit>> securitysplitMap = securitysplitJpaRepository
        .getSecuritysplitMapByIdWatchlist(idWatchlist);

    // Currency conversion is not used since watchlist only calculates security
    // gains in the currency of the instrument
    final DateTransactionCurrencypairMap dateCurrencyMap = null;

    final LocalDate dateTimeFrame = LocalDate.now().minusDays(daysTimeFrame);
    Tenant tenant = tenantJpaRepository.getReferenceById(watchlist.getIdTenant());
    final CompletableFuture<GTNetLastpriceService.SecurityCurrency> securityCurrencyCF = CompletableFuture
        .supplyAsync(() -> updateLastPrice(tenant, watchlist));
    final CompletableFuture<Map<Integer, ISecuritycurrencyIdDateClose>> historyquoteMaxDayCF = CompletableFuture
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

    final SecuritycurrencyGroup securitycurrencyGroup = combineLastPriceHistoryquote(tenant, securityCurrencyCF.join(),
        historyquoteMaxDayCF.join(), historyquoteLastDayYearCF.join(), historyquoteTimeFrameCF.join(),
        securitiesIsUsedElsewhereCF.join(), currencypairIsUsedElsewhereCF.join(),
        watchlistSecurtiesTransactionCF.join(), watchlist, daysTimeFrame, securitysplitMap, dateCurrencyMap);
    securitycurrencyGroup.idWatchlist = idWatchlist;

    return securitycurrencyGroup;
  }

  private SecuritycurrencyGroup combineSecuritycurrencyGroupWithSecurtiesTransaction(
      SecuritycurrencyGroup securitycurrencyGroup, int[] watchlistSecuritesHasTransactionIds) {
    markWatchlistSecurityHasEverTransactionTenant(watchlistSecuritesHasTransactionIds,
        securitycurrencyGroup.securityPositionList);
    return securitycurrencyGroup;
  }

  private SecuritycurrencyGroup createWatchlistWithoutUpdate(final Integer idWatchlist, final Integer idTenant) {
    final Watchlist watchlist = watchlistJpaRepository.getReferenceById(idWatchlist);

    if (!watchlist.getIdTenant().equals(idTenant)) {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }
    final List<SecuritycurrencyPosition<Security>> securityPositionList = createSecuritycurrencyPositionList(
        watchlist.getSecuritycurrencyListByType(Security.class));
    final List<SecuritycurrencyPosition<Currencypair>> currencypairPositionList = createSecuritycurrencyPositionList(
        watchlist.getSecuritycurrencyListByType(Currencypair.class));
    return new SecuritycurrencyGroup(securityPositionList, currencypairPositionList, watchlist.getLastTimestamp(),
        watchlist.getIdWatchlist());
  }

  
  private GTNetLastpriceService.SecurityCurrency updateLastPrice(Tenant tenant, Watchlist watchlist) {
    final List<Security> securities = watchlist.getSecuritycurrencyListByType(Security.class);
    final List<Currencypair> currencypairs = watchlist.getSecuritycurrencyListByType(Currencypair.class);

    final Date timeframe = new Date(
        System.currentTimeMillis() - 1000 * globalparametersJpaRepository.getWatchlistIntradayUpdateTimeout());
    if (watchlist.getLastTimestamp() == null || timeframe.after(watchlist.getLastTimestamp())) {
      watchlist.setLastTimestamp(new Date(System.currentTimeMillis()));
      watchlist = watchlistJpaRepository.save(watchlist);
      log.info("Intraday update for {}", watchlist.getName());
      List<Currencypair> currenciesNotInList = updateDependingCurrencyWhenPerformanceWatchlist(tenant, watchlist, currencypairs);
      return gTNetLastpriceService.updateLastpriceIncludeSupplier(watchlist.getSecuritycurrencyListByType(Security.class),
          watchlist.getSecuritycurrencyListByType(Currencypair.class), currenciesNotInList);
    
    } else {
      log.info("No intraday update for {} because last update was at {} and is not after {}", watchlist.getName(),
          watchlist.getLastTimestamp(), timeframe);
      return new GTNetLastpriceService.SecurityCurrency(securities, currencypairs);
    }
  }

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

  private Map<Integer, ISecuritycurrencyIdDateClose> getMaxDayHistoryquotesByIdWatchlist(final Integer idWatchlist) {
    final List<ISecuritycurrencyIdDateClose> historyquotes = this.historyquoteJpaRepository
        .getYoungestHistorquoteForSecuritycurrencyByWatchlist(idWatchlist);
    return historyquotes.stream()
        .collect(Collectors.toMap(ISecuritycurrencyIdDateClose::getIdSecuritycurrency, Function.identity()));
  }

  private Map<Integer, ISecuritycurrencyIdDateClose> getTimeFrameHistoryquotesByIdWatchlistAndDate(
      final Integer idWatchlist, final LocalDate localDateTimeFrame) {
    final Date date = Date.from(localDateTimeFrame.atStartOfDay(ZoneId.systemDefault()).toInstant());
    final List<ISecuritycurrencyIdDateClose> historyquotes = this.historyquoteJpaRepository
        .getCertainOrOlderDayInHistorquoteForSecuritycurrencyByWatchlist(idWatchlist, date);
    return historyquotes.stream()
        .collect(Collectors.toMap(ISecuritycurrencyIdDateClose::getIdSecuritycurrency, Function.identity()));
  }

  private <S extends Securitycurrency<?>> SecuritycurrencyGroup combineLastPriceHistoryquote(final Tenant tenant,
      final GTNetLastpriceService.SecurityCurrency securityCurrency, final Map<Integer, ISecuritycurrencyIdDateClose> historyquoteMaxDateMap,
      final Map<Integer, ISecuritycurrencyIdDateClose> historyquoteLastDayPrevYear,
      final Map<Integer, ISecuritycurrencyIdDateClose> historyquoteTimeFrame, final int[] securitiesIsUsedElsewhereIds,
      final int[] currencypairIsUsedElsewhereIds, final int[] watchlistSecuritesHasTransactionIds,
      final Watchlist watchlist, final Integer daysTimeFrame, final Map<Integer, List<Securitysplit>> securitysplitMap,
      final DateTransactionCurrencypairMap dateCurrencyMap) {

    final SecuritycurrencyGroup securitycurrencyGroup = new SecuritycurrencyGroup(
        setOpenPositions(tenant, watchlist,
            setDailyChangeAndTimeFrameChange(securityCurrency.securities, historyquoteMaxDateMap,
                historyquoteLastDayPrevYear, historyquoteTimeFrame, daysTimeFrame),
            securitysplitMap, dateCurrencyMap),
        setDailyChangeAndTimeFrameChange(securityCurrency.currencypairs, historyquoteMaxDateMap,
            historyquoteLastDayPrevYear, historyquoteTimeFrame, daysTimeFrame),
        watchlist.getLastTimestamp(), watchlist.getIdWatchlist());

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

  private <S extends Securitycurrency<S>> List<SecuritycurrencyPosition<S>> setDailyChangeAndTimeFrameChange(
      final List<S> securitycurrenyList, final Map<Integer, ISecuritycurrencyIdDateClose> historyquoteMaxDateMap,
      final Map<Integer, ISecuritycurrencyIdDateClose> historyquoteLastDayPrevYear,
      final Map<Integer, ISecuritycurrencyIdDateClose> historyquoteTimeFrame, final Integer daysTimeFrame) {
    final List<SecuritycurrencyPosition<S>> securitycurrencyPositionList = createSecuritycurrencyPositionList(
        securitycurrenyList);
    securitycurrencyPositionList.stream()
        .filter(securitycurrency -> securitycurrency.securitycurrency.getSChangePercentage() == null)
        .forEach(securitycurrency -> setDailyChangeByUsingHistoryquote(historyquoteMaxDateMap, securitycurrency));
    securitycurrencyPositionList.stream()
        .forEach(securitycurrency -> setYtdGainLoss(historyquoteLastDayPrevYear, securitycurrency));
    securitycurrencyPositionList.stream()
        .forEach(securitycurrency -> setTimeFrameGainLoss(historyquoteTimeFrame, securitycurrency, daysTimeFrame));
    return securitycurrencyPositionList;
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
          }, new Date());
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

  private <S extends Securitycurrency<S>> void setDailyChangeByUsingHistoryquote(
      final Map<Integer, ISecuritycurrencyIdDateClose> historyquoteMaxDateMap,
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

  private <S extends Securitycurrency<S>> void setYtdGainLoss(
      final Map<Integer, ISecuritycurrencyIdDateClose> historyquoteTimeFrame,
      final SecuritycurrencyPosition<S> securitycurrencyPosition) {
    final ISecuritycurrencyIdDateClose historyquote = historyquoteTimeFrame
        .get(securitycurrencyPosition.securitycurrency.getIdSecuritycurrency());
    if (historyquote != null && securitycurrencyPosition.securitycurrency.getSLast() != null) {
      final double histroyClose = historyquote.getClose();
      securitycurrencyPosition.ytdChangePercentage = DataHelper
          .roundStandard((securitycurrencyPosition.securitycurrency.getSLast() - histroyClose) / histroyClose * 100);
    }
  }

  private <S extends Securitycurrency<S>> void setTimeFrameGainLoss(
      final Map<Integer, ISecuritycurrencyIdDateClose> historyquoteLastDayPrevYear,
      final SecuritycurrencyPosition<S> securitycurrencyPosition, final Integer daysTimeFrame) {
    final ISecuritycurrencyIdDateClose historyquote = historyquoteLastDayPrevYear
        .get(securitycurrencyPosition.securitycurrency.getIdSecuritycurrency());
    if (historyquote != null && securitycurrencyPosition.securitycurrency.getSLast() != null) {
      final double histroyClose = historyquote.getClose();
      final int years = daysTimeFrame / 365;
      securitycurrencyPosition.timeFrameChangePercentage = DataHelper
          .roundStandard((securitycurrencyPosition.securitycurrency.getSLast() - histroyClose) / histroyClose * 100);
      if (years >= 1) {
        securitycurrencyPosition.timeFrameAnnualChangePercentage = DataHelper.roundStandard(
            (Math.pow(securitycurrencyPosition.timeFrameChangePercentage / 100 + 1, 1.0 / years) - 1.0) * 100);
      }
    }
  }

}
