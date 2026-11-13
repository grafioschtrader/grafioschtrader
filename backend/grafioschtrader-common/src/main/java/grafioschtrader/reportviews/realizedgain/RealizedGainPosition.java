package grafioschtrader.reportviews.realizedgain;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import grafiosch.BaseConstants;
import grafioschtrader.entities.Security;
import grafioschtrader.reportviews.securityaccount.CostLot;
import grafioschtrader.types.CostBasisMethod;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One sale of the realized gain report: a sale of a security, or the close of a margin position, with the gain it
 * realized in the security currency and in the main currency.
 */
@Schema(description = "One realized sale with its proceeds, cost and gain")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RealizedGainPosition {

  @Schema(description = "Identifier of the sale transaction")
  public Integer idTransaction;

  @Schema(description = "The sold security")
  public Security security;

  @Schema(description = "Currency of the security. All amounts without the suffix MC are in this currency")
  public String securityCurrency;

  @Schema(description = "Security account of the sale")
  public Integer idSecurityaccount;

  @Schema(description = "Time of the sale")
  @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME_ISO)
  public LocalDateTime transactionTime;

  @Schema(description = "Units as booked in the transaction, not adjusted for later splits")
  public double units;

  @Schema(description = "Proceeds after the costs of the sale, the cost base plus the gain. Empty for a margin close")
  public Double proceeds;

  @Schema(description = """
      Cost of the units sold according to the cost basis method. Empty for a margin close, whose gain is the result of
      the closed position.""")
  public Double costBase;

  @Schema(description = "Realized gain or loss in the security currency")
  public Double gainLoss;

  @Schema(description = "Gain or loss relative to the cost base in percent, empty without a cost base")
  public Double gainLossPercentage;

  @Schema(description = """
      Gain or loss in the main currency at the exchange rate of the sale day. Empty when no rate of the security
      currency exists for that day. Such a sale is not part of the totals in the main currency.""")
  public Double gainLossMC;

  @Schema(description = "Rate from the security currency into the main currency used for gainLossMC")
  public Double exchangeRate;

  @Schema(description = "The cost basis method by which the gain was calculated. Empty for a margin close")
  public CostBasisMethod costBasisMethod;

  @Schema(description = "Purchases a sale consumed, only with FIFO or LIFO")
  public List<CostLot> consumedLots;

  @Schema(description = """
      True when the purchases did not cover the units of the sale. The uncovered units show no gain, because they were
      given their share of the proceeds as cost.""")
  public Boolean costBasisIncomplete;

  @Schema(description = """
      True when the sale is the sale of a security account transfer or of an ISIN change. The system booked it and
      nothing was sold on the market.""")
  public boolean systemBooking;
}
