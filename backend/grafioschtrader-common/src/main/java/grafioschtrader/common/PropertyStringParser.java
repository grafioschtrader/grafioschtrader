package grafioschtrader.common;

import java.util.HashMap;
import java.util.Map;

/**
 * Parser for PropertyString format: "KEY1=VALUE1,KEY2=VALUE2".
 *
 * <p>This utility class parses configuration strings stored in global parameters that follow the PropertyString format.
 * Keys are normalized to uppercase for case-insensitive lookup. Values are expected to be integers.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * PropertyStringParser parser = PropertyStringParser.parse("LP=1,HP=5");
 * int lpDays = parser.getIntValue("LP", 1); // returns 1
 * int hpDays = parser.getIntValue("HP", 5); // returns 5
 * </pre>
 */
public class PropertyStringParser {

  private final Map<String, Integer> values = new HashMap<>();

  private PropertyStringParser() {
  }

  /**
   * Parses a PropertyString format configuration string.
   *
   * @param propertyString the configuration string in format "KEY1=VALUE1,KEY2=VALUE2"
   * @return a PropertyStringParser instance with parsed values
   */
  public static PropertyStringParser parse(String propertyString) {
    PropertyStringParser parser = new PropertyStringParser();
    if (propertyString == null || propertyString.isBlank()) {
      return parser;
    }
    for (String pair : propertyString.split(",")) {
      String[] kv = pair.trim().split("=");
      if (kv.length == 2) {
        try {
          parser.values.put(kv[0].trim().toUpperCase(), Integer.parseInt(kv[1].trim()));
        } catch (NumberFormatException e) {
          // Skip invalid number values
        }
      }
    }
    return parser;
  }

  /**
   * Gets an integer value for the specified key.
   *
   * @param key the key to look up (case-insensitive)
   * @param defaultValue the default value to return if key is not found
   * @return the integer value for the key, or defaultValue if not found
   */
  public int getIntValue(String key, int defaultValue) {
    return values.getOrDefault(key.toUpperCase(), defaultValue);
  }

  /**
   * Checks if the parser contains a value for the specified key.
   *
   * @param key the key to check (case-insensitive)
   * @return true if the key exists, false otherwise
   */
  public boolean hasKey(String key) {
    return values.containsKey(key.toUpperCase());
  }
}
