package grafioschtrader.error;

/**
 * Used when an user attempt to misuse tenant id.
 *
 * @author Hugo Graf
 *
 */
public class SecurityBreachError {
  public String message;

  public SecurityBreachError(String message) {
    this.message = message;
  }
}
