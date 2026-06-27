package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.BaseRepositoryImpl;
import grafiosch.repository.GlobalparametersJpaRepository;
import grafioschtrader.entities.Cashaccount;

public class CashaccountJpaRepositoryImpl extends BaseRepositoryImpl<Cashaccount>
    implements CashaccountJpaRepositoryCustom {

  @Autowired
  public CashaccountJpaRepository cashaccountJpaRepository;

  // @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  GlobalparametersJpaRepository globalparametersJpaRepository;

  private TransactionJpaRepository transactionJpaRepository;

  @Autowired
  public void setCurrencypairJpaRepository(@Lazy final CurrencypairJpaRepository currencypairJpaRepository) {
    this.currencypairJpaRepository = currencypairJpaRepository;
  }

  @Autowired
  public void setTransactionJpaRepository(@Lazy final TransactionJpaRepository transactionJpaRepository) {
    this.transactionJpaRepository = transactionJpaRepository;
  }

  @Override
  @Transactional
  @Modifying
  public Cashaccount saveOnlyAttributes(final Cashaccount cashaccount, Cashaccount existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    validateActiveToDate(cashaccount, existingEntity);
    Cashaccount createEditCashaccount = cashaccount;
    if (existingEntity != null) {
      createEditCashaccount = existingEntity;
      createEditCashaccount.updateThis(cashaccount);
    }

    if (!createEditCashaccount.getPortfolio().getCurrency().equals(cashaccount.getCurrency())) {
      currencypairJpaRepository.findOrCreateCurrencypairByFromAndToCurrency(
          createEditCashaccount.getPortfolio().getCurrency(), cashaccount.getCurrency(), true);
    }
    return cashaccountJpaRepository.save(createEditCashaccount);
  }

  /**
   * Validates that the account's active-until date is not earlier than its most recent transaction. A {@code null}
   * active-until date (active indefinitely) or a new account without existing transactions imposes no restriction.
   *
   * @param cashaccount    the account being saved, carrying the proposed active-until date
   * @param existingEntity the previously persisted account, or null when creating a new one
   * @throws DataViolationException if the active-until date precedes the latest transaction date
   */
  private void validateActiveToDate(Cashaccount cashaccount, Cashaccount existingEntity) {
    if (cashaccount.getActiveToDate() == null || existingEntity == null
        || existingEntity.getIdSecuritycashAccount() == null) {
      return;
    }
    transactionJpaRepository.findMaxTransactionTimeByCashaccount(existingEntity.getIdSecuritycashAccount())
        .map(LocalDateTime::toLocalDate)
        .filter(maxDate -> maxDate.isAfter(cashaccount.getActiveToDate()))
        .ifPresent(maxDate -> {
          throw new DataViolationException("active.to.date", "gt.account.active.before.transaction",
              new Object[] { maxDate });
        });
  }

  @Override
  public int delEntityWithTenant(Integer id, Integer idTenant) {
    return cashaccountJpaRepository.deleteCashaccount(id, idTenant);
  }

}
