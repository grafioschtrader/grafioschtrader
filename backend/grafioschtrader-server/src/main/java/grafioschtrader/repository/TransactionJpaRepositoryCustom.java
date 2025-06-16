package grafioschtrader.repository;

import java.util.List;

import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.dto.CashAccountTransfer;
import grafioschtrader.dto.ClosedMarginUnits;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.currencypair.CurrencypairWithTransaction;
import grafioschtrader.reportviews.transaction.CashaccountTransactionPosition;

/**
 * Custom repository interface for Transaction entity operations that extend beyond standard JPA functionality.
 * 
 * <p>
 * This interface provides specialized transaction operations including portfolio analysis, cash account transfers,
 * margin trading calculations, currency pair transactions, and transaction import capabilities. It handles business
 * logic for financial transaction processing within the GrafioschTrader application.
 * </p>
 * 
 * <p>
 * Key functionalities include:
 * </p>
 * <ul>
 * <li>Security account transaction retrieval with fees and interest calculations</li>
 * <li>Cash account balance calculations with transaction history</li>
 * <li>Cash account transfer operations with currency conversion</li>
 * <li>Margin trading position management and calculations</li>
 * <li>Currency pair transaction analysis for forex operations</li>
 * <li>Bulk transaction import operations for performance optimization</li>
 * </ul>
 */
public interface TransactionJpaRepositoryCustom extends BaseRepositoryCustom<Transaction> {

  /**
   * Retrieves all security account transactions for a tenant that include fees and interest payments.
   * 
   * <p>
   * This method returns transactions of types ACCUMULATE, REDUCE, DIVIDEND, and FINANCE_COST for securities within the
   * specified tenant's portfolio.
   * </p>
   * 
   * @param idTenant the unique identifier of the tenant
   * @return a list of transactions including fees and interest for all security accounts belonging to the tenant
   */
  List<Transaction> getSecurityAccountWithFeesAndIntrerestTransactionsByTenant(Integer idTenant);

  /**
   * Retrieves transaction history with running balance calculations for a specific cash account.
   * 
   * <p>
   * This method returns an array of transaction positions that include both the transaction details and the running
   * account balance after each transaction. When year is 0, all transactions are returned regardless of date.
   * </p>
   * 
   * @param idSecuritycashAccount the unique identifier of the cash account
   * @param year                  the year to filter transactions (0 for all years)
   * @param transactionTypes      array of transaction type values to include in the results
   * @return array of transaction positions with running balances, ordered chronologically
   */
  CashaccountTransactionPosition[] getTransactionsWithBalanceForCashaccount(final Integer idSecuritycashAccount,
      int year, int[] transactionTypes);

  /**
   * Deletes a transaction and its connected transaction if it's part of a transfer pair.
   * 
   * <p>
   * This method handles the deletion logic for both single transactions and connected transaction pairs (such as cash
   * account transfers). For security transactions, it validates unit integrity and adjusts holdings.
   * </p>
   * 
   * @param idTransaction the unique identifier of the transaction to delete
   * @throws SecurityException                           if the transaction doesn't belong to the current tenant
   * @throws grafiosch.exceptions.DataViolationException if deletion would violate unit integrity
   */
  void deleteSingleDoubleTransaction(Integer idTransaction);

  /**
   * Saves transaction attributes optimized for bulk import operations.
   * 
   * <p>
   * This method provides a lightweight save operation specifically designed for high-volume transaction imports. It
   * bypasses some validation and adjustment logic to improve performance during bulk data processing.
   * </p>
   * 
   * @param transaction    the transaction entity to save with updated attributes
   * @param existingEntity the existing transaction entity (if updating) or null (if creating)
   * @return the saved transaction entity with generated ID and updated timestamps
   */
  Transaction saveOnlyAttributesFormImport(final Transaction transaction, Transaction existingEntity);

  /**
   * Creates or updates a cash account transfer with currency conversion and validation.
   * 
   * <p>
   * This method handles the process of transferring funds between cash accounts, including currency conversion when
   * accounts have different currencies. It validates exchange rates, calculates amounts, and creates connected
   * withdrawal and deposit transactions.
   * </p>
   * 
   * @param cashAccountTransfer         the transfer details including withdrawal and deposit transactions
   * @param cashAccountTransferExisting the existing transfer (if updating) or null (if creating)
   * @return the created/updated cash account transfer with connected transaction IDs
   * @throws grafiosch.exceptions.DataViolationException if amounts don't match or currency validation fails
   * @throws SecurityException                           if accounts don't belong to the current tenant
   */
  CashAccountTransfer updateCreateCashaccountTransfer(CashAccountTransfer cashAccountTransfer,
      CashAccountTransfer cashAccountTransferExisting);

  /**
   * Retrieves currency pair transaction data for analysis and charting.
   * 
   * <p>
   * This method returns transaction data for a specific currency pair, including the currency pair details, related
   * transactions. When forChart is true, it also includes reverse currency pair data.
   * </p>
   * 
   * @param idTenant       the tenant ID to filter transactions
   * @param idCurrencypair the unique identifier of the currency pair
   * @param forChart       if true, includes reverse currency pair data for comprehensive charting
   * @return currency pair transaction data with calculated metrics
   */
  CurrencypairWithTransaction getTransactionForCurrencyPair(Integer idTenant, Integer idCurrencypair, boolean forChart);

  /**
   * Retrieves all transactions for a specific portfolio with security validation.
   * 
   * <p>
   * This method returns all transactions (both cash and security) for a given portfolio, with proper tenant validation
   * to ensure data security.
   * </p>
   * 
   * @param idPortfolio the unique identifier of the portfolio
   * @param idTenant    the tenant ID for security validation
   * @return list of all transactions within the specified portfolio, ordered by transaction time
   * @throws SecurityException if the portfolio doesn't belong to the specified tenant
   */
  List<Transaction> getTransactionsByIdPortfolio(Integer idPortfolio, Integer idTenant);

  /**
   * Calculates the available units for a specific margin trading position. In addition, return whether an existing
   * opening position already has a position referencing it.
   * 
   * <p>
   * This method analyzes margin trading transactions to determine how many units are available for a given position. It
   * examines all connected transactions (ACCUMULATE and REDUCE types) linked to the specified transaction ID.
   * </p>
   * 
   * @param idTransaction the unique identifier of the margin position transaction
   * @return available margin units data including position status and available units count
   */
  ClosedMarginUnits getClosedMarginUnitsByIdTransaction(final Integer idTransaction);

}
