package grafioschtrader.security.filter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import grafiosch.entities.Role;
import grafiosch.repository.ProposeUserTaskJpaRepository;
import grafiosch.rest.RequestMappings;
import grafiosch.security.SecurityConfig;
import grafiosch.security.filter.StatelessAuthenticationFilter;
import grafiosch.security.filter.StatelessLoginFilter;
import grafiosch.service.UserService;
import grafioschtrader.rest.RequestGTMappings;
import grafioschtrader.security.TokenAuthenticationService;

/**
 * Spring Security configuration for the GrafioschTrader application.
 * 
 * <p>This configuration class establishes the complete security framework for the application,
 * including authentication, authorization, and custom filter chains. It implements a stateless
 * JWT-based authentication system with role-based access control and configurable rate limiting.</p>
 * 
 * <h3>Security Architecture:</h3>
 * <ul>
 *   <li><strong>Stateless Authentication:</strong> JWT token-based authentication without server sessions</li>
 *   <li><strong>Custom Filter Chain:</strong> Specialized login and authentication filters for API security</li>
 *   <li><strong>Role-based Authorization:</strong> Granular access control based on user roles and permissions</li>
 *   <li><strong>Rate Limiting:</strong> Configurable request throttling to prevent API abuse</li>
 * </ul>
 * 
 * <h3>Access Control:</h3>
 * <p>Configures public endpoints for documentation, health checks, and machine-to-machine APIs,
 * while securing administrative functions and user-specific data with appropriate role requirements.</p>
 * 
 * <h3>Modern Spring Security:</h3>
 * <p>Uses Spring Boot 3.x compatible configuration patterns with direct UserDetailsService
 * and PasswordEncoder configuration, avoiding deprecated authentication providers.</p>
 */
@Configuration
@EnableWebSecurity
@Order(1)
public class SecurityGTConfig {

  @Autowired
  private MessageSource messages;

  @Autowired
  private UserService userService;

  @Autowired
  private TokenAuthenticationService tokenAuthenticationService;

  @Autowired
  private ProposeUserTaskJpaRepository proposeUserTaskJpaRepository;

  @Value("${gt.limit.request}")
  private boolean limitRequest;

  @Autowired
  private AuthenticationManager authenticationManager;

  /**
   * Configures the main security filter chain with authentication and authorization rules.
   * 
   * <p>This method establishes the complete security configuration including CSRF protection,
   * authentication mechanisms, authorization rules, and custom filter integration. It defines
   * public endpoints, role-based access controls, and integrates custom JWT authentication filters.</p>
   * 
   * <h3>Security Configuration:</h3>
   * <ul>
   *   <li><strong>CSRF Disabled:</strong> Appropriate for stateless JWT authentication</li>
   *   <li><strong>Public Endpoints:</strong> Documentation, health checks, and M2M APIs</li>
   *   <li><strong>Authentication Integration:</strong> Direct UserDetailsService and PasswordEncoder setup</li>
   *   <li><strong>Custom Filters:</strong> JWT login and authentication filters in the filter chain</li>
   * </ul>
   * 
   * <h3>Access Rules:</h3>
   * <p>Defines public access for documentation endpoints, health monitoring, and machine-to-machine
   * APIs, while requiring appropriate roles for administrative functions and user-specific operations.</p>
   * 
   * @param http HttpSecurity configuration object for defining security rules
   * @return configured SecurityFilterChain for the application
   * @throws Exception if security configuration fails
   */
  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

    http.csrf(csrf -> csrf.disable());

    http.authorizeHttpRequests(authz -> {
      authz.requestMatchers("/").permitAll().requestMatchers(HttpMethod.GET, "/swagger-ui/**").permitAll()
          .requestMatchers(HttpMethod.GET, "/api-docs/**").permitAll()
          .requestMatchers(HttpMethod.GET,
              RequestGTMappings.WATCHLIST_MAP + RequestGTMappings.SECURITY_DATAPROVIDER_INTRA_HISTORICAL_RESPONSE + "*")
          .permitAll()
          .requestMatchers(HttpMethod.GET,
              RequestGTMappings.WATCHLIST_MAP + RequestGTMappings.SECURITY_DATAPROVIDER_DIV_SPLIT_HISTORICAL_RESPONSE
                  + "*")
          .permitAll().requestMatchers(HttpMethod.GET, RequestMappings.API + "actuator/**").permitAll()
          .requestMatchers(HttpMethod.GET, RequestGTMappings.M2M_API + "**").permitAll()
          .requestMatchers(HttpMethod.POST, RequestGTMappings.M2M_API + "**").permitAll();
      SecurityConfig.configureGlobalParameters(http);
      authz.requestMatchers(HttpMethod.PUT, RequestGTMappings.TRADINGDAYSPLUS_MAP).hasRole(Role.ADMIN);
    }); // Close authorizeHttpRequests

    http.addFilterBefore(new StatelessLoginFilter("/api/login", tokenAuthenticationService, userService,
        authenticationManager, proposeUserTaskJpaRepository, messages), UsernamePasswordAuthenticationFilter.class);

    http.addFilterBefore(
        new StatelessAuthenticationFilter(tokenAuthenticationService, messages, userService, limitRequest),
        UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  /**
   * Provides BCrypt password encoder for secure password hashing and verification.
   * 
   * @return BCryptPasswordEncoder instance for password operations
   */
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * Provides the authentication manager for credential validation and authentication processing.
   * 
   * @param authConfig Spring Security authentication configuration
   * @return configured AuthenticationManager instance
   * @throws Exception if authentication manager configuration fails
   */
  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
    return authConfig.getAuthenticationManager();
  }

}
