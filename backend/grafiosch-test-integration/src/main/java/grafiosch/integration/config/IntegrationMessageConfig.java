package grafiosch.integration.config;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import grafiosch.config.ExposedResourceBundleMessageSource;

/** Message source containing only the translations supplied by the reusable Grafiosch libraries. */
@Configuration
public class IntegrationMessageConfig {

  @Bean
  MessageSource messageSource() {
    ReloadableResourceBundleMessageSource messageSource = new ExposedResourceBundleMessageSource();
    messageSource.setBasename("classpath:i18n/messages");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setUseCodeAsDefaultMessage(false);
    messageSource.setDefaultLocale(Locale.US);
    return messageSource;
  }

  @Bean
  LocalValidatorFactoryBean validator() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.setValidationMessageSource(messageSource());
    return validator;
  }
}
