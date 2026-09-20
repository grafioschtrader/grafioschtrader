package grafioschtrader.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import grafiosch.BaseConstants;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.entities.AlgoSecurity;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.AlgoAssetclassJpaRepository;
import grafioschtrader.repository.AlgoTopJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;

/** Excludes margin/leveraged instruments and securities that ended before a strategy opens from new assignments. */
@Service
public class AlgoSecurityEligibility {

  private final AlgoAssetclassJpaRepository assetclasses;
  private final AlgoTopJpaRepository tops;
  private final SecurityJpaRepository securities;

  /** Creates the eligibility service with repositories for the owning hierarchy and authoritative security dates. */
  public AlgoSecurityEligibility(AlgoAssetclassJpaRepository assetclasses, AlgoTopJpaRepository tops,
      SecurityJpaRepository securities) {
    this.assetclasses = assetclasses;
    this.tops = tops;
    this.securities = securities;
  }

  /** Filters either candidate source while retaining its existing order and visibility restrictions. */
  public List<Security> filterCandidates(Integer idTenant, Integer idAssetclass, List<Security> candidates) {
    LocalDate openingDate = openingDate(idTenant, idAssetclass);
    return candidates.stream().filter(security -> isEligibleInstrument(security, openingDate)).toList();
  }

  /** Validates additions, replacements and moves, but permits edits to an unchanged persisted assignment. */
  public void validateAssignment(AlgoSecurity assignment, AlgoSecurity existing) {
    if (assignment.getIdAlgoSecurityParent() == null || unchangedAssignment(assignment, existing)) {
      return;
    }
    LocalDate openingDate = openingDate(assignment.getIdTenant(), assignment.getIdAlgoSecurityParent());
    Security security = assignment.getSecurity() == null ? null
        : securities.findByIdTenantPrivateIsNullOrIdTenantPrivateAndIdSecuritycurrency(
            assignment.getSecurity().getIdSecuritycurrency(), assignment.getIdTenant());
    if (security == null) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    if (security.isSimulationTradingExcluded()) {
      throw new DataViolationException("id.securitycurrency", "algo.security.instrument.excluded", null);
    }
    if (!isEligible(security, openingDate)) {
      throw new DataViolationException("id.securitycurrency", "algo.security.ended.before.opening", null);
    }
    assignment.setSecurity(security);
  }

  private boolean unchangedAssignment(AlgoSecurity assignment, AlgoSecurity existing) {
    return existing != null && existing.getIdAlgoAssetclassSecurity() != null
        && Objects.equals(assignment.getIdAlgoAssetclassSecurity(), existing.getIdAlgoAssetclassSecurity())
        && Objects.equals(assignment.getIdAlgoSecurityParent(), existing.getIdAlgoSecurityParent())
        && assignment.getSecurity() != null && existing.getSecurity() != null && Objects
            .equals(assignment.getSecurity().getIdSecuritycurrency(), existing.getSecurity().getIdSecuritycurrency());
  }

  private LocalDate openingDate(Integer idTenant, Integer idAssetclass) {
    AlgoAssetclass assetclass = assetclasses.findById(idAssetclass)
        .filter(parent -> Objects.equals(idTenant, parent.getIdTenant()))
        .orElseThrow(() -> new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH));
    AlgoTop top = tops.findByIdTenantAndIdAlgoAssetclassSecurity(idTenant, assetclass.getIdAlgoAssetclassParent());
    if (top == null) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    return top.getReferenceDate() == null ? null : top.getReferenceDate().plusDays(1);
  }

  /** Shared eligibility for candidates and hierarchy warnings; later-starting instruments remain eligible. */
  public static boolean isEligibleInstrument(Security security, LocalDate openingDate) {
    return security != null && !security.isSimulationTradingExcluded() && isEligible(security, openingDate);
  }

  private static boolean isEligible(Security security, LocalDate openingDate) {
    return openingDate == null || security.getActiveToDate() == null
        || !security.getActiveToDate().isBefore(openingDate);
  }
}
