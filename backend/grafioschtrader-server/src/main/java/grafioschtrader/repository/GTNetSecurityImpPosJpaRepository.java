package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.GTNetSecurityImpPos;

/**
 * Repository for managing GTNet security import position records.
 */
public interface GTNetSecurityImpPosJpaRepository
    extends JpaRepository<GTNetSecurityImpPos, Integer>, GTNetSecurityImpPosJpaRepositoryCustom {

  /**
   * Finds all positions belonging to a specific import header.
   *
   * @param idGtNetSecurityImpHead the header ID
   * @return list of positions for the header
   */
  List<GTNetSecurityImpPos> findByIdGtNetSecurityImpHead(Integer idGtNetSecurityImpHead);

  /**
   * Finds all positions belonging to a header that don't have a linked security yet.
   * Used by the GTNet security import task to identify positions that need lookup.
   *
   * @param idGtNetSecurityImpHead the header ID
   * @return list of positions without linked security
   */
  List<GTNetSecurityImpPos> findByIdGtNetSecurityImpHeadAndSecurityIsNull(Integer idGtNetSecurityImpHead);

  /**
   * Deletes all positions belonging to a specific import header.
   *
   * @param idGtNetSecurityImpHead the header ID
   */
  @Transactional
  @Modifying
  void deleteByIdGtNetSecurityImpHead(Integer idGtNetSecurityImpHead);
}
