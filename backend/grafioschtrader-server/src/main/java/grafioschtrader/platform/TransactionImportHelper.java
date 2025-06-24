package grafioschtrader.platform;

import java.util.List;

import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.Security;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.types.ImportKnownOtherFlags;

/**
* Utility class providing helper methods for transaction import operations, particularly security identification and linking.
* 
* <p>This abstract utility class contains common functionality used across different transaction import implementations.
* The primary focus is on resolving and linking financial securities to imported transaction positions based on
* available identifiers such as ISIN codes and ticker symbols. The class implements intelligent fallback strategies
* to maximize successful security identification while maintaining data integrity.</p>
* 
* <h3>Security Resolution Strategy</h3>
* <p>The security resolution process follows a prioritized approach: first attempting identification by ISIN code
* with currency matching, then falling back to ISIN-only matching when a unique security exists, and finally
* attempting ticker symbol-based identification. This strategy balances accuracy with flexibility to handle
* various data quality scenarios commonly encountered in financial imports.</p>
*/
public abstract class TransactionImportHelper {

  /**
   * Attempts to identify and link a security to an import transaction position using available identifiers.
   * 
   * <p>This method implements a comprehensive security resolution strategy that tries multiple identification
   * approaches to maximize successful linking. The process prioritizes ISIN-based identification with currency
   * matching, provides fallback options for currency mismatches, and supports ticker symbol-based identification
   * for platforms that primarily use symbols rather than ISIN codes.</p>
   * 
   * <h4>Resolution Process</h4>
   * <p>The method follows this resolution sequence:</p>
   * <ol>
   *   <li><b>ISIN with Currency Match:</b> Attempts to find security by ISIN and expected currency</li>
   *   <li><b>ISIN Only (Unique):</b> If currency match fails, checks for unique ISIN match across all currencies</li>
   *   <li><b>Ticker Symbol:</b> Falls back to ticker symbol identification with currency preference</li>
   * </ol>
   * 
   * <h4>Currency Resolution Logic</h4>
   * <p>The method uses security-specific currency when available, falling back to account currency if no
   * security currency is specified. This approach ensures proper matching while accommodating different
   * data completeness scenarios in import files.</p>
   * 
   * <h4>Flag Management</h4>
   * <p>When a security is identified through ISIN but with currency mismatch, the method sets the
   * SECURITY_CURRENCY_MISMATCH flag to alert users to potential data inconsistencies while still
   * allowing the import to proceed with the best available match.</p>
   * 
   * <h4>Ticker Symbol Processing</h4>
   * <p>For ticker symbol identification, the method handles compound symbols (containing colons) by
   * extracting the base symbol portion, accommodating various ticker formats used by different
   * trading platforms and data providers.</p>
   * 
   * @param importTransactionPos The transaction position being processed, which may contain ISIN,
   *                            ticker symbol, and currency information for security identification
   * @param securityJpaRepository Repository for querying security data using various search criteria
   *                             including ISIN, ticker symbols, and currency filters
   */
  public static void setSecurityToImportWhenPossible(ImportTransactionPos importTransactionPos,
      SecurityJpaRepository securityJpaRepository) {

    if (importTransactionPos.getIsin() != null) {
      String currency = importTransactionPos.getCurrencySecurity() == null ? importTransactionPos.getCurrencyAccount()
          : importTransactionPos.getCurrencySecurity();
      Security security = securityJpaRepository.findByIsinAndCurrency(importTransactionPos.getIsin(), currency);
      if (security != null) {
        importTransactionPos.setSecurity(security);
      } else {
        List<Security> securities = securityJpaRepository.findByIsin(importTransactionPos.getIsin());
        if (securities.size() == 1) {
          // When there is only one
          importTransactionPos.setSecurity(securities.get(0));
          importTransactionPos.addKnowOtherFlags(ImportKnownOtherFlags.SECURITY_CURRENCY_MISMATCH);
        }
      }
    } else if (importTransactionPos.getSymbolImp() != null) {
      // Used in corner Trader
      String currency = importTransactionPos.getCurrencySecurity() == null ? importTransactionPos.getCurrencyAccount()
          : importTransactionPos.getCurrencySecurity();
      String[] tickerSymbolParts = importTransactionPos.getSymbolImp().split(":");
      if (tickerSymbolParts.length > 0) {
        Security security = securityJpaRepository.findByTickerSymbolAndCurrency(tickerSymbolParts[0], currency);
        if (security != null) {
          importTransactionPos.setSecurity(security);
        }
      }
    }
  }
}
