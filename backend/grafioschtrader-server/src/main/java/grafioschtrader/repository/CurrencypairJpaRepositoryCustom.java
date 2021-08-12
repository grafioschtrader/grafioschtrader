package grafioschtrader.repository;

import java.util.Date;
import java.util.List;
import java.util.Map;

import grafioschtrader.dto.CrossRateRequest;
import grafioschtrader.dto.CrossRateResponse;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.reportviews.account.CashaccountPositionSummary;
import grafioschtrader.search.SecuritycurrencySearch;

public interface CurrencypairJpaRepositoryCustom extends ISecuritycurrencyService<Currencypair> {

  List<Currencypair> catchAllUpCurrencypairHistoryquote();

  void allCurrenciesFillEmptyDaysInHistoryquote();

  void currencieyFillEmptyDaysInHistoryquote(Currencypair currencypair);

  void fillEmptyCurrencypair(Integer idSecuritycurrency);

  Double getClosePriceForDate(Currencypair currencypair, Date closeDate);

  /**
   * Update last price of a certain currency pair
   *
   * @param currencypair
   * @return
   */
  Currencypair updateLastPrice(Currencypair currencypair);

  /**
   * Update last price of specified currency pairs.
   *
   * @param currencypairs
   * @return
   */
  List<Currencypair> updateLastPriceByList(List<Currencypair> currencypairs);

  List<Currencypair> tryUpToDateIntraDataWhenRetryIntraLoadGreaterThan0(Integer idTenant, Integer idWatchlist);

  List<Currencypair> tryUpToDateHistoricalDataWhenRetryHistoryLoadGreaterThan0(Integer idTenant, Integer idWatchlist);

  void createTaskDataChangeOfEmptyHistoryqoute();

  /**
   * Find a currency by the currency pair, if not then it will be created
   *
   * @param fromCurrency
   * @param toCurrency
   * @return
   */
  Currencypair findOrCreateCurrencypairByFromAndToCurrency(String fromCurrency, String toCurrency);

  Currencypair findOrCreateCurrencypairByFromAndToCurrency(List<Currencypair> currencypairs, String fromCurrency,
      String toCurrency);

  void calcGainLossBasedOnDateOrNewestPrice(List<CashaccountPositionSummary> securitycurrencyPositionSummary,
      Date untilDate);

  Currencypair createNonExistingCurrencypair(String fromCurrency, String toCurrency, boolean loadAsync);

  List<Currencypair> searchByCriteria(final SecuritycurrencySearch securitycurrencySearch);

  CrossRateResponse getCurrencypairForCrossRate(CrossRateRequest crossRateRequest);
  
  List<Currencypair> searchBuilderWithExclusion(Integer idWatchlist, Integer idCorrelationSet,
      SecuritycurrencySearch securitycurrencySearch);

  double getCurrencyExchangeRate(String fromCurrency, String toCurrency, Map<String, Currencypair> currencypairMap);

}
