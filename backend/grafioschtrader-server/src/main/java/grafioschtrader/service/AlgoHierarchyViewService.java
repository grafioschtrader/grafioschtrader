package grafioschtrader.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafioschtrader.dto.AlgoHierarchyDto;
import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.entities.AlgoSecurity;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.AlgoTopAssetSecurity;
import grafioschtrader.repository.AlgoAssetclassJpaRepository;
import grafioschtrader.repository.AlgoTopJpaRepository;

/** Loads the overview and decides all allocation and instrument warnings for the client. */
@Service
public class AlgoHierarchyViewService {
  private final AlgoTopJpaRepository tops;
  private final AlgoAssetclassJpaRepository assetclasses;
  private final AlgoTopReadinessService readinessService;

  @org.springframework.beans.factory.annotation.Autowired
  private AlgoMonitoringService monitoring;

  /** Uses tenant-scoped repositories so simulation viewers read only their home tenant's hierarchy. */
  public AlgoHierarchyViewService(AlgoTopJpaRepository tops, AlgoAssetclassJpaRepository assetclasses,
      AlgoTopReadinessService readinessService) {
    this.tops = tops;
    this.assetclasses = assetclasses;
    this.readinessService = readinessService;
  }

  /**
   * Returns the hierarchy, totals and warning fields in a single response after checking ownership. The red markers
   * are the findings of the readiness check, so that the tree and the refusal of a replay can never disagree.
   */
  @Transactional(readOnly = true)
  public AlgoHierarchyDto getHierarchy(Integer idTenant, Integer idAlgoTop) {
    AlgoTop top = tops.findByIdTenantAndIdAlgoAssetclassSecurity(idTenant, idAlgoTop);
    if (top == null) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    List<AlgoAssetclass> children = assetclasses.findByIdTenantAndIdAlgoAssetclassParent(idTenant, idAlgoTop);
    top.addedPercentage = (float) children.stream().mapToDouble(AlgoHierarchyViewService::percentage).sum();
    top.readiness = readinessService.check(top, children, AlgoTopReadinessService.currentLocale());
    Map<Integer, Set<String>> invalidFields = new HashMap<>();
    top.readiness.issues().stream().filter(issue -> issue.field() != null && issue.idNode() != null)
        .forEach(issue -> invalidFields.computeIfAbsent(issue.idNode(), _ -> new HashSet<>()).add(issue.field()));
    Map<Integer, Set<String>> warningFields = new HashMap<>();
    LocalDate today = LocalDate.now();
    children.forEach(assetclass -> markExpired(assetclass, today, warningFields));
    return new AlgoHierarchyDto(top, children, invalidFields, warningFields, monitoring.isAssigned(idTenant, idAlgoTop),
        monitoring.canEdit(idTenant, idAlgoTop));
  }

  /** An instrument whose trading already ended is highlighted in yellow; it does not make the strategy unusable. */
  private void markExpired(AlgoAssetclass assetclass, LocalDate today, Map<Integer, Set<String>> warningFields) {
    if (assetclass.getAlgoSecurityList() == null) {
      return;
    }
    for (AlgoSecurity member : assetclass.getAlgoSecurityList()) {
      if (member.getSecurity() != null && member.getSecurity().getActiveToDate() != null
          && member.getSecurity().getActiveToDate().isBefore(today)) {
        warningFields.computeIfAbsent(member.getId(), _ -> new HashSet<>()).add("security.activeToDate");
      }
    }
  }

  private static double percentage(AlgoTopAssetSecurity node) {
    return node.getPercentage() == null ? 0 : node.getPercentage();
  }
}
