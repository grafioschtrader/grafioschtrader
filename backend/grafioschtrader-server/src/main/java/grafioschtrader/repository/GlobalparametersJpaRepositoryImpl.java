package grafioschtrader.repository;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DateHelper;
import grafioschtrader.common.UserAccessHelper;
import grafioschtrader.config.ExposedResourceBundleMessageSource;
import grafioschtrader.dto.IPropertiesSelfCheck;
import grafioschtrader.dto.PasswordRegexProperties;
import grafioschtrader.dto.TenantLimit;
import grafioschtrader.dto.ValueKeyHtmlSelectOptions;
import grafioschtrader.entities.Globalparameters;
import grafioschtrader.entities.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

public class GlobalparametersJpaRepositoryImpl implements GlobalparametersJpaRepositoryCustom {

  final static public Map<String, Class<?>> propertiesClassMap = new HashMap<>();
  static {
    propertiesClassMap.put(Globalparameters.GLOB_KEY_PASSWORT_REGEX, PasswordRegexProperties.class);
  }


  @PersistenceContext
  private EntityManager entityManager;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private MessageSource messages;

  @Override
  public int getMaxValueByKey(String key) {
    return TenantLimitsHelper.getMaxValueByKey(globalparametersJpaRepository, key);
  }

  @Override
  public EntityManager getEntityManager() {
    return entityManager;
  }

  @Override
  public Map<String, Integer> getCurrencyPrecision() {
    Map<String, Integer> currencyPrecisionMap = new HashMap<>();
    String curPrecision = globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_CURRENCY_PRECISION)
        .map(Globalparameters::getPropertyString).orElse(Globalparameters.DEFAULT_CURRENCY_PRECISION);
    String[] curSinglePre = curPrecision.split(",");

    for (String element : curSinglePre) {
      String[] pair = element.split("=");
      currencyPrecisionMap.put(pair[0], Integer.parseInt(pair[1]));
    }
    return currencyPrecisionMap;
  }

  @Override
  public int getPrecisionForCurrency(String currency) {
    return getCurrencyPrecision().getOrDefault(currency, GlobalConstants.FID_STANDARD_FRACTION_DIGITS);
  }

  @Override
  public Integer getGTNetMyEntryID() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_GTNET_MY_ENTRY_ID)
        .map(Globalparameters::getPropertyInt).orElse(null);
  }

  @Override
  public Globalparameters saveGTNetMyEntryID(Integer idGtNet) {
    Globalparameters gp = new Globalparameters(Globalparameters.GLOB_KEY_GTNET_MY_ENTRY_ID);
    gp.setPropertyInt(idGtNet);
    gp.setChangedBySystem(true);
    return globalparametersJpaRepository.save(gp);
  }

  @Override
  public int getTaskDataDaysPreserve() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_TASK_DATA_DAYS_PRESERVE)
        .map(Globalparameters::getPropertyInt).orElse(Globalparameters.DEFAULT_TASK_DATA_DAYS_PRESERVE);
  }

  @Override
  public List<TenantLimit> getMaxTenantLimitsByMsgKeys(List<String> msgKeys) {
    return TenantLimitsHelper.getMaxTenantLimitsByMsgKeys(globalparametersJpaRepository, msgKeys);
  }

  @Override
  public int getSecurityCurrencyIntradayUpdateTimeout() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_SC_INTRA_UPDATE_TIMEOUT_SECONDS)
        .map(Globalparameters::getPropertyInt).orElse(Globalparameters.DEFAULT_SC_INTRA_UPDATE_TIMEOUT_SECONDS);
  }

  @Override
  public int getAlertBitmap() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_ALERT_MAIL)
        .map(Globalparameters::getPropertyInt).orElse(Globalparameters.DEFAULT_ALERT_MAIL);
  }

  @Override
  public int getWatchlistIntradayUpdateTimeout() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_W_INTRA_UPDATE_TIMEOUT_SECONDS)
        .map(Globalparameters::getPropertyInt).orElse(Globalparameters.DEFAULT_W_INTRA_UPDATE_TIMEOUT_SECONDS);
  }

  @Override
  public int getMaxLimitExceededCount() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_MAX_LIMIT_EXCEEDED_COUNT)
        .map(Globalparameters::getPropertyInt).orElse(Globalparameters.DEFAULT_MAX_LIMIT_EXCEEDED_COUNT);
  }


  @Override
  public int getJWTExpirationMinutes() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_JWT_EXPIRATION_MINUTES)
        .map(Globalparameters::getPropertyInt).orElse(Globalparameters.DEFAULT_GLOB_KEY_JWT_EXPIRATION_MINUTES);
  }

  @Override
  public int getMaxSecurityBreachCount() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_MAX_SECURITY_BREACH_COUNT)
        .map(Globalparameters::getPropertyInt).orElse(Globalparameters.DEFAULT_MAX_SECURITY_BREACH_COUNT);
  }

  @Override
  public short getMaxHistoryRetry() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_HISTORY_RETRY)
        .map(g -> g.getPropertyInt().shortValue()).orElse(Globalparameters.DEFAULT_HISTORY_RETRY);
  }

  @Override
  public short getMaxIntraRetry() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_INTRA_RETRY)
        .map(g -> g.getPropertyInt().shortValue()).orElse(Globalparameters.DEFAULT_INTRA_RETRY);
  }

  @Override
  public short getMaxDividendRetry() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_DIVIDEND_RETRY)
        .map(g -> g.getPropertyInt().shortValue()).orElse(Globalparameters.DEFAULT_DIVIDEND_RETRY);
  }

  @Override
  public short getMaxSplitRetry() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_SPLIT_RETRY)
        .map(g -> g.getPropertyInt().shortValue()).orElse(Globalparameters.DEFAULT_SPLIT_RETRY);
  }

  @Override
  public int getMaxFillDaysCurrency() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_HISTORY_MAX_FILLDAYS_CURRENCY)
        .map(Globalparameters::getPropertyInt).orElse(Globalparameters.DEFAULT_HISTORY_MAX_FILLDAYS_CURRENCY);
  }

  @Override
  public int getIntradayObservationOrDaysBack() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_INTRADAY_OBSERVATION_OR_DAYS_BACK)
        .map(Globalparameters::getPropertyInt).orElse(Globalparameters.DEFAULT_INTRADAY_OBSERVATION_OR_DAYS_BACK);
  }

  @Override
  public int getIntradayObservationRetryMinus() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_INTRADAY_OBSERVATION_RETRY_MINUS)
        .map(Globalparameters::getPropertyInt).orElse(Globalparameters.DEFAULT_INTRADAY_OBSERVATION_RETRY_MINUS);
  }

  @Override
  public int getIntradayObservationFallingPercentage() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_INTRADAY_OBSERVATION_FALLING_PERCENTAGE)
        .map(Globalparameters::getPropertyInt).orElse(Globalparameters.DEFAULT_INTRADAY_OBSERVATION_FALLING_PERCENTAGE);
  }

  @Override
  public int getHistoryObservationDaysBack() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_HISTORY_OBSERVATION_DAYS_BACK)
        .map(Globalparameters::getPropertyInt).orElse(Globalparameters.DEFAULT_HISTORY_OBSERVATION_DAYS_BACK);
  }

  @Override
  public int getHistoryObservationRetryMinus() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_HISTORY_OBSERVATION_RETRY_MINUS)
        .map(Globalparameters::getPropertyInt).orElse(Globalparameters.DEFAULT_HISTORY_OBSERVATION_RETRY_MINUS);
  }

  @Override
  public int getHistoryObservationFallingPercentage() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_HISTORY_OBSERVATION_FALLING_PERCENTAGE)
        .map(Globalparameters::getPropertyInt).orElse(Globalparameters.DEFAULT_HISTORY_OBSERVATION_FALLING_PERCENTAGE);
  }


  @Override
  public Date getStartFeedDate() throws ParseException {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_START_FEED_DATE)
        .map(g -> DateHelper.getDateFromLocalDate(g.getPropertyDate()))
        .orElse(Globalparameters.DEFAULT_START_FEED_DATE);
  }

  @Override
  public int getUpdatePriceByStockexchange() {
    return globalparametersJpaRepository.findById(Globalparameters.GLOB_KEY_UPDATE_PRICE_BY_EXCHANGE)
        .map(Globalparameters::getPropertyInt).orElse(Globalparameters.DEFAULT_UPDATE_PRICE_BY_EXCHANGE);
  }

  @Override
  public List<ValueKeyHtmlSelectOptions> getCountriesForSelectBox() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    String[] locales = Locale.getISOCountries();
    List<ValueKeyHtmlSelectOptions> valueKeyHtmlSelectOptions = new ArrayList<>();
    Locale userLocale = user.createAndGetJavaLocale();
    for (String countryCode : locales) {
      Locale obj = Locale.of("", countryCode);
      valueKeyHtmlSelectOptions.add(new ValueKeyHtmlSelectOptions(obj.getCountry(), obj.getDisplayCountry(userLocale)));
    }
    Collections.sort(valueKeyHtmlSelectOptions);
    return valueKeyHtmlSelectOptions;
  }

  @Override
  public String getLanguageProperties(final String language) {
    Locale locale = Locale.forLanguageTag(language);
    Properties properties = ((ExposedResourceBundleMessageSource) messages).getMessages(locale);
    JSONObject jsonObject = new JSONObject();
    for (Entry<Object, Object> entry : properties.entrySet()) {
      String key = entry.getKey().toString();
      jsonObject.put(key.startsWith("gt.") || key.startsWith("UDF_") ? key : key.toUpperCase().replaceAll("\\.", "_"), entry.getValue());
    }
    return jsonObject.toString();
  }

  @Override
  public List<ValueKeyHtmlSelectOptions> getAllZoneIds() {
    final List<ValueKeyHtmlSelectOptions> dropdownValues = new ArrayList<>();
    final LocalDateTime dt = LocalDateTime.now();

    for (final String zoneId : ZoneId.getAvailableZoneIds()) {
      final ZoneId zone = ZoneId.of(zoneId);
      final ZonedDateTime zdt = dt.atZone(zone);
      final ZoneOffset zos = zdt.getOffset();
      final String offset = zos.getId().replaceAll("Z", "+00:00");
      dropdownValues.add(new ValueKeyHtmlSelectOptions(zone.toString(), zone.toString() + " (UTC" + offset + ")"));
    }
    return dropdownValues.stream().sorted((zone1, zone2) -> zone1.value.compareTo(zone2.value))
        .collect(Collectors.toList());
  }

  @Override
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

  @Override
  public List<ValueKeyHtmlSelectOptions> getSupportedLocales() {
    final List<ValueKeyHtmlSelectOptions> dropdownValues = new ArrayList<>();
    for (final Locale loc : Locale.getAvailableLocales()) {
      if (GlobalConstants.GT_LANGUAGE_CODES.contains(loc.getLanguage())) {
        final String localeString = loc.toString().replace("_", "-");
        if (localeString.length() <= 5) {
          dropdownValues.add(new ValueKeyHtmlSelectOptions(localeString, localeString));
        }
      }
    }
    return dropdownValues.stream().sorted((x, y) -> x.value.compareTo(y.value)).collect(Collectors.toList());
  }

  @Override
  public Globalparameters saveOnlyAttributes(Globalparameters updGp) throws Exception {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (UserAccessHelper.isAdmin(user)) {
      Optional<Globalparameters> existingGpOpt = globalparametersJpaRepository.findById(updGp.getPropertyName());
      if (existingGpOpt.isPresent()) {
        Globalparameters existingGp = existingGpOpt.get();
        checkBlobPropertyBeforeSave(updGp);
        existingGp.replaceExistingPropertyValue(updGp);
        existingGp = globalparametersJpaRepository.save(existingGp);
        Globalparameters.resetDBValueOfKey(existingGp.getPropertyName());
        return existingGp;
      }
    }
    throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
  }

  private void checkBlobPropertyBeforeSave(Globalparameters updGp) throws Exception {
    if (updGp.getPropertyName().endsWith(Globalparameters.BLOB_PROPERTIES)) {
      Class<?> clazz = propertiesClassMap.get(updGp.getPropertyName());
      String errorMsg = updGp
          .checkBlobPropertyBeforeSave((IPropertiesSelfCheck) clazz.getDeclaredConstructor().newInstance());
      if (errorMsg != null) {
        final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
        throw new IllegalArgumentException(messages.getMessage(errorMsg, null, user.createAndGetJavaLocale()));
      }
    }
  }

  @Override
  public PasswordRegexProperties getPasswordRegexProperties() throws Exception {
    Optional<Globalparameters> globalparametersOpt = globalparametersJpaRepository
        .findById(Globalparameters.GLOB_KEY_PASSWORT_REGEX);
    PasswordRegexProperties prp = new PasswordRegexProperties();
    if (globalparametersOpt.isEmpty()) {
      prp = new PasswordRegexProperties(GlobalConstants.STANDARD_PASSWORD_REGEX,
          GlobalConstants.GT_LANGUAGE_CODES.stream().collect(
              Collectors.toMap(lang -> lang, lang -> messages.getMessage("gt.password.regex", null, Locale.of(lang)))),
          false);
      Globalparameters gp = new Globalparameters(Globalparameters.GLOB_KEY_PASSWORT_REGEX);
      gp.transformClassIntoBlobPropertis(prp);
      globalparametersJpaRepository.save(gp);
    } else {
      prp = (PasswordRegexProperties) globalparametersOpt.get().transformBlobPropertiesIntoClass(prp);
    }

    return prp;
  }

}
