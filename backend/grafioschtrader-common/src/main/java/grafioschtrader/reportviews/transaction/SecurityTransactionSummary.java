package grafioschtrader.reportviews.transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Container for all transactions related to a single security with aggregated position summary.
 * 
 * <p>
 * This class aggregates individual transactions into a comprehensive view that includes detailed transaction history
 * and calculated position metrics such as gains, losses, and current holdings. Provides special handling for margin
 * trading instruments where child transactions must be properly ordered relative to their parent positions.
 * </p>
 * 
 * <p>
 * The transaction list maintains chronological order with margin child transactions inserted immediately after their
 * parent transactions for proper position tracking.
 * </p>
 */
public class SecurityTransactionSummary {

  @Schema(description = "List of individual transaction positions with calculated gains/losses")
  public List<SecurityTransactionPosition> transactionPositionList = new ArrayList<>();

  @Schema(description = "Aggregated position summary with total holdings and performance metrics")
  public SecurityPositionSummary securityPositionSummary;

  /**
   * Creates a new transaction summary for the specified security.
   * 
   * @param security             the security for which transactions are being summarized
   * @param mainCurrency         the base currency for position calculations
   * @param currencyPrecisionMap map of currency codes to decimal precision settings
   */
  public SecurityTransactionSummary(Security security, String mainCurrency, Map<String, Integer> currencyPrecisionMap) {
    securityPositionSummary = new SecurityPositionSummary(mainCurrency, security, currencyPrecisionMap);
    securityPositionSummary.securitycurrency = security;
  }

  /**
   * Creates and adds a transaction position with calculated gains/losses.
   * 
   * <p>
   * For margin instruments, child transactions are inserted at the appropriate position relative to their parent
   * transaction to maintain proper position hierarchy. Regular transactions are appended to the end of the list.
   * </p>
   * 
   * @param transaction the transaction to add to the summary
   */
  public void createAndAddPositionGainLoss(Transaction transaction) {
    if (transaction.getSecurity().isMarginInstrument() && transaction.getConnectedIdTransaction() != null) {
      insertMarginChild(transaction);
    } else {
      transactionPositionList.add(new SecurityTransactionPosition(transaction, securityPositionSummary));
    }
  }

  /**
   * Removes security object references from all transactions to reduce JSON payload size.
   * 
   * <p>
   * This is typically used when the security information is already available in the summary and doesn't need to be
   * duplicated in each individual transaction.
   * </p>
   */
  public void setSecurityInTransactionToNull() {
    transactionPositionList
        .forEach(securityTransactionPosition -> securityTransactionPosition.setSecurityInTransactionToNull());
  }

  /**
   * Inserts a margin child transaction at the correct position relative to its parent.
   * 
   * <p>
   * Margin trading uses a parent-child relationship where the opening position acts as the parent and subsequent
   * transactions (closes, financing costs) are children. This method ensures child transactions are grouped with their
   * parent for proper position tracking and reporting.
   * </p>
   * 
   * <p>
   * The insertion logic finds the parent transaction and places the child immediately after all existing children of
   * the same parent, maintaining chronological order within the parent-child group.
   * </p>
   * 
   * @param transaction the margin child transaction to insert
   */
  private void insertMarginChild(Transaction transaction) {
    for (int i = 0; i < transactionPositionList.size(); i++) {
      SecurityTransactionPosition stp = transactionPositionList.get(i);
      if (stp.transaction.getIdTransaction().equals(transaction.getConnectedIdTransaction())) {
        if (i < transactionPositionList.size() - 1) {
          SecurityTransactionPosition stpChild;
          do {
            i++;
            stpChild = transactionPositionList.get(i);
          } while (i < transactionPositionList.size() - 1 && !stpChild.transaction.isMarginOpenPosition()
              && stpChild.transaction.getConnectedIdTransaction().equals(transaction.getConnectedIdTransaction()));
          if (i == transactionPositionList.size() - 1 && !stpChild.transaction.isMarginOpenPosition()
              && stpChild.transaction.getConnectedIdTransaction().equals(transaction.getConnectedIdTransaction())) {
            i++;
          }
        } else {
          i = transactionPositionList.size();
        }
        transactionPositionList.add(i, new SecurityTransactionPosition(transaction, securityPositionSummary));
        return;
      }
    }
  }

  @Override
  public String toString() {
    return "SecurityTransactionSummary [transactionPositionList=" + transactionPositionList
        + ", securityPositionSummary=" + securityPositionSummary + "]";
  }

}
