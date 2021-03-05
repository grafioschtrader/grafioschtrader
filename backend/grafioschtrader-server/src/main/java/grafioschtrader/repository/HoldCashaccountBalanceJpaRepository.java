package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafioschtrader.entities.HoldCashaccountBalance;
import grafioschtrader.entities.HoldCashaccountBalance.HoldCashaccountBalanceKey;

public interface HoldCashaccountBalanceJpaRepository extends
    JpaRepository<HoldCashaccountBalance, HoldCashaccountBalanceKey>, HoldCashaccountBalanceJpaRepositoryCustom {

  void removeByIdTenant(Integer idTenant);

  void removeByIdTenantAndIdEmIdSecuritycashAccountAndIdEmFromHoldDateGreaterThanEqual(Integer idTenant,
      Integer idSecuritycashAccount, LocalDate fromHoldDate);

  @Query(nativeQuery = true)
  List<CashaccountBalanceChangeTransaction> getCashaccountBalanceByTenant(Integer idTenant);

  @Query(nativeQuery = true)
  List<CashaccountBalanceChangeTransaction> getCashaccountBalanceByCashaccountAndDate(Integer idCashaccount,
      LocalDate fromDate);

  @Query(nativeQuery = true)
  HoldCashaccountBalance getCashaccountBalanceMaxFromDateByCashaccount(Integer idCashaccount);

  public static interface CashaccountBalanceChangeTransaction {
    Integer getIdCashaccount();

    Integer getIdPortfolio();

    String getPortfolioCurrency();

    String getAccountCurrency();

    LocalDate getFromDate();

    Double getWithdrawlDeposit();

    Double getInterestCashaccount();

    Double getFee();

    Double getAccumulateReduce();

    Double getDividend();

    double getTotal();
  }

}
