package grafioschtrader.config;

import java.io.IOException;
import java.util.Locale;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.io.Resource;

public class ExposedResourceBundleMessageSource extends ReloadableResourceBundleMessageSource {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Override
  protected Properties loadProperties(Resource resource, String fileName) throws IOException {
    log.info("Load {}", fileName);
    return super.loadProperties(resource, fileName);
  }

  /**
   * Gets all messages for presented Locale.
   *
   * @param locale user request's locale
   * @return all messages
   */
  public Properties getMessages(Locale locale) {
    return getMergedProperties(locale).getProperties();
  }
}