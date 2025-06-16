package grafioschtrader.repository;

import java.util.concurrent.ExecutionException;

import grafioschtrader.dto.MissingQuotesWithSecurities;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Transaction;

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
   * Adjust security holding for a single transaction.
   * 
   * <p>
   * This method provides efficient incremental updates when a securities transaction is added, modified, or removed. It
   * identifies the affected time period and recalculates only the necessary holding records from that point forward,
   * preserving earlier holdings that remain valid.
   * </p>
   * 
   * <p>
   * <strong>Incremental Processing:</strong>
   * </p>
   * <p>
   * The method determines the impact date from the transaction and:
   * </p>
   * <ul>
   * <li>Preserves holdings before the transaction date</li>
   * <li>Removes and recalculates holdings from the transaction date onward</li>
   * <li>Updates quantity, cost basis, and valuation calculations</li>
   * <li>Maintains proper time-frame continuity</li>
   * </ul>
   * 
   * @param securityaccount the security account containing the affected security
   * @param transaction     the transaction that triggered the holding adjustment
   * @param isAdded         true if the transaction was added, false if removed or modified
   */
  void adjustSecurityHoldingForSecurityaccountAndSecurity(Securityaccount securityaccount, Transaction transaction,
      boolean isAdded);

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
