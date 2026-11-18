package grafiosch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.UserTotp;

/** One authenticator-app factor per user; written only through {@code CredentialChangeService}. */
public interface UserTotpJpaRepository extends JpaRepository<UserTotp, Integer> {

  /**
   * Spends a code of the enabled factor. A step is accepted only when it is later than the last accepted one, so the
   * same code - and every earlier one - is refused afterwards, also by a request that overlaps this one.
   *
   * @param idUser owner of the factor
   * @param step   time step of the verified code
   * @return 1 when the code was spent now, 0 when it was spent already or the factor is not enabled
   */
  @Transactional
  @Modifying
  @Query("""
      UPDATE UserTotp t SET t.lastUsedStep = ?2
      WHERE t.idUser = ?1 AND t.enabled = true AND (t.lastUsedStep IS NULL OR t.lastUsedStep < ?2)""")
  int spendStep(Integer idUser, long step);

  /** Tells whether the password sign-in of this user asks for a second factor. */
  @Query("SELECT COUNT(t) > 0 FROM UserTotp t WHERE t.idUser = ?1 AND t.enabled = true")
  boolean isEnabled(Integer idUser);

  @Modifying
  @Query("DELETE FROM UserTotp t WHERE t.idUser = ?1")
  int deleteByIdUser(Integer idUser);
}
