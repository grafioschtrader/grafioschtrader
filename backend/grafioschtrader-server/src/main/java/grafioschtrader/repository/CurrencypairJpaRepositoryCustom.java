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

  /**
   * For currency pairs, historical end-of-day rates must be available for each day. Otherwise GT cannot calculate the
   * transactions on weekends and holidays. This method, which fills these gaps should be performed daily.
   */
  void allCurrenciesFillEmptyDaysInHistoryquote();

  void currencieyFillEmptyDaysInHistoryquote(Currencypair currencypair) throws Exception;

  void fillEmptyCurrencypair(Integer idSecuritycurrency);

  Double getClosePriceForDate(Currencypair currencypair, Date closeDate);

  void updateAllLastPrices();

  /**
   * Update last price of a certain currency pair
   */
  Currencypair updateLastPrice(Currencypair currencypair);

  /**
   * Update last price of specified currency pairs.
   */
  List<Currencypair> updateLastPriceByList(List<Currencypair> currencypairs);

  List<Currencypair> tryUpToDateIntraDataWhenRetryIntraLoadGreaterThan0(Integer idTenant, Integer idWatchlist);

  List<Currencypair> tryUpToDateHistoricalDataWhenRetryHistoryLoadGreaterThan0(Integer idTenant, Integer idWatchlist);

  void createTaskDataChangeOfEmptyHistoryqoute();

  /**
   * Find a currency by the currency pair, if not then it will be created
   */
  Currencypair findOrCreateCurrencypairByFromAndToCurrency(String fromCurrency, String toCurrency, boolean loadAsync);

  Currencypair findOrCreateCurrencypairByFromAndToCurrency(List<Currencypair> currencypairs, String fromCurrency,
      String toCurrency);

  void calcGainLossBasedOnDateOrNewestPrice(List<CashaccountPositionSummary> securitycurrencyPositionSummary,
      Date untilDate);

  Currencypair createNonExistingCurrencypair(String fromCurrency, String toCurrency, boolean loadAsync);

  String getDataProviderResponseForUser(final Integer idSecuritycurrency, final boolean isIntraday);

  String getDataProviderLinkForUser(final Integer idSecuritycurrency, final boolean isIntraday);

  List<Currencypair> searchByCriteria(final SecuritycurrencySearch securitycurrencySearch);

  /**
   * Determines the necessary currency pairs required to calculate cross rates for a list of security currencies
   * relative to the tenant’s main currency.
   *
   * The path to each cross currency can include up to three nodes. For example: if the requested currency is USD, the
   * tenant's main currency is CHF, and the security currency is EUR, then the system will look for currency pairs like:
   * CHF/USD (or USD/CHF) and CHF/EUR (or EUR/CHF).
   *
   * If required currency pairs do not already exist in the repository or request, they are looked up and potentially
   * created. The method returns both the currency pairs and their most recent close prices.
   *
   * @param crossRateRequest the request containing the security currencies and existing currency pairs
   * @return a {@code CrossRateResponse} with all necessary currency pairs and their close prices
   */
  CrossRateResponse getCurrencypairForCrossRate(CrossRateRequest crossRateRequest);

  List<Currencypair> searchBuilderWithExclusion(Integer idWatchlist, Integer idCorrelationSet,
      SecuritycurrencySearch securitycurrencySearch);

  double getCurrencyExchangeRate(String fromCurrency, String toCurrency, Map<String, Currencypair> currencypairMap);

}
