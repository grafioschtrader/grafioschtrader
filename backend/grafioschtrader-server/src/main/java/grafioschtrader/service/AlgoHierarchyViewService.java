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
import grafioschtrader.GlobalConstants;
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

  /** Uses tenant-scoped repositories so simulation viewers read only their home tenant's hierarchy. */
  public AlgoHierarchyViewService(AlgoTopJpaRepository tops, AlgoAssetclassJpaRepository assetclasses) {
    this.tops = tops;
    this.assetclasses = assetclasses;
  }

  /** Returns the hierarchy, totals and warning fields in a single response after checking ownership. */
  @Transactional(readOnly = true)
  public AlgoHierarchyDto getHierarchy(Integer idTenant, Integer idAlgoTop) {
    AlgoTop top = tops.findByIdTenantAndIdAlgoAssetclassSecurity(idTenant, idAlgoTop);
    if (top == null) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    List<AlgoAssetclass> children = assetclasses.findByIdTenantAndIdAlgoAssetclassParent(idTenant, idAlgoTop);
    Map<Integer, Set<String>> invalidFields = new HashMap<>();
    Map<Integer, Set<String>> warningFields = new HashMap<>();
    top.addedPercentage = (float) children.stream().mapToDouble(AlgoHierarchyViewService::percentage).sum();
    checkTotal(top, top.addedPercentage, invalidFields);
    LocalDate openingDate = top.getReferenceDate() == null ? null : top.getReferenceDate().plusDays(1);
    LocalDate today = LocalDate.now();
    children.forEach(assetclass -> checkAssetclass(assetclass, openingDate, today, invalidFields, warningFields));
    return new AlgoHierarchyDto(top, children, invalidFields, warningFields);
  }

  private void checkAssetclass(AlgoAssetclass assetclass, LocalDate openingDate, LocalDate today,
      Map<Integer, Set<String>> invalidFields, Map<Integer, Set<String>> warningFields) {
    checkTotal(assetclass, assetclass.getAddedPercentage(), invalidFields);
    List<AlgoSecurity> securities = assetclass.getAlgoSecurityList() == null ? List.of()
        : assetclass.getAlgoSecurityList();
    boolean hasValidInstrument = false;
    for (AlgoSecurity member : securities) {
      if (member.getSecurity() != null && member.getSecurity().getActiveToDate() != null
          && member.getSecurity().getActiveToDate().isBefore(today)) {
        warningFields.computeIfAbsent(member.getId(), _ -> new HashSet<>()).add("security.activeToDate");
      }
      boolean eligible = AlgoSecurityEligibility.isEligibleInstrument(member.getSecurity(), openingDate);
      if (!eligible) {
        markInvalid(member, "name", invalidFields);
      }
      hasValidInstrument |= eligible && member.isActivatable() && percentage(member) > 0;
    }
    if (percentage(assetclass) > 0 && !hasValidInstrument) {
      markInvalid(assetclass, "name", invalidFields);
    }
  }

  private void checkTotal(AlgoTopAssetSecurity node, double total, Map<Integer, Set<String>> invalidFields) {
    if (!Double.isFinite(total)
        || Math.abs(total - GlobalConstants.EXPECTED_ADDED_PERCENTAGE) >= GlobalConstants.ADDED_PERCENTAGE_TOLERANCE) {
      markInvalid(node, "addedPercentage", invalidFields);
    }
  }

  private void markInvalid(AlgoTopAssetSecurity node, String field, Map<Integer, Set<String>> invalidFields) {
    invalidFields.computeIfAbsent(node.getId(), _ -> new HashSet<>()).add(field);
  }

  private static double percentage(AlgoTopAssetSecurity node) {
    return node.getPercentage() == null ? 0 : node.getPercentage();
  }
}
