package grafiosch.repository;

import grafiosch.entities.User;

/**
* Custom repository interface for verification token operations.
* 
* <p>This interface extends the standard JPA repository functionality for verification tokens
* with application-specific operations. Verification tokens are used in the user registration
* and email verification process to ensure users have access to their registered email addresses.</p>
* 
* <h3>Purpose:</h3>
* <p>Provides custom database operations for verification tokens that are not covered by
* standard JPA repository methods. This includes creating tokens with specific business
* logic and validation rules.</p>
* 
* <h3>Integration:</h3>
* <p>This interface is typically implemented alongside the main JPA repository interface
* to provide a complete set of database operations for verification token management.
* The implementation handles token creation with proper expiration dates and user associations.</p>
*/ 
public interface VerificationTokenJpaRepositoryCustom {
  /**
   * Creates a new verification token for the specified user.
   * 
   * <p>This method creates and persists a new verification token associated with the given user.
   * The token is used in email verification workflows to confirm that users have access to
   * their registered email addresses.</p>
   * 
   * <p><strong>Token Properties:</strong></p>
   * <ul>
   *   <li>Associates the token with the specified user</li>
   *   <li>Sets an appropriate expiration date based on system configuration</li>
   *   <li>Ensures the token is unique and cryptographically secure</li>
   *   <li>Persists the token to the database for later verification</li>
   * </ul>
   */
  void createVerificationTokenForUser(final User user, final String token);
}
