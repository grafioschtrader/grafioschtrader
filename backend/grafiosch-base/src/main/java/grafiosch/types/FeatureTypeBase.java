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
  MANAGECLIENT,

  /**
   * GTNet peer-to-peer network for data sharing between instances. Enables discovery of other instances, trust token
   * exchange, data sharing negotiation, and intraday price distribution. The whole GTNet implementation — entities,
   * message handlers, REST resources and the Angular components in {@code src/app/lib/gnet} — belongs to the reusable
   * library, which is why the feature is reported here and not from an application's own feature set. Toggled by the
   * launch argument {@code g.use.gtnet} together with the global parameter {@code g.gnet.use}.
   */
  GTNET
}
