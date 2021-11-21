package grafioschtrader.reportviews.transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;

/**
 * Contains all transaction for a single security with a summary of it gains or
 * loss.
 *
 */
public class SecurityTransactionSummary {

  public List<SecurityTransactionPosition> transactionPositionList = new ArrayList<>();
  public SecurityPositionSummary securityPositionSummary;

  public SecurityTransactionSummary(Security security, String mainCurrency, Map<String, Integer> currencyPrecisionMap) {
    securityPositionSummary = new SecurityPositionSummary(mainCurrency, security, currencyPrecisionMap);
    securityPositionSummary.securitycurrency = security;
  }

  public void createAndAddPositionGainLoss(Transaction transaction) {
    if (transaction.getSecurity().isMarginInstrument() && transaction.getConnectedIdTransaction() != null) {
      insertMarginChild(transaction);
    } else {
      transactionPositionList.add(new SecurityTransactionPosition(transaction, securityPositionSummary));
    }
  }

  public void setSecurityInTransactionToNull() {
    transactionPositionList
        .forEach(securityTransactionPosition -> securityTransactionPosition.setSecurityInTransactionToNull());
  }

  /**
   * Margin trade handle opening a position as a parent of all other transactions
   * like close or finance cost. Child position is inserted at the right position.
   *
   * @param transaction
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
