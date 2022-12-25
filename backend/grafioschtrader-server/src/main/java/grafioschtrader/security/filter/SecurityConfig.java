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

import grafioschtrader.entities.Role;
import grafioschtrader.repository.ProposeUserTaskJpaRepository;
import grafioschtrader.rest.RequestMappings;
import grafioschtrader.security.TokenAuthenticationService;
import grafioschtrader.service.UserService;

@Configuration
@EnableWebSecurity
@Order(1)
public class SecurityConfig {

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

    http.csrf().disable();

    http.exceptionHandling().and().anonymous().and().servletApi().and().headers().cacheControl();

    http.authorizeHttpRequests().requestMatchers("/").permitAll()
    // Swagger
    .requestMatchers(HttpMethod.GET, "/swagger-ui/**").permitAll()
    .requestMatchers(HttpMethod.GET, "/api-docs/**").permitAll()
    // It must be accessible before login
    .requestMatchers(HttpMethod.GET, RequestMappings.API + "actuator/**").permitAll()
    .requestMatchers(HttpMethod.GET, RequestMappings.M2M_API + "**").permitAll()
    .requestMatchers(HttpMethod.GET, RequestMappings.API + "globalparameters/locales").permitAll()
    .requestMatchers(HttpMethod.GET, RequestMappings.API + "globalparameters/userformdefinition").permitAll()
    .requestMatchers(HttpMethod.GET, RequestMappings.API + "globalparameters/properties/*").permitAll()
    // Register user
    .requestMatchers(HttpMethod.POST, RequestMappings.USER_MAP).permitAll()
    .requestMatchers(HttpMethod.GET, RequestMappings.API + "user/tokenverify/*").permitAll()
    // for login
    .requestMatchers(HttpMethod.POST, RequestMappings.API + "login").permitAll()
    // Only for Admin
    .requestMatchers(HttpMethod.PUT, RequestMappings.TRADINGDAYSPLUS_MAP).hasRole(Role.ADMIN)
    .requestMatchers(HttpMethod.PATCH, RequestMappings.TASK_DATA_CHANGE_MAP + "/**").hasRole(Role.ADMIN)
    .requestMatchers(HttpMethod.POST, RequestMappings.TASK_DATA_CHANGE_MAP).hasRole(Role.ADMIN)
    .requestMatchers(HttpMethod.PUT, RequestMappings.TASK_DATA_CHANGE_MAP).hasRole(Role.ADMIN)
    .requestMatchers(HttpMethod.DELETE, RequestMappings.TASK_DATA_CHANGE_MAP + "/*").hasRole(Role.ADMIN)
    .requestMatchers(RequestMappings.CONNECTOR_API_KEY_MAP + "/**").hasRole(Role.ADMIN)
    .requestMatchers(RequestMappings.USER_ENTITY_CHANGE_LIMIT_MAP + "/**").hasRole(Role.ADMIN)
    .requestMatchers(RequestMappings.USERADMIN_MAP + "/**").hasRole(Role.ADMIN)
    // For all users
    .requestMatchers(RequestMappings.API + "**").hasAnyRole(Role.USER, Role.LIMIT_EDIT);

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
  AuthenticationManager authenticationManager(
          AuthenticationConfiguration authConfig) throws Exception {
      return authConfig.getAuthenticationManager();
  }
  
}
