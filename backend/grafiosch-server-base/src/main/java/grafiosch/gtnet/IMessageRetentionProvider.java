package grafiosch.gtnet;

import java.util.List;

/**
 * Provider interface for GTNet message retention configuration.
 *
 * Application modules implement this interface to register their message code groups
 * and default retention periods. The scheduled aggregation task discovers all registered
 * providers via Spring dependency injection and deletes old messages accordingly.
 *
 * <h3>Example implementation</h3>
 * <pre>
 * &#64;Bean
 * public IMessageRetentionProvider lastPriceRetention() {
 *     return new IMessageRetentionProvider() {
 *         public String getConfigKey() { return "LP"; }
 *         public List&lt;Byte&gt; getMessageCodes() { return List.of((byte) 60, (byte) 61); }
 *         public int getDefaultRetentionDays() { return 1; }
 *     };
 * }
 * </pre>
 */
public interface IMessageRetentionProvider {

  /**
   * Returns the configuration key used in the PropertyString format.
   * This key is used to look up retention days from the global parameter
   * {@code gt.gtnet.del.message.recv} (e.g., "LP=1,HP=5,SL=5").
   *
   * @return the config key (e.g., "LP", "HP", "SL")
   */
  String getConfigKey();

  /**
   * Returns the list of GTNet message code byte values that should be deleted
   * when retention period expires.
   *
   * @return list of message code bytes
   */
  List<Byte> getMessageCodes();

  /**
   * Returns the default retention period in days, used when the config key
   * is not found in the global parameter.
   *
   * @return default retention days
   */
  int getDefaultRetentionDays();
}
