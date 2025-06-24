package grafiosch.dto;

/**
 * Data transfer object that holds default and database values for configuration parameters. Provides caching mechanism
 * for database values with fallback to default values.
 */
public class MaxDefaultDBValue {
  int defaultValue;
  Integer dbValue;

  /**
   * Creates a new instance with the specified default value.
   * 
   * @param defaultValue the default value to use when no database value is set
   */
  public MaxDefaultDBValue(Integer defaultValue) {
    super();
    this.defaultValue = defaultValue;
  }

  /**
   * Gets the cached database value.
   * 
   * @return the database value, null if not yet loaded from database
   */
  public Integer getDbValue() {
    return dbValue;
  }

  /**
   * Sets the cached database value.
   * 
   * @param dbValue the database value to cache
   */
  public void setDbValue(Integer dbValue) {
    this.dbValue = dbValue;
  }

  /**
   * Gets the default value.
   * 
   * @return the default value
   */
  public int getDefaultValue() {
    return defaultValue;
  }
}