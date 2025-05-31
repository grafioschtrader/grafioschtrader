package grafioschtrader.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import grafiosch.common.UpdateQuery;
import grafioschtrader.entities.HoldSecurityaccountSecurity;
import grafioschtrader.entities.HoldSecurityaccountSecurityKey;
import grafioschtrader.reportviews.performance.IPeriodHolding;

public interface HoldSecurityaccountSecurityJpaRepository
    extends JpaRepository<HoldSecurityaccountSecurity, HoldSecurityaccountSecurityKey>,
    HoldSecurityaccountSecurityJpaRepositoryCustom {

  @UpdateQuery(value = "DELETE FROM hold_securityaccount_security WHERE id_securitycash_account = ?1", nativeQuery = true)
  void removeAllByIdSecuritycashAccount(Integer idSecuritycashAccount);

  void deleteByHsskIdSecuritycashAccountAndHsskIdSecuritycurrency(Integer idSecuritycashAccount,
      Integer idSecuritycurrency);

  @Query("""
      SELECT MIN(hss.hssk.fromHoldDate) AS firstTradingDate
      FROM HoldSecurityaccountSecurity hss WHERE hss.idTenant = ?1""")
  LocalDate findByIdTenantMinFromHoldDate(Integer idTenant);

  @Query("""
      SELECT MIN(hss.hssk.fromHoldDate) AS firstTradingDate
      FROM HoldSecurityaccountSecurity hss WHERE hss.idPortfolio = ?1""")
  LocalDate findByIdPortfolioMinFromHoldDate(Integer idTenant);

  //@formatter:off
  /**
   * Retrieves aggregated buy/sell transaction units and security split ratios for each security
   * within the specified security custody account.
   * <ul>
   *   <li>Filters by Securityaccount ID (?1), referencing the Securitycashaccount base entity.</li>
   *   <li>For direct‐investment instruments (DIRECT_INVESTMENT, ETF, MUTUAL_FUND, PENSION_FUNDS):
   *       sums net units per date (positive for buys, negative for sells).</li>
   *   <li>For margin instruments (CFD, FOREX):
   *       computes factorUnits as ±(units * assetInvestmentValue2) and retains idTransactionMargin for margin tracking.</li>
   *   <li>Includes security split events for any traded security, computing split ratio (to_factor/from_factor).</li>
   * </ul>
   * Results are combined via UNION and ordered by security ID and event date.
   *
   * @param idSecurityaccount the ID of the security custody account (Securityaccount)
   * @return a list of ITransactionSecuritySplit projections with one entry per security and date
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<ITransactionSecuritySplit> getBuySellTransWithSecuritySplitByIdSecurityaccount(Integer idSecurityaccount);

  //@formatter:off
  /**
   * Retrieves aggregated buy/sell transaction units and security split factors
   * for the given security cash account and security.
   * <ul>
   *   <li>For ACCUMULATE (4) and REDUCE (5) transactions: sums units per date,
   *       positive for buys and negative for sells.</li>
   *   <li>For security split events: computes split ratio (to_factor / from_factor).</li>
   * </ul>
   * The results are combined via UNION and ordered by the event date.
   *
   * @param idSecurityaccount   the ID of the security cash account
   * @param idSecuritycurrency  the ID of the security
   * @return a list of ITransactionSecuritySplit projections with one entry per date
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<ITransactionSecuritySplit> getBuySellTransWithSecuritySplitByIdSecurityaccountAndSecurity(
      Integer idSecurityaccount, Integer idSecuritycurrency);

  //@formatter:off
  /**
   * Retrieves a combined list of buy/sell transactions and security splits for the given security account and security.
   * - Selects transactions of type ACCUMULATE (4) and REDUCE (5), calculating factorUnits as ±(units * assetInvestmentValue2).
   * - Unions with security splits, computing factorUnits as split ratio (to_factor/from_factor).
   * - Orders all entries by timestamp (transaction_time or split_date).
   *
   * @param idSecurityaccount    the ID of the security cash account
   * @param idSecuritycurrency  the ID of the security
   * @return a list of ITransactionSecuritySplit projections containing transaction or split details
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<ITransactionSecuritySplit> getBuySellTransWithSecuritySplitByIdSecurityaccountAndSecurityMargin(
      Integer idSecurityaccount, Integer idSecuritycurrency);

  //@formatter:off
  /**
   * Retrieves daily aggregated tenant holdings for the specified tenant and date range.
   * - Calculates aggregated values of security positions, margin gains, and market risk when all required quotes are available.
   * - Summarizes cash deposits, dividends, interest, and fees (negated) from tenant cash account balances.
   * - Applies currency conversion to tenant currency using historical quotes.
   * - Includes external cash transfers to compute net gain per day.
   *
   * @param idTenant the ID of the tenant
   * @param dateFrom the start date of the period (inclusive)
   * @param dateTo   the end date of the period (inclusive)
   * @return a list of IPeriodHolding projections with daily performance metrics
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<IPeriodHolding> getPeriodHoldingsByTenant(Integer idTenant, LocalDate dateFrom, LocalDate dateTo);

  //@formatter:off
  /**
   * Retrieves daily aggregated portfolio holdings for the specified portfolio and date range.
   * - Calculates security position values, margin gains, and market risk when all required quotes are available.
   * - Summarizes cash deposits, dividends, interest, and fees (negated) from cash account balances.
   * - Applies currency conversion to portfolio currency using historical quotes.
   * - Includes external cash transfers to compute net gain per day.
   *
   * @param idPortfolio the ID of the portfolio
   * @param dateFrom    the start date of the period (inclusive)
   * @param dateTo      the end date of the period (inclusive)
   * @return a list of IPeriodHolding projections with daily performance metrics
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<IPeriodHolding> getPeriodHoldingsByPortfolio(Integer idPortfolio, LocalDate dateFrom, LocalDate dateTo);

  //@formatter:off
  /**
   * Retrieves all trading dates and security IDs for which no end-of-day quote exists
   * for securities held by the specified tenant within the given period.
   * <ul>
   *   <li>Scans official trading days (excluding shifted days) per security’s exchange.</li>
   *   <li>Filters to securities actively held by the tenant in their security accounts.</li>
   *   <li>Excludes exchanges with no market value and securities outside their active date range.</li>
   *   <li>Identifies missing quotes where no historyquote record exists for that date.</li>
   * </ul>
   *
   * @param idTenant the tenant ID to scope held securities
   * @param dateFrom the start date of the period (inclusive)
   * @param dateTo   the end date of the period (inclusive)
   * @return a list of DateSecurityQuoteMissing projections containing tradingDate and idSecuritycurrency
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<DateSecurityQuoteMissing> getMissingQuotesForSecurityByTenantAndPeriod(Integer idTenant, LocalDate dateFrom,
      LocalDate dateTo);

  //@formatter:off
  /**
   * Retrieves all hold records for a security with the given ISIN in the specified security account
   * that are active on the provided transaction date.
   * <p>
   * Joins the hold_securityaccount_security table with Security to filter by ISIN,
   * and ensures the transaction date falls within the hold period (from_hold_date ≤ date ≤ to_hold_date).
   *
   * @param isin                the ISIN of the security
   * @param idSecurityaccount   the ID of the security account
   * @param transactionDate     the date to check for an active hold
   * @return a list of HoldSecurityaccountSecurity entities matching the criteria
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<HoldSecurityaccountSecurity> getByISINAndSecurityAccountAndDate(String isin, Integer idSecurityaccount,
      Date transactinDate);

  //@formatter:off
  /**
   * Retrieves all unique trading dates on which at least one security held by the specified tenant
   * has no end-of-day quote. Trading days which can't used for calculation of tenants portfolio
   * performance, because historical data for one or more security holding are not
   * available.
   * <p>
   * - Scans official trading days for each held security’s exchange, excluding shifted days.<br>
   * - Joins to tenant’s held securities and filters by active hold periods.<br>
   * - Excludes exchanges flagged as having no market value and securities outside their active date range.<br>
   * - Selects dates where no Historyquote record exists for the security on that trading day.
   *
   * @param idTenant the ID of the tenant whose held securities are checked
   * @return a set of trading dates with missing quotes across all held securities
   */
  //@formatter:on
  @Query(nativeQuery = true)
  Set<Date> getMissingsQuoteDaysByTenant(Integer idTenant);

  //@formatter:off
  /**
   * Retrieves all unique trading dates on which at least one security held in the specified portfolio
   * has no end-of-day quote.
   * <p>
   * - Scans official trading days for each security’s exchange, excluding shifted days.<br>
   * - Joins to the portfolio’s held securities and filters by active hold periods.<br>
   * - Excludes exchanges without market value and securities outside their active date range.<br>
   * - Selects dates where no Historyquote record exists for a held security on that trading day.
   *
   * @param idPortfolio the ID of the portfolio whose held securities are checked
   * @return a set of trading dates with missing quotes across all securities in the portfolio
   */
  //@formatter:on
  @Query(nativeQuery = true)
  Set<Date> getMissingsQuoteDaysByPortfolio(Integer idPortfolio);

  //@formatter:off
  /**
   * Retrieves all unique “combined holiday” dates for securities held by the given tenant.
   * <p>
   * This query looks up each security’s official trading calendar, including any
   * adjusted (minus) dates, and then filters to those dates that fall within the
   * tenant’s active hold periods. Only exchanges with market value are considered,
   * and results are limited to dates on or before today.
   *
   * @param idTenant the ID of the tenant whose held securities are checked
   * @return a set of dates representing combined holiday adjustments (holdDate)
   *         for the tenant’s active holdings
   */
  //@formatter:on
  @Query(nativeQuery = true)
  Set<Date> getCombinedHolidayOfHoldingsByTenant(Integer idTenant);

  //@formatter:off
  /**
   * Retrieves all unique “combined holiday” dates for securities held in the specified portfolio.
   * <p>
   * - Scans each held security’s official trading calendar, including any minus‐day adjustments (trading_days_minus).<br>
   * - Filters to hold periods for the portfolio (from_hold_date ≤ date ≤ to_hold_date or open‐ended).<br>
   * - Considers only exchanges with market value and dates up to today.<br>
   * - Returns each adjusted trading date (holdDate) where the portfolio holds the security.
   *
   * @param idPortfolio the ID of the portfolio whose held securities are checked
   * @return a set of dates representing combined holiday adjustments for the portfolio’s active holdings
   */
  //@formatter:on
  @Query(nativeQuery = true)
  Set<Date> getCombinedHolidayOfHoldingsByPortfolio(Integer idPortfolio);

  //@formatter:off
  /**
   * Retrieves the IDs of all securities currently held by the specified tenant.
   * <p>
   * Queries the `hold_securityaccount_security` table for records where:
   * <ul>
   *   <li>`id_tenant` matches the given tenant</li>
   *   <li>`to_hold_date` is NULL, indicating an active hold</li>
   * </ul>
   *
   * @param idTenant the tenant ID whose active security holdings are to be fetched
   * @return a list of securitycurrency IDs representing securities still held by the tenant
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<Integer> getIdSecurityByIdTenantWithHoldings(Integer idTenant);

  //@formatter:off
  /**
   * Executes the `holdSecuritySplitTransaction` stored procedure to produce a unified, chronological list
   * of buy/sell transactions and split events for the specified security—excluding any margin trades.
   * <p>
   * Each returned projection includes:
   * <ul>
   *   <li><strong>Tenant and portfolio IDs</strong> (getIdTenant, getIdPortfolio)</li>
   *   <li><strong>Security-account ID</strong> (getIdSecurityaccount)</li>
   *   <li><strong>Event timestamp</strong> (getTsDate) – transaction time or split date</li>
   *   <li><strong>Computed factor units</strong> (getFactorUnits) – total units for buys/sells or split ratio</li>
   *   <li><strong>Tenant and portfolio currency codes</strong> (getTenantCurrency, getPorfolioCurrency)</li>
   *   <li><strong>Margin-transaction ID</strong> (getIdTransactionMargin) – always null, since margin trades are omitted</li>
   * </ul>
   *
   * @param idSecurity the identifier of the security whose buy/sell and split events to fetch
   * @return a list of {@link IHoldSecuritySplitTransactionBySecurity} projections containing
   *         transaction and split details in ascending timestamp order
   */
  //@formatter:on
  @Query(value = "CALL holdSecuritySplitTransaction(:idSecurity);", nativeQuery = true)
  List<IHoldSecuritySplitTransactionBySecurity> getHoldSecuritySplitTransactionBySecurity(
      @Param("idSecurity") Integer idSecurity);

  //@formatter:off
  /**
   * Executes the `holdSecuritySplitMarginTransaction` stored procedure to produce a unified,
   * chronological sequence of margin transactions and split events for a given security.
   * <p>
   * Each returned projection includes:
   * <ul>
   *   <li><strong>Tenant and portfolio identifiers</strong> (getIdTenant, getIdPortfolio)</li>
   *   <li><strong>Security account identifier</strong> (getIdSecurityaccount)</li>
   *   <li><strong>Event timestamp</strong> (getTsDate) – transaction time or split date</li>
   *   <li><strong>Computed factor units</strong> (getFactorUnits) – ±(units × value) for margin trades or split ratio for splits</li>
   *   <li><strong>Tenant and portfolio currency codes</strong> (getTenantCurrency, getPorfolioCurrency)</li>
   *   <li><strong>Margin transaction ID</strong> (getIdTransactionMargin), present only for margin trades</li>
   * </ul>
   *
   * @param idSecurity the identifier of the security for which to fetch margin and split events
   * @return a list of {@link IHoldSecuritySplitTransactionBySecurity} projections containing
   *         detailed event and currency information
   */
  //@formatter:on
  @Query(value = "CALL holdSecuritySplitMarginTransaction(:idSecurity);", nativeQuery = true)
  List<IHoldSecuritySplitTransactionBySecurity> getHoldSecuritySplitMarginTransactionBySecurity(
      @Param("idSecurity") Integer idSecurity);

  //@formatter:off
  /**
   * Projection interface for buy/sell transaction events and security split events
   * associated with a specific security cash account and security.
   * <p>
   * Maps to a native SQL query that unifies:
   * <ul>
   *   <li>ACCUMULATE (buy) and REDUCE (sell) transactions, with factorUnits = ±(units * assetInvestmentValue_2)</li>
   *   <li>Security split events, with factorUnits = (to_factor / from_factor)</li>
   * </ul>
   * Results are ordered by the event timestamp (tsDate).
   */
  //@formatter:off
  public static interface ITransactionSecuritySplit {

      /**
       * The unique identifier of the transaction event.
       * <p>If the record is a split event, this value will be null.
       *
       * @return the transaction ID or null for split events
       */
      Integer getIdTransaction();

      /**
       * The ID of the Security entity associated with this event.
       *
       * @return the securitycurrency ID (always refers to a Security)
       */
      Integer getIdSecuritycurrency();

      /**
       * The timestamp of the event.
       * <p>For transactions, this corresponds to transaction_time; for splits, split_date.
       *
       * @return the event date and time as LocalDateTime
       */
      LocalDateTime getTsDate();

      /**
       * Computed units factor for the event.
       * <ul>
       *   <li>For transactions: ±(units * assetInvestmentValue_2), positive for buys, negative for sells.</li>
       *   <li>For splits: split ratio (to_factor / from_factor).</li>
       * </ul>
       *
       * @return the factor units for this event
       */
      Double getFactorUnits();

      /**
       * The identifier of the margin transaction, when applicable.
       * <p>Only populated for margin-related transactions; null for split events.
       *
       * @return the margin transaction ID or null if not applicable
       */
      Integer getIdTransactionMargin();

      /**
       * The currency code of the underlying security for transaction events.
       * <p>Null for split-only events.
       *
       * @return the ISO currency code or null if not applicable
       */
      String getCurrency();

  }


  /**
   * Projection interface representing a missing end-of-day quote for a security on a specific trading date.<p>
   * Corresponds to rows returned by the native query in
   * {@link #getMissingQuotesForSecurityByTenantAndPeriod(Integer, LocalDate, LocalDate)},
   * which identifies trading dates where no Historyquote record exists for securities held by a tenant.
   */
  public static interface DateSecurityQuoteMissing {

      /**
       * The trading day on which a quote is missing.
       *
       * @return the missing trading date
       */
      LocalDate getTradingDate();

      /**
       * The identifier of the securitycurrency for which the quote is missing.
       *
       * @return the securitycurrency ID
       */
      Integer getIdSecuritycurrency();
  }

  public static class TransactionSecuritySplit implements ITransactionSecuritySplit {

    private Integer idTransaction;

    private Integer idSecuritycurrency;

    private LocalDateTime tsDate;

    /**
     * When transaction: Units or with margin instrument units multiply by value per
     * point.</br>
     * When split: Spit factor
       */
    private Double factorUnits;

    private Integer idTransactionMargin;

    private String currency;

    public TransactionSecuritySplit(Integer idTransaction, Integer idSecuritycurrency, LocalDateTime tsDate,
        Double factorUnits, Integer idTransactionMargin, String currency) {
      this.idTransaction = idTransaction;
      this.idSecuritycurrency = idSecuritycurrency;
      this.tsDate = tsDate;
      this.factorUnits = factorUnits;
      this.idTransactionMargin = idTransactionMargin;
      this.currency = currency;
    }

    @Override
    public Integer getIdTransaction() {
      return idTransaction;
    }

    @Override
    public Integer getIdSecuritycurrency() {
      return idSecuritycurrency;
    }

    @Override
    public LocalDateTime getTsDate() {
      return tsDate;
    }

    @Override
    public Double getFactorUnits() {
      return factorUnits;
    }

    @Override
    public Integer getIdTransactionMargin() {
      return idTransactionMargin;
    }

    @Override
    public String getCurrency() {
      return currency;
    }

  }

  /**
   * Projection interface for results returned by the stored procedure
   * {@code holdSecuritySplitMarginTransaction} for a given security currency.
   * <p>
   * The procedure consolidates both margin transactions (buys/sells on margin instruments)
   * and security split events into a unified sequence of events per tenant,
   * portfolio, and security account, each with computed factor units.
   */
  public static interface IHoldSecuritySplitTransactionBySecurity {

    /**
     * The ID of the tenant owning the security and account.
     *
     * @return tenant identifier
     */
    Integer getIdTenant();

    /**
     * The ID of the portfolio associated with the event.
     *
     * @return portfolio identifier
     */
    Integer getIdPortfolio();

    /**
     * The ID of the security account in which the event occurred.
     *
     * @return security account identifier
     */
    Integer getIdSecurityaccount();

    /**
     * The timestamp of the event.
     * <p>
     * For margin transactions, this corresponds to the transaction time;
     * for split events, the split execution date/time.
     *
     * @return event date and time as LocalDateTime
     */
    LocalDateTime getTsDate();

    /**
     * Computed units factor for the event.
     * <ul>
     *   <li>For margin transactions: units * asset_investment_value_2, with sign indicating buy vs. sell.</li>
     *   <li>For split events: ratio of to_factor over from_factor.</li>
     * </ul>
     *
     * @return calculated factor units
     */
    Double getFactorUnits();

    /**
     * The currency code of the tenant's base currency.
     *
     * @return ISO currency code for tenant
     */
    String getTenantCurrency();

    /**
     * The currency code of the portfolio’s base currency.
     *
     * @return ISO currency code for portfolio
     */
    String getPorfolioCurrency();

    /**
     * The ID of the underlying margin transaction, if applicable.
     * <p>
     * Populated only for margin transactions; null for split events.
     *
     * @return margin transaction ID, or null if not applicable
     */
    Integer getIdTransactionMargin();

  }


}
