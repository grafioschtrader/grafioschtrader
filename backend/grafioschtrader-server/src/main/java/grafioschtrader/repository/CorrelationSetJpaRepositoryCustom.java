package grafioschtrader.repository;

import grafioschtrader.dto.CorrelationResult;
import grafioschtrader.dto.TenantLimit;
import grafioschtrader.entities.CorrelationSet;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyLists;
import grafioschtrader.search.SecuritycurrencySearch;

public interface CorrelationSetJpaRepositoryCustom extends BaseRepositoryCustom<CorrelationSet> {

  int delEntityWithTenant(Integer id, Integer idTenant);

  CorrelationSet addSecuritycurrenciesToCorrelationSet(Integer idCorrelationSet,
      SecuritycurrencyLists securitycurrencyLists);

  CorrelationResult getCalculationByCorrelationSet(Integer idCorrelationSet);

  TenantLimit getCorrelationSetLimit();

  TenantLimit getCorrelationSetInstrumentLimit(Integer idCorrelationSet);

  SecuritycurrencyLists searchByCriteria(final Integer idCorrelationSet,
      final SecuritycurrencySearch securitycurrencySearch);

  CorrelationSet removeInstrumentFromCorrelationSet(Integer idCorrelationSet, Integer idSecuritycurrency);
}
