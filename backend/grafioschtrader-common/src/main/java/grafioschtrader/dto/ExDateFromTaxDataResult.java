package grafioschtrader.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
    Outcome of back-filling dividend ex-dates from the imported Swiss ICTax tax data for a single tax year. The counts
    let the client report how many dividend transactions were considered, how many already had an ex-date, how many
    received one from the tax data, and how many could not be matched.""")
public class ExDateFromTaxDataResult {

  @Schema(description = "Number of dividend transactions of the tenant within the tax year that were examined")
  public int dividendTransactions;

  @Schema(description = "Number of examined transactions that already had an ex-date and were therefore left unchanged")
  public int alreadySet;

  @Schema(description = "Number of transactions whose ex-date was set from the matching tax data")
  public int exDateAssigned;

  @Schema(description = "Number of transactions without an ex-date that could not be matched to any tax payment")
  public int unmatched;

  public ExDateFromTaxDataResult(int dividendTransactions, int alreadySet, int exDateAssigned, int unmatched) {
    this.dividendTransactions = dividendTransactions;
    this.alreadySet = alreadySet;
    this.exDateAssigned = exDateAssigned;
    this.unmatched = unmatched;
  }
}
