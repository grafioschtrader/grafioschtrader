package grafiosch.repository;

import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.entities.User;
import grafiosch.entities.VerificationToken;

public class VerificationTokenJpaRepositoryImpl implements VerificationTokenJpaRepositoryCustom {

  @Autowired
  private VerificationTokenJpaRepository verificationTokenJpaRepository;

  @Override
  public void createVerificationTokenForUser(final User user, final String token) {
    final VerificationToken myToken = new VerificationToken(token, user);
    verificationTokenJpaRepository.save(myToken);
  }
}
