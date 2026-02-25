package grafioschtrader.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.types.AssetclassType;
import grafioschtrader.types.SpecialInvestmentInstruments;

/**
 * Summary of transactions grouped by (specInvestInstrument, categoryType) for a specific security account.
 * Used by the frontend to block deletion of trading periods that still have transactions,
 * and to prevent shortening dateTo below the latest transaction date.
 */
public class TradingPeriodTransactionSummary {

  private byte specInvestInstrument;
  private Byte categoryType;
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  private LocalDate maxTransactionDate;
  private long transactionCount;

  public TradingPeriodTransactionSummary() {
  }

  public TradingPeriodTransactionSummary(byte specInvestInstrument, Byte categoryType, LocalDate maxTransactionDate,
      long transactionCount) {
    this.specInvestInstrument = specInvestInstrument;
    this.categoryType = categoryType;
    this.maxTransactionDate = maxTransactionDate;
    this.transactionCount = transactionCount;
  }

  public SpecialInvestmentInstruments getSpecInvestInstrument() {
    return SpecialInvestmentInstruments.getSpecialInvestmentInstrumentsByValue(specInvestInstrument);
  }

  public AssetclassType getCategoryType() {
    return categoryType == null ? null : AssetclassType.getAssetClassTypeByValue(categoryType);
  }

  public LocalDate getMaxTransactionDate() {
    return maxTransactionDate;
  }

  public long getTransactionCount() {
    return transactionCount;
  }
}
