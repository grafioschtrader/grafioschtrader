package grafioschtrader.nls;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import grafiosch.config.ExposedResourceBundleMessageSource;
import grafiosch.nls.NlsBundleInspector;
import grafioschtrader.config.MessageConfig;

/**
 * Pins the basename precedence of the message source: the application bundle
 * ({@code grafioschtrader-common/message/messages}) must override the library bundle
 * ({@code grafiosch-base/i18n/messages}).
 *
 * <p>
 * Before issue #214 the order was the other way round, so the reusable library had the last word and the application
 * could not relabel a generic text. Since the frontend lib/app split maps onto exactly these two basenames, that would
 * have made a whole class of texts unmovable.
 * </p>
 *
 * <p>
 * The context is built with a plain {@link AnnotationConfigApplicationContext} rather than {@code @SpringBootTest}:
 * {@link MessageConfig} has no collaborators, so no database, no Flyway and no application profile are involved.
 * </p>
 */
class NlsModulePrecedenceTest {

  /** Defined in both modules with different texts, which is what makes it a usable probe. */
  private static final String KEY_DEFINED_IN_BOTH_MODULES = "id.user.redirect";

  @Test
  @DisplayName("The application bundle overrides the library bundle for a key defined in both")
  void applicationOverridesLibrary() {
    String libraryValue = NlsBundleInspector.load(NlsBundleInspector.BASE_BUNDLE, null).entries()
        .get(KEY_DEFINED_IN_BOTH_MODULES);
    String applicationValue = NlsBundleInspector.load(NlsBundleInspector.COMMON_BUNDLE, null).entries()
        .get(KEY_DEFINED_IN_BOTH_MODULES);
    assertThat(applicationValue).as(
        "precondition: '%s' must exist in both bundles with different values, otherwise this test proves nothing",
        KEY_DEFINED_IN_BOTH_MODULES).isNotNull().isNotEqualTo(libraryValue);

    try (var context = new AnnotationConfigApplicationContext(MessageConfig.class, PlaceholderConfig.class)) {
      MessageSource messageSource = context.getBean(MessageSource.class);
      // getMessage() and getMessages() are separate code paths in Spring: the former walks the basenames, the latter
      // merges them. Both have to agree, because the former serves server-side validation errors while the latter
      // produces the payload the client receives.
      assertThat(messageSource.getMessage(KEY_DEFINED_IN_BOTH_MODULES, null, Locale.US))
          .as("resolved through getMessage()").isEqualTo(applicationValue);
      assertThat(((ExposedResourceBundleMessageSource) messageSource).getMessages(Locale.US)
          .getProperty(KEY_DEFINED_IN_BOTH_MODULES)).as("resolved through the merged payload")
          .isEqualTo(applicationValue);
    }
  }

  @Test
  @DisplayName("Basename order alone decides the winner, independent of the shipped texts")
  void firstBasenameWins() {
    // Proves the ordering rule itself on synthetic bundles, so the test still fails loudly if someone reverses
    // setBasenames() again but happens to also change the shipped values of the probe key above.
    assertThat(resolveWithBasenames("classpath:nls/precedence/application", "classpath:nls/precedence/library"))
        .isEqualTo("application");
    assertThat(resolveWithBasenames("classpath:nls/precedence/library", "classpath:nls/precedence/application"))
        .isEqualTo("library");
  }

  /**
   * Supplies the placeholder resolution that Spring Boot would normally contribute, so that the {@code @Value} default
   * of {@code g.nls.cache.seconds} in {@link MessageConfig} is applied outside a Boot context.
   */
  @Configuration
  static class PlaceholderConfig {
    @Bean
    static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
      return new PropertySourcesPlaceholderConfigurer();
    }
  }

  private static String resolveWithBasenames(String... basenames) {
    ReloadableResourceBundleMessageSource messageSource = new ExposedResourceBundleMessageSource();
    messageSource.setBasenames(basenames);
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setUseCodeAsDefaultMessage(false);
    messageSource.setDefaultLocale(Locale.US);
    return messageSource.getMessage("nls.precedence.probe", null, Locale.US);
  }
}
