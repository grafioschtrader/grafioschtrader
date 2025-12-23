package grafioschtrader.config;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import grafiosch.config.ExposedResourceBundleMessageSource;

@Configuration
public class MessageConfig {

  @Bean
  MessageSource messageSource() {
    final ReloadableResourceBundleMessageSource messageSource = new ExposedResourceBundleMessageSource();
    messageSource.setBasenames("classpath:i18n/messages", "classpath:message/messages");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setUseCodeAsDefaultMessage(false);
    messageSource.setDefaultLocale(Locale.US);
    return messageSource;
  }

  @Bean
  LocalValidatorFactoryBean validator() {
    LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
    bean.setValidationMessageSource(messageSource());
    return bean;
  }

}
