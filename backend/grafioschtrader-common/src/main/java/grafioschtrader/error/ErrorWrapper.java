package grafioschtrader.error;

/**
 * Errors are mapped into this class.
 *
 * @author Hugo Graf
 *
 */
public class ErrorWrapper {
  public String className;
  public Object error;

  public ErrorWrapper(Object error) {
    this.className = error.getClass().getSimpleName();
    this.error = error;
  }

}
