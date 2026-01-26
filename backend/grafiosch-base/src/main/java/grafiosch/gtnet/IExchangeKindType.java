package grafiosch.gtnet;

/**
 * Interface for exchange kind type enums.
 *
 * Allows message classes in the base module to work with exchange kinds without depending
 * on specific implementations. Application modules can provide their own enum implementations
 * with specific exchange kind values.
 *
 * <h3>Usage Example</h3>
 * <pre>
 * public enum GTNetExchangeKindType implements IExchangeKindType {
 *     LAST_PRICE((byte) 0),
 *     HISTORICAL_PRICES((byte) 1);
 *     // ...
 * }
 * </pre>
 */
public interface IExchangeKindType {

  /**
   * Returns the byte value representing this exchange kind.
   *
   * @return the byte value for serialization/deserialization
   */
  Byte getValue();

  /**
   * Returns the enum constant name.
   *
   * @return the name of this enum constant
   */
  String name();

  /**
   * Indicates whether this exchange kind participates in bulk synchronization.
   *
   * Syncable kinds (like LAST_PRICE, HISTORICAL_PRICES) participate in data request/accept/revoke
   * flows and are included in default exchange configurations. Non-syncable kinds (like
   * SECURITY_METADATA) use on-demand lookup patterns and are excluded from bulk sync operations.
   *
   * @return true if this kind participates in bulk sync, false for on-demand lookup kinds
   */
  default boolean isSyncable() {
    return true;
  }

  /**
   * Indicates whether this exchange kind supports AC_PUSH_OPEN mode.
   *
   * Push-enabled kinds can be configured with AC_PUSH_OPEN to actively receive pushed updates
   * from remote instances. Kinds that don't support push (like SECURITY_METADATA) can only use
   * AC_CLOSED or AC_OPEN modes.
   *
   * @return true if this kind supports AC_PUSH_OPEN configuration, false otherwise
   */
  default boolean supportsPush() {
    return true;
  }
}
