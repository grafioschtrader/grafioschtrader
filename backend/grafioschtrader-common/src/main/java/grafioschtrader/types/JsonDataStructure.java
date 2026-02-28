package grafioschtrader.types;

/**
 * Defines JSON data structure layouts found in data provider responses.
 * ARRAY_OF_OBJECTS: Each element is an object with named fields (most common).
 * PARALLEL_ARRAYS: Separate arrays for each field, correlated by index (e.g., Finnhub).
 * SINGLE_OBJECT: A single JSON object with direct field access (e.g., intraday quotes).
 * COLUMN_ROW_ARRAYS: Column names in one array and row values in separate arrays, correlated by position
 * (e.g., SIX Swiss Exchange).
 */
public enum JsonDataStructure {
  ARRAY_OF_OBJECTS((byte) 1),
  PARALLEL_ARRAYS((byte) 2),
  SINGLE_OBJECT((byte) 3),
  COLUMN_ROW_ARRAYS((byte) 4);

  private final Byte value;

  private JsonDataStructure(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static JsonDataStructure getByValue(byte value) {
    for (JsonDataStructure type : JsonDataStructure.values()) {
      if (type.getValue() == value) {
        return type;
      }
    }
    return null;
  }
}
