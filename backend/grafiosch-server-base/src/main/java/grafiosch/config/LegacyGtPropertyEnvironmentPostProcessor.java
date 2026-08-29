package grafiosch.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

/**
 * Accepts the {@code gt.} spelling of the nine deployment properties that moved to the library prefix {@code g.} with
 * GitHub issue #75, so that an installation which still supplies the old names keeps working for one upgrade release.
 *
 * <p>
 * The case this exists for is Docker: {@code docker/update.sh} pulls new images but never refreshes
 * {@code docker-compose.yml}, so an installed host goes on passing {@code GT_JWT_SECRET},
 * {@code GT_MAIN_USER_ADMIN_MAIL} and {@code GT_ALLOWED_USERS} into an image that reads only {@code g.*}. Without this
 * bridge the container either fails to start or silently runs with the wrong administrator and user cap. Classic
 * installations are migrated by {@code util/shellscripts/gt_to_g_rename.sh} instead and do not depend on it.
 * </p>
 *
 * <p>
 * A nested placeholder such as {@code @Value("${g.jwt.secret:${gt.jwt.secret}}")} cannot do this: the packaged
 * {@code application.properties} defines the new key, so the outer placeholder is always satisfied and the fallback
 * never fires. For the same reason the resolution below inspects the individual property sources rather than asking
 * {@link org.springframework.core.env.Environment#getProperty(String)}, which would likewise always find the packaged
 * default.
 * </p>
 *
 * <p>
 * <b>Removal:</b> this class, its {@code META-INF/spring.factories} entry and the accompanying release note may be
 * deleted in the release after the one that introduced the rename, once every installation has been updated once.
 * </p>
 */
public class LegacyGtPropertyEnvironmentPostProcessor implements EnvironmentPostProcessor {

  /** Name of the property source holding the bridged values; high priority, but only ever filled with missing keys. */
  private static final String PROPERTY_SOURCE_NAME = "grafioschLegacyGtPropertyBridge";

  /**
   * Legacy name to new name. The very same mapping is encoded in {@code util/shellscripts/gt_to_g_rename.sh}; the two
   * must stay in step. Application-owned keys such as {@code gt.datafeed.*}, {@code gt.eod.*}, {@code gt.use.*} and
   * {@code gt.gtnet.exchange.sync.cron} are deliberately absent - they keep the {@code gt.} prefix.
   */
  private static final Map<String, String> LEGACY_TO_NEW = Map.of("gt.jwt.secret", "g.jwt.secret",
      "gt.main.user.admin.mail", "g.main.user.admin.mail", "gt.allowed.users", "g.allowed.users",
      "gt.demo.account.pattern.de", "g.demo.account.pattern.de", "gt.demo.account.pattern.en",
      "g.demo.account.pattern.en", "gt.purge.cron.expression", "g.purge.cron.expression", "gt.purge.task.data",
      "g.purge.task.data", "gt.gtnet.log.aggregation.cron", "g.gnet.log.aggregation.cron",
      "gt.gtnet.future.message.cron", "g.gnet.future.message.cron");

  private final Log log;

  /**
   * @param logFactory factory handing out a logger that buffers until the logging system is initialized, because an
   *                   environment post processor runs before that happens
   */
  public LegacyGtPropertyEnvironmentPostProcessor(DeferredLogFactory logFactory) {
    this.log = logFactory.getLog(LegacyGtPropertyEnvironmentPostProcessor.class);
  }

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
    Map<String, Object> bridged = new LinkedHashMap<>();
    for (Map.Entry<String, String> mapping : LEGACY_TO_NEW.entrySet()) {
      bridgeIfOnlyLegacyIsDefined(environment, mapping.getKey(), mapping.getValue(), bridged);
    }
    if (!bridged.isEmpty()) {
      environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, bridged));
    }
  }

  /**
   * Publishes the legacy value under the new name when the highest-priority source that knows either name knows only
   * the legacy one. Looking at the first such source is what makes {@code addFirst} safe: no source that outranks it
   * defines the new name, so nothing the user configured can be overridden.
   *
   * @param environment the environment whose sources are inspected
   * @param legacyName  the {@code gt.} spelling
   * @param newName     the {@code g.} spelling that the code reads
   * @param bridged     collects the values to publish; left untouched when there is nothing to bridge
   */
  private void bridgeIfOnlyLegacyIsDefined(ConfigurableEnvironment environment, String legacyName, String newName,
      Map<String, Object> bridged) {
    for (PropertySource<?> source : environment.getPropertySources()) {
      boolean hasNew = source.containsProperty(newName);
      boolean hasLegacy = source.containsProperty(legacyName);
      if (hasNew) {
        // The first source to mention either name uses the new one - nothing to do, whether or not it also
        // carries the legacy name. The new name always wins.
        return;
      }
      if (hasLegacy) {
        bridged.put(newName, source.getProperty(legacyName));
        log.warn("The property '" + legacyName + "' is deprecated and was renamed to '" + newName
            + "' (GitHub issue #75). Its value from '" + source.getName()
            + "' is still honoured in this release; please rename it - a container installation renames the "
            + "environment variable, a classic installation is migrated by util/shellscripts/gt_to_g_rename.sh.");
        return;
      }
    }
  }

}
