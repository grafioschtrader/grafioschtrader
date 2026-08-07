package grafioschtrader.repository;

import java.util.concurrent.ExecutionException;

import grafioschtrader.dto.MissingQuotesWithSecurities;
import grafioschtrader.entities.Securityaccount;

/**
 * Custom repository interface for managing security holdings and time-frame calculations.
 * 
 * <p>
 * This interface provides methods for creating and maintaining security holding records that track buy/sell
 * transactions, corporate actions, and position changes over time. The holdings are organized as time periods to enable
 * efficient performance calculations and historical analysis of security positions.
 * </p>
 * 
 * <p>
 * <strong>Holdings Management:</strong>
 * </p>
 * <p>
 * Security holdings track quantity changes, cost basis adjustments, and valuation updates for individual securities
 * within security accounts. Each holding period represents a time span during which the position remained stable.
 * </p>
 * 
 * <p>
 * <strong>Transaction Integration:</strong>
 * </p>
 * <p>
 * Holdings are automatically updated when securities transactions occur, including purchases, sales, splits, dividends,
 * and other corporate actions that affect position quantities or cost basis.
 * </p>
 */
public interface HoldSecurityaccountSecurityJpaRepositoryCustom {

  /**
   * Creates complete security holdings for all tenants in the system.
   * 
   * <p>
   * This method performs a full rebuild of security holdings time-frames for every tenant. It processes all securities
   * transactions across all security accounts and creates holding periods that accurately represent position changes
   * over time.
   * </p>
   */
  void createSecurityHoldingsEntireForAllTenant();

  /**
   * Security holdings are completely rebuild for a tenant.
   * 
   * <p>
   * This method rebuilds all security holdings time-frames for the specified tenant, processing all historical
   * securities transactions and creating accurate holding periods. It removes existing holdings for the tenant before
   * recalculating from scratch.
   * </p>
   * 
   * <p>
   * <strong>Transaction Processing:</strong>
   * </p>
   * <p>
   * The method processes various transaction types including:
   * </p>
   * <ul>
   * <li>Buy and sell transactions</li>
   * <li>Stock splits and stock dividends</li>
   * </ul>
   * 
   * @param idTenant the tenant identifier for which to rebuild security holdings
   */
  void createSecurityHoldingsEntireByTenant(Integer idTenant);

  /**
   * Rebuilds the holdings series of one security in one security account from what is stored.
   *
   * <p>
   * This is the incremental counterpart of the tenant-wide rebuild: it is called after every create, update and delete
   * of an ACCUMULATE or REDUCE transaction, and it touches only the affected series. A transaction that was moved to
   * another security account or another security needs two calls — one for the series it moved <em>to</em> and one for
   * the series it moved <em>away from</em>.
   * </p>
   *
   * <p>
   * <strong>Call it only after the triggering write has been flushed.</strong> The series is dropped and recreated
   * purely from the database, on the connection of the current database transaction, so the flushed state is what
   * decides the result. Nothing is merged in from memory.
   * </p>
   *
   * @param securityaccount    the security account whose series is rebuilt
   * @param idSecuritycurrency the security whose series is rebuilt
   */
  void rebuildHoldingsForSecurityaccountAndSecurity(Securityaccount securityaccount, Integer idSecuritycurrency);

  /**
   * Rebuilds holdings for a specific security across all tenants and accounts.
   * 
   * <p>
   * This method is used when security-level changes occur that affect all holdings of that security, such as:
   * </p>
   * <ul>
   * <li>Stock splits or stock dividends</li>
   * <li>Historical price data corrections</li>
   * </ul>
   * 
   * <p>
   * <strong>Scope:</strong>
   * </p>
   * <p>
   * Unlike tenant-specific rebuilds, this method processes all holdings of the specified security across the entire
   * system, ensuring consistency when security-level changes affect multiple accounts and tenants.
   * </p>
   * 
   * @param idSecuritycurrency the security identifier for which to rebuild holdings
   */
  void rebuildHoldingsForSecurity(Integer idSecuritycurrency);

  /**
   * Identifies securities with missing historical quotes for performance calculations.
   * 
   * <p>
   * This method analyzes security holdings for a specific year and identifies which securities are missing historical
   * price data needed for accurate performance analysis and valuation calculations.
   * </p>
   * 
   * <p>
   * <strong>Analysis Scope:</strong>
   * </p>
   * <p>
   * The method examines:
   * </p>
   * <ul>
   * <li>Securities held during the specified year</li>
   * <li>Required trading days for complete analysis</li>
   * <li>Available historical quote data</li>
   * <li>Gaps in price history that affect calculations</li>
   * </ul>
   * 
   * 
   * @param year the year for which to analyze missing quotes
   * @return detailed information about securities with missing quote data
   * @throws InterruptedException if concurrent processing is interrupted
   * @throws ExecutionException   if an error occurs during concurrent analysis
   */
  MissingQuotesWithSecurities getMissingQuotesWithSecurities(Integer year)
      throws InterruptedException, ExecutionException;
}
