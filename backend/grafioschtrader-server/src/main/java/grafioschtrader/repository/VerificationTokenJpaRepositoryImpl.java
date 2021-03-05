package grafioschtrader.repository;

import org.springframework.beans.factory.annotation.Autowired;

import grafioschtrader.entities.User;
import grafioschtrader.entities.VerificationToken;

public class VerificationTokenJpaRepositoryImpl {

  @Autowired
  VerificationTokenJpaRepository verificationTokenJpaRepository;

  public void createVerificationTokenForUser(final User user, final String token) {
    final VerificationToken myToken = new VerificationToken(token, user);
    verificationTokenJpaRepository.save(myToken);
  }
}
