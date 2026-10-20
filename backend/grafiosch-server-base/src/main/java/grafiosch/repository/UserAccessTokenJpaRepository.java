package grafiosch.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.UserAccessToken;
import grafiosch.types.AccessTokenKind;
import jakarta.persistence.LockModeType;

/**
 * Repository of the personal access tokens and of the tokens issued to OAuth clients. A token is found by the SHA-256
 * hash of its clear text, which the unique index on {@code token_hash} turns into a single index lookup per
 * authenticated request. The token kind is passed as the byte value of {@link AccessTokenKind}.
 */
public interface UserAccessTokenJpaRepository
    extends JpaRepository<UserAccessToken, Integer>, UserAccessTokenJpaRepositoryCustom {

  @Query("SELECT t FROM UserAccessToken t WHERE t.idUser = ?1 AND t.tokenKind = ?2 ORDER BY t.createdAt DESC")
  List<UserAccessToken> findByIdUserAndKind(Integer idUser, byte tokenKind);

  Optional<UserAccessToken> findByTokenHash(String tokenHash);

  /**
   * Loads a token by its hash and locks its row until the end of the transaction, so that a refresh token is consumed
   * exactly once even when a client sends it twice at the same time.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT t FROM UserAccessToken t WHERE t.tokenHash = ?1")
  Optional<UserAccessToken> lockByTokenHash(String tokenHash);

  @Query("SELECT COUNT(t) > 0 FROM UserAccessToken t WHERE t.idUser = ?1 AND t.tokenName = ?2 AND t.tokenKind = ?3")
  boolean existsByIdUserAndTokenNameAndKind(Integer idUser, String tokenName, byte tokenKind);

  /**
   * Revokes a personal token by deleting it, but only when it belongs to the given user. Tokens of an OAuth client are
   * revoked through their family.
   *
   * @param idUser            the owner
   * @param idUserAccessToken the token to revoke
   * @return the number of deleted rows, 0 when the token does not exist, belongs to someone else or is no personal
   *         token
   */
  @Transactional
  @Modifying
  @Query("DELETE FROM UserAccessToken t WHERE t.idUser = ?1 AND t.idUserAccessToken = ?2 AND t.tokenKind = 1")
  int deletePersonalToken(Integer idUser, Integer idUserAccessToken);

  /** Deletes every token of an OAuth family, used when a replayed refresh token revokes it. */
  @Transactional
  @Modifying
  @Query("DELETE FROM UserAccessToken t WHERE t.idOAuthAuthorization = ?1")
  int deleteByIdOAuthAuthorization(Integer idOAuthAuthorization);

  /** Deletes the expired access tokens of an OAuth family, as part of every rotation. */
  @Transactional
  @Modifying
  @Query("DELETE FROM UserAccessToken t WHERE t.idOAuthAuthorization = ?1 AND t.tokenKind = 2 AND t.expiresAt < ?2")
  int deleteExpiredAccessTokensOfFamily(Integer idOAuthAuthorization, LocalDateTime nowUtc);

  /**
   * Keeps only the most recently consumed refresh tokens of an OAuth family. They stay recognizable so that a replay
   * revokes the family; older ones are deleted in the same transaction as the rotation that consumed the newest.
   *
   * Named query: UserAccessToken.deleteConsumedBeyond
   *
   * @param idOAuthAuthorization the family
   * @param keep                 how many consumed refresh tokens remain, the newest by consumption time and id
   * @return the number of deleted tokens
   */
  @Transactional
  @Modifying
  @Query(nativeQuery = true)
  int deleteConsumedBeyond(Integer idOAuthAuthorization, int keep);

  /**
   * Records the use of a token. The condition on the stored value makes the write happen at most once per hour, even
   * when several requests of the same token arrive at the same time.
   *
   * @param idUserAccessToken the token that was used
   * @param nowUtc            the time of use in UTC
   * @param notAfterUtc       the stored value must be older than this, normally one hour before {@code nowUtc}
   * @return 1 when the time of use was written, otherwise 0
   */
  @Transactional
  @Modifying
  @Query("""
      UPDATE UserAccessToken t SET t.lastUsedAt = ?2
      WHERE t.idUserAccessToken = ?1 AND (t.lastUsedAt IS NULL OR t.lastUsedAt < ?3)""")
  int updateLastUsedAt(Integer idUserAccessToken, LocalDateTime nowUtc, LocalDateTime notAfterUtc);

  /**
   * Deletes the personal tokens whose expiry lies before the given time. Expired tokens stay visible in the dialog for
   * a while, so the purge task passes a time 30 days in the past.
   *
   * @param expiredBeforeUtc personal tokens that expired before this UTC time are deleted
   * @return the number of deleted tokens
   */
  @Transactional
  @Modifying
  @Query("DELETE FROM UserAccessToken t WHERE t.tokenKind = 1 AND t.expiresAt < ?1")
  int deleteExpiredBefore(LocalDateTime expiredBeforeUtc);

  /**
   * Deletes the expired OAuth tokens that were never consumed. A consumed refresh token stays until its family ends,
   * because only then can a replay of it no longer occur.
   *
   * @param nowUtc the current UTC time
   * @return the number of deleted tokens
   */
  @Transactional
  @Modifying
  @Query("DELETE FROM UserAccessToken t WHERE t.tokenKind <> 1 AND t.consumedAt IS NULL AND t.expiresAt < ?1")
  int deleteExpiredOAuthTokens(LocalDateTime nowUtc);
}
