package grafioschtrader.reportviews.securityaccount;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One purchase of a security that a later sale can consume, or the part of such a purchase that a sale has consumed.
 * Lots exist only while a report is calculated, the transactions remain the single source.
 */
@Schema(description = "A purchase, or the consumed part of a purchase, with the cost that belongs to it")
public final class CostLot {

  @Schema(description = "Identifier of the purchase transaction")
  public final Integer idTransaction;

  @Schema(description = "Time of the purchase")
  @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME_ISO)
  public final LocalDateTime transactionTime;

  @Schema(description = """
      Exchange rate from the security currency into the main currency at the purchase. Null for a security in the main
      currency.""")
  public final Double exchangeRate;

  @Schema(description = "Units on the split basis of the report date")
  public double units;

  @Schema(description = "Cost of these units in security currency, purchase costs included")
  public double cost;

  /**
   * @param idTransaction   identifier of the purchase transaction
   * @param transactionTime time of the purchase
   * @param exchangeRate    rate into the main currency at the purchase, null for a security in the main currency
   * @param units           units on the split basis of the report date
   * @param cost            cost of these units in security currency, purchase costs included
   */
  public CostLot(Integer idTransaction, LocalDateTime transactionTime, Double exchangeRate, double units, double cost) {
    this.idTransaction = idTransaction;
    this.transactionTime = transactionTime;
    this.exchangeRate = exchangeRate;
    this.units = units;
    this.cost = cost;
  }

  @Override
  public String toString() {
    return "CostLot [idTransaction=" + idTransaction + ", transactionTime=" + transactionTime + ", exchangeRate="
        + exchangeRate + ", units=" + units + ", cost=" + cost + "]";
  }
}
