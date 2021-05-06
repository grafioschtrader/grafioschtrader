package grafioschtrader.reportviews.transaction;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataHelper;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;

/**
 * Contains the gain/loss of a single transaction. For every Transaction one
 * instance of this class.
 * 
 * @author Hugo Graf
 *
 */
public class SecurityTransactionPosition {
  public Transaction transaction;
  public Double transactionGainLoss;
  public Double transactionGainLossPercentage;
  public Double transactionGainLossMC;
  public Double transactionExchangeRate;
  public Double transactionCurrencyGainLossMC;
  public Double quotationSplitCorrection;

  public SecurityTransactionPosition(Transaction transaction, SecurityPositionSummary securityPositionSummary) {
    this.transaction = transaction;
    this.transactionGainLoss = (securityPositionSummary.transactionGainLoss == null) ? null
        : DataHelper.round(securityPositionSummary.transactionGainLoss, GlobalConstants.FID_STANDARD_FRACTION_DIGITS);
    this.transactionGainLossPercentage = (securityPositionSummary.transactionGainLossPercentage == null) ? null
        : DataHelper.round(securityPositionSummary.transactionGainLossPercentage,
            GlobalConstants.FID_STANDARD_FRACTION_DIGITS);
    this.transactionExchangeRate = securityPositionSummary.transactionExchangeRate;
    this.transactionGainLossMC = securityPositionSummary.transactionGainLossMC;
    this.transactionCurrencyGainLossMC = securityPositionSummary.transactionCurrencyGainLossMC;

  }

  public void setSecurityInTransactionToNull() {
    this.transaction.setSecuritycurrency(null);
  }

}
