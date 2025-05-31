package grafiosch.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import grafiosch.entities.Role;
import grafiosch.rest.RequestMappings;

public class SecurityConfig {

  public static void configureGlobalParameters(HttpSecurity http) {
    try {
      http.authorizeHttpRequests(
          authz -> authz.requestMatchers(HttpMethod.GET, RequestMappings.GLOBALPARAMETERS_MAP + "/locales").permitAll()
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

  public static class SecurityConfigCustomRuntimeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SecurityConfigCustomRuntimeException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
