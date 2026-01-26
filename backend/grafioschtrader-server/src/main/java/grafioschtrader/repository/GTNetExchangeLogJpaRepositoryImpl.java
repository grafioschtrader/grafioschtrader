package grafioschtrader.repository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetConfigEntity;
import grafiosch.entities.GTNetEntity;
import grafiosch.gtnet.GTNetExchangeLogPeriodType;
import grafiosch.gtnet.SupplierConsumerLogTypes;
import grafiosch.gtnet.model.GTNetExchangeLogNodeDTO;
import grafiosch.gtnet.model.GTNetExchangeLogTreeDTO;
import grafiosch.repository.GTNetJpaRepository;
import grafioschtrader.entities.GTNetExchangeLog;
import grafioschtrader.gtnet.GTNetExchangeKindType;
import grafioschtrader.service.GlobalparametersService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Implementation of custom GTNet exchange log repository methods.
 */
public class GTNetExchangeLogJpaRepositoryImpl implements GTNetExchangeLogJpaRepositoryCustom {

  @PersistenceContext
  private EntityManager entityManager;

  @Autowired
  private GTNetExchangeLogJpaRepository gtNetExchangeLogJpaRepository;

  @Autowired
  private GTNetJpaRepository gtNetJpaRepository;

  @Autowired
  private GlobalparametersService globalparametersService;

  @Override
  @Transactional
  public void logExchange(GTNet gtNet, GTNetExchangeKindType entityKind, boolean asSupplier,
      int entitiesSent, int entitiesUpdated, int entitiesInResponse) {
    // Check if global logging is enabled
    if (!globalparametersService.isGTNetLogEnabled()) {
      return;
    }

    // Check if logging is enabled for this entity type and role
    Optional<GTNetEntity> entityOpt = gtNet.getEntityByKind(entityKind.getValue());
    if (entityOpt.isEmpty()) {
      return;
    }
    GTNetConfigEntity config = entityOpt.get().getGtNetConfigEntity();
    if (config == null) {
      return;
    }
    SupplierConsumerLogTypes logLevel = asSupplier ? config.getSupplierLog() : config.getConsumerLog();
    if (logLevel == null || !logLevel.isLoggingEnabled()) {
      return;
    }

    GTNetExchangeLog log = new GTNetExchangeLog(
        gtNet.getIdGtNet(), entityKind, asSupplier,
        entitiesSent, entitiesUpdated, entitiesInResponse);
    entityManager.persist(log);
  }

  @Override
  @Transactional
  public int aggregateLogs(GTNetExchangeLogPeriodType fromPeriod, GTNetExchangeLogPeriodType toPeriod,
      LocalDate beforeDate) {
    List<GTNetExchangeLog> sourceLogs = gtNetExchangeLogJpaRepository
        .findByPeriodTypeAndPeriodStartBefore(fromPeriod.getValue(), beforeDate);

    if (sourceLogs.isEmpty()) {
      return 0;
    }

    // Group by (idGtNet, entityKind, logAsSupplier, targetPeriodStart)
    Map<String, List<GTNetExchangeLog>> grouped = sourceLogs.stream()
        .collect(Collectors.groupingBy(log -> {
          LocalDate targetPeriodStart = calculatePeriodStart(log.getPeriodStart(), toPeriod);
          return log.getIdGtNet() + "-" + log.getEntityKindValue() + "-" + log.isLogAsSupplier() + "-" + targetPeriodStart;
        }));

    List<Integer> idsToDelete = new ArrayList<>();

    for (Map.Entry<String, List<GTNetExchangeLog>> entry : grouped.entrySet()) {
      List<GTNetExchangeLog> logs = entry.getValue();
      GTNetExchangeLog first = logs.get(0);

      // Create aggregated log entry
      GTNetExchangeLog aggregated = new GTNetExchangeLog();
      aggregated.setIdGtNet(first.getIdGtNet());
      aggregated.setEntityKind(first.getEntityKind());
      aggregated.setLogAsSupplier(first.isLogAsSupplier());
      aggregated.setPeriodType(toPeriod);
      aggregated.setPeriodStart(calculatePeriodStart(first.getPeriodStart(), toPeriod));
      aggregated.setTimestamp(first.getTimestamp());

      // Sum all statistics
      for (GTNetExchangeLog log : logs) {
        aggregated.aggregate(log);
        idsToDelete.add(log.getIdGtNetExchangeLog());
      }

      entityManager.persist(aggregated);
    }

    // Delete source logs
    if (!idsToDelete.isEmpty()) {
      gtNetExchangeLogJpaRepository.deleteByIds(idsToDelete);
    }

    return sourceLogs.size();
  }

  @Override
  public GTNetExchangeLogTreeDTO getExchangeLogTree(Integer idGtNet, GTNetExchangeKindType entityKind) {
    GTNet gtNet = gtNetJpaRepository.findById(idGtNet).orElse(null);
    if (gtNet == null) {
      return null;
    }

    GTNetExchangeLogTreeDTO tree = new GTNetExchangeLogTreeDTO(idGtNet, gtNet.getDomainRemoteName());

    // Get logs for this GTNet filtered by entityKind
    List<GTNetExchangeLog> allLogs = gtNetExchangeLogJpaRepository
        .findByIdGtNetAndEntityKindOrderByTimestampDesc(idGtNet, entityKind.getValue());

    // Build supplier tree
    List<GTNetExchangeLog> supplierLogs = allLogs.stream()
        .filter(GTNetExchangeLog::isLogAsSupplier)
        .collect(Collectors.toList());
    buildTree(tree.supplierTotal, supplierLogs);

    // Build consumer tree
    List<GTNetExchangeLog> consumerLogs = allLogs.stream()
        .filter(log -> !log.isLogAsSupplier())
        .collect(Collectors.toList());
    buildTree(tree.consumerTotal, consumerLogs);

    return tree;
  }

  @Override
  public List<GTNetExchangeLogTreeDTO> getAllExchangeLogTrees(GTNetExchangeKindType entityKind) {
    List<GTNetExchangeLogTreeDTO> result = new ArrayList<>();

    // Get all GTNets that have the specified entity kind enabled for exchange
    List<GTNet> gtNets = gtNetJpaRepository.findAll();
    for (GTNet gtNet : gtNets) {
      // Check if this GTNet has communication enabled for this entity kind
      Optional<GTNetEntity> entityOpt = gtNet.getEntityByKind(entityKind.getValue());
      if (entityOpt.isEmpty()) {
        continue;
      }
      GTNetEntity entity = entityOpt.get();
      // Check if exchange is enabled (not ES_NO_EXCHANGE) via the config entity
      if (entity.getGtNetConfigEntity() == null) {
        continue;
      }

      GTNetExchangeLogTreeDTO tree = getExchangeLogTree(gtNet.getIdGtNet(), entityKind);
      if (tree != null) {
        result.add(tree);
      }
    }

    return result;
  }

  /**
   * Builds a hierarchical tree from log entries.
   * Groups by period type, with shortest periods (most recent) at top.
   */
  private void buildTree(GTNetExchangeLogNodeDTO root, List<GTNetExchangeLog> logs) {
    if (logs.isEmpty()) {
      return;
    }

    // Group logs by period type
    Map<GTNetExchangeLogPeriodType, List<GTNetExchangeLog>> byPeriodType = logs.stream()
        .collect(Collectors.groupingBy(GTNetExchangeLog::getPeriodType));

    // Process from shortest to longest period
    for (GTNetExchangeLogPeriodType periodType : GTNetExchangeLogPeriodType.values()) {
      List<GTNetExchangeLog> periodLogs = byPeriodType.get(periodType);
      if (periodLogs == null || periodLogs.isEmpty()) {
        continue;
      }

      // Group by period start within each period type
      Map<LocalDate, List<GTNetExchangeLog>> byPeriodStart = periodLogs.stream()
          .collect(Collectors.groupingBy(GTNetExchangeLog::getPeriodStart));

      for (Map.Entry<LocalDate, List<GTNetExchangeLog>> entry : byPeriodStart.entrySet()) {
        LocalDate periodStart = entry.getKey();
        List<GTNetExchangeLog> periodStartLogs = entry.getValue();

        GTNetExchangeLogNodeDTO node = new GTNetExchangeLogNodeDTO(
            formatPeriodLabel(periodStart, periodType),
            periodType,
            periodStart);

        // Sum statistics for this period
        for (GTNetExchangeLog log : periodStartLogs) {
          node.entitiesSent += log.getEntitiesSent();
          node.entitiesUpdated += log.getEntitiesUpdated();
          node.entitiesInResponse += log.getEntitiesInResponse();
          node.requestCount += log.getRequestCount();
        }

        root.children.add(node);
        root.addStats(node);
      }
    }

    // Sort children: most recent first, then by period type (shortest first)
    root.children.sort((a, b) -> {
      if (a.periodStart != null && b.periodStart != null) {
        int dateCompare = b.periodStart.compareTo(a.periodStart);
        if (dateCompare != 0) return dateCompare;
      }
      if (a.periodType != null && b.periodType != null) {
        return a.periodType.getValue() - b.periodType.getValue();
      }
      return 0;
    });
  }

  /**
   * Calculates the period start date for aggregation.
   */
  private LocalDate calculatePeriodStart(LocalDate date, GTNetExchangeLogPeriodType periodType) {
    return switch (periodType) {
      case INDIVIDUAL, DAILY -> date;
      case WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
      case MONTHLY -> date.withDayOfMonth(1);
      case YEARLY -> date.withDayOfYear(1);
    };
  }

  /**
   * Formats a period label for display.
   */
  private String formatPeriodLabel(LocalDate date, GTNetExchangeLogPeriodType periodType) {
    if (date == null) {
      return "Total";
    }
    return switch (periodType) {
      case INDIVIDUAL -> date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
      case DAILY -> date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
      case WEEKLY -> "Week " + date.get(WeekFields.of(Locale.getDefault()).weekOfYear()) + " " + date.getYear();
      case MONTHLY -> date.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
      case YEARLY -> String.valueOf(date.getYear());
    };
  }
}
