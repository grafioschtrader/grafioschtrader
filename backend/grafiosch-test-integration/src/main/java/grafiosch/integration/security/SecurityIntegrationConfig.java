package grafiosch.integration.security;

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

import grafiosch.repository.ProposeUserTaskJpaRepository;
import grafiosch.security.SecurityConfig;
import grafiosch.security.filter.StatelessAuthenticationFilter;
import grafiosch.security.filter.StatelessLoginFilter;
import grafiosch.service.UserService;

/** Stateless JWT security for the reusable integration host. */
@Configuration
@EnableWebSecurity
@Order(1)
public class SecurityIntegrationConfig {

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http, IntegrationTokenAuthenticationService tokenAuthentication,
      UserService userService, AuthenticationManager authenticationManager,
      ProposeUserTaskJpaRepository proposeUserTaskJpaRepository, MessageSource messages,
      @Value("${gt.limit.request}") boolean limitRequest) throws Exception {
    http.csrf(csrf -> csrf.disable());
    http.authorizeHttpRequests(authz -> authz
        .requestMatchers(HttpMethod.GET, "/api/integration-info", "/api/actuator/**", "/swagger-ui/**", "/api-docs/**")
        .permitAll());
    SecurityConfig.configureGlobalParameters(http);
    http.addFilterBefore(new StatelessLoginFilter("/api/login", tokenAuthentication, userService, authenticationManager,
        proposeUserTaskJpaRepository, messages), UsernamePasswordAuthenticationFilter.class);
    http.addFilterBefore(new StatelessAuthenticationFilter(tokenAuthentication, messages, userService, limitRequest),
        UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
      throws Exception {
    return authenticationConfiguration.getAuthenticationManager();
  }
}
