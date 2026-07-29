package grafiosch.config;

import java.io.IOException;
import java.util.Locale;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.io.Resource;

/**
 * Message source that additionally exposes the whole merged bundle, which is what the language endpoint delivers to the
 * client.
 */
public class ExposedResourceBundleMessageSource extends ReloadableResourceBundleMessageSource {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  private volatile long lastMergedCacheClear = System.currentTimeMillis();

  @Override
  protected Properties loadProperties(Resource resource, String fileName) throws IOException {
    log.info("Load {}", fileName);
    return super.loadProperties(resource, fileName);
  }

  /**
   * Whether bundles are kept in memory for the lifetime of the server, which is the production setting. A finite cache
   * interval instead means texts are expected to be edited while the server runs, so anything downstream that derives
   * data from the bundles must not memoize it either.
   *
   * @return true when {@code cacheSeconds} is negative
   */
  public boolean isCachingForever() {
    return getCacheMillis() < 0;
  }

  /**
   * Gets all messages for presented Locale.
   *
   * <p>
   * Honours {@code cacheSeconds} explicitly, because the inherited implementation does not:
   * {@code getMergedProperties(Locale)} answers from its own {@code cachedMergedProperties} map and never consults the
   * configured cache interval, so a merged bundle stays in memory until {@link #clearCache()} is called. Setting
   * {@code cacheSeconds} alone would therefore let {@code getMessage(...)} pick up an edited text while this method, and
   * with it the entire client payload, stayed pinned to the texts read at startup.
   * </p>
   *
   * @param locale user request's locale
   * @return all messages
   */
  public Properties getMessages(Locale locale) {
    long cacheMillis = getCacheMillis();
    if (cacheMillis >= 0 && System.currentTimeMillis() - lastMergedCacheClear > cacheMillis) {
      synchronized (this) {
        if (System.currentTimeMillis() - lastMergedCacheClear > cacheMillis) {
          clearCache();
          lastMergedCacheClear = System.currentTimeMillis();
        }
      }
    }
    return getMergedProperties(locale).getProperties();
  }
}