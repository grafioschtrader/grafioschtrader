package grafiosch.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.UserPasskey;

/** Passkeys of the users; written only by the passkey services, every user access restricted by {@code id_user}. */
public interface UserPasskeyJpaRepository extends JpaRepository<UserPasskey, Integer> {

  List<UserPasskey> findByIdUserOrderByCreatedAt(Integer idUser);

  @Query("SELECT p FROM UserPasskey p WHERE p.credentialId = ?1")
  Optional<UserPasskey> findByCredentialId(byte[] credentialId);

  int countByIdUser(Integer idUser);

  /**
   * Records a verified sign-in. The counter must increase, except for the constant zero of synchronized passkeys; a
   * passkey deleted meanwhile changes no row and is therefore not restored.
   *
   * @param credentialId    the credential id of the passkey
   * @param signatureCount  the counter the authenticator reported in this assertion
   * @param now             time of the sign-in, UTC
   * @return 1 when the sign-in is accepted, 0 when the counter went backwards or the passkey no longer exists
   */
  @Transactional
  @Modifying
  @Query("""
      UPDATE UserPasskey p SET p.signatureCount = ?2, p.lastUsedAt = ?3 WHERE p.credentialId = ?1
      AND (p.signatureCount < ?2 OR (p.signatureCount = 0 AND ?2 = 0))""")
  int recordUsage(byte[] credentialId, long signatureCount, LocalDateTime now);

  /** Renames a passkey of the user; 0 when it is not the user's. */
  @Transactional
  @Modifying
  @Query("UPDATE UserPasskey p SET p.label = ?3 WHERE p.idUserPasskey = ?1 AND p.idUser = ?2")
  int rename(Integer idUserPasskey, Integer idUser, String label);

  /** Deletes a passkey of the user; 0 when it is not the user's. */
  @Modifying
  @Query("DELETE FROM UserPasskey p WHERE p.idUserPasskey = ?1 AND p.idUser = ?2")
  int deleteOwn(Integer idUserPasskey, Integer idUser);
}
