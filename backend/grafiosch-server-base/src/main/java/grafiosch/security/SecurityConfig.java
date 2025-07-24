package grafiosch.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import grafiosch.entities.Role;
import grafiosch.rest.RequestMappings;

/**
 * Utility class for configuring common Spring Security authorization rules.
 * 
 * <p>
 * This class provides reusable security configuration methods that can be shared across different security
 * configurations. It centralizes authorization rules for global parameters, user management, and administrative
 * endpoints to ensure consistent security policies throughout the application.
 * </p>
 * 
 * <h3>Configuration Areas:</h3>
 * <ul>
 * <li><strong>Public Endpoints:</strong> Global parameters, user registration, and authentication</li>
 * <li><strong>Administrative Functions:</strong> Task management, API keys, and user administration</li>
 * <li><strong>General API Access:</strong> Role-based access for authenticated users</li>
 * </ul>
 * 
 * <h3>Security Principles:</h3>
 * <p>
 * Implements least-privilege access control with public access limited to essential endpoints, administrative functions
 * restricted to admin users, and general API access available to authenticated users with appropriate roles.
 * </p>
 */
public class SecurityConfig {

  public static void configureGlobalParameters(HttpSecurity http) {
    try {
      http.authorizeHttpRequests(
          authz -> authz.requestMatchers(HttpMethod.GET, RequestMappings.GLOBALPARAMETERS_MAP + "/locales").permitAll()
              .requestMatchers(HttpMethod.GET, RequestMappings.RELEASE_NOTE_MAP).permitAll()
              .requestMatchers(HttpMethod.GET, RequestMappings.GLOBALPARAMETERS_MAP + "/passwordrequirements")
              .permitAll().requestMatchers(HttpMethod.GET, RequestMappings.GLOBALPARAMETERS_MAP + "/userformdefinition")
              .permitAll().requestMatchers(HttpMethod.GET, RequestMappings.GLOBALPARAMETERS_MAP + "/properties/*")
              .permitAll().requestMatchers(HttpMethod.POST, RequestMappings.USER_MAP).permitAll()
              .requestMatchers(HttpMethod.GET, RequestMappings.API + "user/tokenverify/*").permitAll()
              .requestMatchers(HttpMethod.POST, RequestMappings.API + "login").permitAll()
              .requestMatchers(HttpMethod.PATCH, RequestMappings.TASK_DATA_CHANGE_MAP + "/**").hasRole(Role.ADMIN)
              .requestMatchers(HttpMethod.POST, RequestMappings.TASK_DATA_CHANGE_MAP).hasRole(Role.ADMIN)
              .requestMatchers(HttpMethod.PUT, RequestMappings.TASK_DATA_CHANGE_MAP).hasRole(Role.ADMIN)
              .requestMatchers(HttpMethod.DELETE, RequestMappings.TASK_DATA_CHANGE_MAP + "/*").hasRole(Role.ADMIN)
              .requestMatchers(RequestMappings.CONNECTOR_API_KEY_MAP + "/**").hasRole(Role.ADMIN)
              .requestMatchers(RequestMappings.USER_ENTITY_CHANGE_LIMIT_MAP + "/**").hasRole(Role.ADMIN)
              .requestMatchers(RequestMappings.USERADMIN_MAP + "/**").hasRole(Role.ADMIN)
              .requestMatchers(RequestMappings.API + "**").hasAnyRole(Role.USER, Role.LIMIT_EDIT));
    } catch (Exception e) {
      throw new SecurityConfigCustomRuntimeException("Error configuring global parameters", e);
    }
  }

// ... filterChain method (with the direct call to configureGlobalParameters as before)

  /**
   * Custom runtime exception for security configuration errors.
   * 
   * <p>
   * This exception class provides specific error handling for security configuration failures, allowing for clear
   * identification and handling of security setup issues during application startup or configuration changes.
   * </p>
   * 
   * <h3>Usage:</h3>
   * <p>
   * Thrown when security configuration methods encounter exceptions during the setup process, providing a wrapped
   * exception with descriptive error messages for debugging and error resolution.
   * </p>
   */
  public static class SecurityConfigCustomRuntimeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates a security configuration exception with message and cause.
     * 
     * <p>
     * Wraps the underlying security configuration exception with a descriptive message to provide context about the
     * configuration failure for easier debugging and error resolution.
     * </p>
     * 
     * @param message descriptive error message explaining the configuration failure
     * @param cause   the underlying exception that caused the configuration failure
     */
    public SecurityConfigCustomRuntimeException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
