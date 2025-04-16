package grafiosch.security;

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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.databind.ObjectMapper;

import grafiosch.dto.ConfigurationWithLogin;
import grafiosch.dto.ConfigurationWithLogin.EntityNameWithKeyName;
import grafiosch.entities.User;
import grafiosch.repository.GlobalparametersJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public abstract class TokenAuthentication {

  protected static final String AUTH_HEADER_NAME = "x-auth-token";

  @Autowired
  protected EntityManager entityManager;

  @Autowired
  protected JwtTokenHandler jwtTokenHandler;

  @Autowired
  private ObjectMapper jacksonObjectMapper;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  public abstract ConfigurationWithLogin getConfigurationWithLogin(boolean uiShowMyProperty, String mostPrivilegedRole,
      boolean passwordRegexOk);

  /**
   * Adds the JWT to the HTTP header, this method is performed once after
   * successful login. Configuration data is also returned. The data contains
   * basic settings for the frontend.
   * 
   * @param response
   * @param authentication
   * @param passwordRegexOk
   * @throws IOException
   */
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
   * The frontend must know certain values for input fields.
   *
   * @return
   */
  protected Map<String, Integer> getGlobalConstantsFieldsByFieldPrefix(Class<?> currentClass, String fieldPrefix) {
    final Map<String, Integer> globalConstantsMap = new HashMap<>();

    while (currentClass != null && currentClass != Object.class) {
      Field[] fields = currentClass.getDeclaredFields();
      for (Field f : fields) {
        if (Modifier.isStatic(f.getModifiers()) && f.getName().startsWith(fieldPrefix)) {
          try {
            globalConstantsMap.put(f.getName(), f.getInt(null));
          } catch (IllegalArgumentException | IllegalAccessException e) {
            e.printStackTrace();
          }
        }
      }
      currentClass = currentClass.getSuperclass();
    }
    return globalConstantsMap;
  }

  /**
   * The frontend may need the field name of the key field of an entity.
   * 
   * @return
   */
  protected List<EntityNameWithKeyName> getAllEntitiyNamesWithTheirKeys() {
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

}
