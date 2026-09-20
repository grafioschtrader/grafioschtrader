package grafiosch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import grafiosch.entities.UserDashboard;

/** Layout storage; all writes must go through DashboardLayoutRepository for ownership and document validation. */
public interface UserDashboardJpaRepository extends JpaRepository<UserDashboard, Integer> {
  /**
   * Every successful save advances revision, including an unchanged document. Called within the validated transaction.
   */
  @Modifying
  @Query("UPDATE UserDashboard d SET d.layout = ?2, d.revision = d.revision + 1 WHERE d.idUser = ?1 AND d.revision = ?3")
  int compareAndSave(Integer idUser, String layout, Long revision);
}
