package grafioschtrader.repository;

import java.util.List;
import java.util.Map;

import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.Security;
import grafioschtrader.platformimport.CombineTemplateAndImpTransPos;
import grafioschtrader.repository.ImportTransactionPosJpaRepositoryImpl.SavedImpPosAndTransaction;

/**
 * Custom repository interface for advanced import transaction position operations and transaction lifecycle management.
 * 
 * <p>
 * This interface extends the standard JPA repository functionality to provide sophisticated import transaction position
 * handling including batch operations, data validation, automatic corrections, and conversion to actual financial
 * transactions. It serves as the core business logic layer for processing imported transaction data from various
 * sources (CSV, PDF) into validated, ready-to-use transaction records.
 * </p>
 */
public interface ImportTransactionPosJpaRepositoryCustom {

  /**
   * Retrieves import transaction positions combined with their associated templates for a given transaction header.
   * This method provides a comprehensive view of the import status, showing both the imported data and the templates
   * used for parsing, along with any potential transaction matches.
   * 
   * <p>
   * The method also automatically sets potential transaction matches (maybe transactions) for positions that may
   * duplicate existing transactions in the system, helping users identify potential duplicates before creating new
   * transaction records.
   * </p>
   * 
   * @param idTransactionHead The ID of the transaction header to retrieve positions for
   * @return A list of combined template and import position data, including:
   *         <ul>
   *         <li>Import transaction position with calculated differences</li>
   *         <li>Associated import template (if any)</li>
   *         <li>Potential duplicate transaction references</li>
   *         <li>Validation status and readiness indicators</li>
   *         </ul>
   */
  List<CombineTemplateAndImpTransPos> getCombineTemplateAndImpTransPosListByTransactionHead(Integer idTransactionHead);

  /**
   * Assigns a security instrument to multiple import transaction positions in a single batch operation. This method
   * validates the security assignment, removes conflicting flags, and updates the readiness status for all affected
   * positions.
   * 
   * <p>
   * Security assignment is a critical step in the import process as it resolves instrument ambiguity and enables
   * accurate transaction processing. The method automatically handles security currency validation and removes related
   * error flags when appropriate.
   * </p>
   * 
   * @param idSecuritycurrency   The ID of the security to assign to the positions
   * @param idTransactionPosList List of import position IDs to update
   * @return Updated list of import transaction positions with assigned security and recalculated readiness status
   * @throws SecurityException if the user lacks permission to access the security or positions
   */
  List<ImportTransactionPos> setSecurity(Integer idSecuritycurrency, List<Integer> idTransactionPosList);

  /**
   * Assigns a cash account to multiple import transaction positions in a batch operation. This method ensures the cash
   * account belongs to the same portfolio and updates the transaction readiness status for all affected positions.
   * 
   * <p>
   * Cash account assignment is essential for proper transaction categorization and portfolio accounting. The method
   * validates currency compatibility and portfolio ownership before assignment.
   * </p>
   * 
   * @param idSecuritycashAccount The ID of the cash account to assign
   * @param idTransactionPosList  List of import position IDs to update
   * @return Updated list of import transaction positions with assigned cash account
   * @throws SecurityException if the user lacks permission to access the cash account or positions
   */
  List<ImportTransactionPos> setCashAccount(Integer idSecuritycashAccount, List<Integer> idTransactionPosList);

  /**
   * Automatically adjusts currency exchange rates or quotations for import positions with calculation discrepancies.
   * This method resolves common import issues where exchange rates or quotations need adjustment to match the total
   * transaction amount, typically due to rounding differences or rate precision issues.
   * 
   * <p>
   * The adjustment logic:
   * </p>
   * <ul>
   * <li>Calculates the required exchange rate or quotation to achieve the reported total</li>
   * <li>Applies the adjustment only when differences are within reasonable tolerance</li>
   * <li>Updates the readiness status after successful adjustment</li>
   * </ul>
   * 
   * @param idTransactionPosList List of import position IDs requiring adjustment
   * @return Updated list of import transaction positions with corrected rates/quotations
   */
  List<ImportTransactionPos> adjustCurrencyExRateOrQuotation(List<Integer> idTransactionPosList);

  /**
   * Accepts and records total amount differences for import positions where manual reconciliation is required. This
   * method allows users to acknowledge discrepancies between calculated and reported totals, typically for transactions
   * where automatic adjustment is not appropriate.
   * 
   * <p>
   * Use cases include:
   * </p>
   * <ul>
   * <li>Transactions with detailed fee structures</li>
   * <li>Rounding differences in foreign currency transactions</li>
   * <li>Platform-specific calculation methods</li>
   * <li>Historical data with incomplete information</li>
   * </ul>
   * 
   * @param idTransactionPosList List of import position IDs with accepted differences
   * @return Updated list of import transaction positions marked as ready despite differences
   */
  List<ImportTransactionPos> acceptTotalDiff(List<Integer> idTransactionPosList);

  /**
   * Deletes multiple import transaction positions in a single batch operation with security validation. This method
   * ensures only positions belonging to the authenticated user's tenant are deleted, providing protection against
   * unauthorized data manipulation.
   * 
   * @param idTransactionPosList List of import position IDs to delete
   * @throws SecurityException if any position belongs to a different tenant
   */
  void deleteMultiple(List<Integer> idTransactionPosList);

  /**
   * Sets potential duplicate transaction references for multiple import positions. This method helps users identify and
   * handle potential duplicate transactions by linking import positions to existing transactions that may represent the
   * same financial activity.
   * 
   * <p>
   * Setting the maybe transaction ID to:
   * </p>
   * <ul>
   * <li><b>null:</b> No potential duplicate identified</li>
   * <li><b>0:</b> User confirmed this is not a duplicate</li>
   * <li><b>transaction ID:</b> Potential duplicate transaction reference</li>
   * </ul>
   * 
   * @param idTransactionMaybe   The transaction ID that may be a duplicate, or null/0 for no duplicate
   * @param idTransactionPosList List of import position IDs to update
   * @return Updated list of import transaction positions with duplicate references
   */
  List<ImportTransactionPos> setIdTransactionMayBe(Integer idTransactionMaybe, List<Integer> idTransactionPosList);

  /**
   * Validates and updates the readiness status for a single import transaction position. This method performs
   * comprehensive validation to determine if the position contains sufficient and valid data to create a financial
   * transaction.
   * 
   * <p>
   * Readiness criteria include:
   * </p>
   * <ul>
   * <li>Required fields populated (cash account, transaction type, amount, date)</li>
   * <li>Security assigned for security-related transactions</li>
   * <li>Amount calculations balanced (or differences accepted)</li>
   * <li>Currency and exchange rate validation</li>
   * <li>Business day adjustment for dividend payments</li>
   * </ul>
   * 
   * @param itp The import transaction position to validate and update
   */
  void setCheckReadyForSingleTransaction(ImportTransactionPos itp);

  /**
   * Automatically calculates and adds exchange rates for dividend transactions in foreign currencies. This method
   * handles the common scenario where dividends are paid in a different currency than the security's trading currency,
   * requiring automatic exchange rate lookup and application.
   * 
   * <p>
   * The method:
   * </p>
   * <ul>
   * <li>Identifies dividend transactions with currency mismatches</li>
   * <li>Looks up historical exchange rates for the transaction date</li>
   * <li>Applies rates to both dividend amounts and tax withholdings</li>
   * <li>Sets appropriate flags for exchange rate handling</li>
   * </ul>
   * 
   * @param importTransactionHead The transaction header context for the dividend
   * @param itp                   The import transaction position representing the dividend payment
   */
  void addPossibleExchangeRateForDividend(ImportTransactionHead importTransactionHead, ImportTransactionPos itp);

  /**
   * Creates actual financial transactions from validated import positions using their IDs. This method converts ready
   * import positions into permanent transaction records, performing final validation and handling connected
   * transactions (such as cash transfers).
   * 
   * @param idTransactionPosList List of import position IDs ready for transaction creation
   * @return Updated list of import transaction positions with created transaction references
   * @throws GeneralNotTranslatedWithArgumentsException if connected transactions are missing
   */
  List<ImportTransactionPos> createAndSaveTransactionsByIds(List<Integer> idTransactionPosList);

  /**
   * Creates and saves financial transactions from import transaction positions with automatic corrections. This is the
   * core method for converting validated import data into permanent transaction records, applying final corrections and
   * handling advanced scenarios like cash account transfers.
   * 
   * <p>
   * The method handles:
   * </p>
   * <ul>
   * <li>Single transactions (buys, sells, dividends, fees)</li>
   * <li>Connected transactions (cash account transfers)</li>
   * <li>Currency pair creation and exchange rate application</li>
   * <li>Bond unit and quotation adjustments</li>
   * <li>Security currency mismatch resolution</li>
   * <li>Transaction error handling and rollback</li>
   * </ul>
   * 
   * <p>
   * Processing includes automatic corrections for:
   * </p>
   * <ul>
   * <li>Bond transactions with unit=1 requiring position-based adjustment</li>
   * <li>Security currency mismatches resolved by portfolio holdings</li>
   * <li>Missing exchange rates for multi-currency transactions</li>
   * <li>Weekend date adjustments for dividend payments</li>
   * </ul>
   * 
   * @param importTransactionPosList List of validated import positions to convert
   * @param idItpMap                 Optional map of position IDs to positions for handling connected transactions
   * @return List of successfully created transaction and position pairs
   */
  List<SavedImpPosAndTransaction> createAndSaveTransactionsFromImpPos(
      List<ImportTransactionPos> importTransactionPosList, Map<Integer, ImportTransactionPos> idItpMap);

  /**
   * Resets the transaction reference for an import position when the associated transaction is deleted. This method
   * maintains data consistency by clearing import position links when their corresponding transactions are removed from
   * the system.
   *
   * @param idTransaction The ID of the deleted transaction to unlink from import positions
   */
  void setTrasactionIdToNullWhenExists(Integer idTransaction);

  /**
   * Assigns a security to all import transaction positions that match by ISIN and currency but have no security
   * assigned yet. This method is called after GTNet import successfully links or creates a security, to automatically
   * update matching import positions so they become ready for transaction creation.
   *
   * <p>
   * For each matching position:
   * <ul>
   *   <li>Assigns the security using {@link ImportTransactionPos#setSecurityRemoveFromFlag(Security)}</li>
   *   <li>Validates and updates readiness status via {@link #setCheckReadyForSingleTransaction(ImportTransactionPos)}</li>
   * </ul>
   *
   * @param security the security to assign to matching positions; if null or missing ISIN/currency, returns 0
   * @return the number of import positions that were updated
   */
  int assignSecurityToMatchingImportPositions(Security security);
}
