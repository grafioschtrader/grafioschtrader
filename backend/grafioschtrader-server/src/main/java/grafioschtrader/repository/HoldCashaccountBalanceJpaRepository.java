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

  //@formatter:off
  /**
   * Retrieves daily cash account balance change transactions for all cash accounts
   * belonging to the specified tenant.
   * <p>
   * For each cash account and transaction date, computes:
   * <ul>
   *   <li>withdrawals and deposits (transaction_type ≤ WITHDRAWAL/DEPOSIT)</li>
   *   <li>interest (transaction_type = INTEREST_CASHACCOUNT)</li>
   *   <li>fees (transaction_type = FEE)</li>
   *   <li>net buys/sells (transaction_type BETWEEN ACCUMULATE and REDUCE)</li>
   *   <li>dividends (transaction_type = DIVIDEND)</li>
   *   <li>total net change</li>
   * </ul>
   * Results are grouped by cash account and date, ordered by account and date.
   *
   * @param idTenant the tenant ID whose cash account transactions are aggregated
   * @return a list of CashaccountBalanceChangeTransaction projections with daily metrics
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<CashaccountBalanceChangeTransaction> getCashaccountBalanceByTenant(Integer idTenant);

  /**
   * Retrieves daily cash account balance change transactions for the specified cash account starting from the given
   * date.
   * <p>
   * Computes the same breakdown as {@link #getCashaccountBalanceByTenant(Integer)}, but filters to a single cash
   * account (?1) and transactions on or after ?2. Results are grouped by date and ordered by date.
   *
   * @param idCashaccount the ID of the cash account
   * @param fromDate      the start date (inclusive) for transactions
   * @return a list of CashaccountBalanceChangeTransaction projections with daily metrics
   */
  @Query(nativeQuery = true)
  List<CashaccountBalanceChangeTransaction> getCashaccountBalanceByCashaccountAndDate(Integer idCashaccount,
      LocalDate fromDate);

  /**
   * Retrieves daily cash account balance change transactions for the specified cash account starting from the given
   * date.
   * <p>
   * Computes the same breakdown as {@link #getCashaccountBalanceByTenant(Integer)}, but filters to a single cash
   * account (?1) and transactions on or after ?2. Results are grouped by date and ordered by date.
   *
   * @param idCashaccount the ID of the cash account
   * @param fromDate      the start date (inclusive) for transactions
   * @return a list of CashaccountBalanceChangeTransaction projections with daily metrics
   */
  @Query(nativeQuery = true)
  HoldCashaccountBalance getCashaccountBalanceMaxFromDateByCashaccount(Integer idCashaccount);

  //@formatter:off
  /**
   * Projection interface representing daily aggregated cash account balance
   * changes.
   * <p>
   * Populated by native queries that sum various transaction types per cash
   * account and date:
   * <ul>
   * <li><strong>withdrawal/deposit</strong> (WITHDRAWAL, DEPOSIT)</li>
   * <li><strong>interest</strong> (INTEREST_CASHACCOUNT)</li>
   * <li><strong>fees</strong> (FEE)</li>
   * <li><strong>accumulate/reduce</strong> (ACCUMULATE, REDUCE)</li>
   * <li><strong>dividends</strong> (DIVIDEND)</li>
   * <li><strong>total</strong> net change</li>
   * </ul>
   * Also provides portfolio context and currency codes.
   */
  //@formatter:on
  public static interface CashaccountBalanceChangeTransaction {

    /**
     * The identifier of the cash account.
     *
     * @return the cash account ID
     */
    Integer getIdCashaccount();

    /**
     * The identifier of the portfolio owning the cash account.
     *
     * @return the portfolio ID
     */
    Integer getIdPortfolio();

    /**
     * The base currency of the portfolio.
     *
     * @return the portfolio currency code
     */
    String getPortfolioCurrency();

    /**
     * The currency of the cash account.
     *
     * @return the account currency code
     */
    String getAccountCurrency();

    /**
     * The date for which these balance changes are aggregated.
     *
     * @return the transaction date
     */
    LocalDate getFromDate();

    /**
     * The net amount of withdrawals (negative) and deposits (positive) on this date.
     *
     * @return the withdrawal/deposit total
     */
    Double getWithdrawlDeposit();

    /**
     * The total interest posted to the cash account on this date.
     *
     * @return the interest amount
     */
    Double getInterestCashaccount();

    /**
     * The total fees charged to the cash account on this date.
     *
     * @return the fee amount
     */
    Double getFee();

    /**
     * The net amount of share buys (positive) and sells (negative) affecting cash.
     *
     * @return the accumulate/reduce total
     */
    Double getAccumulateReduce();

    /**
     * The total dividends credited to the cash account on this date.
     *
     * @return the dividend amount
     */
    Double getDividend();

    /**
     * The net total change in the cash account balance for this date, summing all transaction types.
     *
     * @return the total net change
     */
    double getTotal();
  }

}
