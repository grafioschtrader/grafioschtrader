package grafioschtrader.config;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import grafiosch.config.ExposedResourceBundleMessageSource;

/**
 * Assembles the message source from the bundle of every module that contributes texts.
 */
@Configuration
public class MessageConfig {

  /**
   * Number of seconds a bundle may be cached before it is re-read; {@code -1} keeps it forever, which is what a
   * production server wants. Set it to a small value in a development profile to edit texts without a restart.
   */
  @Value("${g.nls.cache.seconds:-1}")
  private int nlsCacheSeconds;

  @Bean
  MessageSource messageSource() {
    final ReloadableResourceBundleMessageSource messageSource = new ExposedResourceBundleMessageSource();
    // Application bundle first, library bundle second: ReloadableResourceBundleMessageSource resolves a code by
    // walking the basenames in order and merges them in reverse, so in both code paths the FIRST basename wins.
    // The application therefore relabels a generic text from grafiosch-base rather than the other way round, which
    // is what makes the frontend lib/app split expressible through these two basenames (issue #214).
    messageSource.setBasenames("classpath:message/messages", "classpath:i18n/messages");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setUseCodeAsDefaultMessage(false);
    messageSource.setDefaultLocale(Locale.US);
    messageSource.setCacheSeconds(nlsCacheSeconds);
    return messageSource;
  }

  @Bean
  LocalValidatorFactoryBean validator() {
    LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
    bean.setValidationMessageSource(messageSource());
    return bean;
  }

}
