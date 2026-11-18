package grafiosch.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import grafiosch.entities.UserRecoveryCode;

/** Hashed one-time recovery codes; written only through {@code CredentialChangeService}. */
public interface UserRecoveryCodeJpaRepository extends JpaRepository<UserRecoveryCode, Integer> {

  /**
   * Spends a recovery code. Of two overlapping requests with the same code only one changes the row.
   *
   * @param idUser   owner of the code
   * @param codeHash SHA-256 hash of the normalized code
   * @param now      time of use, UTC
   * @return 1 when the code was valid and is spent now, otherwise 0
   */
  @Modifying
  @Query("""
      UPDATE UserRecoveryCode c SET c.usedAt = ?3
      WHERE c.idUser = ?1 AND c.codeHash = ?2 AND c.usedAt IS NULL""")
  int consume(Integer idUser, String codeHash, LocalDateTime now);

  /** Number of codes not spent yet. */
  @Query("SELECT COUNT(c) FROM UserRecoveryCode c WHERE c.idUser = ?1 AND c.usedAt IS NULL")
  int countUnused(Integer idUser);

  @Modifying
  @Query("DELETE FROM UserRecoveryCode c WHERE c.idUser = ?1")
  int deleteByIdUser(Integer idUser);
}
