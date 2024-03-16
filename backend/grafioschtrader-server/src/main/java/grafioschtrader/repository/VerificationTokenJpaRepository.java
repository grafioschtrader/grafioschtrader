package grafioschtrader.repository;

import java.util.Date;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.VerificationToken;

public interface VerificationTokenJpaRepository
    extends JpaRepository<VerificationToken, Integer>, VerificationTokenJpaRepositoryCustom {

  VerificationToken findByToken(String token);

  @Modifying
  @Query("DELETE FROM VerificationToken t WHERE t.expiryDate <= ?1")
  void deleteAllExpiredSince(Date now);
}
