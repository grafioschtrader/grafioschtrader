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

  private int precisionMC;

  public SecurityTransactionPosition(Transaction transaction, SecurityPositionSummary securityPositionSummary) {
    this.transaction = transaction;
    transactionGainLoss = (securityPositionSummary.transactionGainLoss == null) ? null
        : DataHelper.round(securityPositionSummary.transactionGainLoss, GlobalConstants.FID_STANDARD_FRACTION_DIGITS);
    transactionGainLossPercentage = (securityPositionSummary.transactionGainLossPercentage == null) ? null
        : DataHelper.round(securityPositionSummary.transactionGainLossPercentage,
            GlobalConstants.FID_STANDARD_FRACTION_DIGITS);
    transactionExchangeRate = securityPositionSummary.transactionExchangeRate;
    transactionGainLossMC = securityPositionSummary.transactionGainLossMC;
    transactionCurrencyGainLossMC = securityPositionSummary.transactionCurrencyGainLossMC;
    precisionMC = securityPositionSummary.precisionMC;
  }

  public void setSecurityInTransactionToNull() {
    this.transaction.setSecuritycurrency(null);
  }

  public Double getTransactionCurrencyGainLossMC() {
    return transactionCurrencyGainLossMC == null ? null : DataHelper.round(transactionCurrencyGainLossMC, precisionMC);
  }

  public Double getTransactionGainLossMC() {
    return transactionGainLossMC == null ? null : DataHelper.round(transactionGainLossMC, precisionMC);
  }

}
