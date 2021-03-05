package grafioschtrader.platform;

public class BaseTransactionImport {

  private static String ID_PREFIX = "gt.platform.import.";
  protected String id;
  protected String readableName;

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
