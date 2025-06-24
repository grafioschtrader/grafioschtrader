package grafioschtrader.connector.instrument;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.entities.ConnectorApiKey;
import grafiosch.repository.ConnectorApiKeyJpaRepository;
import grafiosch.types.ISubscriptionType;
import grafioschtrader.entities.Securitycurrency;

/**
 * Abstract base class for feed connectors that require API key authentication and subscription management. This class
 * extends BaseFeedConnector to add secure API key handling, subscription validation, and enhanced error reporting that
 * protects sensitive authentication information.
 * 
 * <h3>Subscription Management</h3>
 * <p>
 * Many data providers offer different subscription tiers with varying capabilities. This class provides infrastructure
 * to:
 * </p>
 * <ul>
 * <li>Validate that the current subscription supports requested data types</li>
 * <li>Manage subscription type information</li>
 * <li>Provide extension points for subscription-specific validation</li>
 * </ul>
 * 
 * <h3>Security Features</h3>
 * <p>
 * The class implements several security measures:
 * </p>
 * <ul>
 * <li><strong>API Key Hiding:</strong> Automatically removes API keys from URLs in error messages</li>
 * <li><strong>Parameter Replacement:</strong> Replaces API key parameters with placeholder text</li>
 * <li><strong>URL Sanitization:</strong> Removes query parameters that might contain sensitive data</li>
 * </ul>
 * 
 * <h3>Implementation Guidelines</h3>
 * <p>
 * Subclasses should:
 * </p>
 * <ul>
 * <li>Override hasAPISubscriptionSupport() to implement subscription validation</li>
 * <li>Use getApiKey() to access the API key for requests</li>
 * <li>Use standardApiKeyReplacementForErrors() for consistent error message sanitization</li>
 * <li>Call resetConnectorApiKey() when API key configuration changes</li>
 * </ul>
 */
public abstract class BaseFeedApiKeyConnector extends BaseFeedConnector {

  protected final static String ERROR_API_KEY_REPLACEMENT = "???";

  @Autowired
  private ConnectorApiKeyJpaRepository connectorApiKeyJpaRepository;

  /**
   * Cached API key entity to avoid repeated database lookups. Loaded lazily when first accessed and cached for the
   * lifetime of the connector instance.
   */
  private ConnectorApiKey connectorApiKey;

  /**
   * Constructs a new API key-based feed connector with the specified configuration.
   * 
   * @param supportedFeed   mapping of feed support types to their identifier requirements
   * @param id              the short identifier for this connector (will be prefixed with ID_PREFIX)
   * @param readableNameKey the human-readable name for display purposes
   * @param regexStrPattern regular expression pattern for URL validation, or null if no pattern validation needed
   * @param urlCheckSet     set of URL check types to perform (HISTORY, INTRADAY, or both)
   */
  protected BaseFeedApiKeyConnector(final Map<FeedSupport, FeedIdentifier[]> supportedFeed, final String id,
      final String readableNameKey, String regexStrPattern, EnumSet<UrlCheck> urlCheckSet) {
    super(supportedFeed, id, readableNameKey, regexStrPattern, urlCheckSet);
  }

  /**
   * Enhanced URL validation that includes API subscription verification. Before performing standard URL validation,
   * this method checks whether the data provider's subscription tier covers the requested service type. This prevents
   * configuration of services that are not available under the current subscription plan.
   * 
   * @param securitycurrency the security or currency pair to validate
   * @param feedSupport      the type of feed being validated
   * @param <S>              the type of security currency
   */
  @Override
  public <S extends Securitycurrency<S>> void checkAndClearSecuritycurrencyUrlExtend(
      Securitycurrency<S> securitycurrency, FeedSupport feedSupport) {
    hasAPISubscriptionSupport(securitycurrency, feedSupport);
    super.checkAndClearSecuritycurrencyUrlExtend(securitycurrency, feedSupport);
  }

  public <S extends Securitycurrency<S>> void hasAPISubscriptionSupport(Securitycurrency<S> securitycurrency,
      FeedSupport feedSupport) {
  }

  /**
   * Sanitizes URLs by replacing API key parameters with placeholder text for error reporting. This method uses regular
   * expressions to find and replace API key values in URLs while preserving the URL structure for debugging purposes.
   * 
   * @param url            the URL that may contain an API key parameter
   * @param tokenParamName the name of the parameter that contains the API key (e.g., "apikey", "token")
   * @return the sanitized URL with the API key value replaced by ERROR_API_KEY_REPLACEMENT
   */
  protected String standardApiKeyReplacementForErrors(String url, String tokenParamName) {
    return url.replaceFirst("(.*" + tokenParamName + "=)([^&\\n]*)(\\n|\\t|.*)*",
        "$1" + ERROR_API_KEY_REPLACEMENT + "$3");
  }

  /**
   * Hides API keys and other sensitive query parameters from URLs in error messages. This method removes all query
   * parameters from URLs to prevent accidental exposure of authentication information in logs and error reports. This
   * is a more aggressive approach than parameter-specific replacement.
   * 
   * @param url the URL that may contain sensitive query parameters
   * @return the URL with all query parameters removed (everything after the first '?')
   */
  @Override
  public String hideApiKeyForError(String url) {
    return url.replaceFirst("(^.*)(\\?.*$)", "$1");
  }

  @Override
  public boolean isActivated() {
    getConnectorApiKey();
    return connectorApiKey != null;
  }

  /**
   * Clears the cached API key, forcing it to be reloaded from the database on next access. This method should be called
   * when API key configuration changes or when the connector needs to refresh its authentication credentials.
   * 
   * <p>
   * Common scenarios for calling this method:
   * </p>
   * <ul>
   * <li>After updating API key configuration in the admin interface</li>
   * <li>When authentication errors suggest the cached key may be stale</li>
   * <li>During connector reconfiguration or testing</li>
   * </ul>
   */
  public void resetConnectorApiKey() {
    connectorApiKey = null;
  }

  protected String getApiKey() {
    getConnectorApiKey();
    return connectorApiKey == null ? null : connectorApiKey.getApiKey();
  }

  public ISubscriptionType getSubscriptionType() {
    getConnectorApiKey();
    return connectorApiKey.getSubscriptionType();
  }

  /**
   * Loads the API key configuration from the database if not already cached. This method implements lazy loading to
   * avoid unnecessary database queries while ensuring the API key is available when needed. The loaded API key is
   * cached for the lifetime of the connector instance.
   * 
   * <p>
   * The API key is looked up using the connector's short ID as the primary key. If no configuration is found, the
   * connectorApiKey remains null, indicating the connector is not properly configured.
   * </p>
   */
  private void getConnectorApiKey() {
    if (connectorApiKey == null) {
      Optional<ConnectorApiKey> connectorApiKeyOpt = connectorApiKeyJpaRepository.findById(getShortID());
      if (connectorApiKeyOpt.isPresent()) {
        connectorApiKey = connectorApiKeyOpt.get();
      }
    }
  }
}
