package grafioschtrader.security;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import grafiosch.dto.ConfigurationWithLogin;
import grafiosch.security.TokenAuthentication;
import grafiosch.security.UserAuthentication;
import grafioschtrader.GlobalConstants;
import grafioschtrader.config.FeatureConfig;
import grafioschtrader.service.GlobalparametersService;
import jakarta.transaction.Transactional;

/**
* JWT token authentication service implementation for GrafioschTrader application.
* 
* <p>This service extends the base TokenAuthentication class to provide
* application-specific authentication and configuration functionality. It handles
* JWT token validation for both HTTP requests and WebSocket connections, and
* provides comprehensive configuration data tailored for the trading application.</p>
* 
* <h3>Authentication Support:</h3>
* <ul>
*   <li><strong>HTTP Authentication:</strong> Standard JWT token validation for REST APIs</li>
*   <li><strong>WebSocket Authentication:</strong> STOMP header-based token validation</li>
*   <li><strong>Configuration Delivery:</strong> Trading-specific frontend configuration</li>
* </ul>
* 
* <h3>Configuration Features:</h3>
* <p>Provides specialized configuration data including currency precision settings,
* feature toggles for partially implemented functionality, supported cryptocurrencies,
* and field size constraints specific to the trading application.</p>
*/
@Service
public class TokenAuthenticationService extends TokenAuthentication {

  @Autowired
  private FeatureConfig featureConfig;

  @Autowired
  private GlobalparametersService globalparametersService;

  /**
   * Generates authentication from STOMP WebSocket connection headers.
   * 
   * <p>This method handles JWT token authentication for WebSocket connections
   * using STOMP protocol. It extracts the authentication token from the native
   * headers and validates it to create an Authentication object for WebSocket
   * session security.</p>
   * 
   * <h3>Usage:</h3>
   * <p>Used by WebSocket interceptors and handlers to authenticate STOMP
   * connections, enabling secure real-time communication for trading data,
   * notifications, and live updates.</p>
   * 
   * @param message the WebSocket message containing connection information
   * @param accessor STOMP header accessor for extracting authentication headers
   * @return Authentication object if token is valid, null if authentication fails
   */
  public Authentication generateAuthenticationFromStompHeader(org.springframework.messaging.Message<?> message,
      StompHeaderAccessor accessor) {

    // if (StompCommand.CONNECT.equals(accessor.getCommand())) {
    final String token = accessor.getFirstNativeHeader(AUTH_HEADER_NAME);

    if (token == null || token.isEmpty()) {
      return null;
    }
    return jwtTokenHandler.parseUserFromToken(token).map(UserAuthentication::new).orElse(null);
    // }
    // return null;

  }

  @Override
  @Transactional
  public ConfigurationWithLogin getConfigurationWithLogin(boolean uiShowMyProperty, String mostPrivilegedRole,
      boolean passwordRegexOk) {
    ConfigurationWithLoginGT configurationWithLogin = new ConfigurationWithLoginGT(getAllEntitiyNamesWithTheirKeys(),
        getGlobalConstantsFieldsByFieldPrefix(GlobalConstants.class, "FIELD_SIZE"), uiShowMyProperty,
        mostPrivilegedRole, passwordRegexOk, globalparametersService.getCurrencyPrecision(),
        getGlobalConstantsFieldsByFieldPrefix(GlobalConstants.class, "FID"), featureConfig.getEnabledFeatures());
    return configurationWithLogin;
  }

  /**
   * Extended configuration class for GrafioschTrader-specific login settings.
   * 
   * <p>
   * This inner class extends the base ConfigurationWithLogin to include trading application-specific configuration data
   * such as feature toggles, cryptocurrency support, and currency precision settings.
   * </p>
   */
  static class ConfigurationWithLoginGT extends ConfigurationWithLogin {
    /**
     * List of supported cryptocurrencies for trading operations.
     * 
     * <p>Contains the cryptocurrencies that the application supports for trading,
     * portfolio management, and price tracking. This list is used by the frontend
     * to validate and display available cryptocurrency options.</p>
     */
    public static final List<String> cryptocurrencies = GlobalConstants.CRYPTO_CURRENCY_SUPPORTED;
    /**
     * Certain currencies have a deviation different of two decimal places. This should be made known here.
     */
    public final Map<String, Integer> currencyPrecision;

    /**
     * Creates a comprehensive GrafioschTrader configuration object.
     *
     * <p>Constructs the complete configuration with all necessary data for the
     * trading application frontend, including entity metadata, field constraints,
     * user preferences, financial precision settings, and feature toggles.</p>
     *
     * @param entityNameWithKeyNameList JPA entity metadata for frontend integration
     * @param fieldSize field size constraints from application constants
     * @param uiShowMyProperty user's UI property display preference
     * @param mostPrivilegedRole user's highest authorization role
     * @param passwordRegexOk password policy compliance status
     * @param currencyPrecision currency-specific decimal precision mapping
     * @param standardPrecision standard precision constants for field formatting
     * @param useFeatures set of enabled features for partial functionality control
     */
    public ConfigurationWithLoginGT(List<EntityNameWithKeyName> entityNameWithKeyNameList,
        Map<String, Integer> fieldSize, boolean uiShowMyProperty, String mostPrivilegedRole, boolean passwordRegexOk,
        Map<String, Integer> currencyPrecision, Map<String, Integer> standardPrecision, Set<? extends FeatureType> useFeatures) {
      super(entityNameWithKeyNameList, fieldSize, uiShowMyProperty, mostPrivilegedRole, passwordRegexOk, standardPrecision);
      this.useFeatures = useFeatures;
      this.currencyPrecision = currencyPrecision;
    }
  }

}
