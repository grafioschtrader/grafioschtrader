package grafioschtrader.types;

import grafiosch.dto.ConfigurationWithLogin.FeatureType;

/**
 * Certain functionality is only partially implemented. Therefore, this should not be visible in the frontend. This can
 * be switched on or off.
 */
public enum FeatureTypeGT implements FeatureType {
  /** Real-time data transmission. For example, the transmission of stock prices */
  WEBSOCKET,
  /** Algorithm for trading. For example, the automatic execution of a trading */
  ALGO,
  /**
   * Alarm for security and portfolio events. For example, if the price of a security falls below a previously
   * determined value.
   */
  ALERT
  // GTNet used to be listed here. It moved to grafiosch.types.FeatureTypeBase, because the whole implementation lives
  // in the reusable library and a standalone Grafiosch server has to be able to report the feature as well.
}
