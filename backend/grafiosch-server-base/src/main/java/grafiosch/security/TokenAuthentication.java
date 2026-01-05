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

/**
 * Abstract base class for JWT-based authentication and configuration management.
 * 
 * <p>
 * This class provides core functionality for JWT token handling, authentication processing, and frontend configuration
 * data delivery. It serves as a foundation for implementing stateless authentication with comprehensive client
 * configuration support including entity metadata and application constants.
 * </p>
 * 
 * <h3>Core Responsibilities:</h3>
 * <ul>
 * <li><strong>JWT Authentication:</strong> Token creation, validation, and header management</li>
 * <li><strong>Configuration Delivery:</strong> Frontend configuration and metadata provision</li>
 * <li><strong>Entity Introspection:</strong> JPA entity metadata extraction for client use</li>
 * <li><strong>Constants Exposure:</strong> Application constants discovery for frontend integration</li>
 * </ul>
 * 
 * <h3>Authentication Flow:</h3>
 * <p>
 * Handles the complete authentication lifecycle from successful login through ongoing request validation, providing JWT
 * tokens and configuration data to support stateless authentication in client applications.
 * </p>
 * 
 * <h3>Frontend Integration:</h3>
 * <p>
 * Provides essential metadata and configuration information that frontend applications need for proper operation,
 * including entity field names, application constants, and user-specific settings.
 * </p>
 */
public abstract class TokenAuthentication {

  /**
   * HTTP header name for JWT token transmission.
   * 
   * <p>
   * Standard header name used for sending JWT tokens between client and server, providing consistent token transmission
   * across all authentication requests.
   * </p>
   */
  protected static final String AUTH_HEADER_NAME = "x-auth-token";

  @Autowired
  protected EntityManager entityManager;

  @Autowired
  protected JwtTokenHandler jwtTokenHandler;

  @Autowired
  private ObjectMapper jacksonObjectMapper;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  /**
   * Creates configuration data for successful login responses.
   *
   * <p>
   * Abstract method that must be implemented by subclasses to provide application-specific configuration data that
   * clients need after successful authentication. This typically includes user preferences, application settings, and
   * frontend configuration.
   * </p>
   *
   * @param uiShowMyProperty   flag indicating user's UI property display preference
   * @param mostPrivilegedRole the user's highest privilege role for authorization
   * @param passwordRegexOk    flag indicating if user's password meets current requirements
   * @param idTenant           the tenant ID for tenant-specific configuration
   * @return configuration object containing login and application setup data
   */
  public abstract ConfigurationWithLogin getConfigurationWithLogin(boolean uiShowMyProperty, String mostPrivilegedRole,
      boolean passwordRegexOk, Integer idTenant);

  /**
   * Adds JWT token to response header and sends configuration data after successful login.
   * 
   * <p>
   * This method completes the login process by generating a JWT token for the authenticated user and adding it to the
   * response header. It also serializes and sends comprehensive configuration data that the frontend needs for proper
   * operation, including user preferences and application settings.
   * </p>
   * 
   * <h3>Response Components:</h3>
   * <ul>
   * <li><strong>JWT Token:</strong> Added to x-auth-token header with configured expiration</li>
   * <li><strong>Configuration Data:</strong> JSON response containing frontend setup information</li>
   * <li><strong>User Context:</strong> User preferences and authorization information</li>
   * </ul>
   * 
   * <h3>Token Configuration:</h3>
   * <p>
   * Uses globally configured JWT expiration time from application parameters, ensuring consistent token lifetime across
   * the application.
   * </p>
   * 
   * @param response        HTTP response to add token header and configuration data
   * @param authentication  user authentication object containing user details
   * @param passwordRegexOk flag indicating password policy compliance status
   * @throws IOException if response writing or JSON serialization fails
   */
  public void addJwtTokenToHeader(final HttpServletResponse response, final UserAuthentication authentication,
      boolean passwordRegexOk) throws IOException {

    final UserDetails user = authentication.getDetails();
    response.addHeader(AUTH_HEADER_NAME,
        jwtTokenHandler.createTokenForUser(user, globalparametersJpaRepository.getJWTExpirationMinutes()));
    PrintWriter out = response.getWriter();
    final User userEntity = (User) user;
    jacksonObjectMapper.writeValue(out, getConfigurationWithLogin(userEntity.isUiShowMyProperty(),
        userEntity.getMostPrivilegedRole(), passwordRegexOk, userEntity.getIdTenant()));
  }

  /**
   * Extracts and validates JWT token from request header to generate authentication.
   * 
   * <p>
   * This method processes incoming requests by extracting the JWT token from the standard authentication header and
   * validating it to create an Authentication object. It handles the stateless authentication flow for protected
   * endpoints.
   * </p>
   * 
   * <h3>Error Handling:</h3>
   * <p>
   * Returns null for missing, empty, or invalid tokens, allowing security filters to handle unauthenticated requests
   * appropriately.
   * </p>
   * 
   * @param request HTTP request containing the authentication token header
   * @return Authentication object if token is valid, null if token is missing or invalid
   */
  public Authentication generateAuthenticationFromRequest(final HttpServletRequest request) {
    final String token = request.getHeader(AUTH_HEADER_NAME);
    if (token == null || token.isEmpty()) {
      return null;
    }
    return jwtTokenHandler.parseUserFromToken(token).map(UserAuthentication::new).orElse(null);
  }

  /**
   * Extracts static constant fields from a class hierarchy for frontend use.
   * 
   * <p>
   * This method uses reflection to discover static integer constants in a class and its superclasses that match a
   * specified prefix. It enables frontend applications to access application constants for validation, configuration,
   * and business logic without hardcoding values.
   * </p>
   * 
   * <h3>Discovery Process:</h3>
   * <ul>
   * <li><strong>Hierarchy Traversal:</strong> Examines class and all superclasses</li>
   * <li><strong>Field Filtering:</strong> Selects static fields matching the prefix</li>
   * <li><strong>Value Extraction:</strong> Retrieves integer values using reflection</li>
   * </ul>
   * 
   * <h3>Use Cases:</h3>
   * <p>
   * Common for exposing validation limits, status codes, configuration constants, and other application parameters that
   * frontend code needs for proper operation and validation.
   * </p>
   * 
   * @param currentClass the class to examine for static constants
   * @param fieldPrefix  prefix that field names must start with to be included
   * @return map of field names to their integer values
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
   * Extracts entity names and their primary key field names from JPA metamodel.
   * 
   * <p>
   * This method introspects the JPA metamodel to discover all concrete entity classes and their primary key field
   * names. This information is essential for frontend applications that need to work with entity data, form generation,
   * and API endpoint construction.
   * </p>
   * 
   * <h3>Introspection Process:</h3>
   * <ul>
   * <li><strong>Metamodel Access:</strong> Uses JPA metamodel for entity discovery</li>
   * <li><strong>Concrete Entities:</strong> Filters out abstract entities</li>
   * <li><strong>Key Extraction:</strong> Identifies primary key field names</li>
   * </ul>
   * 
   * <h3>Frontend Usage:</h3>
   * <p>
   * Enables dynamic form generation, API endpoint construction, and entity manipulation in client applications without
   * hardcoding entity structure information.
   * </p>
   * 
   * @return list of entity name and key field name pairs for all concrete entities
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
