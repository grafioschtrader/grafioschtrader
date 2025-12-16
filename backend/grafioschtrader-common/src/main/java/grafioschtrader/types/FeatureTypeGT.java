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
  ALERT,
  /**
   * GTNet peer-to-peer network for data sharing between Grafioschtrader instances. Enables discovery of other
   * instances, trust token exchange, data sharing negotiation, and intraday price distribution.
   */
  GTNET
}
