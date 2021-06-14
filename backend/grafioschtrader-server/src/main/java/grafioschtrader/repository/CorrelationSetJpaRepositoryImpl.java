package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.Set;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import grafioschtrader.GlobalConstants;
import grafioschtrader.dto.CorrelationResult;
import grafioschtrader.dto.TenantLimit;
import grafioschtrader.entities.CorrelationSet;
import grafioschtrader.entities.Globalparameters;
import grafioschtrader.entities.User;
import grafioschtrader.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafioschtrader.reports.CorrelationReport;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyLists;
import grafioschtrader.search.SecuritycurrencySearch;

public class CorrelationSetJpaRepositoryImpl extends BaseRepositoryImpl<CorrelationSet>
    implements CorrelationSetJpaRepositoryCustom {

  @Autowired
  private CorrelationSetJpaRepository correlationSetJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Override
  public CorrelationSet saveOnlyAttributes(CorrelationSet correlationSet, CorrelationSet existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {

    return RepositoryHelper.saveOnlyAttributes(correlationSetJpaRepository, correlationSet, existingEntity,
        updatePropertyLevelClasses);
  }

  @Override
  @Transactional
  public int delEntityWithTenant(Integer idCorrelationSet, Integer idTenant) {
    return correlationSetJpaRepository.deleteByIdCorrelationSetAndIdTenant(idCorrelationSet, idTenant);
  }

  @Override
  public CorrelationResult getCalculationByCorrelationSet(Integer idCorrelationSet) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Optional<CorrelationSet> correlationSetOpt = correlationSetJpaRepository
        .findByIdTenantAndIdCorrelationSet(user.getIdTenant(), idCorrelationSet);
    if (correlationSetOpt.isPresent()) {
      CorrelationSet correlationSet = correlationSetOpt.get();
      if (correlationSet.getSecuritycurrencyList().size() >= 2) {
        var correlationReport = new CorrelationReport(jdbcTemplate);
        return correlationReport.calcCorrelation(correlationSet);
      } else {
        throw new GeneralNotTranslatedWithArgumentsException("gt.correlation.needs.twoormore", null);
      }
    } else {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }
  }

  @Override
  public SecuritycurrencyLists searchByCriteria(final Integer idCorrelationSet,
      final SecuritycurrencySearch securitycurrencySearch) {

    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Optional<CorrelationSet> correlationSetOpt = correlationSetJpaRepository
        .findByIdTenantAndIdCorrelationSet(user.getIdTenant(), idCorrelationSet);
    if (correlationSetOpt.isEmpty()) {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }

    return new SecuritycurrencyLists(
        securityJpaRepository.searchBuilderWithExclusion(null, idCorrelationSet, securitycurrencySearch,
            user.getIdTenant()),
        currencypairJpaRepository.searchBuilderWithExclusion(null, idCorrelationSet, securitycurrencySearch));
  }

  @Override
  public CorrelationSet removeInstrumentFromCorrelationSet(final Integer idCorrelationSet,
      final Integer idSecuritycurrency) {
    var user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();

    Optional<CorrelationSet> correlationSetOpt = correlationSetJpaRepository
        .findByIdTenantAndIdCorrelationSet(user.getIdTenant(), idCorrelationSet);
    if (correlationSetOpt.isEmpty()) {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    } else {
      var correlationSet = correlationSetOpt.get();
      correlationSet.removeInstrument(idSecuritycurrency);
      return correlationSetJpaRepository.save(correlationSet);
    }
  }

  @Override
  public TenantLimit getCorrelationSetLimit() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new TenantLimit(
        globalparametersJpaRepository.getMaxValueByKey(Globalparameters.GLOB_KEY_MAX_CORRELATION_SET),
        correlationSetJpaRepository.countByIdTenant(user.getIdTenant()).intValue(),
        Globalparameters.GLOB_KEY_MAX_CORRELATION_SET, CorrelationSet.class.getSimpleName());
  }

  @Override
  public TenantLimit getCorrelationSetInstrumentLimit(Integer idCorrelationSet) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new TenantLimit(
        globalparametersJpaRepository.getMaxValueByKey(Globalparameters.GLOB_KEY_MAX_CORRELATION_INSTRUMENTS),
        correlationSetJpaRepository.countInstrumentsInCorrelationSet(user.getIdTenant(), idCorrelationSet).intValue(),
        Globalparameters.GLOB_KEY_MAX_CORRELATION_INSTRUMENTS, CorrelationSet.class.getSimpleName());
  }

  @Override
  public CorrelationSet addSecuritycurrenciesToCorrelationSet(Integer idCorrelationSet,
      SecuritycurrencyLists securitycurrencyLists) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Optional<CorrelationSet> correlationSetOpt = correlationSetJpaRepository
        .findByIdTenantAndIdCorrelationSet(user.getIdTenant(), idCorrelationSet);
    if (correlationSetOpt.isEmpty()) {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    } else {
      var correlationSet = correlationSetOpt.get();
      if (correlationSet.getSecuritycurrencyList().size()
          + securitycurrencyLists.getLength() <= globalparametersJpaRepository
              .getMaxValueByKey(Globalparameters.GLOB_KEY_MAX_CORRELATION_INSTRUMENTS)) {
        securitycurrencyLists.currencypairList
            .forEach(currencypair -> correlationSet.getSecuritycurrencyList().add(currencypair));
        securitycurrencyLists.securityList.forEach(security -> correlationSet.getSecuritycurrencyList().add(security));
        return correlationSetJpaRepository.save(correlationSet);
      } else {
        throw new SecurityException(GlobalConstants.LIMIT_SECURITY_BREACH);
      }

    }

  }

}
