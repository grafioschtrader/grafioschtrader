package grafioschtrader.repository;

import grafioschtrader.entities.Transaction;

/**
 * Custom repository interface for managing cash account balance holdings and time-frame calculations.
 * 
 * <p>
 * This interface provides methods for creating and maintaining cash account balance records that track deposits,
 * withdrawals, interest, fees, and other cash-affecting transactions over time. The holdings are organized as time
 * periods with start and end dates to enable efficient performance calculations and historical analysis.
 * </p>
 * 
 * <p>
 * <strong>Data Organization:</strong>
 * </p>
 * <p>
 * Cash account balance holdings are structured as time periods where each record represents a span during which the
 * account balance remained stable. New periods are created when transactions occur that affect the account balance.
 * </p>
 * 
 * <p>
 * <strong>Multi-Currency Support:</strong>
 * </p>
 * <p>
 * Balance calculations include currency conversion factors for both tenant and portfolio base currencies, enabling
 * consistent reporting across different currency contexts.
 * </p>
 */
public interface HoldCashaccountBalanceJpaRepositoryCustom {

  /**
   * Creates complete cash account balance holdings for all tenants in the system.
   * 
   * <p>
   * This method performs a full rebuild of cash account balance time-frames for every tenant. It processes all cash
   * transactions (deposits, withdrawals, interest, fees) and creates holding periods that accurately represent balance
   * changes over time.
   * </p>
   * 
   */
  void createCashaccountBalanceEntireForAllTenants();

  /**
   * Creates complete cash account balance holdings for a specific tenant.
   * 
   * <p>
   * This method rebuilds all cash account balance time-frames for the specified tenant, processing all historical
   * transactions and creating accurate holding periods. It removes existing holdings for the tenant before
   * recalculating from scratch.
   * </p>
   * 
   * @param idTenant the tenant identifier for which to rebuild cash account holdings
   */
  void createCashaccountBalanceEntireByTenant(Integer idTenant);

  /**
   * Adjusts cash account balance holdings incrementally based on a single transaction change.
   * 
   * <p>
   * This method provides efficient incremental updates when a transaction is added, modified, or removed. It identifies
   * the affected time period and recalculates only the necessary holding records from that point forward, preserving
   * earlier holdings that remain valid.
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
   * <li>Applies currency conversion using current exchange rates</li>
   * <li>Updates related portfolio and tenant currency calculations</li>
   * </ul>
   * 
   * @param transaction the transaction that triggered the balance adjustment
   */
  void adjustCashaccountBalanceByIdCashaccountAndFromDate(Transaction transaction);
}
