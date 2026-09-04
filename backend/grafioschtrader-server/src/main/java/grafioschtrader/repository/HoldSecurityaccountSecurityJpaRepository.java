package grafioschtrader.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import grafiosch.common.UpdateQuery;
import grafioschtrader.dto.HoldConsistencyDefect;
import grafioschtrader.entities.HoldSecurityaccountSecurity;
import grafioschtrader.entities.HoldSecurityaccountSecurityKey;
import grafioschtrader.reportviews.performance.IPeriodHolding;

public interface HoldSecurityaccountSecurityJpaRepository
    extends JpaRepository<HoldSecurityaccountSecurity, HoldSecurityaccountSecurityKey>,
    HoldSecurityaccountSecurityJpaRepositoryCustom {

  @UpdateQuery(value = "DELETE FROM hold_securityaccount_security WHERE id_securitycash_account = ?1", nativeQuery = true)
  void removeAllByIdSecuritycashAccount(Integer idSecuritycashAccount);

  //@formatter:off
  /**
   * Retrieves all open security positions for a tenant at a given reference date.
   * A position is considered open when the reference date falls within the hold period
   * (from_hold_date &le; refDate and to_hold_date is null or &ge; refDate) and the holdings are non-zero.
   *
   * @param idTenant the tenant ID
   * @param refDate  the reference date at which to evaluate positions
   * @return a list of holding records with non-zero positions active at the reference date
   */
  //@formatter:on
  @Query("""
      SELECT hss FROM HoldSecurityaccountSecurity hss
      WHERE hss.idTenant = :idTenant
        AND hss.hssk.fromHoldDate <= :refDate
        AND (hss.toHoldDate IS NULL OR hss.toHoldDate >= :refDate)
        AND hss.hodlings <> 0""")
  List<HoldSecurityaccountSecurity> findOpenPositionsAtDate(@Param("idTenant") Integer idTenant,
      @Param("refDate") LocalDate refDate);

  void deleteByHsskIdSecuritycashAccountAndHsskIdSecuritycurrency(Integer idSecuritycashAccount,
      Integer idSecuritycurrency);

  //@formatter:off
  /**
   * Counts, per tenant and kind, the {@code hold_securityaccount_security} rows that disagree with the ACCUMULATE and
   * REDUCE transactions and the splits they are derived from. Read-only; nothing is repaired.
   * <p>
   * Checked are: periods starting on a date that is neither a transaction nor a split ({@code ORPHAN_DATE}), every row's
   * {@code holdings} against the split-adjusted signed unit sum as of that row's own {@code from_hold_date}
   * ({@code ROW_HOLDINGS}), period chaining, the denormalised tenant/portfolio ids and the two denormalised currency
   * pair ids. The complete expected row set is
   * <em>not</em> predicted: a row is only written while the position is non-zero and same-day margin transactions
   * collapse into one, so an absent row cannot be distinguished from a legitimately closed position in SQL.
   * {@code margin_real_holdings}, {@code margin_average_price} and {@code split_price_factor} are out of scope because
   * they come from the {@code holdSecuritySplit*} stored procedures.
   * <p>
   * {@code ROW_HOLDINGS} is evaluated per row rather than on the newest row alone, because the newest row is not the
   * final state: when a position is sold off completely the writer emits no zero-holdings row, it only closes the last
   * one with a {@code to_hold_date}. Comparing that row against the final unit sum would report every closed position
   * as a defect. Evaluating each row as of its own start date is correct by construction and covers every row instead
   * of one per position.
   * <p>
   * For the same reason {@code PERIOD_CHAIN} does not require a gapless chain ending in an open period. A closed
   * position legitimately leaves a closed last row, and a position that is repurchased later legitimately leaves a gap
   * between {@code to_hold_date} and the next {@code from_hold_date}. Only genuine breakages are reported: a row that
   * has a successor while being still open, and a period that overlaps its successor.
   * <p>
   * {@code INVERTED_PERIOD} reports a row whose {@code to_hold_date} lies before its {@code from_hold_date}. Such a row
   * is invisible to every {@code from <= date AND date <= to} query while still carrying holdings, so it is reported on
   * its own and excluded from {@code ROW_HOLDINGS}, where the per-row comparison would be meaningless.
   * <p>
   * Every date comparison is made on {@code tt_date}, never on the date part of {@code transaction_time}, and matches
   * how the rebuild keys the periods. {@code transaction_time} is a TIMESTAMP column and is rendered in the time zone
   * of the database session, so it shifts when the database is served from a host in another zone, while {@code tt_date}
   * is frozen when the row is written.
   * <p>
   * The split adjustment multiplies each transaction by the product of {@code to_factor / from_factor} of every split
   * between it and the row date, computed as {@code EXP(SUM(LOG(...)))}; the factors are ratios of positive integers,
   * so the tolerance is applied relative to the expected holdings to absorb the floating point error.
   * <p>
   * {@code CURRENCYPAIR} compares the two denormalised currency pair ids against the pair the writer resolves for the
   * row: the currency of the position converted into the tenant currency and into the portfolio currency, and
   * {@code NULL} where the two currencies are equal. The comparison uses {@code <=>}, and a currency pair row that does
   * not exist at all leaves the expected side {@code NULL}, so a dangling id is reported as well. Neither column has a
   * foreign key, and the reporting queries join the historical rates through them, so a wrong id would otherwise
   * convert at the wrong rate without any complaint.
   * <p>
   * Named query: HoldSecurityaccountSecurity.countConsistencyDefects
   *
   * @param tolerance relative tolerance for the holdings comparison, e.g. 0.000001
   * @return one row per tenant and defect kind, empty when everything agrees
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<HoldConsistencyDefect> countConsistencyDefects(double tolerance);

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
   *
   * <p>
   * A day is only reported when it can be valued completely. Beside {@code countQuotes = countSecurities}, which drops
   * a day on which a held security has no price, the query counts every hold row whose currency pair is set but carries
   * no rate for that day, in {@code missingFxSecurities} for the security positions and in {@code missingFxCash} for
   * the cash balances, and the two {@code HAVING} clauses remove such a day as well. The
   * {@code IFNULL(hqc0.close, 1)} and {@code IFNULL(hqc1.close, 1)} of the conversion are therefore not a silent
   * fallback: they apply the factor one only to a hold row that has no currency pair at all, because it is already held
   * in the reporting currency. Neither the {@code IFNULL} nor the two counters may be removed on their own - without
   * the counters a missing rate would add the foreign currency amount unconverted, without the {@code IFNULL} every
   * position already held in the reporting currency would become NULL.
   * </p>
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
   *
   * <p>
   * A day is only reported when it can be valued completely. Beside {@code countQuotes = countSecurities}, which drops
   * a day on which a held security has no price, the query counts every hold row whose currency pair is set but carries
   * no rate for that day, in {@code missingFxSecurities} for the security positions and in {@code missingFxCash} for
   * the cash balances, and the two {@code HAVING} clauses remove such a day as well. The
   * {@code IFNULL(hqc0.close, 1)} and {@code IFNULL(hqc1.close, 1)} of the conversion are therefore not a silent
   * fallback: they apply the factor one only to a hold row that has no currency pair at all, because it is already held
   * in the reporting currency. Neither the {@code IFNULL} nor the two counters may be removed on their own - without
   * the counters a missing rate would add the foreign currency amount unconverted, without the {@code IFNULL} every
   * position already held in the reporting currency would become NULL.
   * </p>
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
   * Determines the zero base date of a tenant, that is the last trading day before the very first security position was
   * opened. The period performance report treats its start date as an excluded baseline, so only a day on which nothing
   * was invested yet yields amounts that start at zero. On the first hold date itself the opening purchase is already
   * included, which would silently move the result of that day into the baseline.
   *
   * Named query: HoldSecurityaccountSecurity.getZeroBaseDateByTenant
   * Parameters in SQL:
   * - ?1 - the tenant, used both for the first hold date and for the cash account coverage check
   *
   * The candidate is taken from trading_days_plus, so it is never a weekend and never a global holiday. It additionally
   * has to be covered by a hold_cashaccount_balance row, otherwise no baseline row could be produced for it.
   *
   * @param idTenant the ID of the tenant
   * @return the last trading day before the first holding, or null when the tenant has no holdings at all or its cash
   *         accounts only start on or after the first hold date
   */
  //@formatter:on
  @Query(nativeQuery = true)
  LocalDate getZeroBaseDateByTenant(Integer idTenant);

  //@formatter:off
  /**
   * Determines the zero base date of a single portfolio. Behaves exactly like
   * {@link #getZeroBaseDateByTenant(Integer)} but scopes both the first hold date and the cash account coverage check
   * to one portfolio.
   *
   * Named query: HoldSecurityaccountSecurity.getZeroBaseDateByPortfolio
   * Parameters in SQL:
   * - ?1 - the portfolio, used both for the first hold date and for the cash account coverage check
   *
   * @param idPortfolio the ID of the portfolio
   * @return the last trading day before the first holding of this portfolio, or null when there is none
   */
  //@formatter:on
  @Query(nativeQuery = true)
  LocalDate getZeroBaseDateByPortfolio(Integer idPortfolio);

  //@formatter:off
  /**
   * Produces the single baseline row of a tenant for the zero base date returned by
   * {@link #getZeroBaseDateByTenant(Integer)}. Since no security was held on that day, the whole securities leg is a
   * constant zero and the net gain collapses to the cash balance minus the external cash transfers. Cash balances,
   * dividends, interest and fees are read from hold_cashaccount_balance and converted with the closing rate of that day
   * exactly like {@link #getPeriodHoldingsByTenant(Integer, LocalDate, LocalDate)} does, so the row can be prepended to
   * that result without a unit break.
   *
   * <p>
   * The baseline row is only delivered when every cash balance of that day can be converted. The query counts the hold
   * rows whose currency pair is set but has no rate in {@code missingFxCash} and suppresses the row when the counter is
   * not zero, so a foreign currency balance never enters the baseline unconverted. The {@code IFNULL(hqc1.close, 1)} of
   * the conversion therefore applies the factor one only to a cash account that has no currency pair because it is
   * already held in the reporting currency.
   * </p>
   *
   * Named query: HoldSecurityaccountSecurity.getPeriodHoldingZeroBaseByTenant
   * Parameters in SQL:
   * - ?1 - the tenant
   * - ?2 - the zero base date, used as the reported date and as the reference date of all hold periods
   *
   * @param idTenant     the ID of the tenant
   * @param zeroBaseDate the trading day the baseline is calculated for
   * @return a list with exactly one IPeriodHolding projection, or an empty list when no cash account of the tenant
   *         covers the given date
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<IPeriodHolding> getPeriodHoldingZeroBaseByTenant(Integer idTenant, LocalDate zeroBaseDate);

  //@formatter:off
  /**
   * Produces the single baseline row of one portfolio. Behaves exactly like
   * {@link #getPeriodHoldingZeroBaseByTenant(Integer, LocalDate)} but uses the portfolio currency for the conversion
   * and the portfolio share of the external cash transfers.
   *
   * Named query: HoldSecurityaccountSecurity.getPeriodHoldingZeroBaseByPortfolio
   * Parameters in SQL:
   * - ?1 - the portfolio
   * - ?2 - the zero base date, used as the reported date and as the reference date of all hold periods
   *
   * @param idPortfolio  the ID of the portfolio
   * @param zeroBaseDate the trading day the baseline is calculated for
   * @return a list with exactly one IPeriodHolding projection, or an empty list when no cash account of the portfolio
   *         covers the given date
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<IPeriodHolding> getPeriodHoldingZeroBaseByPortfolio(Integer idPortfolio, LocalDate zeroBaseDate);

  //@formatter:off
  /**
   * Retrieves all trading dates and security IDs for which no end-of-day quote exists
   * for securities held by the specified tenant within the given period.
   * <ul>
   *   <li>Scans official trading days (excluding shifted days) per security’s exchange.</li>
   *   <li>Filters to securities actively held by the tenant in their security accounts.</li>
   *   <li>Excludes exchanges with no market value and securities outside their active date range.</li>
   *   <li>Identifies missing quotes where no historyquote record exists for that date.</li>
   *   <li>Reports the currency pairs of the holdings and cash balances in the same way, since a day without an
   *       exchange rate into the tenant currency is just as unusable as one without a security quote.</li>
   * </ul>
   *
   * @param idTenant the tenant ID to scope held securities
   * @param dateFrom the start date of the period (inclusive)
   * @param dateTo   the end date of the period (inclusive)
   * @return a list of DateSecurityQuoteMissing projections containing tradingDate and idSecuritycurrency, where the
   *         id addresses either a security or a currency pair
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
      LocalDate transactinDate);

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
   * - Selects dates where no Historyquote record exists for the security on that trading day.<br>
   * - Adds the days on which a currency pair used to convert a holding or a cash balance into the tenant currency has
   *   no rate, because such a day cannot be valued either and would otherwise be converted at a rate of one.
   *
   * @param idTenant the ID of the tenant whose held securities are checked
   * @return a set of trading dates with missing quotes across all held securities and their currency pairs
   */
  //@formatter:on
  @Query(nativeQuery = true)
  Set<LocalDate> getMissingsQuoteDaysByTenant(Integer idTenant);

  //@formatter:off
  /**
   * Retrieves all unique trading dates on which at least one security held in the specified portfolio
   * has no end-of-day quote.
   * <p>
   * - Scans official trading days for each security’s exchange, excluding shifted days.<br>
   * - Joins to the portfolio’s held securities and filters by active hold periods.<br>
   * - Excludes exchanges without market value and securities outside their active date range.<br>
   * - Selects dates where no Historyquote record exists for a held security on that trading day.<br>
   * - Adds the days on which a currency pair used to convert a holding or a cash balance into the portfolio currency
   *   has no rate, because such a day cannot be valued either.
   *
   * @param idPortfolio the ID of the portfolio whose held securities are checked
   * @return a set of trading dates with missing quotes across all securities in the portfolio and their currency pairs
   */
  //@formatter:on
  @Query(nativeQuery = true)
  Set<LocalDate> getMissingsQuoteDaysByPortfolio(Integer idPortfolio);

  //@formatter:off
  /**
   * Returns the currency pairs referenced by the holdings of a tenant that carry no historical price at all.
   * <p>
   * Such a pair makes every single day of the affected hold period unusable, so the period performance report finds no
   * valid trading day left and has to tell the user which currency cannot be converted instead of returning nothing.
   * It is the difference between a gap in the price history, which only invalidates individual days, and a currency
   * pair that was never delivered by any connector.
   *
   * Named query: HoldSecurityaccountSecurity.getCurrencypairsWithoutAnyQuoteByTenant
   * Parameters in SQL:
   * - ?1 - the tenant, applied to both hold_securityaccount_security and hold_cashaccount_balance
   *
   * @param idTenant the ID of the tenant whose holdings are checked
   * @return the readable names of the affected currency pairs in the form FROM/TO, sorted alphabetically, empty when
   *         every referenced currency pair has at least one price
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<String> getCurrencypairsWithoutAnyQuoteByTenant(Integer idTenant);

  //@formatter:off
  /**
   * Behaves exactly like {@link #getCurrencypairsWithoutAnyQuoteByTenant(Integer)} but scopes the check to one
   * portfolio and therefore to the currency pairs into the portfolio currency.
   *
   * Named query: HoldSecurityaccountSecurity.getCurrencypairsWithoutAnyQuoteByPortfolio
   * Parameters in SQL:
   * - ?1 - the portfolio, applied to both hold_securityaccount_security and hold_cashaccount_balance
   *
   * @param idPortfolio the ID of the portfolio whose holdings are checked
   * @return the readable names of the affected currency pairs in the form FROM/TO, sorted alphabetically
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<String> getCurrencypairsWithoutAnyQuoteByPortfolio(Integer idPortfolio);

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
  Set<LocalDate> getCombinedHolidayOfHoldingsByTenant(Integer idTenant);

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
  Set<LocalDate> getCombinedHolidayOfHoldingsByPortfolio(Integer idPortfolio);

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
   *   <li><strong>Event timestamp</strong> (getTsDate) – tt_date combined with the time of day of the transaction, or
   *       the split date</li>
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
   *   <li><strong>Event timestamp</strong> (getTsDate) – tt_date combined with the time of day of the transaction, or
   *       the split date</li>
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
       * <p>For splits this is split_date at midnight. For transactions it is
       * {@code TIMESTAMP(tt_date, TIME(transaction_time))}: the <b>date</b> part comes from the business date
       * {@code tt_date} and is what the holding periods are keyed on, the <b>time</b> part only orders several events
       * of the same day so that a split is applied before the transactions of that day. The date part of
       * {@code transaction_time} must not be used — it is a TIMESTAMP column and therefore moves when the database is
       * served in another time zone.
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
     * point.<br>
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
     * For split events the split date at midnight. For margin transactions
     * {@code TIMESTAMP(tt_date, TIME(transaction_time))} — the date part comes from the business date {@code tt_date},
     * the time part only orders several events of the same day. See
     * {@link ITransactionSecuritySplit#getTsDate()} for why the date part of {@code transaction_time} must not be used.
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
