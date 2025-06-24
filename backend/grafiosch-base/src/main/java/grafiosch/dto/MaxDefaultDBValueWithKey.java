package grafiosch.dto;

/**
 * Wrapper class that associates a configuration key with its corresponding default and database values. Provides
 * immutable pairing of parameter keys with their value objects.
 */
public class MaxDefaultDBValueWithKey {
  /**
   * The configuration parameter key.
   */
  public final String key;

  /**
   * The value object containing default and cached database values.
   */
  public final MaxDefaultDBValue maxDefaultDBValue;

  /**
   * Creates a new key-value pair wrapper.
   * 
   * @param key               the configuration parameter key
   * @param maxDefaultDBValue the value object containing default and database values
   */
  public MaxDefaultDBValueWithKey(String key, MaxDefaultDBValue maxDefaultDBValue) {
    this.key = key;
    this.maxDefaultDBValue = maxDefaultDBValue;
  }
}
