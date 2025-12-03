package grafioschtrader.reportviews.transaction;

import grafiosch.BaseConstants;
import grafiosch.common.DataHelper;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.types.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a single transaction with calculated gain/loss metrics and position tracking.
 * 
 * <p>
 * This class wraps a transaction with computed performance metrics including gains/losses
 * in both transaction and main currencies, exchange rates, and position holdings after
 * the transaction. Provides split-adjusted data for historical analysis and charting.
 * </p>
 */
@Schema(description = "Transaction position with calculated gains, losses, and performance metrics")
public class SecurityTransactionPosition {
  @Schema(description = "The underlying transaction record")
  public Transaction transaction;
  
  @Schema(description = "Gain or loss from this transaction in transaction currency")
  public Double transactionGainLoss;

  @Schema(description = "Percentage gain or loss from this transaction")
  public Double transactionGainLossPercentage;

  @Schema(description = "Gain or loss from this transaction in main currency")
  public Double transactionGainLossMC;

  @Schema(description = "Exchange rate used for currency conversion")
  public Double transactionExchangeRate;

  @Schema(description = "Currency-specific gain or loss in main currency")
  public Double transactionCurrencyGainLossMC;

  @Schema(description = "Split-adjusted quotation for historical chart display")
  public Double quotationSplitCorrection;

  @Schema(description = "Total holdings after this transaction (split-adjusted)")
  public Double holdingsSplitAdjusted;

  /** Decimal precision for main currency formatting */
  private int precisionMC;

  /**
   * Creates a transaction position by copying calculated metrics from the position summary.
   * 
   * <p>
   * Rounds gain/loss values to standard precision and sets holdings to zero for
   * hypothetical transactions. All monetary values are formatted according to
   * the configured currency precision.
   * </p>
   * 
   * @param transaction the underlying transaction
   * @param securityPositionSummary the position summary containing calculated metrics
   */
  public SecurityTransactionPosition(Transaction transaction, SecurityPositionSummary securityPositionSummary) {
    this.transaction = transaction;
    transactionGainLoss = (securityPositionSummary.transactionGainLoss == null) ? null
        : DataHelper.round(securityPositionSummary.transactionGainLoss, BaseConstants.FID_STANDARD_FRACTION_DIGITS);
    transactionGainLossPercentage = (securityPositionSummary.transactionGainLossPercentage == null) ? null
        : DataHelper.round(securityPositionSummary.transactionGainLossPercentage,
            BaseConstants.FID_STANDARD_FRACTION_DIGITS);
    transactionExchangeRate = securityPositionSummary.transactionExchangeRate;
    transactionGainLossMC = securityPositionSummary.transactionGainLossMC;
    transactionCurrencyGainLossMC = securityPositionSummary.transactionCurrencyGainLossMC;
    precisionMC = securityPositionSummary.precisionMC;
    holdingsSplitAdjusted = transaction.getTransactionType() == TransactionType.HYPOTHETICAL_SELL
        || transaction.getTransactionType() == TransactionType.HYPOTHETICAL_BUY ? 0.0 : securityPositionSummary.units;
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
