package grafioschtrader.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafiosch.common.UpdateQuery;
import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.entities.Transaction;

public interface TransactionJpaRepository extends JpaRepository<Transaction, Integer>, TransactionJpaRepositoryCustom,
    UpdateCreateJpaRepository<Transaction> {

  // TODO remove it after usage

  @Query(value = """
      SELECT t.* FROM transaction t JOIN cashaccount c ON t.id_cash_account = c.id_securitycash_account
      JOIN security s ON t.id_securitycurrency = s.id_securitycurrency
      JOIN currencypair cp ON t.id_currency_pair = cp.id_securitycurrency
      WHERE cp.from_currency = s.currency""", nativeQuery = true)
  List<Transaction> findWrongCurrencypairTransaction();

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

  /**
   * This native query for removing all transaction exists because deleteAll() is not working. Maybe because of the two
   * references to
   */
  @UpdateQuery(value = "DELETE FROM transaction", nativeQuery = true)
  void removeAllTransaction();

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

  @UpdateQuery(value = "DELETE FROM Transaction WHERE id_cash_account = ?1", nativeQuery = true)
  void deleteByCashaccount_IdSecuritycashAccount(Integer idCashaccount);

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
