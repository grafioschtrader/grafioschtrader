package grafioschtrader.reportviews.performance;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;

import org.apache.commons.beanutils.PropertyUtils;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.common.DataBusinessHelper;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Data container for period holding values and computed differences used in performance analysis.
 * 
 * <p>
 * This class serves multiple purposes in the performance reporting system:
 * </p>
 * <ul>
 * <li>Stores snapshot values for a specific date (holdings, balances, gains)</li>
 * <li>Represents calculated differences between two time periods</li>
 * <li>Provides aggregated totals and computed metrics</li>
 * </ul>
 *
 * <p>
 * All monetary values are expressed in "MC" (Main Currency), which is either the tenant's base currency or the
 * portfolio's base currency depending on the analysis scope.
 * </p>
 * 
 * <p>
 * <strong>Difference Calculations:</strong>
 * </p>
 * <p>
 * The class supports automatic difference calculation between two instances using reflection, enabling easy
 * period-over-period comparisons.
 * </p>
 */
@Schema(description = "Data container for period holding values and computed differences used in performance analysis.")
public class PeriodHoldingAndDiff {
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  @Schema(description = "The date for which these holding values apply.")
  private LocalDate date;
  
  @Schema(description = "Cumulative realized dividends in main currency.")
  private double dividendRealMC;

  @Schema(description = "Total security account costs and account costs in tenant currency")
  private double feeRealMC;

  @Schema(description = "Cumulative interest earned on cash accounts in main currency.")
  private double interestCashaccountRealMC;

  @Schema(description = "Net accumulation/reduction amount from security transactions in main currency.")
  private double accumulateReduceMC;

  @Schema(description = "Total cash balance across all accounts in main currency.")
  private double cashBalanceMC;

  @Schema(description = "External cash transfers (deposits/withdrawals) in main currency.")
  private double externalCashTransferMC;

  @Schema(description = "Market value of all securities holdings in main currency.")
  private double securitiesMC;

  @Schema(description = "Realized gains from closed margin positions in main currency.")
  private double marginCloseGainMC;

  @Schema(description = "Market risk (unrealized value) of open positions in main currency.")
  private double securityRiskMC;

  @Schema(description = "Total investment gain/loss in main currency.")
  private double gainMC;

  public PeriodHoldingAndDiff() {
  }

  public LocalDate getDate() {
    return date;
  }

  public void setDate(LocalDate date) {
    this.date = date;
  }

  public double getDividendRealMC() {
    return dividendRealMC;
  }

  public void setDividendRealMC(double dividendRealMC) {
    this.dividendRealMC = dividendRealMC;
  }

  public double getFeeRealMC() {
    return feeRealMC;
  }

  public void setFeeRealMC(double feeMC) {
    this.feeRealMC = feeMC;
  }

  public double getInterestCashaccountRealMC() {
    return interestCashaccountRealMC;
  }

  public void setInterestCashaccountRealMC(double interestCashaccountRealMC) {
    this.interestCashaccountRealMC = interestCashaccountRealMC;
  }

  public double getAccumulateReduceMC() {
    return accumulateReduceMC;
  }

  public void setAccumulateReduceMC(double accumulateReduceMC) {
    this.accumulateReduceMC = accumulateReduceMC;
  }

  public double getCashBalanceMC() {
    return cashBalanceMC;
  }

  public void setCashBalanceMC(double cashBalanceMC) {
    this.cashBalanceMC = cashBalanceMC;
  }

  public double getExternalCashTransferMC() {
    return externalCashTransferMC;
  }

  public void setExternalCashTransferMC(double externalCashTransferMC) {
    this.externalCashTransferMC = externalCashTransferMC;
  }

  public double getSecuritiesMC() {
    return securitiesMC;
  }

  @Schema(description = "Returns the combined value of securities and margin gains with standard rounding.")
  public double getSecuritiesAndMarginGainMC() {
    return DataBusinessHelper.roundStandard(securitiesMC + marginCloseGainMC);
  }

  public void setSecuritiesMC(double securitiesMC) {
    this.securitiesMC = securitiesMC;
  }

  public double getSecurityRiskMC() {
    return securityRiskMC;
  }

  public void setSecurityRiskMC(double securityRiskMC) {
    this.securityRiskMC = securityRiskMC;
  }

  public double getGainMC() {
    return gainMC;
  }

  public void setGainMC(double gainMC) {
    this.gainMC = gainMC;
  }

  public double getMarginCloseGainMC() {
    return marginCloseGainMC;
  }

  public void setMarginCloseGainMC(double marginCloseGainMC) {
    this.marginCloseGainMC = marginCloseGainMC;
  }

  @Schema(description = "Returns the total gain including both regular gains and margin gains with standard rounding.")
  public double getTotalGainMC() {
    return DataBusinessHelper.roundStandard(gainMC + marginCloseGainMC);
  }

  @Schema(description = "Returns the total portfolio balance including cash, securities, and margin gains with standard rounding.")
  public double getTotalBalanceMC() {
    return DataBusinessHelper.roundStandard(cashBalanceMC + this.securitiesMC + marginCloseGainMC);
  }

  /**
   * Calculates the difference between this instance and another instance using reflection.
   * 
   * <p>
   * This method automatically computes differences for all double-type properties that have both getter and setter
   * methods. The calculation is performed as (this.value - subtrahend.value) for each property.
   * </p>
   * 
   * <p>
   * The result is a new PeriodHoldingAndDiff instance containing the differences, which can be used for
   * period-over-period analysis.
   * </p>
   * 
   * @param subtrahendsHolding the instance to subtract from this one
   * @return a new instance containing the calculated differences
   */
  public PeriodHoldingAndDiff calculateDiff(PeriodHoldingAndDiff subtrahendsHolding)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    PropertyDescriptor[] propertyDescriptors = PropertyUtils.getPropertyDescriptors(this);
    PeriodHoldingAndDiff diff = new PeriodHoldingAndDiff();
    for (PropertyDescriptor property : propertyDescriptors) {
      if (property.getWriteMethod() != null && property.getPropertyType() == double.class) {
        double minuedValue = (double) PropertyUtils.getProperty(this, property.getName());
        double subtrahends = (double) PropertyUtils.getProperty(subtrahendsHolding, property.getName());
        PropertyUtils.setProperty(diff, property.getName(),
            DataBusinessHelper.roundStandard(minuedValue - subtrahends));
      }
    }
    return diff;
  }

}