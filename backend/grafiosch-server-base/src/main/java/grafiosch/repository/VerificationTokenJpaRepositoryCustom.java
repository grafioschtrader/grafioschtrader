package grafiosch.repository;

import grafiosch.entities.User;

public interface VerificationTokenJpaRepositoryCustom {
  void createVerificationTokenForUser(final User user, final String token);
}
