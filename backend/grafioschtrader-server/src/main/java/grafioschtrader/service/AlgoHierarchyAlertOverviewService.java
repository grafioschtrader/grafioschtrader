package grafioschtrader.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.types.Language;
import grafioschtrader.dto.AlgoTopAlertGroupDto;
import grafioschtrader.dto.AlgoTopAlertGroupDto.NodeAlerts;
import grafioschtrader.dto.AlgoTopAlertGroupDto.NodeLevel;
import grafioschtrader.dto.AlgoTopAlertGroupDto.StrategyAlert;
import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.entities.AlgoSecurity;
import grafioschtrader.entities.AlgoStrategy;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.repository.AlgoTopJpaRepository;
import grafioschtrader.service.AlgoAlertScopeResolver.Hierarchy;

/**
 * Lists the alerts of every AlgoTop hierarchy of a tenant, so that the user can see which of them the live evaluation
 * actually considers.
 *
 * <p>
 * An alert may be configured on any hierarchy, but only the one referenced by the main tenant's monitoring assignment
 * is evaluated live, and a historical replay evaluates no alerts at all. An alert on any other hierarchy is therefore
 * kept but dormant until that hierarchy is assigned. The overview reports this per hierarchy and per alert, with the
 * same activation rule as {@link AlgoAlertScopeResolver}, rather than leaving the client to derive it.
 * </p>
 */
@Service
public class AlgoHierarchyAlertOverviewService {

  private final AlgoTopJpaRepository algoTopJpaRepository;
  private final AlgoAlertScopeResolver scopeResolver;
  private final AlgoMonitoringService monitoring;

  public AlgoHierarchyAlertOverviewService(AlgoTopJpaRepository algoTopJpaRepository,
      AlgoAlertScopeResolver scopeResolver, AlgoMonitoringService monitoring) {
    this.algoTopJpaRepository = algoTopJpaRepository;
    this.scopeResolver = scopeResolver;
    this.monitoring = monitoring;
  }

  /**
   * Returns the alerts of every hierarchy of the tenant. Hierarchies without any alert are left out, and so are nodes
   * without one.
   *
   * @param idTenant the tenant whose hierarchies are read
   * @param language the language the asset class buckets are named in
   * @return one group per hierarchy carrying alerts, ordered by hierarchy name
   */
  @Transactional(readOnly = true)
  public List<AlgoTopAlertGroupDto> overview(Integer idTenant, Language language) {
    List<AlgoTopAlertGroupDto> groups = new ArrayList<>();
    for (AlgoTop top : algoTopJpaRepository.findByIdTenantOrderByName(idTenant)) {
      boolean assigned = monitoring.isAssigned(idTenant, top.getId());
      List<NodeAlerts> nodes = nodesOf(top, assigned, language);
      if (!nodes.isEmpty()) {
        groups.add(new AlgoTopAlertGroupDto(top.getId(), top.getName(), assigned, nodes));
      }
    }
    return groups;
  }

  /** Walks the hierarchy in tree order: the top itself, then each bucket followed by its instruments. */
  private List<NodeAlerts> nodesOf(AlgoTop top, boolean assigned, Language language) {
    Hierarchy hierarchy = scopeResolver.readHierarchy(top);
    List<NodeAlerts> nodes = new ArrayList<>();
    addNode(nodes, hierarchy, top.getId(), top.getName(), NodeLevel.TOP, assigned);
    for (AlgoAssetclass bucket : hierarchy.buckets()) {
      addNode(nodes, hierarchy, bucket.getId(), bucketName(bucket, language), NodeLevel.ASSETCLASS, assigned);
      for (AlgoSecurity member : hierarchy.membersByBucket().get(bucket.getId())) {
        if (member.getSecurity() != null) {
          addNode(nodes, hierarchy, member.getId(),
              member.getSecurity().getName() + ", " + member.getSecurity().getCurrency(), NodeLevel.SECURITY, assigned);
        }
      }
    }
    return nodes;
  }

  private void addNode(List<NodeAlerts> nodes, Hierarchy hierarchy, Integer idNode, String nodeName,
      NodeLevel nodeLevel, boolean assigned) {
    List<StrategyAlert> alerts = hierarchy.strategiesByNode().getOrDefault(idNode, List.of()).stream()
        .filter(strategy -> AlgoAlertEvaluationCoordinator.isAlertType(strategy.getAlgoStrategyImplementations()))
        .map(strategy -> toAlert(strategy, assigned)).toList();
    if (!alerts.isEmpty()) {
      nodes.add(new NodeAlerts(idNode, nodeName, nodeLevel, alerts));
    }
  }

  /** Same activation rule as the live scopes of {@link AlgoAlertScopeResolver}. */
  private static StrategyAlert toAlert(AlgoStrategy strategy, boolean assigned) {
    return new StrategyAlert(strategy.getIdAlgoRuleStrategy(), strategy.getAlgoStrategyImplementations().name(),
        strategy.isAlertEnabled(), assigned && strategy.isActivatable() && strategy.isAlertEnabled());
  }

  /** A custom category carries its own name, otherwise the bucket is named by its asset class subcategory. */
  private static String bucketName(AlgoAssetclass bucket, Language language) {
    if (bucket.getName() != null) {
      return bucket.getName();
    }
    return bucket.getAssetclass() == null ? null : bucket.getAssetclass().getSubCategoryByLanguage(language);
  }
}
