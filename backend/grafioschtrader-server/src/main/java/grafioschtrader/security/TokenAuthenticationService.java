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
import grafioschtrader.types.FeatureType;
import jakarta.transaction.Transactional;

@Service
public class TokenAuthenticationService extends TokenAuthentication {

  @Autowired
  private FeatureConfig featureConfig;

  @Autowired
  private GlobalparametersService globalparametersService;

  /**
   * Used to connect with Websocket.
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

  static class ConfigurationWithLoginGT extends ConfigurationWithLogin {
    /**
     * Certain functionality is only partially implemented. Therefore, this should not be visible in the frontend. This
     * can be switched on or off
     *
     */
    public Set<FeatureType> useFeatures;
    public static final List<String> cryptocurrencies = GlobalConstants.CRYPTO_CURRENCY_SUPPORTED;
    /**
     * Certain currencies have a deviation of two decimal places. This should be made known here.
     */
    public final Map<String, Integer> currencyPrecision;
    public final Map<String, Integer> standardPrecision;

    public ConfigurationWithLoginGT(List<EntityNameWithKeyName> entityNameWithKeyNameList,
        Map<String, Integer> fieldSize, boolean uiShowMyProperty, String mostPrivilegedRole, boolean passwordRegexOk,
        Map<String, Integer> currencyPrecision, Map<String, Integer> standardPrecision, Set<FeatureType> useFeatures) {
      super(entityNameWithKeyNameList, fieldSize, uiShowMyProperty, mostPrivilegedRole, passwordRegexOk);
      this.useFeatures = useFeatures;
      this.currencyPrecision = currencyPrecision;
      this.standardPrecision = standardPrecision;
    }
  }

}
