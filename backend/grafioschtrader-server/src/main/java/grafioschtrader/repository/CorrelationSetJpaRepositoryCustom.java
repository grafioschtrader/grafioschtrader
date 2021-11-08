package grafioschtrader.repository;

import java.util.List;

import grafioschtrader.dto.CorrelationLimits;
import grafioschtrader.dto.CorrelationResult;
import grafioschtrader.dto.CorrelationRollingResult;
import grafioschtrader.dto.TenantLimit;
import grafioschtrader.entities.CorrelationSet;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyLists;
import grafioschtrader.search.SecuritycurrencySearch;

public interface CorrelationSetJpaRepositoryCustom extends BaseRepositoryCustom<CorrelationSet> {

  int delEntityWithTenant(Integer id, Integer idTenant);

  CorrelationSet addSecuritycurrenciesToCorrelationSet(Integer idCorrelationSet,
      SecuritycurrencyLists securitycurrencyLists);

  CorrelationResult getCalculationByCorrelationSet(Integer idCorrelationSet);

  CorrelationLimits getCorrelationSetLimit();

  TenantLimit getCorrelationSetInstrumentLimit(Integer idCorrelationSet);

  SecuritycurrencyLists searchByCriteria(final Integer idCorrelationSet,
      final SecuritycurrencySearch securitycurrencySearch);

  CorrelationSet removeInstrumentFromCorrelationSet(Integer idCorrelationSet, Integer idSecuritycurrency);

  List<CorrelationRollingResult> getRollingCorrelations(Integer idCorrelationSet, Integer[][] securityIdsPairs);
}
