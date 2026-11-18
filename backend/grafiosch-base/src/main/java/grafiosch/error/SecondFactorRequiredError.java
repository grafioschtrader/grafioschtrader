package grafiosch.error;

/**
 * Answer of the password sign-in for an account with an enabled second factor, when no valid code came with the
 * request. The client keeps e-mail and password and repeats the sign-in with a code. It is sent only after the password
 * was verified, so it reveals no more than a successful sign-in does.
 */
public class SecondFactorRequiredError extends SecurityBreachError {

  /** The request carried a code, but it was wrong, malformed or already spent. */
  public final boolean invalidCode;

  /** Too many wrong proofs of this user; no code is checked until the lock expires. */
  public final boolean locked;

  public SecondFactorRequiredError(String message, boolean invalidCode, boolean locked) {
    super(message);
    this.invalidCode = invalidCode;
    this.locked = locked;
  }
}
