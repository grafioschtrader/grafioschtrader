package grafiosch.repository;

import java.util.List;

import grafiosch.dto.PasswordRegexProperties;
import grafiosch.dto.TenantLimit;
import grafiosch.dto.ValueKeyHtmlSelectOptions;
import grafiosch.entities.Globalparameters;
import jakarta.persistence.EntityManager;

public interface GlobalparametersJpaRepositoryCustom {

  EntityManager getEntityManager();

  /**
   * How many days of background task history are kept.
   * Uses GLOB_KEY_TASK_DATA_DAYS_PRESERVE with default fallback to DEFAULT_TASK_DATA_DAYS_PRESERVE (10 days).
   * 
   * @return the number of days to preserve task data history
   */
  int getTaskDataDaysPreserve();

  /**
   * There is a maximum number of data records for certain entities, which can be queried.
   * Delegates to TenantLimitsHelper for cached retrieval with database override support.
   * 
   * @param key the global parameter key in format "gt.limit.day.{EntityClassName}"
   * @return the maximum value for the specified key from database or default configuration
   */
  int getMaxValueByKey(String key);

  /**
   * Retrieves tenant limit information using message keys.
   * Converts message keys to parameter keys and delegates to TenantLimitsHelper.
   * 
   * @param msgKeys list of message keys in format "LIMIT_DAY_{ENTITYNAME}"
   * @return a list of TenantLimit objects with current usage vs limits for authenticated user's tenant
   */
  List<TenantLimit> getMaxTenantLimitsByMsgKeys(List<String> msgKeys);

  /**
   * Gets the password validation regex properties from blob storage.
   * Creates default configuration if not exists, using STANDARD_PASSWORD_REGEX and localized error messages.
   * Validates configuration completeness including regex syntax and language coverage.
   * 
   * @return the password regex configuration with validation rules and error messages
   * @throws Exception if blob properties cannot be parsed or are invalid
   */
  PasswordRegexProperties getPasswordRegexProperties() throws Exception;

  /**
   * Gets the JWT token expiration time in minutes.
   * Uses GLOB_KEY_JWT_EXPIRATION_MINUTES with default fallback to 1440 minutes (24 hours).
   * 
   * @return the JWT token expiration time in minutes
   */
  int getJWTExpirationMinutes();

  /**
   * Gets a list of countries formatted for HTML select box options.
   * Uses ISO country codes and displays country names in the authenticated user's locale.
   * Results are sorted alphabetically by display name.
   * 
   * @return list of country options with ISO codes as keys and localized names as values
   */
  List<ValueKeyHtmlSelectOptions> getCountriesForSelectBox();

  /**
   * Gets language-specific properties as a JSON string.
   * Retrieves message properties for the specified language and converts keys to uppercase format.
   * Keys starting with "gt." or "UDF_" preserve original format, others are converted to UPPER_CASE.
   * 
   * @param language the language code to retrieve properties for (e.g., "en", "de")
   * @return JSON string containing localized message properties
   */
  String getLanguageProperties(final String language);

  /**
   * Gets the alert notification bitmap configuration.
   * Uses GLOB_KEY_ALERT_MAIL with default fallback to Integer.MAX_VALUE (all alerts enabled).
   * 
   * @return the alert bitmap value for email notification configuration
   */
  int getAlertBitmap();
  
  /**
   * Maximum number of violations against request limit before blocking the user.
   * Uses GLOB_KEY_MAX_LIMIT_EXCEEDED_COUNT with default fallback to 20 violations.
   * 
   * @return the maximum limit exceeded count before user blocking
   */
  int getMaxLimitExceededCount();

  /**
   * Gets the maximum number of security breaches allowed before action is taken.
   * Uses GLOB_KEY_MAX_SECURITY_BREACH_COUNT with default fallback to 5 breaches.
   * 
   * @return the maximum security breach count before enforcement action
   */
  int getMaxSecurityBreachCount();

  /**
   * Gets all available time zone IDs formatted for select boxes.
   * Includes UTC offset information for each zone calculated from current LocalDateTime.
   * Results are sorted alphabetically by display value including offset.
   * 
   * @return list of time zone options with zone IDs as keys and "ZoneId (UTC±offset)" as values
   */
  List<ValueKeyHtmlSelectOptions> getAllZoneIds();
  
  /**
   * Gets all supported locales formatted for select boxes.
   * Filters available locales to only include supported G_LANGUAGE_CODES ("de", "en").
   * Converts locale format from underscore to hyphen and limits to 5 characters or less.
   * 
   * @return list of locale options sorted alphabetically (e.g., "en-US", "de-DE")
   */
  List<ValueKeyHtmlSelectOptions> getSupportedLocales();

  Globalparameters saveGTNetMyEntryID(Integer idGtNet);
  
  Integer getGTNetMyEntryID();
  
  /**
   * Checks whether GTNet functionality is enabled.
   *
   * GTNet is enabled when the global parameter 'gt.gtnet.use' has a non-zero property_int value. If the parameter is not
   * configured in the database, returns the default value (disabled).
   *
   * @return true if GTNet is enabled, false otherwise
   */
   boolean isGTNetEnabled(); 
  
  
  Globalparameters saveOnlyAttributes(Globalparameters globalparameters) throws Exception;

}
