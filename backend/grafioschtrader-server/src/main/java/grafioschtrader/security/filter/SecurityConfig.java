package grafioschtrader.security.filter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import grafioschtrader.entities.Role;
import grafioschtrader.repository.ProposeUserTaskJpaRepository;
import grafioschtrader.rest.RequestMappings;
import grafioschtrader.security.TokenAuthenticationService;
import grafioschtrader.service.UserService;

@Configuration
@EnableWebSecurity
@Order(1)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

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

  public SecurityConfig() {
    super(true);
  }

  @Override
  protected void configure(final HttpSecurity http) throws Exception {

    http.csrf().disable();

    http.exceptionHandling().and().anonymous().and().servletApi().and().headers().cacheControl();

    http.authorizeRequests()
        // It must be accessible before login
        .antMatchers(HttpMethod.GET, "/api/globalparameters/locales").permitAll()
        .antMatchers(HttpMethod.GET, "/api/globalparameters/userformdefinition").permitAll()
        .antMatchers(HttpMethod.GET, "/api/globalparameters/properties/*").permitAll().antMatchers("/").permitAll()
        .antMatchers(HttpMethod.GET, "/api/actuator/**").permitAll()
        // Registered user
        .antMatchers(HttpMethod.POST, RequestMappings.USER_MAP + "/").permitAll()
        .antMatchers(HttpMethod.GET, "/api/user/tokenverify/*").permitAll().antMatchers(HttpMethod.POST, "/api/login")
        .permitAll().antMatchers(HttpMethod.PUT, RequestMappings.TRADINGDAYSPLUS_MAP + "/").hasRole(Role.ADMIN)
        .antMatchers(RequestMappings.USER_ENTITY_CHANGE_LIMIT_MAP + "/**").hasRole(Role.ADMIN)
        .antMatchers(RequestMappings.USERADMIN_MAP + "/**").hasRole(Role.ADMIN)
        .antMatchers(HttpMethod.DELETE, RequestMappings.HISTORYQUOTE_MAP + "/").hasRole(Role.ALL_EDIT)
        .antMatchers(HttpMethod.GET, "/api/**").hasAnyRole(Role.USER, Role.LIMIT_EDIT)
        .antMatchers(HttpMethod.POST, "/api/**").hasAnyRole(Role.USER, Role.LIMIT_EDIT)
        .antMatchers(HttpMethod.PUT, "/api/**").hasAnyRole(Role.USER, Role.LIMIT_EDIT)
        .antMatchers(HttpMethod.DELETE, "/api/**").hasAnyRole(Role.USER, Role.LIMIT_EDIT);

    http.addFilterBefore(new StatelessLoginFilter("/api/login", tokenAuthenticationService, userService,
        authenticationManager(), proposeUserTaskJpaRepository, messages), UsernamePasswordAuthenticationFilter.class);

    http.addFilterBefore(
        new StatelessAuthenticationFilter(tokenAuthenticationService, messages, userService, limitRequest),
        UsernamePasswordAuthenticationFilter.class);
  }

  @Bean
  @Override
  public AuthenticationManager authenticationManagerBean() throws Exception {
    return super.authenticationManagerBean();
  }

  @Override
  protected void configure(final AuthenticationManagerBuilder auth) throws Exception {
    auth.userDetailsService(userService).passwordEncoder(new BCryptPasswordEncoder());
  }

  @Override
  protected UserDetailsService userDetailsService() {
    return userService;
  }
}
