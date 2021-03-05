package grafioschtrader.repository;

import grafioschtrader.entities.User;

public interface VerificationTokenJpaRepositoryCustom {
  void createVerificationTokenForUser(final User user, final String token);
}
