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

  @Transactional
  @Modifying
  void deleteByHoldCashaccountKey_IdSecuritycashAccountAndHoldCashaccountKey_fromHoldDateAfter(
      Integer idSecuritycashAccount, LocalDate fromDate);

  void removeByIdTenant(Integer idTenant);

  @Transactional
  @Modifying
  void deleteByHoldCashaccountKey_IdSecuritycashAccount(Integer idSecuritycashAccount);

  //@formatter:off
  /**
   * Retrieves foreign exchange rates for all cash‐account deposit and withdrawal transactions
   * belonging to the specified tenant.
   * <p>
   * Combines rates for:
   * <ul>
   *   <li>Transactions in portfolios where cash‐account currency ≠ portfolio currency</li>
   *   <li>Transactions where cash‐account currency ≠ tenant base currency</li>
   * </ul>
   * Each entry includes the transaction date, source currency, target currency, and closing rate.
   *
   * @param idTenant the tenant ID whose cash‐account FX rates are fetched
   * @return a list of CashaccountForeignExChangeRate projections
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<CashaccountForeignExChangeRate> getCashaccountForeignExChangeRateByIdTenant(Integer idTenant);

  /**
   * Retrieves distinct foreign exchange rates for all cash‐account deposit and withdrawal transactions across all
   * tenants and accounts.
   * <p>
   * Filters for transactions of type WITHDRAWAL or DEPOSIT (transaction_type ≤ 1), and uses hold_cashaccount_deposit
   * timing to align with historyquote records.
   *
   * @return a list of CashaccountForeignExChangeRate projections
   */
  @Query(nativeQuery = true)
  List<CashaccountForeignExChangeRate> getCashaccountForeignExChangeRate();

  /**
   * Finds the most recent cash‐account deposit record before the given date.
   * <p>
   * Selects the hold_cashaccount_deposit entry with the largest from_hold_date that is strictly less than the specified
   * date for the given account.
   *
   * @param idSecuritycashAccount the cash‐account ID
   * @param date                  the cutoff date (exclusive)
   * @return the HoldCashaccountDeposit record immediately preceding the date
   */
  @Query(nativeQuery = true)
  HoldCashaccountDeposit getLastBeforeDateByCashaccount(Integer idSecuritycashAccount, LocalDate date);

  /**
   * Retrieves the previous deposit-hold record for each cash account relative to its next deposit or withdrawal
   * transaction.
   * <p>
   * For every cash account, finds the hold_cashaccount_deposit entry whose from_hold_date is the greatest date before
   * the next transaction date (t.tt_date). Returns one record per account.
   *
   * @return a list of HoldCashaccountDeposit entities representing the prior hold period
   */
  @Query(nativeQuery = true)
  List<HoldCashaccountDeposit> getPrevHoldingRecords();

  /**
   * Projection interface representing foreign exchange rates applicable to cash account deposit and withdrawal
   * transactions.
   * <p>
   * Instances are populated by native SQL queries that join transaction, currency pair, and historyquote tables,
   * aligning each transaction date with the corresponding end-of-day FX rate.
   */
  public static interface CashaccountForeignExChangeRate {

    /**
     * The date on which the foreign exchange rate applies.
     *
     * @return the transaction or quote date
     */
    LocalDate getDate();

    /**
     * The source currency code from which conversion occurs.
     *
     * @return the ISO currency code of the cash account currency
     */
    String getFromCurrency();

    /**
     * The target currency code to which funds are converted.
     *
     * @return the ISO currency code of the portfolio or tenant base currency
     */
    String getToCurrency();

    /**
     * The closing foreign exchange rate on the given date.
     *
     * @return the FX close price for converting fromCurrency to toCurrency
     */
    Double getClose();
  }
}
