package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.BaseRepositoryImpl;
import grafiosch.repository.RepositoryHelper;
import grafioschtrader.dto.FeeModelComparisonResponse;
import grafioschtrader.dto.TradingPeriodTransactionSummary;
import grafioschtrader.entities.SecaccountTradingPeriod;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.service.FeeModelComparisonService;

public class SecurityaccountJpaRepositoryImpl extends BaseRepositoryImpl<Securityaccount>
    implements SecurityaccountJpaRepositoryCustom {

  @Autowired
  private SecurityaccountJpaRepository securityaccountJpaRepository;

  @Autowired
  private TransactionJpaRepository transactionJpaRepository;

  @Autowired
  private FeeModelComparisonService feeModelComparisonService;

  @Override
  @Transactional
  @Modifying
  public Securityaccount saveOnlyAttributes(final Securityaccount securityaccount, Securityaccount existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {
    validateTradingPeriods(securityaccount, existingEntity);
    return RepositoryHelper.saveOnlyAttributes(securityaccountJpaRepository, securityaccount, existingEntity,
        updatePropertyLevelClasses);
  }

  /**
   * Validates trading periods for overlap and transaction conflicts before saving.
   */
  private void validateTradingPeriods(Securityaccount securityaccount, Securityaccount existingEntity) {
    List<SecaccountTradingPeriod> newPeriods = securityaccount.getTradingPeriods();
    if (newPeriods == null || newPeriods.isEmpty()) {
      return;
    }

    // 1. Overlap validation: group by (specInvestInstrument, categoryType)
    var grouped = newPeriods.stream().collect(Collectors.groupingBy(
        p -> p.getSpecInvestInstrument().name() + "|" + (p.getCategoryType() == null ? "null" : p.getCategoryType().name())));

    for (var entry : grouped.entrySet()) {
      List<SecaccountTradingPeriod> group = new ArrayList<>(entry.getValue());
      group.sort((a, b) -> a.getDateFrom().compareTo(b.getDateFrom()));
      for (int i = 0; i < group.size() - 1; i++) {
        SecaccountTradingPeriod current = group.get(i);
        SecaccountTradingPeriod next = group.get(i + 1);
        if (current.getDateTo() == null || !current.getDateTo().isBefore(next.getDateFrom())) {
          throw new DataViolationException("date.from", "gt.trading.period.overlap",
              new Object[] { current.getSpecInvestInstrument(), current.getCategoryType() });
        }
      }
    }

    // 2. Transaction conflict validation: only check if editing an existing account
    if (existingEntity != null && existingEntity.getIdSecuritycashAccount() != null) {
      List<TradingPeriodTransactionSummary> summaries = transactionJpaRepository
          .getTransactionSummariesBySecurityaccount(existingEntity.getIdSecuritycashAccount());
      for (TradingPeriodTransactionSummary summary : summaries) {
        boolean covered = newPeriods.stream().anyMatch(p ->
            p.getSpecInvestInstrument() == summary.getSpecInvestInstrument()
                && (p.getCategoryType() == null || p.getCategoryType() == summary.getCategoryType())
                && !p.getDateFrom().isAfter(summary.getMaxTransactionDate())
                && (p.getDateTo() == null || !p.getDateTo().isBefore(summary.getMaxTransactionDate())));
        if (!covered) {
          throw new DataViolationException("date.to", "gt.trading.period.transaction.conflict",
              new Object[] { summary.getSpecInvestInstrument(), summary.getCategoryType(),
                  summary.getMaxTransactionDate() });
        }
      }
    }
  }

  @Override
  public int delEntityWithTenant(Integer id, Integer idTenant) {
    return securityaccountJpaRepository.deleteSecurityaccount(id, idTenant);
  }

  @Override
  public List<TradingPeriodTransactionSummary> getTransactionSummaries(Integer idSecuritycashAccount) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    // Verify the account belongs to the current tenant
    Securityaccount sa = securityaccountJpaRepository.findByIdSecuritycashAccountAndIdTenant(
        idSecuritycashAccount, user.getIdTenant());
    if (sa == null) {
      return List.of();
    }
    return transactionJpaRepository.getTransactionSummariesBySecurityaccount(idSecuritycashAccount);
  }

  @Override
  public FeeModelComparisonResponse getFeeModelComparison(Integer idSecuritycashAccount, boolean excludeZeroCost) {
    return feeModelComparisonService.compare(idSecuritycashAccount, excludeZeroCost);
  }

}
