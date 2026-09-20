package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.BaseRepositoryImpl;
import grafiosch.service.EntityLimitService;
import grafioschtrader.algo.AlgoTopCreate;
import grafioschtrader.algo.AlgoTopCreate.AssetclassPercentage;
import grafioschtrader.algo.AlgoTopCreateFromPortfolio;
import grafioschtrader.algo.AlgoTopCreateFromWatchlist;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.config.LimitKeyConfig;
import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.entities.AlgoSecurity;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.AlgoTopAssetSecurity;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Security;
import grafioschtrader.service.AlgoAlertScopeLifecycle;
import grafioschtrader.service.AlgoAllocationWeights;
import grafioschtrader.service.AlgoHierarchyWriteGuard;
import grafioschtrader.service.AlgoHistoricalValuationService;

public class AlgoTopJpaRepositoryImpl extends BaseRepositoryImpl<AlgoTop> implements AlgoTopJpaRepositoryCustom {

  @Autowired
  private AlgoHistoricalValuationService historicalValuation;
  @Autowired
  private SimulationSourceRepository source;
  @Autowired
  private WatchlistJpaRepository watchlists;
  @Autowired
  private EntityLimitService limits;
  @Autowired
  private AlgoTopJpaRepository algoTopJpaRepository;
  @Autowired
  private AlgoAssetclassJpaRepository algoAssetclassJpaRepository;
  @Autowired
  private AlgoSecurityJpaRepository algoSecurityJpaRepository;
  @Autowired
  AssetclassJpaRepository assetclassJpaRepository;

  @Autowired
  private AlgoAlertScopeLifecycle alertScopeLifecycle;

  @Autowired
  private AlgoHierarchyWriteGuard hierarchyWriteGuard;

  @Override
  @Transactional(rollbackFor = Exception.class)
  public AlgoTop saveOnlyAttributes(AlgoTop algoTopOrAlgoTopCreate, AlgoTop existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    hierarchyWriteGuard.assertHierarchyWritable();
    if (algoTopOrAlgoTopCreate instanceof AlgoTopCreateFromPortfolio atcfp) {
      return createFromPortfolioHoldings(atcfp);
    } else if (algoTopOrAlgoTopCreate instanceof AlgoTopCreateFromWatchlist atcfw) {
      return createFromWatchlist(atcfw);
    } else if (algoTopOrAlgoTopCreate instanceof AlgoTopCreate) {
      // When new
      var algoTop = new AlgoTop();
      BeanUtils.copyProperties(algoTopOrAlgoTopCreate, algoTop);
      algoTop = algoTopJpaRepository.save(algoTop);
      AlgoTopCreate algoTopCreate = (AlgoTopCreate) algoTopOrAlgoTopCreate;

      List<Integer> assetclassIds = algoTopCreate.assetclassPercentageList.stream()
          .map(assetclassPercentage -> assetclassPercentage.idAssetclass).collect(Collectors.toList());
      List<Assetclass> assetclassList = assetclassJpaRepository.findAllById(assetclassIds);

      int i = 0;
      for (AssetclassPercentage assetclassPercentage : algoTopCreate.assetclassPercentageList) {
        algoAssetclassJpaRepository.save(new AlgoAssetclass(algoTop.getIdTenant(),
            algoTop.getIdAlgoAssetclassSecurity(), assetclassList.get(i++), assetclassPercentage.percentage));
      }
      return algoTop;
    } else {
      // When changed
      var before = alertScopeLifecycle.snapshot(algoTopOrAlgoTopCreate.getIdTenant());
      AlgoTop saved = algoTopJpaRepository.save(algoTopOrAlgoTopCreate);
      alertScopeLifecycle.changed(saved.getIdTenant(), before);
      return saved;
    }
  }

  /**
   * Creates an AlgoTop hierarchy from the tenant's portfolio holdings at the given reference date. Calculates invested
   * value vs. cash to determine AlgoTop percentage, then groups securities by asset class to create AlgoAssetclass and
   * AlgoSecurity children with proportional weightings.
   */
  private AlgoTop createFromPortfolioHoldings(AlgoTopCreateFromPortfolio dto) {
    Integer idTenant = dto.getIdTenant();
    AlgoHistoricalValuationService.validateDate(dto.getReferenceDate());
    var firstDate = source.firstTransactionDate(idTenant);
    if (firstDate == null || dto.getReferenceDate().isBefore(firstDate))
      throw new DataViolationException("reference.date", "algo.reference.date.before.first.transaction", null);
    var snapshot = historicalValuation.value(idTenant, dto.getReferenceDate());
    snapshot.requireAvailable();
    double topPercentage = AlgoAllocationWeights.topPercentage(snapshot.equity(), snapshot.grossExposure());
    Map<Integer, Double> exposure = new TreeMap<>();
    Map<Integer, Security> securities = new TreeMap<>();
    snapshot.positions().forEach(p -> {
      securities.put(p.security().getId(), p.security());
      exposure.merge(p.security().getId(), p.grossExposure() * snapshot.fx().get(p.security().getCurrency()),
          Double::sum);
    });
    if (securities.values().stream().anyMatch(s -> s.getAssetClass() == null))
      throw AlgoHistoricalValuationService.invalid("algo.allocation.invalid", "");
    var groups = securities.values().stream()
        .collect(Collectors.groupingBy(s -> s.getAssetClass().getId(), TreeMap::new, Collectors.toList()));
    var user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (!limits.fitsWithinLimit(user, LimitKeyConfig.KEY_ALGO_TOP, null, 1)
        || !limits.fitsWithinLimit(user, LimitKeyConfig.KEY_ALGO_ASSETCLASS, null, groups.size())
        || !limits.fitsWithinLimit(user, LimitKeyConfig.KEY_ALGO_SECURITY, null, securities.size()))
      throw AlgoHistoricalValuationService.invalid("algo.allocation.limit", "");
    Map<Integer, Double> groupExposure = new TreeMap<>();
    groups.forEach((id, list) -> groupExposure.put(id, list.stream().mapToDouble(s -> exposure.get(s.getId())).sum()));
    var groupWeights = AlgoAllocationWeights.normalize(groupExposure);
    AlgoTop top = new AlgoTop();
    BeanUtils.copyProperties(dto, top);
    // Older clients may still submit a watchlist, but this hierarchy comes exclusively from dated holdings.
    top.setIdWatchlist(null);
    top.setPercentage((float) topPercentage);
    top = algoTopJpaRepository.save(top);
    persistBucketsAndMembers(idTenant, top, groups, groupWeights, group -> {
      Map<Integer, Double> values = new TreeMap<>();
      group.forEach(s -> values.put(s.getId(), exposure.get(s.getId())));
      return AlgoAllocationWeights.normalize(values);
    });
    return top;
  }

  /**
   * Creates a complete AlgoTop hierarchy from the instruments of the linked watchlist. A watchlist carries no amounts,
   * so nothing can be read off it the way the portfolio variant reads exposures: both generated levels are weighted
   * equally, the asset classes among themselves and the instruments within their asset class. The AlgoTop itself is
   * fully invested, and the reference date stays empty because it is the provenance of derived weights and equal
   * weights have none.
   */
  private AlgoTop createFromWatchlist(AlgoTopCreateFromWatchlist dto) {
    Integer idTenant = dto.getIdTenant();
    var watchlist = watchlists.findById(dto.getIdWatchlist()).orElse(null);
    if (watchlist == null || !idTenant.equals(watchlist.getIdTenant()))
      throw new SecurityException(grafiosch.BaseConstants.CLIENT_SECURITY_BREACH);
    // Not the lazy securitycurrencyList: this query keeps the currency pairs of the watchlist out of the hierarchy,
    // which can only hold instruments, and orders by instrument id so the generation is reproducible.
    List<Security> securities = watchlists.securitiesOfWatchlist(dto.getIdWatchlist());
    if (securities.isEmpty())
      throw AlgoHistoricalValuationService.invalid("algo.watchlist.empty", "");
    var withoutAssetclass = securities.stream().filter(s -> s.getAssetClass() == null).map(Security::getName).distinct()
        .sorted().toList();
    if (!withoutAssetclass.isEmpty())
      throw AlgoHistoricalValuationService.invalid("algo.allocation.invalid", String.join(", ", withoutAssetclass));
    var groups = securities.stream()
        .collect(Collectors.groupingBy(s -> s.getAssetClass().getId(), TreeMap::new, Collectors.toList()));
    var user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    if (!limits.fitsWithinLimit(user, LimitKeyConfig.KEY_ALGO_TOP, null, 1)
        || !limits.fitsWithinLimit(user, LimitKeyConfig.KEY_ALGO_ASSETCLASS, null, groups.size())
        || !limits.fitsWithinLimit(user, LimitKeyConfig.KEY_ALGO_SECURITY, null, securities.size()))
      throw AlgoHistoricalValuationService.invalid("algo.allocation.limit", "");
    AlgoTop top = new AlgoTop();
    BeanUtils.copyProperties(dto, top);
    top.setPercentage(100f);
    top = algoTopJpaRepository.save(top);
    persistBucketsAndMembers(idTenant, top, groups, AlgoAllocationWeights.normalize(equalValues(groups.keySet())),
        group -> AlgoAllocationWeights.normalize(equalValues(group.stream().map(Security::getId).toList())));
    return top;
  }

  /**
   * Saves the two generated levels below an already persisted AlgoTop: one bucket per asset class group, and one
   * instrument node below each bucket.
   *
   * @param idTenant      owner of every generated node
   * @param top           the saved AlgoTop the buckets hang below
   * @param groups        the instruments of the hierarchy, grouped by asset class id
   * @param groupWeights  parent-relative percentage of each bucket, keyed by asset class id
   * @param memberWeights parent-relative percentages of one bucket's instruments, keyed by instrument id
   */
  private void persistBucketsAndMembers(Integer idTenant, AlgoTop top, Map<Integer, List<Security>> groups,
      Map<Integer, Float> groupWeights, Function<List<Security>, Map<Integer, Float>> memberWeights) {
    for (var entry : groups.entrySet()) {
      var bucket = algoAssetclassJpaRepository.save(new AlgoAssetclass(idTenant, top.getId(),
          entry.getValue().getFirst().getAssetClass(), groupWeights.get(entry.getKey())));
      var weights = memberWeights.apply(entry.getValue());
      for (Security security : entry.getValue()) {
        AlgoSecurity child = new AlgoSecurity();
        child.setIdTenant(idTenant);
        child.setIdAlgoSecurityParent(bucket.getId());
        child.setSecurity(security);
        child.setPercentage(weights.get(security.getId()));
        algoSecurityJpaRepository.save(child);
      }
    }
  }

  /**
   * The input of {@link AlgoAllocationWeights#normalize(Map)} for siblings that carry no amount to weight them by, so
   * that they are split equally with the residual hundredths still assigned by largest remainder.
   *
   * @param ids the siblings to weight
   * @return the same weight for each of them
   */
  private static Map<Integer, Double> equalValues(Collection<Integer> ids) {
    Map<Integer, Double> values = new TreeMap<>();
    ids.forEach(id -> values.put(id, 1.0));
    return values;
  }

  @Override
  public void normalizeChildPercentages(Integer idAlgoAssetclassSecurity, Integer idTenant) {
    hierarchyWriteGuard.assertHierarchyWritable();
    // Try AlgoAssetclass children first (parent is AlgoTop)
    List<AlgoAssetclass> assetclassChildren = algoAssetclassJpaRepository
        .findByIdTenantAndIdAlgoAssetclassParent(idTenant, idAlgoAssetclassSecurity);
    if (!assetclassChildren.isEmpty()) {
      normalizeList(assetclassChildren);
      algoAssetclassJpaRepository.saveAll(assetclassChildren);
      return;
    }
    // Try AlgoSecurity children (parent is AlgoAssetclass)
    List<AlgoSecurity> securityChildren = algoSecurityJpaRepository
        .findByIdAlgoSecurityParentAndIdTenant(idAlgoAssetclassSecurity, idTenant);
    if (!securityChildren.isEmpty()) {
      normalizeList(securityChildren);
      algoSecurityJpaRepository.saveAll(securityChildren);
    }
  }

  @Override
  public void normalizeAllPercentages(Integer idAlgoAssetclassSecurity, Integer idTenant) {
    hierarchyWriteGuard.assertHierarchyWritable();
    List<AlgoAssetclass> assetclassChildren = algoAssetclassJpaRepository
        .findByIdTenantAndIdAlgoAssetclassParent(idTenant, idAlgoAssetclassSecurity);
    if (assetclassChildren.isEmpty()) {
      return;
    }
    normalizeList(assetclassChildren);
    algoAssetclassJpaRepository.saveAll(assetclassChildren);

    for (AlgoAssetclass ac : assetclassChildren) {
      List<AlgoSecurity> securityChildren = algoSecurityJpaRepository
          .findByIdAlgoSecurityParentAndIdTenant(ac.getIdAlgoAssetclassSecurity(), idTenant);
      if (!securityChildren.isEmpty()) {
        normalizeList(securityChildren);
        algoSecurityJpaRepository.saveAll(securityChildren);
      }
    }
  }

  private void normalizeList(List<? extends AlgoTopAssetSecurity> children) {
    double sum = children.stream().mapToDouble(c -> c.getPercentage() != null ? c.getPercentage() : 0.0).sum();
    if (sum == 0.0) {
      return;
    }
    double runningTotal = 0.0;
    for (int i = 0; i < children.size(); i++) {
      AlgoTopAssetSecurity child = children.get(i);
      float oldPct = child.getPercentage() != null ? child.getPercentage() : 0.0f;
      if (i < children.size() - 1) {
        float normalized = (float) DataBusinessHelper.roundPercentage(oldPct / sum * 100.0);
        child.setPercentage(normalized);
        runningTotal += normalized;
      } else {
        child.setPercentage((float) DataBusinessHelper.roundPercentage(100.0 - runningTotal));
      }
    }
  }

  @Autowired
  private AlgoTradingRepository tradingRepository;

  public int delEntityWithTenant(Integer idAlgoAssetclassSecurity, Integer idTenant) {
    hierarchyWriteGuard.assertHierarchyWritable();
    int deleted = algoTopJpaRepository.deleteByIdAlgoAssetclassSecurityAndIdTenant(idAlgoAssetclassSecurity, idTenant);
    tradingRepository.clearRemovedAssignments(idTenant);
    return deleted;
  }

}
