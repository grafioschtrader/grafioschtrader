package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.GTNetSecurityImpGap;

/**
 * Repository for managing GTNetSecurityImpGap entities. Gap records document what didn't match
 * when importing securities from GTNet peers.
 */
@Repository
public interface GTNetSecurityImpGapJpaRepository extends JpaRepository<GTNetSecurityImpGap, Integer> {

  /**
   * Finds all gap records for a specific import position.
   *
   * @param idGtNetSecurityImpPos the ID of the import position
   * @return list of gap records for the position
   */
  List<GTNetSecurityImpGap> findByIdGtNetSecurityImpPos(Integer idGtNetSecurityImpPos);

  /**
   * Finds all gap records for multiple import positions.
   *
   * @param idGtNetSecurityImpPosList the list of import position IDs
   * @return list of gap records for all specified positions
   */
  List<GTNetSecurityImpGap> findByIdGtNetSecurityImpPosIn(List<Integer> idGtNetSecurityImpPosList);

  /**
   * Deletes all gap records for a specific import position.
   * Used when re-processing a position to clear old gaps before recording new ones.
   *
   * @param idGtNetSecurityImpPos the ID of the import position
   */
  @Transactional
  @Modifying
  void deleteByIdGtNetSecurityImpPos(Integer idGtNetSecurityImpPos);
}
