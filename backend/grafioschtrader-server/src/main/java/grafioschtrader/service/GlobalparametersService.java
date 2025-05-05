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

import grafiosch.common.DateHelper;
import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.entities.Globalparameters;
import grafiosch.entities.User;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafioschtrader.GlobalConstants;
import grafioschtrader.GlobalParamKeyDefault;

@Service
public class GlobalparametersService {

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;
  
  @Autowired
  private MessageSource messages;
  
 
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
  
  
  public int getPrecisionForCurrency(String currency) {
    return getCurrencyPrecision().getOrDefault(currency, GlobalConstants.FID_STANDARD_FRACTION_DIGITS);
  }
  
  /**
   * Return the maximum attempts. This value is determined by the global
   * parameters.
   */
  public short getMaxIntraRetry() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_INTRA_RETRY)
        .map(g -> g.getPropertyInt().shortValue()).orElse(GlobalParamKeyDefault.DEFAULT_INTRA_RETRY);
  }
  
  public int getIntradayObservationRetryMinus() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_INTRADAY_OBSERVATION_RETRY_MINUS)
        .map(Globalparameters::getPropertyInt).orElse(GlobalParamKeyDefault.DEFAULT_INTRADAY_OBSERVATION_RETRY_MINUS);
  }

  public int getIntradayObservationFallingPercentage() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_INTRADAY_OBSERVATION_FALLING_PERCENTAGE)
        .map(Globalparameters::getPropertyInt).orElse(GlobalParamKeyDefault.DEFAULT_INTRADAY_OBSERVATION_FALLING_PERCENTAGE);
  }
  
  public int getSecurityCurrencyIntradayUpdateTimeout() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_SC_INTRA_UPDATE_TIMEOUT_SECONDS)
        .map(Globalparameters::getPropertyInt).orElse(GlobalParamKeyDefault.DEFAULT_SC_INTRA_UPDATE_TIMEOUT_SECONDS);
  }
  
  /**
   * Monitoring of historical price data. Transmission error with maximum
   * repetition "gt.history.retry" minus this number. If "gt.history.retry"
   * contains the value 4, this value should be 0 or 1. Thus, an instrument with 4
   * or 3 retries would be considered non-functioning.
   *
   * @return  Value that is subtracted from the "gt.history.retry" value.
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
  
  
  public Globalparameters saveGTNetMyEntryID(Integer idGtNet) {
    Globalparameters gp = new Globalparameters(GlobalParamKeyDefault.GLOB_KEY_GTNET_MY_ENTRY_ID);
    gp.setPropertyInt(idGtNet);
    gp.setChangedBySystem(true);
    return globalparametersJpaRepository.save(gp);
  }
  
  public Optional<Globalparameters> getGlobalparametersByProperty(String property) {
    return globalparametersJpaRepository.findById(property);
  }
  
  public short getMaxDividendRetry() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_DIVIDEND_RETRY)
        .map(g -> g.getPropertyInt().shortValue()).orElse(GlobalParamKeyDefault.DEFAULT_DIVIDEND_RETRY);
  }

  public short getMaxSplitRetry() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_SPLIT_RETRY)
        .map(g -> g.getPropertyInt().shortValue()).orElse(GlobalParamKeyDefault.DEFAULT_SPLIT_RETRY);
  }
  
  /**
   * At 0 the update of the historical prices is done once otherwise according to stock market close
   */
  public int getUpdatePriceByStockexchange() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_UPDATE_PRICE_BY_EXCHANGE)
        .map(Globalparameters::getPropertyInt).orElse(GlobalParamKeyDefault.DEFAULT_UPDATE_PRICE_BY_EXCHANGE);
  }
  
  /**
   * Monitoring of historical price data. The instrument is only taken into
   * account if a successful transfer has taken place within the current date
   * minus this number of days. In this way, any instruments that are no longer
   * active but are listed as active do not distort the calculation.
   *
   * @return Number of days
   */
  public int getHistoryObservationDaysBack() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_HISTORY_OBSERVATION_DAYS_BACK)
        .map(Globalparameters::getPropertyInt).orElse(GlobalParamKeyDefault.DEFAULT_HISTORY_OBSERVATION_DAYS_BACK);
  }
  
  public Date getStartFeedDate() throws ParseException {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_START_FEED_DATE)
        .map(g -> DateHelper.getDateFromLocalDate(g.getPropertyDate()))
        .orElse(GlobalParamKeyDefault.DEFAULT_START_FEED_DATE);
  }
  
  public int getIntradayObservationOrDaysBack() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_INTRADAY_OBSERVATION_OR_DAYS_BACK)
        .map(Globalparameters::getPropertyInt).orElse(GlobalParamKeyDefault.DEFAULT_INTRADAY_OBSERVATION_OR_DAYS_BACK);
  }
  
  
  public int getMaxFillDaysCurrency() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_HISTORY_MAX_FILLDAYS_CURRENCY)
        .map(Globalparameters::getPropertyInt).orElse(GlobalParamKeyDefault.DEFAULT_HISTORY_MAX_FILLDAYS_CURRENCY);
  }
  
  
  /**
   * Return the maximum attempts. This value is determined by the global
   * parameters.
    */
  public short getMaxHistoryRetry() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_HISTORY_RETRY)
        .map(g -> g.getPropertyInt().shortValue()).orElse(GlobalParamKeyDefault.DEFAULT_HISTORY_RETRY);
  }
  
  /**
   * Monitoring of historical price data. Message when at least this percentage of
   * a connector has failed. At 100%, the connector probably no longer works at
   * all.
   *
   * @return Percentage threshold
   */
  public int getHistoryObservationFallingPercentage() {
    return globalparametersJpaRepository.findById(GlobalParamKeyDefault.GLOB_KEY_HISTORY_OBSERVATION_FALLING_PERCENTAGE)
        .map(Globalparameters::getPropertyInt).orElse(GlobalParamKeyDefault.DEFAULT_HISTORY_OBSERVATION_FALLING_PERCENTAGE);
  }
  
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
