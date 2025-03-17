package grafiosch.error;

/**
 * Used when an user attempt to misuse tenant id.
 */
public class SecurityBreachError {
  public String message;

  public SecurityBreachError(String message) {
    this.message = message;
  }
}
