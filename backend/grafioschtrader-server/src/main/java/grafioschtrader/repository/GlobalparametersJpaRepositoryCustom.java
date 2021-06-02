package grafioschtrader.repository;

import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;

import grafioschtrader.dto.TenantLimit;
import grafioschtrader.dto.ValueKeyHtmlSelectOptions;
import grafioschtrader.entities.Globalparameters;

public interface GlobalparametersJpaRepositoryCustom {
  
  Map<String, Integer> getCurrencyPrecision();

  int getPrecisionForCurrency(String currency);
  
  int getTaskDataDaysPreserve();
  
  int getSecurityCurrencyIntradayUpdateTimeout();

  int getWatchlistIntradayUpdateTimeout();

  int getMaxValueByKey(String key);

  EntityManager getEntityManager();

  List<TenantLimit> getMaxTenantLimitsByMsgKeys(List<String> msgKeys);

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

  int getMaxFillDaysCurrency();

  Date getStartFeedDate() throws ParseException;

  int getMaxLimitExceededCount();

  int getMaxSecurityBreachCount();

  List<ValueKeyHtmlSelectOptions> getAllZoneIds();

  List<ValueKeyHtmlSelectOptions> getSupportedLocales();

  Globalparameters saveOnlyAttributes(Globalparameters globalparameters);

  List<ValueKeyHtmlSelectOptions> getCurrencies();
  
}
