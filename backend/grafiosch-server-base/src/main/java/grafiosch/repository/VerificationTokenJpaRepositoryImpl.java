package grafiosch.repository;

import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.entities.User;
import grafiosch.entities.VerificationToken;

/**
 * Implementation of custom verification token repository operations.
 * 
 * <p>This class provides the concrete implementation for custom verification token
 * database operations that extend beyond standard JPA repository functionality.
 * It handles the creation and persistence of verification tokens used in user
 * registration and email verification workflows.</p>
 */ 
public class VerificationTokenJpaRepositoryImpl implements VerificationTokenJpaRepositoryCustom {

  @Autowired
  private VerificationTokenJpaRepository verificationTokenJpaRepository;

  @Override
  public void createVerificationTokenForUser(final User user, final String token) {
    final VerificationToken myToken = new VerificationToken(token, user);
    verificationTokenJpaRepository.save(myToken);
  }
}
