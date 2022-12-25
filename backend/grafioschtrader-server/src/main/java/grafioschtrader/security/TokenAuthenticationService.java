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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.User;
import grafioschtrader.repository.GlobalparametersJpaRepository;
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

  public void addJwtTokenToHeader(final HttpServletResponse response, final UserAuthentication authentication)
      throws IOException {

    final UserDetails user = authentication.getDetails();
    response.addHeader(AUTH_HEADER_NAME, jwtTokenHandler.createTokenForUser(user));
    PrintWriter out = response.getWriter();
    jacksonObjectMapper.writeValue(out, getConfigurationWithLogin(((User) user).isUiShowMyProperty()));
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
  public ConfigurationWithLogin getConfigurationWithLogin(boolean uiShowMyProperty) {
    Map<String, Integer> standardPrecision = new HashMap<>();
    ConfigurationWithLogin configurationWithLogin = new ConfigurationWithLogin(useWebsockt, useAlgo,
        globalparametersJpaRepository.getCurrencyPrecision(), standardPrecision, uiShowMyProperty);

    Field[] fields = GlobalConstants.class.getDeclaredFields();
    for (Field f : fields) {
      if (Modifier.isStatic(f.getModifiers()) && f.getName().startsWith("FID")) {
        try {
          standardPrecision.put(f.getName(), f.getInt(null));
        } catch (IllegalArgumentException | IllegalAccessException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    }

    final Set<EntityType<?>> entityTypeList = entityManager.getMetamodel().getEntities();
    for (EntityType<?> entity : entityTypeList) {
      Class<?> clazz = entity.getBindableJavaType();
      if (!Modifier.isAbstract(clazz.getModifiers())) {
        SingularAttribute<?, ?> id = entity.getId(entity.getIdType().getJavaType());
        configurationWithLogin.entityNameWithKeyNameList.add(new EntityNameWithKeyName(entity.getName(), id.getName()));
      }
    }
    return configurationWithLogin;
  }

  static class ConfigurationWithLogin {
    public final List<EntityNameWithKeyName> entityNameWithKeyNameList = new ArrayList<>();
    public final boolean useWebsocket;
    public final boolean useAlgo;
    public static final List<String> cryptocurrencies = GlobalConstants.CRYPTO_CURRENCY_SUPPORTED;
    public final Map<String, Integer> currencyPrecision;
    public final Map<String, Integer> standardPrecision;
    public final boolean uiShowMyProperty;

    public ConfigurationWithLogin(boolean useWebsocket, boolean useAlgo, Map<String, Integer> currencyPrecision,
        Map<String, Integer> standardPrecision, boolean uiShowMyProperty) {
      this.useWebsocket = useWebsocket;
      this.useAlgo = useAlgo;
      this.currencyPrecision = currencyPrecision;
      this.standardPrecision = standardPrecision;
      this.uiShowMyProperty = uiShowMyProperty;
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

}
