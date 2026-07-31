package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import grafioschtrader.dto.HoldConsistencyDefect;
import grafioschtrader.entities.HoldCashaccountBalance;
import grafioschtrader.entities.HoldCashaccountBalance.HoldCashaccountBalanceKey;

public interface HoldCashaccountBalanceJpaRepository extends
    JpaRepository<HoldCashaccountBalance, HoldCashaccountBalanceKey>, HoldCashaccountBalanceJpaRepositoryCustom {

  void removeByIdTenant(Integer idTenant);

  //@formatter:off
  /**
   * Retrieves all cash account balance records for a tenant that are active at the given reference date.
   * A record is active when the reference date falls within the hold period
   * (from_hold_date &le; refDate and to_hold_date is null or &ge; refDate).
   *
   * @param idTenant the tenant ID
   * @param refDate  the reference date at which to evaluate balances
   * @return a list of cash account balance records active at the reference date
   */
  //@formatter:on
  @Query("""
      SELECT hcab FROM HoldCashaccountBalance hcab
      WHERE hcab.idTenant = :idTenant
        AND hcab.idEm.fromHoldDate <= :refDate
        AND (hcab.toHoldDate IS NULL OR hcab.toHoldDate >= :refDate)""")
  List<HoldCashaccountBalance> findCashBalancesAtDate(@Param("idTenant") Integer idTenant,
      @Param("refDate") LocalDate refDate);

  void removeByIdTenantAndIdEmIdSecuritycashAccountAndIdEmFromHoldDateGreaterThanEqual(Integer idTenant,
      Integer idSecuritycashAccount, LocalDate fromHoldDate);

  /**
   * Retrieves the balance from the most recent record strictly before the given date.
   * Returns null if no record exists before the date (balance is implicitly 0).
   *
   * Named query: HoldCashaccountBalance.getBalanceBeforeDate
   *
   * @param idCashaccount the cash account ID
   * @param beforeDate    the date to look before (exclusive)
   * @return the balance from the most recent record before the date, or null if none exists
   */
  @Query(nativeQuery = true)
  Double getBalanceBeforeDate(Integer idCashaccount, LocalDate beforeDate);

  /**
   * Retrieves the minimum balance from all records at or after the given date.
   * Returns null if no records exist at or after the date.
   *
   * Named query: HoldCashaccountBalance.getMinBalanceFromDate
   *
   * @param idCashaccount the cash account ID
   * @param fromDate      the start date (inclusive)
   * @return the minimum balance from records at or after the date, or null if none exists
   */
  @Query(nativeQuery = true)
  Double getMinBalanceFromDate(Integer idCashaccount, LocalDate fromDate);

  //@formatter:off
  /**
   * Retrieves daily cash account balance change transactions for all cash accounts
   * belonging to the specified tenant.
   * <p>
   * For each cash account and transaction date, computes:
   * <ul>
   *   <li>withdrawals and deposits (transaction_type ≤ WITHDRAWAL/DEPOSIT)</li>
   *   <li>interest (transaction_type = INTEREST_CASHACCOUNT)</li>
   *   <li>fees (transaction_type = FEE or FINANCE_COST)</li>
   *   <li>net buys/sells (transaction_type BETWEEN ACCUMULATE and REDUCE)</li>
   *   <li>dividends (transaction_type = DIVIDEND)</li>
   *   <li>total net change</li>
   * </ul>
   * The five categories together add up to the total; FINANCE_COST is grouped with FEE because it is
   * a cost on a finance instrument, and because leaving it out of every category made the breakdown
   * shown by the performance report silently disagree with the balance.
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
   * Counts, per tenant and kind, the {@code hold_cashaccount_balance} rows that disagree with the transactions they are
   * derived from. Read-only; nothing is repaired.
   * <p>
   * The expected content is reproduced entirely from the {@code transaction} table with a window function: every column
   * is a running total in the cash account's own currency, so neither exchange rates nor historical quotes are involved
   * and the comparison is exact. Checked are surplus rows, absent rows, all six value columns, the chaining of
   * {@code to_hold_date} to the next {@code from_hold_date}, and the denormalised tenant/portfolio ids.
   * <p>
   * {@code balance} is the one column the writer rounds, to the account currency's precision
   * ({@code HoldCashaccountBalanceJpaRepositoryImpl} via {@code getPrecisionForCurrency}), so it gets its own tolerance
   * of one currency unit, decoded from the {@code gt.currency.precision} globalparameter inside the query and defaulting
   * to two digits. Comparing the stored value against the <em>unrounded</em> running sum with that tolerance is
   * deliberate: rounding the expected sum in SQL instead makes every balance that lands exactly on half a currency unit
   * flip, because MariaDB's decimal window aggregate and the writer's double accumulation round it in opposite
   * directions. The remaining five columns are stored unrounded and keep the caller's tolerance.
   * <p>
   * Named query: HoldCashaccountBalance.countConsistencyDefects
   *
   * @param tolerance absolute tolerance for the five unrounded category columns, e.g. 0.005; the {@code balance}
   *                  comparison ignores it and uses the currency's own unit instead
   * @return one row per tenant and defect kind, empty when everything agrees
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<HoldConsistencyDefect> countConsistencyDefects(double tolerance);

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
     * The total fees charged to the cash account on this date, including FINANCE_COST on finance
     * instruments.
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
