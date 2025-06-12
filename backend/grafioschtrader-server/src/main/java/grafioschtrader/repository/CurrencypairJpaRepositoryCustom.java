package grafioschtrader.repository;

import java.util.Date;
import java.util.List;

import grafioschtrader.dto.CrossRateRequest;
import grafioschtrader.dto.CrossRateResponse;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.reportviews.account.CashaccountPositionSummary;
import grafioschtrader.search.SecuritycurrencySearch;

/**
 * Custom repository methods for the {@link Currencypair} entity. Extends {@link ISecuritycurrencyService} to provide
 * common functionality for security and currency pair entities.
 */
public interface CurrencypairJpaRepositoryCustom extends ISecuritycurrencyService<Currencypair> {

  /**
   * Fetches and updates the historical end-of-day price data for all currency pairs from their respective data
   * providers up to the most recent trading day.
   *
   * @return A list of {@link Currencypair} entities for which the history was updated.
   */
  List<Currencypair> catchAllUpCurrencypairHistoryquote();

  /**
   * For currency pairs, historical end-of-day rates must be available for each day. Otherwise GT cannot calculate the
   * transactions on weekends and holidays. This method, which fills these gaps should be performed daily.
   */
  void allCurrenciesFillEmptyDaysInHistoryquote();

  /**
   * Fills any missing historical end-of-day price data for a specific currency pair.
   * This is crucial for ensuring continuous data, especially for calculations involving weekends and holidays.
   *
   * @param currencypair The {@link Currencypair} to process.
   * @throws Exception If an error occurs during the data filling process.
   */
  void currencieyFillEmptyDaysInHistoryquote(Currencypair currencypair) throws Exception;

  /**
   * Fills any missing historical end-of-day price data for a currency pair identified by its ID.
   *
   * @param idSecuritycurrency The ID of the {@link Currencypair}.
   */
  void fillEmptyCurrencypair(Integer idSecuritycurrency);

  /**
   * Retrieves the closing price for a given currency pair on a specific date.
   *
   * @param currencypair The {@link Currencypair} for which to get the close price.
   * @param closeDate    The specific date for which the close price is requested.
   * @return The closing price as a {@link Double}, or null if not found.
   */
  Double getClosePriceForDate(Currencypair currencypair, Date closeDate);

  /**
   * Updates the last (most recent) price for all currency pairs from their intraday data providers.
   */
  void updateAllLastPrices();

  /**
   * Updates the last (most recent) price of a certain currency pair from its intraday data provider.
   *
   * @param currencypair The {@link Currencypair} to update.
   * @return The updated {@link Currencypair} entity with the new last price.
   */
  Currencypair updateLastPrice(Currencypair currencypair);

  /**
   * Updates the last (most recent) price of a certain currency pair from its intraday data provider.
   *
   * @param currencypair The {@link Currencypair} to update.
   * @return The updated {@link Currencypair} entity with the new last price.
   */
  List<Currencypair> updateLastPriceByList(List<Currencypair> currencypairs);

  /**
   * Attempts to update intraday data for currency pairs in a given watchlist for a specific tenant, if their retry
   * intraday load count is greater than 0. The action is normally triggered by the user via the user interface.
   *
   * @param idTenant    The ID of the tenant. For security reasons.
   * @param idWatchlist The ID of the watchlist.
   * @return A list of {@link Currencypair} entities for which intraday data update was attempted.
   */
  List<Currencypair> tryUpToDateIntraDataWhenRetryIntraLoadGreaterThan0(Integer idTenant, Integer idWatchlist);

  /**
   * Attempts to update historical data for currency pairs in a given watchlist for a specific tenant, if their retry
   * historical load count is greater than 0. The action is normally triggered by the user via the user interface.
   *
   * @param idTenant    The ID of the tenant. For security reasons.
   * @param idWatchlist The ID of the watchlist.
   * @return A list of {@link Currencypair} entities for which historical data update was attempted.
   */
  List<Currencypair> tryUpToDateHistoricalDataWhenRetryHistoryLoadGreaterThan0(Integer idTenant, Integer idWatchlist);

  /**
   * Creates a background task for each currency pair that does not yet have historical price data. The number of failed
   * attempts must not exceed the retry limit. There is a one-minute interval between the individual jobs created.
   */
  void createTaskDataChangeOfEmptyHistoryqoute();

  /**
   * Finds a currency pair by its 'from' and 'to' currency codes. If it doesn't exist, it will be created.
   *
   * @param fromCurrency The 'from' currency code (e.g., "EUR").
   * @param toCurrency   The 'to' currency code (e.g., "USD").
   * @param loadAsync    If true, and the currency pair is created, its historical data will be loaded asynchronously.
   * @return The found or newly created {@link Currencypair}.
   */
  Currencypair findOrCreateCurrencypairByFromAndToCurrency(String fromCurrency, String toCurrency, boolean loadAsync);

  /**
   * Finds a currency pair by its 'from' and 'to' currency codes within a provided list of currency pairs. If not found
   * in the list, it attempts to create a new one (without asynchronous data loading).
   *
   * @param currencypairs A list of existing {@link Currencypair} to search within first.
   * @param fromCurrency  The 'from' currency code.
   * @param toCurrency    The 'to' currency code.
   * @return The found or newly created {@link Currencypair}.
   */
  Currencypair findOrCreateCurrencypairByFromAndToCurrency(List<Currencypair> currencypairs, String fromCurrency,
      String toCurrency);

  /**
   * Calculates gain/loss for a list of cash account positions based on a specified date or the newest price. Of course,
   * the price gains on the securities are also calculated here.
   *
   * @param cashaccountPositionSummary A list of {@link CashaccountPositionSummary}.
   * @param untilDate                  The date until which to calculate the gain/loss. If null, the newest price is
   *                                   used.
   */
  void calcGainLossBasedOnDateOrNewestPrice(List<CashaccountPositionSummary> securitycurrencyPositionSummary,
      Date untilDate);

  /**
   * Creates a non-existing currency pair defined by 'from' and 'to' currency codes. A currency pair can be created by
   * the system or by the user.
   *
   * @param fromCurrency The 'from' currency code.
   * @param toCurrency   The 'to' currency code.
   * @param loadAsync    If true, historical data for the newly created currency pair will be loaded asynchronously.
   * @return The newly created {@link Currencypair}.
   */
  Currencypair createNonExistingCurrencypair(String fromCurrency, String toCurrency, boolean loadAsync);

  /**
   * Retrieves the raw response from the data provider for a currency pair's price data (historical or intraday).
   * This is used when the UI needs to display data provider information that might require backend processing (e.g., API keys).
   *
   * @param idSecuritycurrency The ID of the currency pair.
   * @param isIntraday True if intraday data is requested, false for historical data.
   * @return The raw response string from the data provider.
   */
  String getDataProviderResponseForUser(final Integer idSecuritycurrency, final boolean isIntraday);

  /**
   * Gets the direct link to the data provider for a currency pair's price data (historical or intraday) for user
   * display. In the user interface, the user can follow this link to see how the backend receives the external price
   * data.
   *
   * @param idSecuritycurrency The ID of the {@link Currencypair}.
   * @param isIntraday         True if intraday data is requested, false for historical data.
   * @return The URL string for the data provider.
   */
  String getDataProviderLinkForUser(final Integer idSecuritycurrency, final boolean isIntraday);

  /**
   * Searches for currency pairs based on the provided search criteria.
   *
   * @param securitycurrencySearch The {@link SecuritycurrencySearch} criteria.
   * @return A list of {@link Currencypair} entities matching the criteria.
   */
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

  /**
   * Searches for currency pairs based on criteria, excluding those in a given watchlist or correlation set. It is
   * intended, for example, to expand a correlation set or watch list with additional instruments. Therefore, existing
   * instruments may no longer appear in the selection.
   *
   * @param idWatchlist            The ID of the watchlist to exclude (can be null).
   * @param idCorrelationSet       The ID of the correlation set to exclude (can be null).
   * @param securitycurrencySearch The search criteria.
   * @return A list of {@link Currencypair} entities matching the criteria.
   */
  List<Currencypair> searchBuilderWithExclusion(Integer idWatchlist, Integer idCorrelationSet,
      SecuritycurrencySearch securitycurrencySearch);

  /**
   * Gets the exchange rate between two currencies using a provided map of existing currency pairs. If a direct pair
   * (e.g., FROM/TO or TO/FROM) is not found, it may attempt to calculate via a common currency (e.g., EUR).
   *
   * @param fromCurrency    The 'from' currency code.
   * @param toCurrency      The 'to' currency code.
   * @param currencypairMap A map of existing {@link Currencypair} entities, keyed by their string representation (e.g.,
   *                        "EURUSD").
   * @return The exchange rate as a double.
   * @throws ArithmeticException if the exchange rate cannot be determined.
   */
  // double getCurrencyExchangeRate(String fromCurrency, String toCurrency, Map<String, Currencypair> currencypairMap);

}
