package grafioschtrader.repository;

import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.helper.TransactionPreImage;

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
   * <li>Preserves holdings before the impact date</li>
   * <li>Removes and recalculates holdings from the impact date onward</li>
   * <li>Applies currency conversion using current exchange rates</li>
   * <li>Updates related portfolio and tenant currency calculations</li>
   * </ul>
   *
   * <p>
   * <strong>Why the pre-image matters:</strong> the stored rows are cumulative running totals, so the impact date is the
   * <em>earlier</em> of the transaction's former and current date. Recalculating only from the new date would leave
   * every row in between carrying the old amount forever. Likewise, a transaction moved to another cash account leaves
   * the former account untouched unless that account is recalculated too.
   * </p>
   *
   * @param transaction the transaction that triggered the balance adjustment, already persisted or already deleted
   * @param preImage    where the transaction was booked before the update, or {@code null} for a create or a delete
   *                    (see {@link grafioschtrader.repository.helper.TransactionPreImage})
   */
  void adjustCashaccountBalance(Transaction transaction, TransactionPreImage preImage);
}
