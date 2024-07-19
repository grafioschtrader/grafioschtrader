package grafioschtrader.security;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.GlobalConstants;
import grafioschtrader.GlobalConstants.UDFPrefixSuffix;
import grafioschtrader.entities.User;
import grafioschtrader.repository.GlobalparametersJpaRepository;
import grafioschtrader.types.UDFDataType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;

@Service
public class TokenAuthenticationService {

  @Value("${gt.use.websocket}")
  private boolean useWebsockt;

  @Value("${gt.use.algo}")
  private boolean useAlgo;

  private static final String AUTH_HEADER_NAME = "x-auth-token";

  private final JwtTokenHandler jwtTokenHandler;

  @Autowired
  private ObjectMapper jacksonObjectMapper;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @PersistenceContext
  private EntityManager entityManager;

  public TokenAuthenticationService(final JwtTokenHandler jwtTokenHandler) {
    this.jwtTokenHandler = jwtTokenHandler;
  }

  public void addJwtTokenToHeader(final HttpServletResponse response, final UserAuthentication authentication,
      boolean passwordRegexOk) throws IOException {

    final UserDetails user = authentication.getDetails();
    response.addHeader(AUTH_HEADER_NAME,
        jwtTokenHandler.createTokenForUser(user, globalparametersJpaRepository.getJWTExpirationMinutes()));
    PrintWriter out = response.getWriter();
    jacksonObjectMapper.writeValue(out, getConfigurationWithLogin(((User) user).isUiShowMyProperty(),
        ((User) user).getMostPrivilegedRole(), passwordRegexOk));
  }

  /**
   * Get the token from header with every request.
   *
   * @param request
   * @return
   */
  public Authentication generateAuthenticationFromRequest(final HttpServletRequest request) {

    final String token = request.getHeader(AUTH_HEADER_NAME);
    if (token == null || token.isEmpty()) {
      return null;
    }

    return jwtTokenHandler.parseUserFromToken(token).map(UserAuthentication::new).orElse(null);
  }

  /**
   * Used to connect with Websocket.
   *
   * @param message
   * @param accessor
   * @return
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

  @Transactional
  public ConfigurationWithLogin getConfigurationWithLogin(boolean uiShowMyProperty, String mostPrivilegedRole,
      boolean passwordRegexOk) {
    ConfigurationWithLogin configurationWithLogin = new ConfigurationWithLogin(getAllEntitiyNamesWithTheirKeys(),
        useWebsockt, useAlgo, globalparametersJpaRepository.getCurrencyPrecision(),
        getGlobalConstantsFieldsByFieldPrefix("FID"), getGlobalConstantsFieldsByFieldPrefix("FIELD_SIZE"),
        uiShowMyProperty, mostPrivilegedRole, passwordRegexOk);
    return configurationWithLogin;
  }

  /**
   * The frontend must know certain values for input fields.
   * 
   * @return
   */
  private Map<String, Integer> getGlobalConstantsFieldsByFieldPrefix(String fieldPrefix) {
    final Map<String, Integer> globalConstantsMap = new HashMap<>();
    Field[] fields = GlobalConstants.class.getDeclaredFields();
    for (Field f : fields) {
      if (Modifier.isStatic(f.getModifiers()) && f.getName().startsWith(fieldPrefix)) {
        try {
          globalConstantsMap.put(f.getName(), f.getInt(null));
        } catch (IllegalArgumentException | IllegalAccessException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    }
    return globalConstantsMap;
  }

  /**
   * The frontend may need the field name of the key field of an entity.
   * 
   * @return
   */
  private List<EntityNameWithKeyName> getAllEntitiyNamesWithTheirKeys() {
    List<EntityNameWithKeyName> entityNameWithKeyNameList = new ArrayList<>();
    final Set<EntityType<?>> entityTypeList = entityManager.getMetamodel().getEntities();
    for (EntityType<?> entity : entityTypeList) {
      Class<?> clazz = entity.getBindableJavaType();
      if (!Modifier.isAbstract(clazz.getModifiers())) {
        SingularAttribute<?, ?> id = entity.getId(entity.getIdType().getJavaType());
        entityNameWithKeyNameList.add(new EntityNameWithKeyName(entity.getName(), id.getName()));
      }
    }
    return entityNameWithKeyNameList;
  }

  static class ConfigurationWithLogin {
    public final List<EntityNameWithKeyName> entityNameWithKeyNameList;
    public final boolean useWebsocket;
    public final boolean useAlgo;
    public static final List<String> cryptocurrencies = GlobalConstants.CRYPTO_CURRENCY_SUPPORTED;
    public final Map<String, Integer> currencyPrecision;
    public final Map<String, Integer> standardPrecision;
    public final Map<String, Integer> fieldSize;
    public final boolean uiShowMyProperty;
    public final String mostPrivilegedRole;
    public final boolean passwordRegexOk;
    public final UDFConfig udfConfig = new UDFConfig();

    public ConfigurationWithLogin(List<EntityNameWithKeyName> entityNameWithKeyNameList, boolean useWebsocket,
        boolean useAlgo, Map<String, Integer> currencyPrecision, Map<String, Integer> standardPrecision,
        Map<String, Integer> fieldSize, boolean uiShowMyProperty, String mostPrivilegedRole, boolean passwordRegexOk) {
      this.entityNameWithKeyNameList = entityNameWithKeyNameList;
      this.useWebsocket = useWebsocket;
      this.useAlgo = useAlgo;
      this.currencyPrecision = currencyPrecision;
      this.standardPrecision = standardPrecision;
      this.fieldSize = fieldSize;
      this.uiShowMyProperty = uiShowMyProperty;
      this.mostPrivilegedRole = mostPrivilegedRole;
      this.passwordRegexOk = passwordRegexOk;
    }
  }

  static class EntityNameWithKeyName {
    public String entityName;
    public String keyName;

    public EntityNameWithKeyName(String entityName, String keyName) {
      this.entityName = entityName;
      this.keyName = keyName;
    }
  }
  
  static class UDFConfig {
    public Set<String> udfGeneralSupportedEntities = GlobalConstants.UDF_GENERAL_ENTITIES.stream()
        .map(c -> c.getSimpleName()).collect(Collectors.toSet());
    public Map<UDFDataType, UDFPrefixSuffix> uDFPrefixSuffixMap = GlobalConstants.uDFPrefixSuffixMap;;
  }

}
