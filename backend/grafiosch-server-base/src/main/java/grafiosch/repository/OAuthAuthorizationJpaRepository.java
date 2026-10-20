package grafiosch.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.OAuthAuthorization;
import jakarta.persistence.LockModeType;

/**
 * Repository of the OAuth token families. Deleting a family deletes its tokens through the foreign key of
 * {@code user_access_token}.
 */
public interface OAuthAuthorizationJpaRepository extends JpaRepository<OAuthAuthorization, Integer> {

  List<OAuthAuthorization> findByIdUserOrderByCreatedAtDesc(Integer idUser);

  Optional<OAuthAuthorization> findByIdUserAndClientId(Integer idUser, String clientId);

  /**
   * Loads a family and locks its row until the end of the transaction. Every refresh of the family passes this lock, so
   * two refreshes of one family never rotate at the same time.
   *
   * @param idOAuthAuthorization the family
   * @return the locked family, empty when it no longer exists
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a FROM OAuthAuthorization a WHERE a.idOAuthAuthorization = ?1")
  Optional<OAuthAuthorization> lockById(Integer idOAuthAuthorization);

  /**
   * Revokes a family of the user by deleting it together with its tokens.
   *
   * @return the number of deleted families, 0 when it does not exist or belongs to someone else
   */
  @Transactional
  @Modifying
  int deleteByIdUserAndIdOAuthAuthorization(Integer idUser, Integer idOAuthAuthorization);

  /**
   * Deletes the families that were revoked or have ended. Their tokens are already refused, and the foreign key removes
   * them with the family.
   *
   * @param nowUtc the current UTC time
   * @return the number of deleted families
   */
  @Transactional
  @Modifying
  @Query("DELETE FROM OAuthAuthorization a WHERE a.revokedAt IS NOT NULL OR a.expiresAt < ?1")
  int deleteRevokedOrEnded(LocalDateTime nowUtc);
}
