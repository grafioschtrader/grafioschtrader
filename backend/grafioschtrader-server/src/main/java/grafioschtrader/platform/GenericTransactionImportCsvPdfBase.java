package grafioschtrader.platform;

import java.util.ArrayList;
import java.util.List;

import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.platformimport.ImportProperties;
import grafioschtrader.repository.ImportTransactionPosJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.types.TransactionType;

/**
 * Abstract base class providing common transaction import processing logic for CSV, PDF, and text file formats.
 * 
 * <p>
 * This class contains shared functionality for transforming parsed transaction data into import positions, handling
 * different transaction types, managing cash account assignments, and calculating exchange rates for multi-currency
 * operations. It serves as the foundation for platform-specific import implementations while ensuring consistent
 * behavior across all supported file formats.
 * </p>
 * 
 * <h3>Transaction Type Processing</h3>
 * <p>
 * The class handles three categories of transactions:
 * </p>
 * <ul>
 * <li><b>Security Transactions</b> - Buy/sell/dividend operations (ACCUMULATE, REDUCE, DIVIDEND)</li>
 * <li><b>Account Transfers</b> - Multi-property transactions representing cash transfers between accounts</li>
 * <li><b>Cash Transactions</b> - Single-property transactions without security involvement</li>
 * </ul>
 * 
 * <h3>Cash Account Management</h3>
 * <p>
 * Automatically assigns appropriate cash accounts based on:
 * </p>
 * <ul>
 * <li>Currency matching between transaction and available accounts</li>
 * <li>Portfolio structure and account preferences</li>
 * <li>Security account linkage for consistent cash flow tracking</li>
 * </ul>
 * 
 * <h3>Multi-Currency Support</h3>
 * <p>
 * For account transfers involving different currencies:
 * </p>
 * <ul>
 * <li>Automatically detects currency pairs from transaction amounts</li>
 * <li>Calculates exchange rates based on deposit/withdrawal amounts</li>
 * <li>Links related transactions for proper accounting</li>
 * </ul>
 * 
 * <h3>Error Handling and Validation</h3>
 * <p>
 * The class ensures data integrity through:
 * </p>
 * <ul>
 * <li>Security instrument resolution and validation</li>
 * <li>Cash account assignment verification</li>
 * <li>Exchange rate calculation for currency conversions</li>
 * <li>Transaction readiness state checking before persistence</li>
 * </ul>
 */
public abstract class GenericTransactionImportCsvPdfBase {

  /** Import session container providing context for this import operation. */
  protected final ImportTransactionHead importTransactionHead;

  /** Available import templates for parsing different document formats. */
  protected List<ImportTransactionTemplate> importTransactionTemplateList;

  /**
   * Creates a new generic transaction import processor with the specified import context.
   * 
   * @param importTransactionHead         Import session container with portfolio and account information
   * @param importTransactionTemplateList Available templates for parsing transaction documents
   */
  public GenericTransactionImportCsvPdfBase(final ImportTransactionHead importTransactionHead,
      List<ImportTransactionTemplate> importTransactionTemplateList) {
    this.importTransactionHead = importTransactionHead;
    this.importTransactionTemplateList = importTransactionTemplateList;
  }

  /**
   * Assigns an appropriate cash account to the import position based on currency and portfolio structure. Selects the
   * preferred cash account that matches the transaction currency and is linked to the securities account for consistent
   * cash flow tracking.
   * 
   * @param cashaccountList      Available cash accounts in the portfolio
   * @param importTransactionPos Import position requiring cash account assignment
   */
  protected void setCashaccoutWhenPossibe(List<Cashaccount> cashaccountList,
      ImportTransactionPos importTransactionPos) {
    Cashaccount preferedCashaccount = Portfolio.getPreferedCashaccountForPorfolioBySecurityAccountAndCurrency(
        cashaccountList, importTransactionHead.getSecurityaccount().getIdSecuritycashAccount(),
        importTransactionPos.getCurrencyAccount());
    if (preferedCashaccount != null) {
      importTransactionPos.setCashaccount(preferedCashaccount);
    }
  }

  /**
   * Processes and saves successfully parsed transaction data, handling different transaction types appropriately.
   * Routes transactions to specific handlers based on type: security transactions, account transfers, or simple cash
   * transactions. Ensures proper cash account assignment and persistence for all created positions.
   * 
   * @param importTransactionTemplate         Template used for parsing the transaction data
   * @param cashaccountList                   Available cash accounts in the portfolio
   * @param importPropertiesList              Parsed transaction properties from the document
   * @param fileNameOriginal                  Original filename of the imported document
   * @param importTransactionPosJpaRepository Repository for persisting import positions
   * @param securityJpaRepository             Repository for resolving security instruments
   */
  protected void checkAndSaveSuccessImportTransaction(ImportTransactionTemplate importTransactionTemplate,
      List<Cashaccount> cashaccountList, List<ImportProperties> importPropertiesList, String fileNameOriginal,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository,
      SecurityJpaRepository securityJpaRepository) {
    List<ImportTransactionPos> importTransactionPosList = new ArrayList<>();
    switch (importPropertiesList.getFirst().getTransactionType()) {
    case ACCUMULATE:
    case DIVIDEND:
    case REDUCE:
      importTransactionPosList.add(createSecurityTransaction(importTransactionTemplate, cashaccountList,
          importPropertiesList, fileNameOriginal, securityJpaRepository));
      break;

    default:
      if (importPropertiesList.size() > 1) {
        // Account Transfer in the same portfolio
        importTransactionPosList.addAll(accountTransferSamePortfolio(importTransactionTemplate, cashaccountList,
            importPropertiesList, fileNameOriginal, importTransactionPosJpaRepository));
      } else {
        // Transaction without security
        importTransactionPosList.add(ImportTransactionPos.createFromImportPropertiesSuccess(
            importTransactionHead.getIdTenant(), fileNameOriginal, importTransactionHead.getIdTransactionHead(),
            importTransactionTemplate.getIdTransactionImportTemplate(), importPropertiesList.getFirst()));
      }
    }
    importTransactionPosList.forEach(importTransactionPos -> {
      setCashaccountAndCheckReadyState(cashaccountList, importTransactionPos, importTransactionPosJpaRepository);
      importTransactionPosJpaRepository.save(importTransactionPos);
    });
  }

  /**
   * Processes cash transfers between different accounts within the same portfolio. Handles multi-currency transfers by
   * calculating appropriate exchange rates based on the deposit and withdrawal amounts. Links the related transactions
   * for proper accounting.
   * 
   * @param importTransactionTemplate         Template used for parsing the transfer data
   * @param cashaccountList                   Available cash accounts in the portfolio
   * @param importPropertiesList              Transfer properties (typically deposit and withdrawal)
   * @param fileNameOriginal                  Original filename of the imported document
   * @param importTransactionPosJpaRepository Repository for persisting import positions
   * @return List of linked import positions representing the complete transfer
   */
  private List<ImportTransactionPos> accountTransferSamePortfolio(ImportTransactionTemplate importTransactionTemplate,
      List<Cashaccount> cashaccountList, List<ImportProperties> importPropertiesList, String fileNameOriginal,
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository) {

    List<ImportTransactionPos> importTransactionPosList = new ArrayList<>();
    int lineNumberIndex = 0;
    if (importPropertiesList.get(0).getTransactionType() == TransactionType.WITHDRAWAL) {
      lineNumberIndex = 1;
    }
    ImportProperties depositIp = importPropertiesList.get(lineNumberIndex);
    ImportProperties withdrawalIp = importPropertiesList.get((lineNumberIndex + 1) % 2);
    Currencypair currencypair = DataBusinessHelper.getCurrencypairWithSetOfFromAndTo(withdrawalIp.getCac(),
        depositIp.getCac());
    Double exchangeRate = null;
    if (currencypair != null) {
      if (depositIp.getCac().equals(currencypair.getFromCurrency())) {
        exchangeRate = Math.abs(withdrawalIp.getTa() / depositIp.getTa());
      } else {
        exchangeRate = Math.abs(depositIp.getTa() / withdrawalIp.getTa());
      }
      depositIp.setCex(exchangeRate);
      withdrawalIp.setCex(exchangeRate);
    }

    importTransactionPosList
        .add(createAndSaveImportTransactionPos(importTransactionPosJpaRepository, importTransactionTemplate,
            importTransactionHead.getIdTenant(), fileNameOriginal, importTransactionHead.getIdTransactionHead(),
            importTransactionTemplate.getIdTransactionImportTemplate(), depositIp));
    importTransactionPosList
        .add(createAndSaveImportTransactionPos(importTransactionPosJpaRepository, importTransactionTemplate,
            importTransactionHead.getIdTenant(), fileNameOriginal, importTransactionHead.getIdTransactionHead(),
            importTransactionTemplate.getIdTransactionImportTemplate(), withdrawalIp));

    importTransactionPosList.get(0).setConnectedIdTransactionPos(importTransactionPosList.get(1).getIdTransactionPos());
    importTransactionPosList.get(1).setConnectedIdTransactionPos(importTransactionPosList.get(0).getIdTransactionPos());
    return importTransactionPosList;
  }

  /**
   * Creates an import position for security-related transactions (buy/sell/dividend). Resolves the security instrument
   * and associates it with the import position for proper portfolio tracking and transaction processing.
   * 
   * @param importTransactionTemplate Template used for parsing the transaction
   * @param cashaccountList           Available cash accounts in the portfolio
   * @param importPropertiesList      Security transaction properties
   * @param fileNameOriginal          Original filename of the imported document
   * @param securityJpaRepository     Repository for resolving security instruments
   * @return Import position ready for security transaction processing
   */
  protected ImportTransactionPos createSecurityTransaction(ImportTransactionTemplate importTransactionTemplate,
      List<Cashaccount> cashaccountList, List<ImportProperties> importPropertiesList, String fileNameOriginal,
      SecurityJpaRepository securityJpaRepository) {
    ImportTransactionPos importTransactionPos = ImportTransactionPos.createFromImportPropertiesSecuritySuccess(
        importTransactionHead.getIdTenant(), fileNameOriginal, importTransactionHead.getIdTransactionHead(),
        importTransactionTemplate.getIdTransactionImportTemplate(), importPropertiesList);
    TransactionImportHelper.setSecurityToImportWhenPossible(importTransactionPos, securityJpaRepository);
    return importTransactionPos;
  }

  /**
   * Assigns cash account and validates the import position is ready for transaction creation. Performs final validation
   * checks to ensure the import position has all required data for successful transaction processing.
   * 
   * @param cashaccountList                   Available cash accounts in the portfolio
   * @param importTransactionPos              Import position to validate and prepare
   * @param importTransactionPosJpaRepository Repository for transaction readiness validation
   */
  protected void setCashaccountAndCheckReadyState(List<Cashaccount> cashaccountList,
      ImportTransactionPos importTransactionPos, ImportTransactionPosJpaRepository importTransactionPosJpaRepository) {
    setCashaccoutWhenPossibe(cashaccountList, importTransactionPos);
    importTransactionPosJpaRepository.setCheckReadyForSingleTransaction(importTransactionPos);
  }

  /**
   * Creates and immediately persists an import position for simple transactions. Used internally for account transfers
   * and other multi-step transaction processing.
   * 
   * @param importTransactionPosJpaRepository Repository for persisting the import position
   * @param importTransactionTemplate         Template used for parsing
   * @param idTenant                          Tenant identifier
   * @param fileName                          Original filename
   * @param idTransactionHead                 Import session identifier
   * @param idTransactionImportTemplate       Template identifier
   * @param importProperties                  Parsed transaction properties
   * @return Persisted import position with assigned ID
   */
  private ImportTransactionPos createAndSaveImportTransactionPos(
      ImportTransactionPosJpaRepository importTransactionPosJpaRepository,
      ImportTransactionTemplate importTransactionTemplate, Integer idTenant, String fileName, Integer idTransactionHead,
      Integer idTransactionImportTemplate, ImportProperties importProperties) {
    ImportTransactionPos importTransactionPos = ImportTransactionPos.createFromImportPropertiesSuccess(
        importTransactionHead.getIdTenant(), fileName, importTransactionHead.getIdTransactionHead(),
        importTransactionTemplate.getIdTransactionImportTemplate(), importProperties);
    return importTransactionPosJpaRepository.save(importTransactionPos);
  }

}
