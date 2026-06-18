package grafiosch.types;

import grafiosch.dto.ConfigurationWithLogin.FeatureType;

/**
 * Library-level optional features that can be switched on or off per deployment via the {@code g.use.*} properties.
 *
 * <p>
 * Mirrors the application-specific feature mechanism (for example GrafioschTrader's {@code gt.use.*} features) but at
 * the reusable library level, so any application built on this base can enable or disable these features independently.
 * Enabled values are delivered to the frontend in the login configuration's feature set and used there to show or hide
 * the corresponding UI.
 * </p>
 */
public enum FeatureTypeBase implements FeatureType {
  /**
   * Managing clients on behalf of others: a user (advisor) can create additional tenants with a read-only client login,
   * switch between the tenants they manage, and return to their own tenant. Toggled by {@code g.use.manageclient}.
   */
  MANAGECLIENT
}
