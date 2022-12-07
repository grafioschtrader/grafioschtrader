package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.GlobalConstants;
import grafioschtrader.algo.AlgoTopCreate;
import grafioschtrader.algo.AlgoTopCreate.AssetclassPercentage;
import grafioschtrader.algo.RuleStrategy;
import grafioschtrader.algo.simulate.SimulateRule;
import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.User;

public class AlgoTopJpaRepositoryImpl extends BaseRepositoryImpl<AlgoTop> implements AlgoTopJpaRepositoryCustom {

  @Autowired
  AlgoTopJpaRepository algoTopJpaRepository;
  @Autowired
  AlgoAssetclassJpaRepository algoAssetclassJpaRepository;
  @Autowired
  WatchlistJpaRepository watchlistJpaRepository;
  @Autowired
  HistoryquoteJpaRepository historyquoteJpaRepository;
  @Autowired
  SecurityJpaRepository securityJpaRepository;
  @Autowired
  TransactionJpaRepository transactionJpaRepository;

  @Autowired
  AssetclassJpaRepository assetclassJpaRepository;

  void simulateRuleOrStrategy(Integer idAlgoAssetclassSecurity, LocalDate startDate, LocalDate endDate) {

    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    AlgoTop algoTop = algoTopJpaRepository.getReferenceById(idAlgoAssetclassSecurity);
    if (user.getIdTenant().equals(algoTop.getIdTenant())) {
      if (algoTop.getRuleStrategy() == RuleStrategy.RS_RULE) {
        SimulateRule simulateRule = new SimulateRule(algoAssetclassJpaRepository, watchlistJpaRepository,
            historyquoteJpaRepository, securityJpaRepository, transactionJpaRepository);
        simulateRule.simulate(startDate, endDate, algoTop);
      }
    } else {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }

  }

  @Override
  public AlgoTop saveOnlyAttributes(AlgoTop algoTopOrAlgoTopCreate, AlgoTop existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    if (algoTopOrAlgoTopCreate instanceof AlgoTopCreate) {
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
      return algoTopJpaRepository.save(algoTopOrAlgoTopCreate);
    }
  }

  public int delEntityWithTenant(Integer idAlgoAssetclassSecurity, Integer idTenant) {
    return algoTopJpaRepository.deleteByIdAlgoAssetclassSecurityAndIdTenant(idAlgoAssetclassSecurity, idTenant);
  }

}
