package grafioschtrader.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.common.UpdateQuery;
import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.dto.TradingPeriodTransactionSummary;
import grafioschtrader.entities.Transaction;

public interface TransactionJpaRepository extends JpaRepository<Transaction, Integer>, TransactionJpaRepositoryCustom,
    UpdateCreateJpaRepository<Transaction> {

  long countByIdStandingOrder(Integer idStandingOrder);

  /**
   * Counts all transactions belonging to a tenant. Used to enforce the total (lifetime) transaction limit
   * {@code gt.max.transaction} per tenant.
   *
   * @param idTenant the tenant id
   * @return the total number of transactions owned by the tenant
   */
  int countByIdTenant(Integer idTenant);

  /**
   * Counts the number of transactions per standing order for a batch of standing order IDs.
   * Used to populate the transactionCount transient field without N+1 queries.
   *
   * @param ids list of standing order IDs
   * @return list of [idStandingOrder, count] pairs
   */
  @Query("SELECT t.idStandingOrder, COUNT(t) FROM Transaction t WHERE t.idStandingOrder IN :ids GROUP BY t.idStandingOrder")
  List<Object[]> countByStandingOrderIds(@Param("ids") List<Integer> ids);

  /**
   * Retrieves all transactions created by a specific standing order, with security and cashaccount eagerly fetched.
   *
   * @param idStandingOrder the standing order ID
   * @return transactions ordered by transaction time descending
   */
  @Query("SELECT t FROM Transaction t LEFT JOIN FETCH t.security LEFT JOIN FETCH t.cashaccount "
      + "WHERE t.idStandingOrder = :idStandingOrder ORDER BY t.transactionTime DESC")
  List<Transaction> findByIdStandingOrderWithDetails(@Param("idStandingOrder") Integer idStandingOrder);

  /**
   * Returns transaction summaries grouped by (specialInvestmentInstrument, categoryType)
   * for a given security account. Used to prevent deletion or shortening of trading periods
   * that still cover existing transactions.
   *
   * @param idSecurityaccount the security account id
   * @return list of summaries with max transaction date and count per instrument/category group
   */
  @Query(value = """
      SELECT new grafioschtrader.dto.TradingPeriodTransactionSummary(
        a.specialInvestmentInstrument, a.categoryType, MAX(t.transactionDate), COUNT(t))
      FROM Transaction t JOIN t.security s JOIN s.assetClass a
      WHERE t.idSecurityaccount = :idSecurityaccount
      GROUP BY a.specialInvestmentInstrument, a.categoryType""")
  List<TradingPeriodTransactionSummary> getTransactionSummariesBySecurityaccount(
      @Param("idSecurityaccount") Integer idSecurityaccount);

 
  /**
   * Finds the latest transaction date for a specific security in a given security account, excluding system-created
   * transfer transactions. Used to validate that a new transfer date is after all existing transactions.
   *
   * @param idSecurityaccount  the security account ID
   * @param idSecuritycurrency the security ID
   * @return the latest transaction time, or empty if no non-transfer transactions exist
   */
  @Query("SELECT MAX(t.transactionTime) FROM Transaction t JOIN t.security s " +
      "WHERE t.idSecurityaccount = ?1 AND s.idSecuritycurrency = ?2 AND t.idSecurityTransfer IS NULL")
  Optional<LocalDateTime> findMaxTransactionTimeBySecurityaccountAndSecurity(
      Integer idSecurityaccount, Integer idSecuritycurrency);

  /**
   * Counts non-transfer transactions after a given date for a specific security in a security account.
   * Used to determine if a transfer can be reversed (no subsequent transactions should exist in the target account).
   *
   * @param idSecurityaccount  the security account ID
   * @param idSecuritycurrency the security ID
   * @param afterDate          the date after which to count transactions
   * @return number of non-transfer transactions after the given date
   */
  @Query("SELECT COUNT(t) FROM Transaction t JOIN t.security s " +
      "WHERE t.idSecurityaccount = ?1 AND s.idSecuritycurrency = ?2 " +
      "AND t.transactionTime > ?3 AND t.idSecurityTransfer IS NULL")
  long countTransactionsAfterDate(Integer idSecurityaccount, Integer idSecuritycurrency, LocalDateTime afterDate);

  /**
   * Finds the latest transaction time booked against the given cash account. Used to ensure an account's
   * active-until date cannot be set earlier than its most recent transaction.
   *
   * @param idCashaccount the cash account id
   * @return the latest transaction time, or empty if the account has no transactions
   */
  @Query("SELECT MAX(t.transactionTime) FROM Transaction t WHERE t.cashaccount.idSecuritycashAccount = ?1")
  Optional<LocalDateTime> findMaxTransactionTimeByCashaccount(Integer idCashaccount);

  /**
   * Finds the latest transaction time booked against the given security account. Used to ensure an account's
   * active-until date cannot be set earlier than its most recent transaction.
   *
   * @param idSecurityaccount the security account id
   * @return the latest transaction time, or empty if the account has no transactions
   */
  @Query("SELECT MAX(t.transactionTime) FROM Transaction t WHERE t.idSecurityaccount = ?1")
  Optional<LocalDateTime> findMaxTransactionTimeBySecurityaccount(Integer idSecurityaccount);

  List<Transaction> findByIdSecurityTransfer(Integer idSecurityTransfer);

  List<Transaction> findByIdSecurityActionApp(Integer idSecurityActionApp);

  /**
   * Reassigns all transactions for a given old security to a new security after a specified date.
   * Used during ISIN change (SecurityAction) to move post-action-date transactions to the new security.
   * <p>
   * The action date itself is <b>excluded</b>: the system SELL/BUY pair is priced with the closing quote of the
   * <i>old</i> security on that day, so the action date still belongs to the old security. Were it included, a user
   * transaction dated exactly on the action date would be moved to the new security and at the same time drop out of
   * the residual-units sum, so its units would never be converted by the split ratio of the action.
   * </p>
   *
   * @param idTenant       the tenant owning the transactions
   * @param oldSecurityId  the old security ID to match
   * @param newSecurityId  the new security ID to assign
   * @param appId          the SecurityActionApplication ID to tag reassigned transactions
   * @param fromDate       the action date (exclusive) after which transactions are reassigned
   * @return number of rows updated
   */
  @Transactional
  @Modifying
  @Query(value = "UPDATE transaction SET id_securitycurrency = :newSecurityId, id_security_action_app = :appId "
      + "WHERE id_tenant = :idTenant AND id_securitycurrency = :oldSecurityId "
      + "AND tt_date > :fromDate", nativeQuery = true)
  int reassignTransactionsToNewSecurity(@Param("idTenant") Integer idTenant,
      @Param("oldSecurityId") Integer oldSecurityId, @Param("newSecurityId") Integer newSecurityId,
      @Param("appId") Integer appId, @Param("fromDate") LocalDate fromDate);

  /**
   * Reverts reassigned transactions back to the old security during ISIN change reversal.
   * Only reverts non-system-created transactions (those that were bulk-reassigned, not the SELL/BUY pair).
   * <p>
   * The note is compared with the NULL-safe {@code <=>} operator, because an ordinary user transaction usually carries
   * no note at all and {@code NULL != 'System-Created'} evaluates to NULL rather than TRUE. With a plain {@code !=}
   * exactly those transactions would stay on the new security, keeping a dangling id_security_action_app.
   * </p>
   *
   * @param oldSecurityId the original security ID to restore
   * @param appId         the SecurityActionApplication ID identifying reassigned transactions
   * @return number of rows updated
   */
  @Transactional
  @Modifying
  @Query(value = "UPDATE transaction SET id_securitycurrency = :oldSecurityId, id_security_action_app = NULL "
      + "WHERE id_security_action_app = :appId AND NOT (note <=> 'System-Created')", nativeQuery = true)
  int revertReassignedTransactions(@Param("oldSecurityId") Integer oldSecurityId,
      @Param("appId") Integer appId);

  List<Transaction> findBySecurity_idSecuritycurrency(Integer idSecuritycurrency);

  /**
   * Get close or finance cost of a margin position
   */
  List<Transaction> findByIdTenantAndConnectedIdTransactionAndUnitsIsNotNull(Integer idTenant,
      Integer connectedIdTransaction);

  @Query(value = "SELECT t FROM Portfolio p JOIN p.securitycashaccountList a JOIN a.transactionList t LEFT JOIN Fetch t.security s"
      + " JOIN Fetch t.cashaccount WHERE p.idPortfolio=?1 ORDER BY t.transactionTime")
  List<Transaction> getTransactionsByIdPortfolio(Integer idPortfolio);

  // This produces less queries then without the query. Fetch t.security s makes
  // the difference.
  @Query(value = "SELECT t FROM Portfolio p JOIN p.securitycashaccountList a JOIN a.transactionList t LEFT JOIN Fetch t.security s"
      + " JOIN Fetch t.cashaccount WHERE p.idTenant=?1 ORDER BY t.transactionTime")
  List<Transaction> findByIdTenantOrderByTransactionTimeDesc(Integer idTenant);

  Transaction findByIdTransactionAndIdTenant(Integer idTransaction, Integer idTenant);

  List<Transaction> findByCashaccount_idSecuritycashAccountAndIdTenantOrderByTransactionTimeDesc(
      Integer idSecuritycashAccount, Integer idTenant);

  @Query(value = "SELECT t FROM Transaction t WHERE t.idTenant = :idTenant"
      + " AND (t.idTransaction = :idTransaction OR t.connectedIdTransaction = :idTransaction) ORDER BY t.transactionTime")
  List<Transaction> getMarginForIdTenantAndIdTransactionOrderByTransactionTime(Integer idTenant, Integer idTransaction);

  @UpdateQuery(value = "DELETE FROM transaction WHERE id_tenant = ?1 AND note = 'System-Created' AND transaction_type = 6", nativeQuery = true)
  void removeSystemCreatedDividensFromTenant(Integer idTenant);

  /**
   * Return all margin transactions for a certain security account
   */
  @Query(value = """
      SELECT t FROM Transaction t JOIN t.security s JOIN s.assetClass a WHERE t.idSecurityaccount = ?1
      AND (a.specialInvestmentInstrument = 4 OR a.categoryType = 8)""")
  List<Transaction> getMarginTransactionMapForSecurityaccount(Integer idSecurityaccount);

  /**
   * Return all margin transactions for a certain security account and security
   */
  @Query(value = """
      SELECT t FROM Transaction t JOIN t.security s JOIN s.assetClass a WHERE s.idSecuritycurrency = :idSecurity AND
      t.idSecurityaccount = :idSecurityaccount AND (a.specialInvestmentInstrument = 4 OR a.categoryType = 8)""")
  List<Transaction> getMarginTransactionMapForSecurityaccountAndSecurity(Integer idSecurityaccount, Integer idSecurity);

  /**
   * Return all margin transactions for a certain security
   */
  @Query(value = """
      SELECT t FROM Transaction t JOIN t.security s JOIN s.assetClass a
      WHERE s.idSecuritycurrency = :idSecurity AND (a.specialInvestmentInstrument = 4 OR a.categoryType = 8)""")
  List<Transaction> getMarginTransactionMapForSecurity(Integer idSecurity);

  /**
   * All external cash transfers (WITHDRAWAL = 0, DEPOSIT = 1) of one cash account, oldest first. Used to replay
   * {@link grafioschtrader.entities.HoldCashaccountDeposit} for an account from scratch.
   *
   * @param idCashaccount the cash account
   * @return the deposits and withdrawals ordered by transaction time
   */
  @Query(value = """
      SELECT t FROM Transaction t WHERE t.cashaccount.idSecuritycashAccount = ?1 AND t.transactionType <= 1
      ORDER BY t.transactionTime""")
  List<Transaction> findDepositWithdrawalByCashaccount(Integer idCashaccount);

  /**
   * The external cash transfers (WITHDRAWAL = 0, DEPOSIT = 1) of one cash account booked strictly after the given date,
   * oldest first. Used to replay {@link grafioschtrader.entities.HoldCashaccountDeposit} from a surviving hold row on
   * without re-reading the account's whole history.
   *
   * @param idCashaccount the cash account
   * @param afterDate     exclusive lower bound on {@code tt_date}
   * @return the deposits and withdrawals ordered by transaction time
   */
  @Query(value = """
      SELECT t FROM Transaction t WHERE t.cashaccount.idSecuritycashAccount = ?1 AND t.transactionType <= 1
      AND t.transactionDate > ?2 ORDER BY t.transactionTime""")
  List<Transaction> findDepositWithdrawalByCashaccountAfterDate(Integer idCashaccount, LocalDate afterDate);

  @Query(value = "SELECT t FROM Portfolio p JOIN p.securitycashaccountList a JOIN a.securityTransactionList t JOIN Fetch t.security s"
      + " JOIN Fetch t.cashaccount WHERE p.idTenant=?1 AND s.idSecuritycurrency=?2 ORDER BY t.transactionTime")
  List<Transaction> findByIdTenantAndIdSecurity(Integer idTenant, Integer idSecuritycurrency);

  @Query(value = "SELECT t FROM Transaction t WHERE t.idTenant=?1 AND (t.idTransaction = ?2 OR t.connectedIdTransaction = ?3) ORDER BY t.transactionTime")
  List<Transaction> findByIdTenantAndIdTransactionOrConnectedIdTransaction(Integer idTenant, Integer idTransaction,
      Integer connectedIdTransaction);

  @Query(value = "SELECT t FROM Portfolio p JOIN p.securitycashaccountList a JOIN a.securityTransactionList t JOIN Fetch t.security s"
      + " JOIN Fetch t.cashaccount WHERE p.idTenant=?1 AND a.idSecuritycashAccount IN ?2 AND s.idSecuritycurrency=?3 ORDER BY t.transactionTime")
  List<Transaction> findByIdTenantAndSecurityAccountsIdSecurity(Integer idTenant, List<Integer> idsSecurityaccount,
      Integer idSecuritycurrency);

  @Query(value = "SELECT t FROM Transaction t JOIN Fetch t.security s WHERE s.idSecuritycurrency = ?2 AND t.idSecurityaccount IN ?1 ORDER BY t.transactionTime")
  List<Transaction> findByIdPortfolioAndIdSecurity(List<Integer> idsSecurityaccounts, Integer idSecuritycurrency);

  @Query(value = "SELECT t FROM Transaction t JOIN t.security s WHERE t.idSecurityaccount = ?1 AND s.idSecuritycurrency = ?2 ORDER BY t.transactionTime")
  List<Transaction> findByIdSecurityaccountAndIdSecurity(Integer idSecuritycashAccount, Integer idSecuritycurrency);

  /**
   * Loads the security transactions with the given IDs for the transaction receipt PDF generation. The inner fetch
   * joins restrict the result to security transactions and eagerly load the security with its asset class and the
   * cash account, which the receipt generator accesses outside a persistence context. Transactions of other tenants
   * are silently excluded by the tenant condition.
   *
   * @param idTenant       the tenant of the authenticated user
   * @param idTransactions the IDs of the requested transactions
   * @return matching transactions ordered by transaction time
   */
  @Query(value = "SELECT t FROM Transaction t JOIN FETCH t.security s JOIN FETCH s.assetClass JOIN FETCH t.cashaccount"
      + " WHERE t.idTenant = ?1 AND t.idTransaction IN ?2 ORDER BY t.transactionTime")
  List<Transaction> findForReceiptsByIdTenantAndIdTransactionIn(Integer idTenant, List<Integer> idTransactions);

  /**
   * Loads all transactions of a tenant for the re-importable CSV export. The security with its asset class is left
   * fetch joined (cash-only transactions have no security; the asset class decides the margin marker) and the cash
   * account with its portfolio is fetch joined for the per-securities-account file grouping of cash rows. Everything
   * is loaded eagerly because the CSV generator runs outside a persistence context.
   *
   * @param idTenant the tenant of the authenticated user
   * @return all transactions of the tenant ordered by transaction time
   */
  @Query(value = "SELECT t FROM Transaction t LEFT JOIN FETCH t.security s LEFT JOIN FETCH s.assetClass"
      + " JOIN FETCH t.cashaccount c JOIN FETCH c.portfolio WHERE t.idTenant = ?1 ORDER BY t.transactionTime")
  List<Transaction> findForCsvExportByIdTenant(Integer idTenant);

  /**
   * Loads the tenant's WITHDRAWAL/DEPOSIT transactions that are not connected to a counterpart transaction. These are
   * the candidates of the cash transfer relink step that restores the pairing of transfers whose two sides were
   * imported through separate CSV files. WITHDRAWAL/DEPOSIT rows use connectedIdTransaction exclusively for transfer
   * pairing, so the type filter cannot match margin links.
   *
   * @param idTenant the tenant of the authenticated user
   * @return unconnected withdrawal/deposit transactions ordered by transaction time
   */
  @Query(value = "SELECT t FROM Transaction t JOIN FETCH t.cashaccount c JOIN FETCH c.portfolio"
      + " WHERE t.idTenant = ?1 AND t.transactionType IN (0, 1) AND t.connectedIdTransaction IS NULL"
      + " ORDER BY t.transactionTime")
  List<Transaction> findUnconnectedTransferCandidates(Integer idTenant);

  /**
   * Returns the transactions of a specific cash account over a definable period of time according to specified
   * transaction types.
   */
  @Query(value = """
      SELECT t FROM Transaction t JOIN t.cashaccount c
      WHERE c.idSecuritycashAccount= ?1 AND t.idTenant=?2 AND t.transactionDate>=?3 AND t.transactionDate<=?4 AND t.transactionType IN ?5""")
  List<Transaction> findByTenantAndCashaccountAndYearAndTransactionType(Integer idSecuritycashAccount, Integer idTenant,
      LocalDate transactionDateFrom, LocalDate transactionDateTo, int[] transactionTypes);

  /**
   * It works only for security transactions.
   */
  @Query(value = """
      SELECT t FROM Portfolio p JOIN p.securitycashaccountList a JOIN a.securityTransactionList t
      JOIN Fetch t.security s JOIN Fetch t.cashaccount WHERE p.idTenant = ?1 AND s.idSecuritycurrency = t.security.idSecuritycurrency
      AND t.transactionType >=4  AND t.transactionType <= ?2  ORDER BY t.transactionTime, s.idSecuritycurrency""")
  List<Transaction> getSecurityAccountTransactionsByTenant(Integer idTenant, Byte transactonMaxType);

  /**
   * Returns the dividend security transactions of a tenant booked within the given date range, with the security
   * eagerly fetched. Used to back-fill missing ex-dates from the Swiss ICTax tax data.
   *
   * @param idTenant            the tenant whose transactions are loaded
   * @param transactionType     the transaction type to match (the dividend type)
   * @param transactionDateFrom inclusive lower bound on the transaction date (tt_date)
   * @param transactionDateTo   inclusive upper bound on the transaction date (tt_date)
   * @return matching dividend transactions ordered by ISIN and transaction date
   */
  @Query(value = """
      SELECT t FROM Transaction t JOIN FETCH t.security s
      WHERE t.idTenant = ?1 AND t.transactionType = ?2 AND t.transactionDate >= ?3 AND t.transactionDate <= ?4
      ORDER BY s.isin, t.transactionDate""")
  List<Transaction> getDividendTransactionsByTenantAndPeriod(Integer idTenant, byte transactionType,
      LocalDate transactionDateFrom, LocalDate transactionDateTo);

  @Query(value = """
      SELECT t FROM Portfolio p JOIN p.securitycashaccountList a JOIN a.transactionList t JOIN Fetch t.cashaccount
      LEFT JOIN Fetch t.security WHERE p.idTenant=?1 AND t.idCurrencypair=?2 ORDER BY t.transactionTime""")
  List<Transaction> findByCurrencypair(Integer idTenant, Integer idCurrencypair);

  //@formatter:off
  /**
   * Retrieves a list of Transaction objects associated with the specified watchlist ID.
   *
   * This query joins Watchlist, Securitycurrency, Portfolio, Securitycashaccount, and Transaction entities.
   * It returns all transactions that match the following criteria:
   * - The portfolio belongs to the same tenant as the watchlist.
   * - The transaction's security is part of the watchlist's associated security list.
   *
   * This method is useful for displaying or analyzing transactions tied to a specific investment watchlist.
   * Results are ordered chronologically by transaction time.
   *
   * @param idWatchlist the ID of the watchlist whose related transactions should be retrieved
   * @return a list of matching Transaction records
   */
  //@formatter:on
  @Query
  List<Transaction> findByIdWatchlist(Integer idWatchlist);

  //@formatter:off
  /**
   * Retrieves all Transaction records where the associated historical exchange rate (history quote)
   * used for conversion may have been modified after the transaction was created.
   *
   * This native query detects potential inconsistencies or data integrity issues in currency conversion logic by:
   * - Selecting transactions of type WITHDRAWAL or DEPOSIT (i.e., TransactionType 0 or 1)
   * - Filtering for cases where the currency of the cash account differs from the tenant or portfolio currency
   * - Comparing the `create_modify_time` of the associated history quote with the deposit’s `valid_timestamp`
   *
   * These transactions may require reevaluation due to updated history quotes that were not yet valid at the
   * time of the transaction. This helps ensure accurate portfolio valuations over time, especially in multi-currency environments.
   *
   * Results are ordered by tenant ID, cash account ID, and transaction time.
   *
   * @return a list of Transaction records potentially affected by newer history quote data after the deposit occurred
   */
  //@formatter:on
  @Query(nativeQuery = true)
  List<Transaction> getTransactionWhyHistoryquoteYounger();

}
