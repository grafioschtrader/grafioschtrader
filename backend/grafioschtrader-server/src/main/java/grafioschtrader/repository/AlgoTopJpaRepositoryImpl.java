package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;

import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.BaseRepositoryImpl;
import grafioschtrader.algo.AlgoTopCreate;
import grafioschtrader.algo.AlgoTopCreate.AssetclassPercentage;
import grafioschtrader.algo.AlgoTopCreateFromPortfolio;
import grafioschtrader.dto.ISecuritycurrencyIdDateClose;
import grafioschtrader.entities.AlgoAssetclass;
import grafioschtrader.entities.AlgoSecurity;
import grafioschtrader.entities.AlgoTop;
import grafioschtrader.entities.AlgoTopAssetSecurity;
import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.HoldCashaccountBalance;
import grafioschtrader.entities.HoldSecurityaccountSecurity;
import grafioschtrader.entities.Security;

public class AlgoTopJpaRepositoryImpl extends BaseRepositoryImpl<AlgoTop> implements AlgoTopJpaRepositoryCustom {

  @Autowired
  private AlgoTopJpaRepository algoTopJpaRepository;
  @Autowired
  private AlgoAssetclassJpaRepository algoAssetclassJpaRepository;
  @Autowired
  private AlgoSecurityJpaRepository algoSecurityJpaRepository;
  @Autowired
  AssetclassJpaRepository assetclassJpaRepository;
  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdSecurityRepo;
  @Autowired
  private HoldCashaccountBalanceJpaRepository holdCashRepo;
  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;
  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Override
  public AlgoTop saveOnlyAttributes(AlgoTop algoTopOrAlgoTopCreate, AlgoTop existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    if (algoTopOrAlgoTopCreate instanceof AlgoTopCreateFromPortfolio atcfp) {
      return createFromPortfolioHoldings(atcfp);
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
      return algoTopJpaRepository.save(algoTopOrAlgoTopCreate);
    }
  }

  /**
   * Creates an AlgoTop hierarchy from the tenant's portfolio holdings at the given reference date.
   * Calculates invested value vs. cash to determine AlgoTop percentage, then groups securities by
   * asset class to create AlgoAssetclass and AlgoSecurity children with proportional weightings.
   */
  private AlgoTop createFromPortfolioHoldings(AlgoTopCreateFromPortfolio dto) {
    Integer idTenant = dto.getIdTenant();
    LocalDate refDate = dto.getReferenceDate();

    // Validate reference date is after first transaction
    LocalDate firstTradeDate = holdSecurityRepo.findByIdTenantMinFromHoldDate(idTenant);
    if (firstTradeDate == null || refDate.isBefore(firstTradeDate)) {
      throw new DataViolationException("reference.date", "algo.reference.date.before.first.transaction", null);
    }

    // 1. Get open security positions at reference date
    List<HoldSecurityaccountSecurity> positions = holdSecurityRepo.findOpenPositionsAtDate(idTenant, refDate);
    if (positions.isEmpty()) {
      throw new DataViolationException("reference.date", "algo.no.positions.at.date",
          new Object[] { refDate.toString() });
    }

    // 2. Aggregate holdings by idSecuritycurrency (sum across security accounts)
    Map<Integer, Double> holdingsBySecId = new HashMap<>();
    Map<Integer, Integer> currencyPairBySecId = new HashMap<>();
    for (HoldSecurityaccountSecurity pos : positions) {
      Integer secId = pos.getHssk().getIdSecuritycurrency();
      holdingsBySecId.merge(secId, pos.getHodlings(), Double::sum);
      // Keep currency pair for tenant conversion (last one wins, they should be the same per security)
      currencyPairBySecId.put(secId, pos.getIdCurrencypairTenant());
    }

    // 3. Get closing prices for all securities at reference date
    List<Integer> allIds = new ArrayList<>(holdingsBySecId.keySet());
    // Also collect currency pair ids for FX conversion
    List<Integer> fxPairIds = currencyPairBySecId.values().stream()
        .filter(id -> id != null).distinct().collect(Collectors.toList());
    List<Integer> allPriceIds = new ArrayList<>(allIds);
    allPriceIds.addAll(fxPairIds);

    Date sqlDate = Date.valueOf(refDate);
    List<ISecuritycurrencyIdDateClose> priceResults = historyquoteJpaRepository.getIdDateCloseByIdsAndDate(
        allPriceIds, sqlDate);

    Map<Integer, Double> priceById = new HashMap<>();
    for (ISecuritycurrencyIdDateClose pc : priceResults) {
      priceById.put(pc.getIdSecuritycurrency(), pc.getClose());
    }

    // 4. Calculate total invested value in tenant currency
    double totalInvested = 0.0;
    Map<Integer, Double> valueBySecId = new HashMap<>();
    for (Map.Entry<Integer, Double> entry : holdingsBySecId.entrySet()) {
      Integer secId = entry.getKey();
      double holdings = entry.getValue();
      Double closePrice = priceById.get(secId);
      if (closePrice == null) {
        continue; // Skip securities without price data
      }
      double posValue = Math.abs(holdings) * closePrice;

      // Currency conversion to tenant currency
      Integer fxPairId = currencyPairBySecId.get(secId);
      if (fxPairId != null) {
        Double fxRate = priceById.get(fxPairId);
        if (fxRate != null && fxRate != 0.0) {
          posValue *= fxRate;
        }
      }
      valueBySecId.put(secId, posValue);
      totalInvested += posValue;
    }

    // 5. Calculate total cash in tenant currency
    List<HoldCashaccountBalance> cashBalances = holdCashRepo.findCashBalancesAtDate(idTenant, refDate);
    double totalCash = 0.0;
    for (HoldCashaccountBalance cb : cashBalances) {
      double cashValue = cb.getBalance();
      Integer fxPairId = cb.getIdCurrencypairTenant();
      if (fxPairId != null) {
        Double fxRate = priceById.get(fxPairId);
        if (fxRate != null && fxRate != 0.0) {
          cashValue *= fxRate;
        }
      }
      totalCash += cashValue;
    }

    double totalPortfolio = totalInvested + totalCash;
    if (totalPortfolio <= 0.0) {
      throw new DataViolationException("reference.date", "algo.no.positions.at.date",
          new Object[] { refDate.toString() });
    }

    // 6. Save AlgoTop
    var algoTop = new AlgoTop();
    BeanUtils.copyProperties(dto, algoTop);
    algoTop.setPercentage((float) (totalInvested / totalPortfolio * 100.0));
    algoTop.setReferenceDate(refDate);
    algoTop = algoTopJpaRepository.save(algoTop);

    // 7. Load Security entities and group by asset class
    List<Security> securities = securityJpaRepository.findAllById(new ArrayList<>(valueBySecId.keySet()));
    Map<Integer, List<Security>> secByAssetclass = securities.stream()
        .filter(s -> s.getAssetClass() != null)
        .collect(Collectors.groupingBy(s -> s.getAssetClass().getIdAssetClass()));

    // 8. For each asset class group, save AlgoAssetclass and AlgoSecurity children
    for (Map.Entry<Integer, List<Security>> acEntry : secByAssetclass.entrySet()) {
      List<Security> groupSecurities = acEntry.getValue();
      double groupValue = groupSecurities.stream()
          .mapToDouble(s -> valueBySecId.getOrDefault(s.getIdSecuritycurrency(), 0.0))
          .sum();

      if (groupValue <= 0.0) {
        continue;
      }

      // Use the asset class from the first security in the group
      Assetclass assetclass = groupSecurities.get(0).getAssetClass();
      float acPercentage = (float) (groupValue / totalInvested * 100.0);

      AlgoAssetclass algoAc = algoAssetclassJpaRepository.save(new AlgoAssetclass(
          algoTop.getIdTenant(), algoTop.getIdAlgoAssetclassSecurity(), assetclass, acPercentage));

      // Save individual AlgoSecurity entries
      for (Security sec : groupSecurities) {
        Double secValue = valueBySecId.get(sec.getIdSecuritycurrency());
        if (secValue == null || secValue <= 0.0) {
          continue;
        }
        float secPercentage = (float) (secValue / groupValue * 100.0);
        AlgoSecurity algoSec = new AlgoSecurity();
        algoSec.setIdTenant(algoTop.getIdTenant());
        algoSec.setIdAlgoSecurityParent(algoAc.getIdAlgoAssetclassSecurity());
        algoSec.setSecurity(sec);
        algoSec.setPercentage(secPercentage);
        algoSecurityJpaRepository.save(algoSec);
      }
    }

    return algoTop;
  }

  @Override
  public void normalizeChildPercentages(Integer idAlgoAssetclassSecurity, Integer idTenant) {
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
    double sum = children.stream()
        .mapToDouble(c -> c.getPercentage() != null ? c.getPercentage() : 0.0)
        .sum();
    if (sum == 0.0) {
      return;
    }
    double runningTotal = 0.0;
    for (int i = 0; i < children.size(); i++) {
      AlgoTopAssetSecurity child = children.get(i);
      float oldPct = child.getPercentage() != null ? child.getPercentage() : 0.0f;
      if (i < children.size() - 1) {
        float normalized = (float) (Math.round(oldPct / sum * 10000.0) / 100.0);
        child.setPercentage(normalized);
        runningTotal += normalized;
      } else {
        child.setPercentage((float) (Math.round((100.0 - runningTotal) * 100.0) / 100.0));
      }
    }
  }

  public int delEntityWithTenant(Integer idAlgoAssetclassSecurity, Integer idTenant) {
    return algoTopJpaRepository.deleteByIdAlgoAssetclassSecurityAndIdTenant(idAlgoAssetclassSecurity, idTenant);
  }

}
