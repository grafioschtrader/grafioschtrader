package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.List;

import grafioschtrader.entities.GTNet;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.gtnet.GTNetExchangeLogPeriodType;
import grafioschtrader.gtnet.model.GTNetExchangeLogTreeDTO;

/**
 * Custom repository methods for GTNet exchange log operations.
 */
public interface GTNetExchangeLogJpaRepositoryCustom {

  /**
   * Logs an exchange operation. Creates an INDIVIDUAL period log entry.
   *
   * @param gtNet the remote GTNet domain
   * @param entityKind the type of data exchanged
   * @param asSupplier true if logging as supplier, false if as consumer
   * @param entitiesSent number of entities sent or received
   * @param entitiesUpdated number of entities successfully updated
   * @param entitiesInResponse number of entities in the response
   */
  void logExchange(GTNet gtNet, GTNetExchangeKindType entityKind, boolean asSupplier,
      int entitiesSent, int entitiesUpdated, int entitiesInResponse);

  /**
   * Aggregates log entries from one period type to the next.
   * Groups entries by (idGtNet, entityKind, logAsSupplier) and sums statistics.
   *
   * @param fromPeriod the source period type to aggregate from
   * @param toPeriod the target period type to aggregate to
   * @param beforeDate only aggregate entries with periodStart before this date
   * @return number of source entries that were aggregated
   */
  int aggregateLogs(GTNetExchangeLogPeriodType fromPeriod, GTNetExchangeLogPeriodType toPeriod, LocalDate beforeDate);

  /**
   * Gets the exchange log tree structure for a specific GTNet and entity kind.
   * Returns hierarchical data with supplier and consumer totals.
   *
   * @param idGtNet the GTNet identifier
   * @param entityKind the entity kind to filter by
   * @return tree structure DTO for frontend display
   */
  GTNetExchangeLogTreeDTO getExchangeLogTree(Integer idGtNet, GTNetExchangeKindType entityKind);

  /**
   * Gets all log entries for all GTNets that support communication for the specified entity kind.
   * Only returns GTNets where the corresponding GTNetEntity has exchange enabled.
   *
   * @param entityKind the entity kind to filter by
   * @return list of tree DTOs, one per GTNet with communication enabled for this entity kind
   */
  List<GTNetExchangeLogTreeDTO> getAllExchangeLogTrees(GTNetExchangeKindType entityKind);
}
