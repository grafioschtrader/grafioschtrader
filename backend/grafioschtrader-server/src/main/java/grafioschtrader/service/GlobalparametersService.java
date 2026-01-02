package grafioschtrader.service;

import java.text.ParseException;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import grafiosch.BaseConstants;
import grafiosch.common.DateHelper;
import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.entities.Globalparameters;
import grafiosch.entities.User;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafioschtrader.GlobalConstants;
import grafioschtrader.GlobalParamKeyDefault;

/**
 * Service for managing and retrieving global configuration parameters used throughout the GrafioschTrader application.
 * 
 * All configuration values are retrieved from the global parameters repository with fallback to predefined default
 * values when specific parameters are not configured. This ensures the application can operate with sensible defaults
 * while allowing administrators to customize behavior through database configuration.
 * 
 * The service integrates with Spring Security to access user context for localization and user-specific settings where
 * applicable.
 */
@Service
public class GlobalparametersService {

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private MessageSource messages;

  /**
   * Retrieves currency precision configuration as a map of currency codes to decimal places.
   * 
   * Parses the currency precision global parameter which is stored as a comma-separated string of "CURRENCY=PRECISION"
   * pairs (e.g., "USD=2,JPY=0,BTC=8"). If the global parameter is not configured, uses the default precision settings.
   * 
   * @return a map where keys are ISO currency codes and values are the number of decimal places to use for that
   *         currency
   * @see GlobalParamKeyDefault#DEFAULT_CURRENCY_PRECISION
   * @see GlobalParamKeyDefault#GLOB_KEY_CURRENCY_PRECISION
   */
  public Map<String, Integer> getCurrencyPrecision() {
    Map<String, Integer> currencyPrecisionMap = new HashMap<>();
    String curPrecision = globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_CURRENCY_PRECISION)
        .map(Globalparameters::getPropertyString).orElse(GlobalParamKeyDefault.DEFAULT_CURRENCY_PRECISION);
    String[] curSinglePre = curPrecision.split(",");
    for (String element : curSinglePre) {
      String[] pair = element.split("=");
      currencyPrecisionMap.put(pair[0], Integer.parseInt(pair[1]));
    }
    return currencyPrecisionMap;
  }

  /**
   * Gets the decimal precision for a specific currency.
   * 
   * Looks up the precision for the given currency in the currency precision map. If no specific precision is configured
   * for the currency, returns the standard fraction digits default.
   * 
   * @param currency the ISO currency code (e.g., "USD", "EUR", "BTC")
   * @return the number of decimal places to use for the specified currency
   * @see #getCurrencyPrecision()
   * @see BaseConstants#FID_STANDARD_FRACTION_DIGITS
   */
  public int getPrecisionForCurrency(String currency) {
    return getCurrencyPrecision().getOrDefault(currency, BaseConstants.FID_STANDARD_FRACTION_DIGITS);
  }

  /**
   * Gets the maximum number of retry attempts for intraday price data updates. This value relates to each individual
   * security.
   * 
   * This configuration controls how many times the system will retry fetching intraday price data from external sources
   * before marking the operation as failed.
   * 
   * @return the maximum number of intraday retry attempts, defaults to
   *         {@link GlobalParamKeyDefault#DEFAULT_INTRA_RETRY} if not configured
   * @see GlobalParamKeyDefault#GLOB_KEY_INTRA_RETRY
   */
  public short getMaxIntraRetry() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_INTRA_RETRY)
        .map(g -> g.getPropertyInt().shortValue()).orElse(GlobalParamKeyDefault.DEFAULT_INTRA_RETRY);
  }

  /**
   * Gets the retry threshold offset for intraday data observation monitoring.
   * 
   * This value is subtracted from the maximum retry count to determine when an instrument should be flagged as
   * potentially non-functioning in monitoring reports. For example, if max retries is 4 and this value is 1,
   * instruments with 3 or 4 retries would be considered problematic.
   * 
   * @return the number to subtract from max retry count for monitoring thresholds
   * @see GlobalParamKeyDefault#GLOB_KEY_INTRADAY_OBSERVATION_RETRY_MINUS
   * @see GlobalParamKeyDefault#DEFAULT_INTRADAY_OBSERVATION_RETRY_MINUS
   */
  public int getIntradayObservationRetryMinus() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_INTRADAY_OBSERVATION_RETRY_MINUS)
        .map(Globalparameters::getPropertyInt).orElse(GlobalParamKeyDefault.DEFAULT_INTRADAY_OBSERVATION_RETRY_MINUS);
  }

  /**
   * Gets the percentage threshold for intraday data connector failure alerts.
   * 
   * When at least this percentage of instruments using a particular data connector fail to update, the system will
   * generate monitoring alerts. A value of 100% indicates the connector has completely stopped working.
   * 
   * @return the percentage threshold (0-100) for connector failure detection
   * @see GlobalParamKeyDefault#GLOB_KEY_INTRADAY_OBSERVATION_FALLING_PERCENTAGE
   * @see GlobalParamKeyDefault#DEFAULT_INTRADAY_OBSERVATION_FALLING_PERCENTAGE
   */
  public int getIntradayObservationFallingPercentage() {
    return globalparametersJpaRepository
        .findById(GlobalParamKeyDefault.GLOB_KEY_INTRADAY_OBSERVATION_FALLING_PERCENTAGE)
        .map(Globalparameters::getPropertyInt)
        .orElse(GlobalParamKeyDefault.DEFAULT_INTRADAY_OBSERVATION_FALLING_PERCENTAGE);
  }

  /**
   * Gets the timeout duration for security and currency intraday update operations.
   * 
   * This controls how long the system will wait for intraday price updates to complete before timing out the operation.
   * Longer timeouts allow for more reliable data collection but may delay other system operations.
   * 
   * @return the timeout duration in seconds
   * @see GlobalParamKeyDefault#GLOB_KEY_SC_INTRA_UPDATE_TIMEOUT_SECONDS
   * @see GlobalParamKeyDefault#DEFAULT_SC_INTRA_UPDATE_TIMEOUT_SECONDS
   */
  public int getSecurityCurrencyIntradayUpdateTimeout() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_SC_INTRA_UPDATE_TIMEOUT_SECONDS)
        .map(Globalparameters::getPropertyInt).orElse(GlobalParamKeyDefault.DEFAULT_SC_INTRA_UPDATE_TIMEOUT_SECONDS);
  }

  /**
   * Monitoring of historical price data. Transmission error with maximum repetition "gt.history.retry" minus this
   * number. If "gt.history.retry" contains the value 4, this value should be 0 or 1. Thus, an instrument with 4 or 3
   * retries would be considered non-functioning.
   *
   * @return Value that is subtracted from the "gt.history.retry" value.
   */

  public int getHistoryObservationRetryMinus() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_HISTORY_OBSERVATION_RETRY_MINUS)
        .map(Globalparameters::getPropertyInt).orElse(GlobalParamKeyDefault.DEFAULT_HISTORY_OBSERVATION_RETRY_MINUS);
  }

  public int getWatchlistIntradayUpdateTimeout() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_W_INTRA_UPDATE_TIMEOUT_SECONDS)
        .map(Globalparameters::getPropertyInt).orElse(GlobalParamKeyDefault.DEFAULT_W_INTRA_UPDATE_TIMEOUT_SECONDS);
  }

  public Integer getGTNetMyEntryID() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_GTNET_MY_ENTRY_ID)
        .map(Globalparameters::getPropertyInt).orElse(null);
  }

  /**
   * Checks whether GTNet functionality is enabled.
   *
   * GTNet is enabled when the global parameter 'gt.gtnet.use' has a non-zero property_int value. If the parameter is not
   * configured in the database, returns the default value (disabled).
   *
   * @return true if GTNet is enabled, false otherwise
   * @see GlobalParamKeyDefault#GLOB_KEY_GTNET_USE
   * @see GlobalParamKeyDefault#DEFAULT_GTNET_USE
   */
  public boolean isGTNetEnabled() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_GTNET_USE)
        .map(g -> g.getPropertyInt() != null && g.getPropertyInt() != 0)
        .orElse(GlobalParamKeyDefault.DEFAULT_GTNET_USE != 0);
  }

  /**
   * Checks whether GTNet exchange logging is enabled.
   *
   * GTNet logging is enabled when the global parameter 'gt.gtnet.use.log' has a non-zero property_int value.
   * If the parameter is not configured in the database, returns the default value (disabled).
   * This controls whether GTNetExchangeLog records are created for data exchanges.
   *
   * @return true if GTNet logging is enabled, false otherwise
   * @see GlobalParamKeyDefault#GLOB_KEY_GTNET_USE_LOG
   * @see GlobalParamKeyDefault#DEFAULT_GTNET_USE_LOG
   */
  public boolean isGTNetLogEnabled() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_GTNET_USE_LOG)
        .map(g -> g.getPropertyInt() != null && g.getPropertyInt() != 0)
        .orElse(GlobalParamKeyDefault.DEFAULT_GTNET_USE_LOG != 0);
  }

  public Globalparameters saveGTNetMyEntryID(Integer idGtNet) {
    Globalparameters gp = new Globalparameters(GlobalParamKeyDefault.GLOB_KEY_GTNET_MY_ENTRY_ID);
    gp.setPropertyInt(idGtNet);
    gp.setChangedBySystem(true);
    return globalparametersJpaRepository.save(gp);
  }

  /**
   * Gets the timestamp when GTNetExchange was last synchronized with peers.
   *
   * The timestamp is stored as a Date in the global parameters. If the parameter is not configured
   * or has never been set, returns the epoch (1970-01-01) indicating no sync has occurred.
   *
   * @return the last sync timestamp, or epoch (0L) if never synced
   * @see GlobalParamKeyDefault#GLOB_KEY_GTNET_EXCHANGE_SYNC_TIMESTAMP
   */
  public Date getGTNetExchangeSyncTimestamp() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_GTNET_EXCHANGE_SYNC_TIMESTAMP)
        .map(g -> DateHelper.convertToDateViaInstant(g.getPropertyDateTime()))
        .orElse(GlobalParamKeyDefault.DEFAULT_GTNET_EXCHANGE_SYNC_TIMESTAMP);
  }

  /**
   * Updates the GTNet exchange sync timestamp to the current time.
   *
   * Called after a successful GTNet exchange synchronization job to record when the last sync occurred.
   * This timestamp is used to determine which GTNetExchange entries have changed since the last sync.
   *
   * @return the saved Globalparameters entity
   * @see GlobalParamKeyDefault#GLOB_KEY_GTNET_EXCHANGE_SYNC_TIMESTAMP
   */
  public Globalparameters updateGTNetExchangeSyncTimestamp() {
    Globalparameters gp = globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_GTNET_EXCHANGE_SYNC_TIMESTAMP)
        .orElse(new Globalparameters(GlobalParamKeyDefault.GLOB_KEY_GTNET_EXCHANGE_SYNC_TIMESTAMP));
    gp.setPropertyDateTime(java.time.LocalDateTime.now());
    gp.setChangedBySystem(true);
    return globalparametersJpaRepository.save(gp);
  }

  /**
   * Retrieves a global parameter entity by its property name.
   * 
   * This is a generic method for accessing any global parameter directly. Most callers should use the specific typed
   * methods provided by this service rather than this generic accessor.
   * 
   * @param property the property name/key to look up
   * @return an Optional containing the global parameter if found, empty otherwise
   */
  public Optional<Globalparameters> getGlobalparametersByProperty(String property) {
    return globalparametersJpaRepository.findById(property);
  }

  /**
   * Gets the maximum number of retry attempts for dividend data updates. This value relates to each individual
   * security.
   * 
   * Controls how many times the system will attempt to fetch dividend information from external sources before giving
   * up.
   * 
   * @return the maximum number of dividend retry attempts
   * @see GlobalParamKeyDefault#GLOB_KEY_DIVIDEND_RETRY
   * @see GlobalParamKeyDefault#DEFAULT_DIVIDEND_RETRY
   */
  public short getMaxDividendRetry() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_DIVIDEND_RETRY)
        .map(g -> g.getPropertyInt().shortValue()).orElse(GlobalParamKeyDefault.DEFAULT_DIVIDEND_RETRY);
  }

  /**
   * Gets the maximum number of retry attempts for stock split data updates. This value relates to each individual
   * security.
   * 
   * 
   * Controls retry behavior when fetching stock split information from external data sources.
   * 
   * @return the maximum number of stock split retry attempts
   * @see GlobalParamKeyDefault#GLOB_KEY_SPLIT_RETRY
   * @see GlobalParamKeyDefault#DEFAULT_SPLIT_RETRY
   */
  public short getMaxSplitRetry() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_SPLIT_RETRY)
        .map(g -> g.getPropertyInt().shortValue()).orElse(GlobalParamKeyDefault.DEFAULT_SPLIT_RETRY);
  }

  /**
   * Gets the historical price update strategy configuration.
   * 
   * 
   * Determines whether historical price updates are performed:
   * <ul>
   * <li>0: Once per day regardless of stock exchange</li>
   * <li>1: According to individual stock exchange closing times</li>
   * </ul>
   * 
   * 
   * Using stock exchange-specific timing can provide more timely updates but requires more complex scheduling logic.
   * 
   * @return 0 for daily updates, 1 for exchange-specific timing
   * @see GlobalParamKeyDefault#GLOB_KEY_UPDATE_PRICE_BY_EXCHANGE
   * @see GlobalParamKeyDefault#DEFAULT_UPDATE_PRICE_BY_EXCHANGE
   */
  public int getUpdatePriceByStockexchange() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_UPDATE_PRICE_BY_EXCHANGE)
        .map(Globalparameters::getPropertyInt).orElse(GlobalParamKeyDefault.DEFAULT_UPDATE_PRICE_BY_EXCHANGE);
  }

  /**
   * Gets the lookback period for historical price data monitoring.
   * 
   * 
   * Instruments are only included in monitoring calculations if they have had a successful data transfer within this
   * many days from the current date. This prevents inactive or delisted instruments from skewing monitoring statistics
   * and alerts.
   * 
   * @return the number of days to look back for successful transfers
   * @see GlobalParamKeyDefault#GLOB_KEY_HISTORY_OBSERVATION_DAYS_BACK
   * @see GlobalParamKeyDefault#DEFAULT_HISTORY_OBSERVATION_DAYS_BACK
   */
  public int getHistoryObservationDaysBack() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_HISTORY_OBSERVATION_DAYS_BACK)
        .map(Globalparameters::getPropertyInt).orElse(GlobalParamKeyDefault.DEFAULT_HISTORY_OBSERVATION_DAYS_BACK);
  }

  /**
   * Gets the configured start date for data feed operations.
   * 
   * 
   * This represents the earliest date from which the system will attempt to collect historical data. Data before this
   * date is not processed to avoid unnecessary historical data collection and to maintain reasonable system
   * performance.
   * 
   * @return the start date for data feed operations
   * @throws ParseException if the configured date string cannot be parsed
   * @see GlobalParamKeyDefault#GLOB_KEY_START_FEED_DATE
   * @see GlobalParamKeyDefault#DEFAULT_START_FEED_DATE
   */
  public Date getStartFeedDate() throws ParseException {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_START_FEED_DATE)
        .map(g -> DateHelper.getDateFromLocalDate(g.getPropertyDate()))
        .orElse(GlobalParamKeyDefault.DEFAULT_START_FEED_DATE);
  }

  /**
   * Gets the alternative lookback period for intraday observation monitoring.
   * 
   * 
   * This provides an alternative time window for intraday monitoring when the primary observation criteria are not met.
   * It allows for more flexible monitoring strategies depending on market conditions and data availability.
   * 
   * @return the alternative lookback period in days for intraday monitoring
   * @see GlobalParamKeyDefault#GLOB_KEY_INTRADAY_OBSERVATION_OR_DAYS_BACK
   * @see GlobalParamKeyDefault#DEFAULT_INTRADAY_OBSERVATION_OR_DAYS_BACK
   */
  public int getIntradayObservationOrDaysBack() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_INTRADAY_OBSERVATION_OR_DAYS_BACK)
        .map(Globalparameters::getPropertyInt).orElse(GlobalParamKeyDefault.DEFAULT_INTRADAY_OBSERVATION_OR_DAYS_BACK);
  }

  /**
   * Gets the maximum number of days for currency exchange rate gap filling.
   * 
   * 
   * When historical currency exchange rate data has gaps, the system will attempt to fill missing values by
   * interpolation or using the last known rate. This parameter limits how large a gap can be filled to ensure data
   * quality and prevent propagation of stale rates.
   * 
   * @return the maximum number of days for currency rate gap filling
   * @see GlobalParamKeyDefault#GLOB_KEY_HISTORY_MAX_FILLDAYS_CURRENCY
   * @see GlobalParamKeyDefault#DEFAULT_HISTORY_MAX_FILLDAYS_CURRENCY
   */
  public int getMaxFillDaysCurrency() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_HISTORY_MAX_FILLDAYS_CURRENCY)
        .map(Globalparameters::getPropertyInt).orElse(GlobalParamKeyDefault.DEFAULT_HISTORY_MAX_FILLDAYS_CURRENCY);
  }

  /**
   * Gets the maximum number of retry attempts for historical price data updates.
   * 
   * 
   * This configuration controls how many times the system will retry fetching historical price data from external
   * sources before marking the operation as failed.
   * 
   * @return the maximum number of historical data retry attempts
   * @see GlobalParamKeyDefault#GLOB_KEY_HISTORY_RETRY
   * @see GlobalParamKeyDefault#DEFAULT_HISTORY_RETRY
   */
  public short getMaxHistoryRetry() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_HISTORY_RETRY)
        .map(g -> g.getPropertyInt().shortValue()).orElse(GlobalParamKeyDefault.DEFAULT_HISTORY_RETRY);
  }

  /**
   * Gets the percentage threshold for historical data connector failure alerts.
   * 
   * 
   * When at least this percentage of instruments using a particular historical data connector fail to update, the
   * system will generate monitoring alerts. A value of 100% indicates the connector has completely stopped working for
   * historical data retrieval.
   * 
   * @return the percentage threshold (0-100) for historical connector failure detection
   * @see GlobalParamKeyDefault#GLOB_KEY_HISTORY_OBSERVATION_FALLING_PERCENTAGE
   * @see GlobalParamKeyDefault#DEFAULT_HISTORY_OBSERVATION_FALLING_PERCENTAGE
   */
  public int getHistoryObservationFallingPercentage() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_HISTORY_OBSERVATION_FALLING_PERCENTAGE)
        .map(Globalparameters::getPropertyInt)
        .orElse(GlobalParamKeyDefault.DEFAULT_HISTORY_OBSERVATION_FALLING_PERCENTAGE);
  }

  /**
   * Gets a list of all available currencies formatted for HTML select elements.
   * 
   * 
   * This method combines standard ISO currencies with supported cryptocurrencies to provide a comprehensive list for
   * user interface elements. The list includes:
   * <ul>
   * <li>All available ISO 4217 currency codes, sorted alphabetically</li>
   * <li>Supported cryptocurrencies with localized labels indicating their crypto status</li>
   * </ul>
   * 
   * 
   * The cryptocurrency labels are localized using the current user's locale and message source configuration.
   * 
   * @return a list of currency options suitable for HTML select elements, where each option contains the currency code
   *         as both key and initial value, with cryptocurrencies having descriptive labels
   * @see GlobalConstants#CRYPTO_CURRENCY_SUPPORTED
   * @see ValueKeyHtmlSelectOptions
   */
  public List<ValueKeyHtmlSelectOptions> getCurrencies() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    final List<ValueKeyHtmlSelectOptions> currencies = Currency.getAvailableCurrencies().stream()
        .sorted((currency1, currency2) -> currency1.getCurrencyCode().compareTo(currency2.getCurrencyCode()))
        .map(currency -> new ValueKeyHtmlSelectOptions(currency.getCurrencyCode(), currency.getCurrencyCode()))
        .collect(Collectors.toList());
    // Add crypto currency
    GlobalConstants.CRYPTO_CURRENCY_SUPPORTED.forEach(cc -> currencies.add(new ValueKeyHtmlSelectOptions(cc,
        cc + "(" + messages.getMessage("cryptocurrency", null, user.createAndGetJavaLocale()) + ")")));

    return currencies;
  }

}
