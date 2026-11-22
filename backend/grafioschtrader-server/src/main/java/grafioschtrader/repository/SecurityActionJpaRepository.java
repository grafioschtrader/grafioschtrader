package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.SecurityAction;
import grafioschtrader.types.SecurityActionType;
import jakarta.persistence.LockModeType;

public interface SecurityActionJpaRepository extends JpaRepository<SecurityAction, Integer> {

  List<SecurityAction> findAllByOrderByActionDateDesc();

  /**
   * Tells whether the given security is the successor of an ISIN change, that is whether it was referenced as the new
   * security of an ISIN change. Such a security starts its life at the action date, so a split dated at or before its
   * first historical quote belongs to its predecessor and must not be applied a second time.
   *
   * @param idSecuritycurrency the security to test
   * @return true when at least one ISIN change points to this security as its new security
   */
  default boolean isIsinChangeSuccessor(Integer idSecuritycurrency) {
    return existsBySecurityNew_IdSecuritycurrencyAndActionType(idSecuritycurrency,
        SecurityActionType.ISIN_CHANGE.getValue());
  }

  boolean existsBySecurityNew_IdSecuritycurrencyAndActionType(Integer idSecuritycurrency, byte actionType);

  /**
   * Returns the recorded spin-offs whose parent is the given security, oldest first.
   *
   * @param idSecuritycurrency the parent security
   * @return the spin-offs of the parent, empty when it has none
   */
  default List<SecurityAction> findSpinOffsOfParent(Integer idSecuritycurrency) {
    return findBySecurityOld_IdSecuritycurrencyAndActionTypeOrderByActionDateAsc(idSecuritycurrency,
        SecurityActionType.SPIN_OFF.getValue());
  }

  List<SecurityAction> findBySecurityOld_IdSecuritycurrencyAndActionTypeOrderByActionDateAsc(
      Integer idSecuritycurrency, byte actionType);

  /**
   * Tells whether a spin-off of one of the given securities dated after the given date has not yet decided whether the
   * history source of its parent is adjusted for it. Such a source may have divided the closes from the given date on,
   * so the legs of an earlier spin-off cannot be priced yet.
   *
   * @param idsSecuritycurrency parent and child of the earlier spin-off
   * @param date                the action date of the earlier spin-off
   * @return true when such a later spin-off exists
   */
  @Query("""
      SELECT COUNT(a) > 0 FROM SecurityAction a WHERE a.actionType = 1
      AND a.securityOld.idSecuritycurrency IN :idsSecuritycurrency AND a.actionDate > :date
      AND (a.historyBasis IS NULL OR a.historyBasis = 0)""")
  boolean existsLaterSpinOffWithUndeterminedHistory(List<Integer> idsSecuritycurrency, LocalDate date);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<SecurityAction> findFirstBySecurityOld_IdSecuritycurrencyAndActionDateAndActionType(
      Integer idSecuritycurrency, LocalDate actionDate, byte actionType);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a FROM SecurityAction a WHERE a.idSecurityAction = :id")
  Optional<SecurityAction> findByIdForUpdate(Integer id);

  @Modifying(flushAutomatically = true)
  @Query("UPDATE SecurityAction a SET a.appliedCount = a.appliedCount + :delta WHERE a.idSecurityAction = :id")
  void incrementAppliedCount(Integer id, int delta);
}
