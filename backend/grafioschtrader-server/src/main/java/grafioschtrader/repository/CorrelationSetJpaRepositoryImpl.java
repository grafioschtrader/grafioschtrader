package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import grafiosch.BaseConstants;
import grafiosch.common.DateHelper;
import grafiosch.dto.TenantLimit;
import grafiosch.entities.User;
import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafiosch.repository.BaseRepositoryImpl;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafiosch.repository.RepositoryHelper;
import grafioschtrader.GlobalParamKeyDefault;
import grafioschtrader.dto.CorrelationLimits;
import grafioschtrader.dto.CorrelationResult;
import grafioschtrader.dto.CorrelationRollingResult;
import grafioschtrader.entities.CorrelationSet;
import grafioschtrader.entities.Security;
import grafioschtrader.reports.CorrelationReport;
import grafioschtrader.reportviews.securitycurrency.SecuritycurrencyLists;
import grafioschtrader.search.SecuritycurrencySearch;
import grafioschtrader.types.SamplingPeriodType;
import jakarta.transaction.Transactional;

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
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Override
  public CorrelationSet saveOnlyAttributes(CorrelationSet correlationSet, CorrelationSet existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    correlationSet.validateBeforeSave();
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
    var correlationSet = getCorrelationSetById(idCorrelationSet);
    if (correlationSet.getSecuritycurrencyList().size() >= 2) {
      var correlationReport = new CorrelationReport(jdbcTemplate, currencypairJpaRepository, historyquoteJpaRepository);
      return correlationReport.calcCorrelationForMatrix(correlationSet);
    } else {
      throw new GeneralNotTranslatedWithArgumentsException("gt.correlation.needs.twoormore", null);
    }
  }

  @Override
  public SecuritycurrencyLists searchByCriteria(final Integer idCorrelationSet,
      final SecuritycurrencySearch securitycurrencySearch) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    setMinMaxDateForInstrumentSelection(idCorrelationSet, securitycurrencySearch,
        getCorrelationSetById(idCorrelationSet));
    securitycurrencySearch.setNoMarketValue(true);
    return new SecuritycurrencyLists(
        securityJpaRepository.searchBuilderWithExclusion(null, idCorrelationSet, securitycurrencySearch,
            user.getIdTenant()),
        currencypairJpaRepository.searchBuilderWithExclusion(null, idCorrelationSet, securitycurrencySearch));
  }

  private void setMinMaxDateForInstrumentSelection(final Integer idCorrelationSet,
      final SecuritycurrencySearch securitycurrencySearch, CorrelationSet correlationSet) {

    List<Security> securities = correlationSet.getSecurityList();
    securitycurrencySearch.setMaxFromDate(DateHelper.getDateFromLocalDate(correlationSet.getDateFrom()));
    securitycurrencySearch.setMinToDate(DateHelper.getDateFromLocalDate(correlationSet.getDateTo()));
    if (!securities.isEmpty()) {
      securitycurrencySearch.setMaxFromDate(
          DateHelper.getMaxMinDate(securities.stream().map(Security::getActiveFromDate).max(Date::compareTo).get(),
              securitycurrencySearch.getMaxFromDate(), true));
      securitycurrencySearch.setMinToDate(
          DateHelper.getMaxMinDate(securities.stream().map(Security::getActiveToDate).min(Date::compareTo).get(),
              securitycurrencySearch.getMinToDate(), false));
    }
  }

  @Override
  public CorrelationSet removeInstrumentFromCorrelationSet(final Integer idCorrelationSet,
      final Integer idSecuritycurrency) {
    var correlationSet = getCorrelationSetById(idCorrelationSet);
    correlationSet.removeInstrument(idSecuritycurrency);
    return correlationSetJpaRepository.save(correlationSet);
  }

  @Override
  public CorrelationLimits getCorrelationSetLimit() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new CorrelationLimits(new TenantLimit(
        globalparametersJpaRepository.getMaxValueByKey(GlobalParamKeyDefault.GLOB_KEY_MAX_CORRELATION_SET),
        correlationSetJpaRepository.countByIdTenant(user.getIdTenant()).intValue(),
        GlobalParamKeyDefault.GLOB_KEY_MAX_CORRELATION_SET, CorrelationSet.class.getSimpleName()));
  }

  @Override
  public TenantLimit getCorrelationSetInstrumentLimit(Integer idCorrelationSet) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new TenantLimit(
        globalparametersJpaRepository.getMaxValueByKey(GlobalParamKeyDefault.GLOB_KEY_MAX_CORRELATION_INSTRUMENTS),
        correlationSetJpaRepository.countInstrumentsInCorrelationSet(user.getIdTenant(), idCorrelationSet).intValue(),
        GlobalParamKeyDefault.GLOB_KEY_MAX_CORRELATION_INSTRUMENTS, CorrelationSet.class.getSimpleName());
  }

  @Override
  public CorrelationSet addSecuritycurrenciesToCorrelationSet(Integer idCorrelationSet,
      SecuritycurrencyLists securitycurrencyLists) {
    var correlationSet = getCorrelationSetById(idCorrelationSet);
    if (correlationSet.getSecuritycurrencyList().size()
        + securitycurrencyLists.getLength() <= globalparametersJpaRepository
            .getMaxValueByKey(GlobalParamKeyDefault.GLOB_KEY_MAX_CORRELATION_INSTRUMENTS)) {
      securitycurrencyLists.currencypairList
          .forEach(currencypair -> correlationSet.getSecuritycurrencyList().add(currencypair));
      securitycurrencyLists.securityList.forEach(security -> correlationSet.getSecuritycurrencyList().add(security));
      return correlationSetJpaRepository.save(correlationSet);
    } else {
      throw new SecurityException(BaseConstants.LIMIT_SECURITY_BREACH);
    }
  }

  @Override
  public List<CorrelationRollingResult> getRollingCorrelations(Integer idCorrelationSet, Integer[][] securityIdsPairs) {
    var cs = getCorrelationSetById(idCorrelationSet);

    if (cs.getSamplingPeriod() == SamplingPeriodType.ANNUAL_RETURNS) {
      throw new IllegalArgumentException("Rolling correlation not possible for annual!");
    }
    var correlationSetDummy = new CorrelationSet(cs.getIdTenant(), cs.getName(), cs.getIdCorrelationSet(),
        cs.getDateFrom(), cs.getDateTo(), cs.getSamplingPeriod().getValue(), cs.getRolling(), cs.isAdjustCurrency());

    Set<Integer> ids = Stream.of(securityIdsPairs).flatMap(Stream::of).collect(Collectors.toSet());
    correlationSetDummy.setSecuritycurrencyList(cs.getSecuritycurrencyList().stream()
        .filter(sc -> ids.contains(sc.getIdSecuritycurrency())).collect(Collectors.toList()));
    var correlationReport = new CorrelationReport(jdbcTemplate, currencypairJpaRepository, historyquoteJpaRepository);
    return correlationReport.getRollingCorrelation(correlationSetDummy, securityIdsPairs);
  }

  private CorrelationSet getCorrelationSetById(Integer idCorrelationSet) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Optional<CorrelationSet> correlationSetOpt = correlationSetJpaRepository
        .findByIdTenantAndIdCorrelationSet(user.getIdTenant(), idCorrelationSet);
    if (correlationSetOpt.isEmpty()) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    return correlationSetOpt.get();
  }

}
