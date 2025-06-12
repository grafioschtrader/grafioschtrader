package grafioschtrader.reportviews.currencypair;

import java.util.List;

import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Transaction;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
Represents a currency pair with its associated transactions and calculated financial metrics. 
Includes transaction summaries, gain/loss calculations, and optional reverse currency pair data for comprehensive analysis.
""")
public class CurrencypairWithTransaction {

  @Schema(description = "Total amount in the base currency (from currency) across all transactions")
  public double sumAmountFrom = 0d;
  @Schema(description = "Total amount in the quote currency (to currency) across all transactions")
  public double sumAmountTo = 0d;

  @Schema(description = "Calculated gain or loss in the quote currency based on current exchange rates")
  public double gainTo = 0d;
  @Schema(description = "Calculated gain or loss in the base currency based on current exchange rates")
  public double gainFrom = 0d;

  @Schema(description = "The currency pair entity containing exchange rate and metadata")
  public Currencypair currencypair;
  @Schema(description = "List of all transactions that involve this currency pair")
  public List<Transaction> transactionList;

  @Schema(description = """
      Optional reverse currency pair data (e.g., if main pair is EUR/USD, this would contain USD/EUR data). 
      Used for bidirectional analysis and comprehensive charting.""")
  public CurrencypairWithTransaction cwtReverse;

  public CurrencypairWithTransaction(Currencypair currencypair) {
    this.currencypair = currencypair;
  }

  @Override
  public String toString() {
    return "CurrencypairWithTransaction [sumAmountFrom=" + sumAmountFrom + ", sumAmountTo=" + sumAmountTo + ", gainTo="
        + gainTo + ", gainFrom=" + gainFrom + ", currencypair=" + currencypair + "]";
  }

}
