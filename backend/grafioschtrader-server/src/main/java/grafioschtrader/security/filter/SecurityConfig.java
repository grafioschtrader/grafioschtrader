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
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

    http.csrf().disable();

    http.exceptionHandling().and().anonymous().and().servletApi().and().headers().cacheControl();

    http.authorizeRequests().antMatchers("/").permitAll()
        // It must be accessible before login
        .antMatchers(HttpMethod.GET, RequestMappings.API + "actuator/**").permitAll()
        .antMatchers(HttpMethod.GET, RequestMappings.M2M_API + "**").permitAll()
        .antMatchers(HttpMethod.GET, RequestMappings.API + "globalparameters/locales").permitAll()
        .antMatchers(HttpMethod.GET, RequestMappings.API + "globalparameters/userformdefinition").permitAll()
        .antMatchers(HttpMethod.GET, RequestMappings.API + "globalparameters/properties/*").permitAll()
        // Register user
        .antMatchers(HttpMethod.POST, RequestMappings.USER_MAP + "/").permitAll()
        .antMatchers(HttpMethod.GET, RequestMappings.API + "user/tokenverify/*").permitAll()
        // for login
        .antMatchers(HttpMethod.POST, RequestMappings.API + "login").permitAll()
        // Only for Admin
        .antMatchers(HttpMethod.PUT, RequestMappings.TRADINGDAYSPLUS_MAP + "/").hasRole(Role.ADMIN)
        .antMatchers(HttpMethod.PATCH, RequestMappings.TASK_DATA_CHANGE_MAP + "/**").hasRole(Role.ADMIN)
        .antMatchers(HttpMethod.POST, RequestMappings.TASK_DATA_CHANGE_MAP + "/").hasRole(Role.ADMIN)
        .antMatchers(HttpMethod.PUT, RequestMappings.TASK_DATA_CHANGE_MAP + "/").hasRole(Role.ADMIN)
        .antMatchers(HttpMethod.DELETE, RequestMappings.TASK_DATA_CHANGE_MAP + "/*").hasRole(Role.ADMIN)
        .antMatchers(RequestMappings.CONNECTOR_API_KEY_MAP + "/**").hasRole(Role.ADMIN)
        .antMatchers(RequestMappings.USER_ENTITY_CHANGE_LIMIT_MAP + "/**").hasRole(Role.ADMIN)
        .antMatchers(RequestMappings.USERADMIN_MAP + "/**").hasRole(Role.ADMIN)
        // For all users
        .antMatchers(RequestMappings.API + "**").hasAnyRole(Role.USER, Role.LIMIT_EDIT);

    http.addFilterBefore(new StatelessLoginFilter("/api/login", tokenAuthenticationService, userService,
        authenticationManager, proposeUserTaskJpaRepository, messages), UsernamePasswordAuthenticationFilter.class);

    http.addFilterBefore(
        new StatelessAuthenticationFilter(tokenAuthenticationService, messages, userService, limitRequest),
        UsernamePasswordAuthenticationFilter.class);
       
    http.authenticationProvider(authenticationProvider());
    
    return http.build();
  }
  
  @Bean
  public DaoAuthenticationProvider authenticationProvider() {
      DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
      authProvider.setUserDetailsService(userService);
      authProvider.setPasswordEncoder(new BCryptPasswordEncoder());
      return authProvider;
  }

  @Bean
  public AuthenticationManager authenticationManager(
          AuthenticationConfiguration authConfig) throws Exception {
      return authConfig.getAuthenticationManager();
  }
  
}
