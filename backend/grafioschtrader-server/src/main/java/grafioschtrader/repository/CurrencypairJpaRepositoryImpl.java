/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package grafioschtrader.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import grafiosch.common.DataHelper;
import grafiosch.common.DateHelper;
import grafiosch.entities.TaskDataChange;
import grafiosch.entities.User;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.GlobalConstants;
import grafioschtrader.GlobalParamKeyDefault;
import grafioschtrader.connector.instrument.IFeedConnector;
import grafioschtrader.dto.CrossRateRequest;
import grafioschtrader.dto.CrossRateResponse;
import grafioschtrader.dto.CrossRateResponse.CurrenciesAndClosePrice;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Tenant;
import grafioschtrader.priceupdate.historyquote.BaseHistoryquoteThru;
import grafioschtrader.priceupdate.historyquote.HistoryquoteThruConnector;
import grafioschtrader.priceupdate.historyquote.IHistoryquoteLoad;
import grafioschtrader.priceupdate.historyquote.SecurityCurrencyMaxHistoryquoteData;
import grafioschtrader.priceupdate.intraday.IntradayThruConnector;
import grafioschtrader.reportviews.account.CashaccountPositionSummary;
import grafioschtrader.search.CurrencySearchBuilder;
import grafioschtrader.search.SecuritycurrencySearch;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.TaskTypeExtended;
import jakarta.annotation.PostConstruct;

public class CurrencypairJpaRepositoryImpl extends SecuritycurrencyService<Currencypair, CashaccountPositionSummary>
    implements IPositionCloseOnLatestPrice<Currencypair, CashaccountPositionSummary>, CurrencypairJpaRepositoryCustom {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired
  protected CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Autowired
  private TenantJpaRepository tenantJpaRepository;

  ////////////////////////////////////////////////////////////////
  // Historical prices
  ////////////////////////////////////////////////////////////////

  @PostConstruct
  private void postConstruct() {
    historyquoteThruConnector = new HistoryquoteThruConnector<>(entityManager, globalparametersService,
        feedConnectorbeans, this, Currencypair.class);
    intradayThruConnector = new IntradayThruConnector<>(currencypairJpaRepository, globalparametersService,
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
    if (currencypairOpt.isPresent()) {
      fillEmptyCurrencypair(currencypairOpt.get());
    } else {
      throw new IllegalArgumentException("The currency pair with ID " + idSecuritycurrency + " was not found!");
    }
  }

  @Override
  @Transactional
  @Modifying
  public List<Currencypair> catchAllUpCurrencypairHistoryquote() {
    return historyquoteThruConnector.catchAllUpSecuritycurrencyHistoryquote(null);
  }

  @Override
  @Transactional
  @Modifying
  public void allCurrenciesFillEmptyDaysInHistoryquote() {
    final List<Currencypair> currencypairs = currencypairJpaRepository.findAll();
    final int maxFillDays = globalparametersService.getMaxFillDaysCurrency();
    currencypairs.forEach(currencypair -> currencyFillEmptyDaysInHistoryquote(currencypair, maxFillDays));
  }

  @Override
  public void currencieyFillEmptyDaysInHistoryquote(Currencypair currencypair) {
    final int maxFillDays = globalparametersService.getMaxFillDaysCurrency();
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
  protected Currencypair afterFullLoad(final Currencypair currencypair) throws Exception {
    final int maxFillDays = globalparametersService.getMaxFillDaysCurrency();
    currencypair.getHistoryquoteList().sort((h1, h2) -> h1.getDate().compareTo(h2.getDate()));
    return currencyFillEmptyDaysInHistoryquote(currencypair, maxFillDays);
  }

  private Currencypair currencyFillEmptyDaysInHistoryquote(Currencypair currencypair, int maxFillDays) {
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
    return currencypair;
  }

  @Override
  public String getDataProviderResponseForUser(final Integer idSecuritycurrency, final boolean isIntraday) {
    ConnectorData<Currencypair> ct = getConnectorData(idSecuritycurrency, isIntraday, currencypairJpaRepository);
    return ct.feedConnector
        .getContentOfPageRequest(isIntraday ? ct.feedConnector.getCurrencypairIntradayDownloadLink(ct.securitycurrency)
            : ct.feedConnector.getCurrencypairHistoricalDownloadLink(ct.securitycurrency));
  }

  @Override
  public String getDataProviderLinkForUser(final Integer idSecuritycurrency, final boolean isIntraday) {
    ConnectorData<Currencypair> ct = getConnectorData(idSecuritycurrency, isIntraday, currencypairJpaRepository);
    return isIntraday ? intradayThruConnector.createDownloadLink(ct.securitycurrency, ct.feedConnector)
        : historyquoteThruConnector.createDownloadLink(ct.securitycurrency, ct.feedConnector);
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
      BaseHistoryquoteThru<Currencypair> baseHistoryquoteThru, List<Integer> idsStockexchange) {
    return this.currencypairJpaRepository.getMaxHistoryquote(maxHistoryRetry);
  }

  @Override
  protected boolean historyNeedToBeReloaded(final Currencypair securityCurrencyChanged,
      final Currencypair securitycurreny2) {
    return !(securityCurrencyChanged.getIdConnectorHistory().equals(securitycurreny2.getIdConnectorHistory())
        && ObjectUtils.nullSafeEquals(securityCurrencyChanged.getUrlHistoryExtend(),
            securitycurreny2.getUrlHistoryExtend())
        && securityCurrencyChanged.getFromCurrency().equals(securitycurreny2.getFromCurrency())
        && securityCurrencyChanged.getToCurrency().equals(securitycurreny2.getToCurrency()));
  }

  @Override
  @Transactional
  public Currencypair findOrCreateCurrencypairByFromAndToCurrency(final String fromCurrency, final String toCurrency,
      boolean loadAsync) {
    Currencypair currencypair = currencypairJpaRepository.findByFromCurrencyAndToCurrency(fromCurrency, toCurrency);
    if (currencypair == null) {
      currencypair = createNonExistingCurrencypair(fromCurrency, toCurrency, loadAsync);
    }
    return currencypair;
  }

  @Override
  @Transactional
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
  public Currencypair createNonExistingCurrencypair(String fromCurrency, String toCurrency, boolean loadAsync) {
    Currencypair currencypairNew = new Currencypair(fromCurrency, toCurrency);

    if (currencypairNew.getIsCryptocurrency()) {
      currencypairNew.setIdConnectorIntra(globalparametersJpaRepository
          .getReferenceById(GlobalParamKeyDefault.GLOB_KEY_CRYPTOCURRENCY_INTRA_CONNECTOR).getPropertyString());
      currencypairNew.setIdConnectorHistory(globalparametersJpaRepository
          .getReferenceById(GlobalParamKeyDefault.GLOB_KEY_CRYPTOCURRENCY_HISTORY_CONNECTOR).getPropertyString());
    } else {
      currencypairNew.setIdConnectorIntra(globalparametersJpaRepository
          .getReferenceById(GlobalParamKeyDefault.GLOB_KEY_CURRENCY_INTRA_CONNECTOR).getPropertyString());
      currencypairNew.setIdConnectorHistory(globalparametersJpaRepository
          .getReferenceById(GlobalParamKeyDefault.GLOB_KEY_CURRENCY_HISTORY_CONNECTOR).getPropertyString());
    }
    Currencypair currencypair = currencypairJpaRepository.save(currencypairNew);
    log.info("Create non existing currencypair from {}, to {}", fromCurrency, toCurrency);
    if (loadAsync) {
      securityServiceAsyncExectuion.asyncLoadHistoryIntraData(this, currencypairNew, false,
          globalparametersService.getMaxIntraRetry(),
          globalparametersService.getSecurityCurrencyIntradayUpdateTimeout());
    } else {
      currencypair = fillEmptyCurrencypair(currencypair);
    }

    return currencypair;
  }

  public Currencypair fillEmptyCurrencypair(Currencypair currencypair) {
    currencypair = historyquoteThruConnector.createHistoryQuotesAndSave(currencypairJpaRepository, currencypair, null,
        null);
    currencieyFillEmptyDaysInHistoryquote(currencypair);
    updateLastPrice(currencypair);
    return currencypair;
  }

  @Override
  public void createTaskDataChangeOfEmptyHistoryqoute() {
    List<Integer> ids = currencypairJpaRepository.getAllIdOfEmptyHistorqute();
    for (int i = 0; i < ids.size(); i++) {
      taskDataChangeJpaRepository
          .save(new TaskDataChange(TaskTypeExtended.LOAD_EMPTY_CURRENCYPAIR_HISTORYQUOTES, TaskDataExecPriority.PRIO_VERY_LOW,
              LocalDateTime.now().plusMinutes(i), ids.get(i), Currencypair.class.getSimpleName()));
    }
  }


  @Override
  public void updateIntraSecurityCurrency(final Currencypair securitycurrency, final IFeedConnector feedConnector)
      throws Exception {
    feedConnector.updateCurrencyPairLastPrice(securitycurrency);
  }

  @Override
  public SecurityCurrencypairJpaRepository<Currencypair> getJpaRepository() {
    return currencypairJpaRepository;
  }

  @Override
  public void calculatePositionClose(final CashaccountPositionSummary cashaccountPositionSummary, final Double price) {
    cashaccountPositionSummary.gainLossCurrencyMC = DataHelper
        .round(cashaccountPositionSummary.balanceCurrencyTransaction * price
            - cashaccountPositionSummary.balanceCurrencyTransactionMC, 2);
  }

  @Override
   public void updateAllLastPrices() {
    updateLastPriceByList(currencypairJpaRepository
        .findByRetryIntraLoadLessThan(globalparametersService.getMaxIntraRetry()), true);

  }

  @Override
  public Currencypair updateLastPrice(final Currencypair currencypair) {
    final List<Currencypair> currencypairs = Arrays.asList(currencypair);
    intradayThruConnector.updateLastPriceOfSecuritycurrency(currencypairs, false);
    return currencypairs.getFirst();
  }

  @Override
  public List<Currencypair> updateLastPriceByList(final List<Currencypair> currencypairs) {
    return updateLastPriceByList(currencypairs, false);
  }


  private List<Currencypair> updateLastPriceByList(final List<Currencypair> currencypairs, boolean singleThread) {
    return intradayThruConnector.updateLastPriceOfSecuritycurrency(currencypairs, singleThread);
  }


  @Override
  public void calcGainLossBasedOnDateOrNewestPrice(
      final List<CashaccountPositionSummary> securitycurrencyPositionSummary, final Date untilDate) {
    super.calcGainLossBasedOnDateOrNewestPrice(securitycurrencyPositionSummary, this, untilDate);

  }

  @Override
  public List<Currencypair> tryUpToDateIntraDataWhenRetryIntraLoadGreaterThan0(Integer idTenant, Integer idWatchlist) {
    return intradayThruConnector.updateLastPriceOfSecuritycurrency(
        currencypairJpaRepository.findByIdTenantAndIdWatchlistWhenRetryIntraThan0(idTenant, idWatchlist), (short) -1, false);
  }

  @Override
  public List<Currencypair> searchByCriteria(final SecuritycurrencySearch securitycurrencySearch) {
    return searchBuilderWithExclusion(null, null, securitycurrencySearch);
  }

  @Override
  public List<Currencypair> searchBuilderWithExclusion(final Integer idWatchlist, final Integer idCorrelationSet,
      final SecuritycurrencySearch securitycurrencySearch) {

    return (securitycurrencySearch.getIdConnectorHistory() != null
        || securitycurrencySearch.getIdConnectorIntra() != null
        || securitycurrencySearch.getAssetclassType() == AssetclassType.CURRENCY_PAIR
        || (securitycurrencySearch.getAssetclassType() == null && securitycurrencySearch.getName() != null)
            && securitycurrencySearch.getIsin() == null)
                ? currencypairJpaRepository
                    .findAll(new CurrencySearchBuilder(idWatchlist, idCorrelationSet, securitycurrencySearch))
                : Collections.emptyList();
  }

  @Override
  public double getCurrencyExchangeRate(final String fromCurrency, final String toCurrency,
      final Map<String, Currencypair> currencypairMap) {
    double currencyExchangeRate = 1.0;
    if (!fromCurrency.equals(toCurrency)) {
      Currencypair currencypair = currencypairMap.computeIfAbsent(fromCurrency,
          fc -> currencypairJpaRepository.findOrCreateCurrencypairByFromAndToCurrency(fc, toCurrency, true));
      currencyExchangeRate = currencypair.getSLast();
    }
    return currencyExchangeRate;
  }

  @Override
  public CrossRateResponse getCurrencypairForCrossRate(CrossRateRequest crossRateRequest) {
    List<Currencypair> currencypairsMissing = new ArrayList<>();
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Tenant tenant = tenantJpaRepository.getReferenceById(user.getIdTenant());
    String mainCurrencyTenant = tenant.getCurrency();

    CrossRateResponse crossRateResponse = new CrossRateResponse(mainCurrencyTenant);

    if (crossRateRequest.needNewCurrencypair(mainCurrencyTenant)) {
      List<Currencypair> currencypairList = currencypairJpaRepository.findAll();
      Map<String, Map<String, Currencypair>> fromCurrencyMap = currencypairList.stream().collect(Collectors.groupingBy(
          Currencypair::getFromCurrency, Collectors.toMap(Currencypair::getToCurrency, Function.identity())));
      Map<String, Map<String, Currencypair>> toCurrencyMap = currencypairList.stream().collect(Collectors.groupingBy(
          Currencypair::getToCurrency, Collectors.toMap(Currencypair::getFromCurrency, Function.identity())));

      for (String sc : crossRateRequest.securityCurrencyList) {
        List<Currencypair> foundList = crossRateRequest.getExistingCurrencies().stream()
            .filter(cp -> cp.getFromCurrency().equals(sc) || cp.getToCurrency().equals(sc))
            .collect(Collectors.toList());
        if (foundList.isEmpty()) {
          // List does not contain in from an to the security currency
          Map<String, Currencypair> foundFromCurrenciesMap = fromCurrencyMap.get(mainCurrencyTenant);
          if (foundFromCurrenciesMap != null) {
            Currencypair foundExactCurrencypair = foundFromCurrenciesMap.get(sc);
            if (foundExactCurrencypair == null) {
              // Check reverse
              Map<String, Currencypair> foundToCurrenciesMap = toCurrencyMap.get(mainCurrencyTenant);
              if (foundToCurrenciesMap != null) {
                foundExactCurrencypair = foundToCurrenciesMap.get(sc);
              }
            }
            if (foundExactCurrencypair == null) {
              currencypairsMissing.add(findOrCreateCurrencypairByFromAndToCurrency(mainCurrencyTenant, sc, false));
            } else {
              currencypairsMissing.add(foundExactCurrencypair);
            }
          }
        }
      }
    }
    currencypairsMissing
        .forEach(currencypair -> crossRateResponse.currenciesAndClosePrice.add(new CurrenciesAndClosePrice(currencypair,
            historyquoteJpaRepository.getHistoryquoteDateClose(currencypair.getIdSecuritycurrency()))));
    return crossRateResponse;
  }

  @Override
  public List<Historyquote> fillGap(Currencypair securitycurrency) {
    return Collections.emptyList();

  }

  @Override
  public HistoryquoteJpaRepository getHistoryquoteJpaRepository() {
    return historyquoteJpaRepository;
  }

}
