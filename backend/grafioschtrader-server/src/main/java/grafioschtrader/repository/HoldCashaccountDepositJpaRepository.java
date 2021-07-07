package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.entities.HoldCashaccountDeposit;
import grafioschtrader.entities.HoldCashaccountDeposit.HoldCashaccountDepositKey;

public interface HoldCashaccountDepositJpaRepository extends
    JpaRepository<HoldCashaccountDeposit, HoldCashaccountDepositKey>, HoldCashaccountDepositJpaRepositoryCustom {

  /**
   * Returns the currency exchanges on deposit and withdrawal transactions. It
   * combines the currency exchange for the tenant main currency and the portfolio
   * currency as well.
   *
   * @param idTenant
   * @return
   */
  @Query(nativeQuery = true)
  List<CashaccountForeignExChangeRate> getCashaccountForeignExChangeRateByIdTenant(Integer idTenant);

  @Query(nativeQuery = true)
  List<CashaccountForeignExChangeRate> getCashaccountForeignExChangeRate();

  void removeByIdTenant(Integer idTenant);

  @Transactional
  @Modifying
  void deleteByHoldCashaccountKey_IdSecuritycashAccountAndHoldCashaccountKey_fromHoldDateAfter(
      Integer idSecuritycashAccount, LocalDate fromDate);

  @Transactional
  @Modifying
  void deleteByHoldCashaccountKey_IdSecuritycashAccount(Integer idSecuritycashAccount);

  @Query(nativeQuery = true)
  HoldCashaccountDeposit getLastBeforeDateByCashaccount(Integer idSecuritycashAccount, LocalDate date);

  @Query(nativeQuery = true)
  List<HoldCashaccountDeposit> getPrevHoldingRecords();

  public static interface CashaccountForeignExChangeRate {
    public LocalDate getDate();

    public String getFromCurrency();

    public String getToCurrency();

    public Double getClose();
  }
}
