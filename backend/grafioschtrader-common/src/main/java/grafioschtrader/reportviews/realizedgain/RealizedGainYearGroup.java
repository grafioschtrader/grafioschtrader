package grafioschtrader.reportviews.realizedgain;

import java.util.ArrayList;
import java.util.List;

import grafiosch.common.DataHelper;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The sales of one calendar year. The sums of the sales on the market and those of the system bookings of a security
 * account transfer or an ISIN change are kept apart, because a system booking realizes a gain that no market sale
 * produced: it shifts gain between depots and years, and only the sum over all years is the economic gain.
 */
@Schema(description = "Realized sales of one calendar year with the sums in the main currency")
public class RealizedGainYearGroup {

  @Schema(description = "Calendar year of the sales")
  public int year;

  @Schema(description = "Sum of the gains of the market sales in the main currency")
  public double yearGainMC;

  @Schema(description = "Sum of the losses of the market sales in the main currency, a positive amount")
  public double yearLossMC;

  @Schema(description = "Gains minus losses of the market sales in the main currency")
  public double yearNetMC;

  @Schema(description = "Sum of the gains of the system bookings in the main currency")
  public double systemGainMC;

  @Schema(description = "Sum of the losses of the system bookings in the main currency, a positive amount")
  public double systemLossMC;

  @Schema(description = "Gains minus losses of the system bookings in the main currency")
  public double systemNetMC;

  @Schema(description = "Number of the market sales, a sale without gain counts as well")
  public int saleCount;

  @Schema(description = "Number of the system bookings")
  public int systemBookingCount;

  @Schema(description = """
      True when a sale of the year has no cost for all of its units or no exchange rate. The sums then do not cover
      every sale completely.""")
  public boolean incomplete;

  @Schema(description = "The sales of the year in the order of their time")
  public List<RealizedGainPosition> positions = new ArrayList<>();

  public RealizedGainYearGroup() {
  }

  public RealizedGainYearGroup(int year) {
    this.year = year;
  }

  /**
   * Adds a sale to the year. A sale without a rate into the main currency is listed but not summed, and it marks the
   * year as incomplete, so does a sale whose purchases did not cover its units.
   *
   * @param position the sale, its gainLossMC is already rounded to the precision of the main currency
   */
  public void add(RealizedGainPosition position) {
    positions.add(position);
    if (position.systemBooking) {
      systemBookingCount++;
    } else {
      saleCount++;
    }
    if (position.gainLossMC == null || Boolean.TRUE.equals(position.costBasisIncomplete)) {
      incomplete = true;
    }
    if (position.gainLossMC == null) {
      return;
    }
    final double gain = Math.max(position.gainLossMC, 0.0);
    final double loss = Math.max(-position.gainLossMC, 0.0);
    if (position.systemBooking) {
      systemGainMC += gain;
      systemLossMC += loss;
    } else {
      yearGainMC += gain;
      yearLossMC += loss;
    }
  }

  /**
   * Rounds the sums and derives the net amounts once all sales of the year are added.
   *
   * @param precisionMC decimal places of the main currency
   */
  public void finish(int precisionMC) {
    yearGainMC = DataHelper.round(yearGainMC, precisionMC);
    yearLossMC = DataHelper.round(yearLossMC, precisionMC);
    yearNetMC = DataHelper.round(yearGainMC - yearLossMC, precisionMC);
    systemGainMC = DataHelper.round(systemGainMC, precisionMC);
    systemLossMC = DataHelper.round(systemLossMC, precisionMC);
    systemNetMC = DataHelper.round(systemGainMC - systemLossMC, precisionMC);
  }
}
