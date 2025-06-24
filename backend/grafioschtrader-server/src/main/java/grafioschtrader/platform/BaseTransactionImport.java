package grafioschtrader.platform;

public abstract class BaseTransactionImport {

  /** Prefix applied to all platform IDs to create unique identifiers within the import system. */
  private static String ID_PREFIX = "gt.platform.import.";
  
  /** Unique identifier for this trading platform, automatically prefixed with the standard namespace. */
  protected String id;
  
  /** Human-readable name of the trading platform for display in user interfaces. */
  protected String readableName;

  /**
   * Creates a new trading platform transaction import handler with standardized identification.
   * 
   * @param id Platform identifier (will be prefixed with "gt.platform.import.")
   * @param readableName Human-readable platform name for display purposes
   */
  public BaseTransactionImport(final String id, final String readableName) {
    this.id = ID_PREFIX + id;
    this.readableName = readableName;
  }

  public String getID() {
    return id;
  }

  public String getReadableName() {
    return readableName;
  }
}
