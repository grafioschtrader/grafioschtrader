package grafioschtrader.repository;

import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import grafiosch.entities.Globalparameters;
import grafioschtrader.dto.PasswordRegexProperties;
import grafioschtrader.dto.TenantLimit;
import grafioschtrader.dto.ValueKeyHtmlSelectOptions;
import jakarta.persistence.EntityManager;

public interface GlobalparametersJpaRepositoryCustom {

  Map<String, Integer> getCurrencyPrecision();

  int getPrecisionForCurrency(String currency);

  int getTaskDataDaysPreserve();

  int getSecurityCurrencyIntradayUpdateTimeout();

  int getWatchlistIntradayUpdateTimeout();

  int getMaxValueByKey(String key);

  Integer getGTNetMyEntryID();

  Globalparameters saveGTNetMyEntryID(Integer idGtNet);

  EntityManager getEntityManager();

  List<TenantLimit> getMaxTenantLimitsByMsgKeys(List<String> msgKeys);

  PasswordRegexProperties getPasswordRegexProperties() throws Exception;

  /**
   * Return the maximum attempts. This value is determined by the global
   * parameters.
   *
   * @return
   */
  short getMaxHistoryRetry();

  /**
   * Return the maximum attempts. This value is determined by the global
   * parameters.
   *
   * @return
   */
  short getMaxIntraRetry();

  short getMaxDividendRetry();

  short getMaxSplitRetry();

  int getIntradayObservationOrDaysBack();

  int getIntradayObservationRetryMinus();

  int getIntradayObservationFallingPercentage();

  /**
   * Monitoring of historical price data. The instrument is only taken into
   * account if a successful transfer has taken place within the current date
   * minus this number of days. In this way, any instruments that are no longer
   * active but are listed as active do not distort the calculation.
   *
   * @return Number of days
   */
  int getHistoryObservationDaysBack();

  /**
   * Monitoring of historical price data. Transmission error with maximum
   * repetition "gt.history.retry" minus this number. If "gt.history.retry"
   * contains the value 4, this value should be 0 or 1. Thus, an instrument with 4
   * or 3 retries would be considered non-functioning.
   *
   * @return  Value that is subtracted from the "gt.history.retry" value.
   */
  int getHistoryObservationRetryMinus();

  /**
   * Monitoring of historical price data. Message when at least this percentage of
   * a connector has failed. At 100%, the connector probably no longer works at
   * all.
   *
   * @return Percentage threshold
   */
  int getHistoryObservationFallingPercentage();

  int getMaxFillDaysCurrency();

  int getJWTExpirationMinutes();

  Date getStartFeedDate() throws ParseException;

  List<ValueKeyHtmlSelectOptions> getCountriesForSelectBox();

  String getLanguageProperties(final String language);

  int getAlertBitmap();

  int getMaxLimitExceededCount();

  int getMaxSecurityBreachCount();

  List<ValueKeyHtmlSelectOptions> getAllZoneIds();

  List<ValueKeyHtmlSelectOptions> getSupportedLocales();

  Globalparameters saveOnlyAttributes(Globalparameters globalparameters) throws Exception;

  List<ValueKeyHtmlSelectOptions> getCurrencies();

  int getUpdatePriceByStockexchange();

}
