package grafiosch.repository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
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

import grafiosch.BaseConstants;
import grafiosch.GlobalParamKeyBaseDefault;
import grafiosch.common.UserAccessHelper;
import grafiosch.config.ExposedResourceBundleMessageSource;
import grafiosch.dto.IPropertiesSelfCheck;
import grafiosch.dto.PasswordRegexProperties;
import grafiosch.dto.TenantLimit;
import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.entities.Globalparameters;
import grafiosch.entities.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Implementation of custom global parameters repository operations. Provides access to system-wide configuration
 * parameters with database storage, caching mechanisms, and blob-based property validation. Handles
 * internationalization support and admin-only modifications.
 */
public class GlobalparametersJpaRepositoryImpl implements GlobalparametersJpaRepositoryCustom {

  final static public Map<String, Class<?>> propertiesClassMap = new HashMap<>();
  static {
    propertiesClassMap.put(GlobalParamKeyBaseDefault.GLOB_KEY_PASSWORT_REGEX, PasswordRegexProperties.class);
  }

  @PersistenceContext
  private EntityManager entityManager;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private MessageSource messages;

  @Override
  public int getMaxValueByKey(String key) {
    return TenantLimitsHelper.getMaxValueByKey(entityManager, key);
  }

  @Override
  public EntityManager getEntityManager() {
    return entityManager;
  }

  @Override
  public int getTaskDataDaysPreserve() {
    return globalparametersJpaRepository.findById(GlobalParamKeyBaseDefault.GLOB_KEY_TASK_DATA_DAYS_PRESERVE)
        .map(Globalparameters::getPropertyInt).orElse(GlobalParamKeyBaseDefault.DEFAULT_TASK_DATA_DAYS_PRESERVE);
  }

  @Override
  public List<TenantLimit> getMaxTenantLimitsByMsgKeys(List<String> msgKeys) {
    return TenantLimitsHelper.getMaxTenantLimitsByMsgKeys(entityManager, msgKeys);
  }

  @Override
  public int getAlertBitmap() {
    return globalparametersJpaRepository.findById(GlobalParamKeyBaseDefault.GLOB_KEY_ALERT_MAIL)
        .map(Globalparameters::getPropertyInt).orElse(GlobalParamKeyBaseDefault.DEFAULT_ALERT_MAIL);
  }

  @Override
  public int getMaxLimitExceededCount() {
    return globalparametersJpaRepository.findById(BaseConstants.GLOB_KEY_MAX_LIMIT_EXCEEDED_COUNT)
        .map(Globalparameters::getPropertyInt).orElse(GlobalParamKeyBaseDefault.DEFAULT_MAX_LIMIT_EXCEEDED_COUNT);
  }

  @Override
  public int getJWTExpirationMinutes() {
    return globalparametersJpaRepository.findById(GlobalParamKeyBaseDefault.GLOB_KEY_JWT_EXPIRATION_MINUTES)
        .map(Globalparameters::getPropertyInt)
        .orElse(GlobalParamKeyBaseDefault.DEFAULT_GLOB_KEY_JWT_EXPIRATION_MINUTES);
  }

  @Override
  public int getMaxSecurityBreachCount() {
    return globalparametersJpaRepository.findById(BaseConstants.GLOB_KEY_MAX_SECURITY_BREACH_COUNT)
        .map(Globalparameters::getPropertyInt).orElse(GlobalParamKeyBaseDefault.DEFAULT_MAX_SECURITY_BREACH_COUNT);
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
      boolean hasPrefix = BaseConstants.PREFIXES_PARAM.stream().anyMatch(key::startsWith);
      jsonObject.put(hasPrefix || key.startsWith("UDF_") ? key : key.toUpperCase().replaceAll("\\.", "_"),
          entry.getValue());
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
  public List<ValueKeyHtmlSelectOptions> getSupportedLocales() {
    final List<ValueKeyHtmlSelectOptions> dropdownValues = new ArrayList<>();
    for (final Locale loc : Locale.getAvailableLocales()) {
      if (BaseConstants.G_LANGUAGE_CODES.contains(loc.getLanguage())) {
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
    throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
  }

  /**
   * Validates blob properties before saving by deserializing and checking configuration objects. Uses
   * propertiesClassMap to determine the appropriate validation class for blob properties. Performs syntax validation
   * and completeness checks using the IPropertiesSelfCheck interface.
   * 
   * @param updGp the global parameters entity containing blob property data to validate
   * @throws Exception if blob property validation fails, object instantiation fails, or localized error message
   *                   retrieval fails
   */
  private void checkBlobPropertyBeforeSave(Globalparameters updGp) throws Exception {
    if (updGp.getPropertyName().endsWith(BaseConstants.BLOB_PROPERTIES)) {
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
        .findById(GlobalParamKeyBaseDefault.GLOB_KEY_PASSWORT_REGEX);
    PasswordRegexProperties prp = new PasswordRegexProperties();
    if (globalparametersOpt.isEmpty()) {
      prp = new PasswordRegexProperties(BaseConstants.STANDARD_PASSWORD_REGEX,
          BaseConstants.G_LANGUAGE_CODES.stream().collect(
              Collectors.toMap(lang -> lang, lang -> messages.getMessage("gt.password.regex", null, Locale.of(lang)))),
          false);
      Globalparameters gp = new Globalparameters(GlobalParamKeyBaseDefault.GLOB_KEY_PASSWORT_REGEX);
      gp.transformClassIntoBlobPropertis(prp);
      globalparametersJpaRepository.save(gp);
    } else {
      prp = (PasswordRegexProperties) globalparametersOpt.get().transformBlobPropertiesIntoClass(prp);
    }

    return prp;
  }

}
