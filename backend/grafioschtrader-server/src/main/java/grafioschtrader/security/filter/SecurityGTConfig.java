package grafioschtrader.security.filter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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

    http.authenticationProvider(authenticationProvider());

    return http.build();
  }

  @Bean
  DaoAuthenticationProvider authenticationProvider() {
    DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
    authProvider.setUserDetailsService(userService);
    authProvider.setPasswordEncoder(new BCryptPasswordEncoder());
    return authProvider;
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
    return authConfig.getAuthenticationManager();
  }

}
