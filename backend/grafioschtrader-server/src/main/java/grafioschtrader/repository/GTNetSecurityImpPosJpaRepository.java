package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
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

  /**
   * Finds positions by header ID and historyquote import status. Used to track positions that need historical data
   * loading or to retrieve statistics on import outcomes.
   *
   * @param idGtNetSecurityImpHead the header ID
   * @param historyquoteStatus     the status value (see HistoryquoteImportStatus enum)
   * @return list of positions matching the criteria
   */
  List<GTNetSecurityImpPos> findByIdGtNetSecurityImpHeadAndHistoryquoteStatus(Integer idGtNetSecurityImpHead,
      byte historyquoteStatus);

  /**
   * Finds all positions that have a linked security but are pending historical data import. Used by the import task to
   * identify positions that need GTNet historical data loading after security creation.
   *
   * @return list of positions with security but pending historyquote import
   */
  @Query("SELECT p FROM GTNetSecurityImpPos p WHERE p.security IS NOT NULL AND p.historyquoteStatus = 0")
  List<GTNetSecurityImpPos> findPendingHistoricalImport();
}
