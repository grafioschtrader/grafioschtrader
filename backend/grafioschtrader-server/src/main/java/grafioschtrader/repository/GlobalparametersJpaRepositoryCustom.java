package grafioschtrader.repository;

import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import grafioschtrader.dto.PasswordRegexProperties;
import grafioschtrader.dto.TenantLimit;
import grafioschtrader.dto.ValueKeyHtmlSelectOptions;
import grafioschtrader.entities.Globalparameters;
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
  
  int getHistoryObeservationDaysBack();
  
  int getHistoryObeservationRetryMinus();
  
  int getHistoryObeservationFallingPercentage();
  
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
