package grafioschtrader.reportviews.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;

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

  @Schema(description = """
      Share of the position's currency result attributable to this transaction: its flow in the security currency
      multiplied by the difference between the reporting date rate and the rate of its own date. Null until
      SecurityTransactionSummary.applyReportRate has run, and null for a security denominated in the main currency,
      which has no currency result.""")
  public Double transactionGainLossCurrencyMC;

  /**
   * Signed flow of this transaction in the security currency, positive when money went into the position. Only needed
   * to finish {@link #transactionGainLossCurrencyMC} once the reporting rate is known, never serialised.
   */
  @JsonIgnore
  public Double flowSC;

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
    flowSC = securityPositionSummary.transactionFlowSC;
    precisionMC = securityPositionSummary.precisionMC;
    holdingsSplitAdjusted = transaction.getTransactionType() == TransactionType.HYPOTHETICAL_SELL
        || transaction.getTransactionType() == TransactionType.HYPOTHETICAL_BUY ? 0.0 : securityPositionSummary.units;
  }

  public void setSecurityInTransactionToNull() {
    this.transaction.setSecuritycurrency(null);
  }

  public Double getTransactionGainLossCurrencyMC() {
    return transactionGainLossCurrencyMC == null ? null
        : DataHelper.round(transactionGainLossCurrencyMC, precisionMC);
  }

  /**
   * Finishes the per-transaction currency result now that the reporting date rate is known.
   *
   * @param reportExchangeRate rate from the security currency into the main currency at the reporting date
   */
  void applyReportRate(final double reportExchangeRate) {
    transactionGainLossCurrencyMC = flowSC == null ? null
        : flowSC * (reportExchangeRate - (transactionExchangeRate == null ? reportExchangeRate
            : transactionExchangeRate));
  }

  public Double getTransactionGainLossMC() {
    return transactionGainLossMC == null ? null : DataHelper.round(transactionGainLossMC, precisionMC);
  }

}
