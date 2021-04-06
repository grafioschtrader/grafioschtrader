/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package grafioschtrader.repository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataHelper;
import grafioschtrader.common.DateHelper;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Globalparameters;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.TaskDataChange;
import grafioschtrader.priceupdate.historyquote.BaseHistoryquoteThru;
import grafioschtrader.priceupdate.historyquote.HistoryquoteThruConnector;
import grafioschtrader.priceupdate.historyquote.IHistoryquoteLoad;
import grafioschtrader.priceupdate.historyquote.SecurityCurrencyMaxHistoryquoteData;
import grafioschtrader.priceupdate.intraday.IntradayThruConnector;
import grafioschtrader.reportviews.account.CashaccountPositionSummary;
import grafioschtrader.search.CurrencySearchBuilder;
import grafioschtrader.search.SecuritycurrencySearch;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.TaskType;

/**
 *
 * @author Hugo Graf
 */
public class CurrencypairJpaRepositoryImpl extends SecuritycurrencyService<Currencypair, CashaccountPositionSummary>
    implements IPositionCloseOnLatestPrice<Currencypair, CashaccountPositionSummary>, CurrencypairJpaRepositoryCustom {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  CurrencypairJpaRepository currencypairJpaRepository;
  
  @Autowired
  TaskDataChangeJpaRepository taskDataChangeJpaRepository;
  

  ////////////////////////////////////////////////////////////////
  // Historical prices
  ////////////////////////////////////////////////////////////////

  @PostConstruct
  private void postConstruct() {
    historyquoteThruConnector = new HistoryquoteThruConnector<>(entityManager, globalparametersJpaRepository,
        feedConnectorbeans, this, Currencypair.class);
    intradayThruConnector = new IntradayThruConnector<>(currencypairJpaRepository, globalparametersJpaRepository,
        feedConnectorbeans, this);
  }

  @Override
  protected IHistoryquoteLoad<Currencypair> getHistorquoteLoad(Currencypair currencypair) {
    return historyquoteThruConnector;
  }

  @Override
  @Transactional
  @Modifying
  public void fillEmptyCurrencypair(Integer idSecuritycurrency) {
    Optional<Currencypair> currencypairOpt = currencypairJpaRepository.findById(idSecuritycurrency);
    if(currencypairOpt.isPresent()) {
      fillEmptyCurrencypair(currencypairOpt.get());
    }
  }
  
  @Override
  @Transactional
  @Modifying
  public List<Currencypair> catchAllUpCurrencypairHistoryquote() {
    return historyquoteThruConnector.catchAllUpSecuritycurrencyHistoryquote();
  }

  @Override
  @Transactional
  @Modifying
  public void allCurrenciesFillEmptyDaysInHistoryquote() {
    final List<Currencypair> currencypairs = currencypairJpaRepository.findAll();
    final int maxFillDays = globalparametersJpaRepository.getMaxFillDaysCurrency();
    currencypairs.forEach(currencypair -> currencyFillEmptyDaysInHistoryquote(currencypair, maxFillDays));
  }

  @Override
  public void currencieyFillEmptyDaysInHistoryquote(Currencypair currencypair) {
    final int maxFillDays = globalparametersJpaRepository.getMaxFillDaysCurrency();
    currencyFillEmptyDaysInHistoryquote(currencypair, maxFillDays);
  }

  @Override
  public Double getClosePriceForDate(Currencypair currencypair, Date closeDate) {

    if (DateHelper.isTodayOrAfter(closeDate)) {
      return currencypair.getSLast();
    } else {
      Optional<Historyquote> historyquoteOpt = historyquoteJpaRepository
          .findByIdSecuritycurrencyAndDate(currencypair.getIdSecuritycurrency(), closeDate);
      if (historyquoteOpt.isPresent()) {
        return historyquoteOpt.get().getClose();
      } else if (DateHelper.setTimeToZeroAndAddDay(closeDate, GlobalConstants.EX_CHANGE_RATE_DAYS_LIMIT_LATEST_PRICE)
          .after(new Date())) {
        currencypair.getSLast();
      }
    }
    return null;
  }

  @Override
  protected void afterFullLoad(final Currencypair currencypair) {
    final int maxFillDays = globalparametersJpaRepository.getMaxFillDaysCurrency();
    currencypair.getHistoryquoteList().sort((h1, h2) -> h1.getDate().compareTo(h2.getDate()));
    currencyFillEmptyDaysInHistoryquote(currencypair, maxFillDays);
  }

  private void currencyFillEmptyDaysInHistoryquote(Currencypair currencypair, int maxFillDays) {
    if (currencypair.getHistoryquoteList() != null) {
      for (int i = currencypair.getHistoryquoteList().size() - 1; i >= 1; i--) {
        final Historyquote dayBeforHoleHistoryquote = currencypair.getHistoryquoteList().get(i - 1);
        final Historyquote dayAfterHoleHistoryquote = currencypair.getHistoryquoteList().get(i);
        final long datediff = DateHelper.getDateDiff(dayBeforHoleHistoryquote.getDate(),
            dayAfterHoleHistoryquote.getDate(), TimeUnit.DAYS);

        if (datediff > maxFillDays) {
          // Try to get the missing dates from data provider
          int sizeBefore = currencypair.getHistoryquoteList().size();
          Date fromDate = DateHelper.setTimeToZeroAndAddDay(dayBeforHoleHistoryquote.getDate(), 1);
          Date toDate = DateHelper.setTimeToZeroAndAddDay(dayAfterHoleHistoryquote.getDate(), -1);
          log.info("Difference: {} for currency pair {}/{}, Date: {} to {}", datediff, currencypair.getFromCurrency(),
              currencypair.getToCurrency(), fromDate, toDate);
          currencypair = historyquoteThruConnector.createHistoryQuotesAndSave(currencypairJpaRepository, currencypair,
              fromDate, toDate);

          int sizeAfter = currencypair.getHistoryquoteList().size();
          if (sizeAfter > sizeBefore) {
            // Some history quotes were added. Now, maybe some holidays still missing ->
            // check again
            currencypair.sortHistoryquoteASC();
            i += sizeAfter - sizeBefore + 1;
            continue;
          } else {
            // Fix the missing with day before the missing
            historyquoteJpaRepository.fillMissingPeriodWithHistoryquotes(dayBeforHoleHistoryquote,
                dayAfterHoleHistoryquote);
          }
        } else if (datediff > 1 && datediff <= maxFillDays) {
          historyquoteJpaRepository.fillMissingPeriodWithHistoryquotes(dayBeforHoleHistoryquote,
              dayAfterHoleHistoryquote);
        }
      }
    }
  }

  @Override
  public List<Currencypair> tryUpToDateHistoricalDataWhenRetryHistoryLoadGreaterThan0(Integer idTenant,
      Integer idWatchlist) {
    return historyquoteThruConnector.fillHistoryquoteForSecuritiesCurrencies(currencypairJpaRepository
        .findWithConnectorByIdTenantAndIdWatchlistWhenRetryHistoryGreaterThan0(idTenant, idWatchlist),
        DateHelper.getCalendar(new Date()));
  }

  @Override
  public List<Historyquote> getHistoryQuote(final Currencypair currencypair, final Date fromDate, final Date toDate,
      final IFeedConnector feedConector) throws Exception {
    return feedConector.getEodCurrencyHistory(currencypair, fromDate, toDate);
  }

  @Override
  @Transactional
  @Modifying
  public Currencypair catchUpSecurityCurrencypairHisotry(Currencypair currencypair, final Date fromDate,
      final Date toDate) {
    currencypair = currencypairJpaRepository.findByIdSecuritycurrency(currencypair.getIdSecuritycurrency());
    return historyquoteThruConnector.createHistoryQuotesAndSave(currencypairJpaRepository, currencypair, fromDate,
        toDate);
  }

  @Override
  public List<SecurityCurrencyMaxHistoryquoteData<Currencypair>> getMaxHistoryquoteResult(final short maxHistoryRetry,
      BaseHistoryquoteThru<Currencypair> baseHistoryquoteThru) {
    return this.currencypairJpaRepository.getMaxHistoryquote(maxHistoryRetry);
  }

  @Override
  protected boolean historyNeedToBeReloaded(final Currencypair securityCurrencyChanged,
      final Currencypair securitycurreny2) {
    return !(securityCurrencyChanged.getIdConnectorHistory().equals(securitycurreny2.getIdConnectorHistory())
        && securityCurrencyChanged.getFromCurrency().equals(securitycurreny2.getFromCurrency())
        && securityCurrencyChanged.getToCurrency().equals(securitycurreny2.getToCurrency()));
  }

  @Override
  public Currencypair findOrCreateCurrencypairByFromAndToCurrency(final String fromCurrency, final String toCurrency) {
    Currencypair currencypair = currencypairJpaRepository.findByFromCurrencyAndToCurrency(fromCurrency, toCurrency);
    if (currencypair == null) {
      createNonExistingCurrencypair(fromCurrency, toCurrency, true);
    }
    return currencypair;
  }

  @Override
  public Currencypair findOrCreateCurrencypairByFromAndToCurrency(List<Currencypair> currencypairs,
      final String fromCurrency, final String toCurrency) {
    Optional<Currencypair> currencypairOpt = currencypairs.stream()
        .filter(currencypair -> currencypair.getFromCurrency().equals(fromCurrency)
            && currencypair.getToCurrency().equals(toCurrency))
        .findFirst();
    if (currencypairOpt.isPresent()) {
      return currencypairOpt.get();
    } else {
      return createNonExistingCurrencypair(fromCurrency, toCurrency, true);
    }
  }

  @Override
  @Transactional
  public Currencypair createNonExistingCurrencypair(String fromCurrency, String toCurrency, boolean loadAsync) {
    Currencypair currencypairNew = new Currencypair(fromCurrency, toCurrency);
    // currencypairNew.setCreateUserId(AppSettings.SYSTEM_ID_USER);

    if (currencypairNew.getIsCryptocurrency()) {
      currencypairNew.setIdConnectorIntra(
          globalparametersJpaRepository.getOne(Globalparameters.GLOB_KEY_CRYPTOCURRENCY_INTRA_CONNECTOR).getPropertyString());
      currencypairNew.setIdConnectorHistory(globalparametersJpaRepository
          .getOne(Globalparameters.GLOB_KEY_CRYPTOCURRENCY_HISTORY_CONNECTOR).getPropertyString());
    } else {
      currencypairNew.setIdConnectorIntra(
          globalparametersJpaRepository.getOne(Globalparameters.GLOB_KEY_CURRENCY_INTRA_CONNECTOR).getPropertyString());
      currencypairNew.setIdConnectorHistory(globalparametersJpaRepository
          .getOne(Globalparameters.GLOB_KEY_CURRENCY_HISTORY_CONNECTOR).getPropertyString());
    }
    Currencypair currencypair = currencypairJpaRepository.save(currencypairNew);
    log.info("Create non existing currencypair from {}, to {}", fromCurrency, toCurrency);
    if (loadAsync) {
      securityServiceAsyncExectuion.asyncLoadHistoryIntraData(this, currencypairNew, false,
          globalparametersJpaRepository.getMaxIntraRetry(),
          globalparametersJpaRepository.getSecurityCurrencyIntradayUpdateTimeout());
    } else {
      currencypair = fillEmptyCurrencypair(currencypair);
    }

    return currencypair;
  }
  
  public Currencypair fillEmptyCurrencypair(Currencypair currencypair) {
    currencypair = historyquoteThruConnector.createHistoryQuotesAndSave(currencypairJpaRepository, currencypair,
        null, null);
    currencieyFillEmptyDaysInHistoryquote(currencypair);
    updateLastPrice(currencypair);
    return currencypair;
  }
  

  @Override
  public void createTaskDataChangeOfEmptyHistoryqoute() {
    List<Integer> ids = currencypairJpaRepository.getAllIdOfEmptyHistorqute();
    for(int i = 0; i < ids.size(); i++) {
      taskDataChangeJpaRepository.save(new TaskDataChange(TaskType.LOAD_EMPTY_CURRENCYPAIR_HISTORYQOUTES, (short) 42,
          LocalDateTime.now().plusMinutes(i), ids.get(i), Currencypair.TABNAME));
    }
  }
  
  
  @Override
  public void updateAllLastPrice() {
    intradayThruConnector.updateLastPriceOfSecuritycurrency(currencypairJpaRepository.findAll());
  }

  @Override
  public void updateIntraSecurityCurrency(final Currencypair securitycurrency, final IFeedConnector feedConnector)
      throws Exception {
    feedConnector.updateCurrencyPairLastPrice(securitycurrency);
  }

  @Override
  public JpaRepository<Currencypair, Integer> getJpaRepository() {
    return currencypairJpaRepository;
  }

  @Override
  public void calculatePositionClose(final CashaccountPositionSummary cashaccountPositionSummary, final Double price) {
    cashaccountPositionSummary.gainLossCurrencyMC = DataHelper
        .round(cashaccountPositionSummary.balanceCurrencyTransaction * price
            - cashaccountPositionSummary.balanceCurrencyTransactionMC, 2);

  }

  @Override
  public Currencypair updateLastPrice(final Currencypair currencypair) {
    final List<Currencypair> currencypairs = Arrays.asList(currencypair);
    intradayThruConnector.updateLastPriceOfSecuritycurrency(currencypairs);
    return currencypairs.get(0);
  }

  @Override
  public List<Currencypair> updateLastPriceByList(final List<Currencypair> currencypairs) {
    intradayThruConnector.updateLastPriceOfSecuritycurrency(currencypairs);
    return currencypairs;
  }

  @Override
  public void calcGainLossBasedOnDateOrNewestPrice(
      final List<CashaccountPositionSummary> securitycurrencyPositionSummary, final Date untilDate) {
    super.calcGainLossBasedOnDateOrNewestPrice(securitycurrencyPositionSummary, this, untilDate);

  }

  @Override
  public List<Currencypair> tryUpToDateIntraDataWhenRetryIntraLoadGreaterThan0(Integer idTenant, Integer idWatchlist) {
    return intradayThruConnector.updateLastPriceOfSecuritycurrency(
        currencypairJpaRepository.findByIdTenantAndIdWatchlistWhenRetryIntraThan0(idTenant, idWatchlist), (short) -1);
  }

  @Override
  public List<Currencypair> searchByCriteria(final SecuritycurrencySearch securitycurrencySearch) {
    return watchlistSearchForAdding(null, securitycurrencySearch);
  }

  @Override
  public List<Currencypair> watchlistSearchForAdding(final Integer idWatchlist,
      final SecuritycurrencySearch securitycurrencySearch) {

    return (securitycurrencySearch.assetclassType == AssetclassType.CURRENCY_PAIR
        || (securitycurrencySearch.assetclassType == null && securitycurrencySearch.name != null)
            && securitycurrencySearch.isin == null)
                ? currencypairJpaRepository.findAll(new CurrencySearchBuilder(idWatchlist, securitycurrencySearch))
                : Collections.emptyList();
  }

  @Override
  public double getCurrencyExchangeRate(final String fromCurrency, final String toCurrency,
      final Map<String, Currencypair> currencypairMap) {
    double currencyExchangeRate = 1.0;
    if (!fromCurrency.equals(toCurrency)) {
      Currencypair currencypair = currencypairMap.computeIfAbsent(fromCurrency,
          fc -> currencypairJpaRepository.findOrCreateCurrencypairByFromAndToCurrency(fc, toCurrency));
      currencyExchangeRate = currencypair.getSLast();
    }
    return currencyExchangeRate;
  }

}
