package grafiosch.config;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import grafiosch.types.FeatureTypeBase;

/**
 * Binds the library-level feature toggles from the {@code g.use.*} properties (for example in {@code application.yaml}).
 *
 * <p>
 * This is the reusable-library counterpart of an application's own feature configuration (such as GrafioschTrader's
 * {@code gt.use.*}). Each enabled toggle becomes a {@link FeatureTypeBase} value that is merged into the feature set
 * delivered to the frontend at login, where it controls visibility of the corresponding UI.
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "g.use")
public class BaseFeatureConfig {

  /** Whether the manage-client feature (advisor manages additional tenants with read-only client logins) is enabled. */
  private boolean manageclient;

  /**
   * Master switch for GTNet. When false, GTNet is inert for this run regardless of the database parameter
   * {@code g.gnet.use} — no peer status check on startup, no offline broadcast on shutdown, no GTNet price/history
   * retrieval, and the GTNET feature is not reported to the frontend. Intended as a launch argument
   * ({@code --g.use.gtnet=false}) for development, so the database parameter need not be toggled.
   */
  private boolean gtnet = true;

  /**
   * Returns the set of enabled library-level features.
   *
   * @return the enabled {@link FeatureTypeBase} values, possibly empty
   */
  public Set<FeatureTypeBase> getEnabledFeatures() {
    EnumSet<FeatureTypeBase> features = EnumSet.noneOf(FeatureTypeBase.class);
    if (manageclient) {
      features.add(FeatureTypeBase.MANAGECLIENT);
    }
    return features;
  }

  public boolean isManageclient() {
    return manageclient;
  }

  public void setManageclient(boolean manageclient) {
    this.manageclient = manageclient;
  }

  public boolean isGtnet() {
    return gtnet;
  }

  public void setGtnet(boolean gtnet) {
    this.gtnet = gtnet;
  }
}
