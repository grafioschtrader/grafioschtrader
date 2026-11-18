package grafiosch.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.UserPasswordReset;

/** A natural primary key bounds reset credentials to one per user; no generic write endpoint exists. */
public interface UserPasswordResetJpaRepository extends JpaRepository<UserPasswordReset, Integer> {
  Optional<UserPasswordReset> findByTokenHash(String tokenHash);

  /** Consumes a live token atomically; exactly one overlapping confirmation can delete it. */
  @Modifying
  @Query("DELETE FROM UserPasswordReset r WHERE r.tokenHash = ?1 AND r.expiryDate > ?2")
  int consume(String tokenHash, LocalDateTime now);

  @Modifying
  @Query("DELETE FROM UserPasswordReset r WHERE r.idUser = ?1")
  void deleteByIdUser(Integer idUser);

  @Transactional
  @Modifying
  @Query("DELETE FROM UserPasswordReset r WHERE r.expiryDate < ?1")
  int deleteAllExpiredSince(LocalDateTime now);
}
