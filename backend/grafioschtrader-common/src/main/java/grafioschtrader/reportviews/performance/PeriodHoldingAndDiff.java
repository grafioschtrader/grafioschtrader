package grafioschtrader.reportviews.performance;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;

import org.apache.commons.beanutils.PropertyUtils;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafioschtrader.common.DataBusinessHelper;
import io.swagger.v3.oas.annotations.media.Schema;

public class PeriodHoldingAndDiff {
  @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT)
  private LocalDate date;
  private double dividendRealMC;
  @Schema(description = "Total security account costs and account costs in tenant currency")
  private double feeRealMC;
  private double interestCashaccountRealMC;
  private double accumulateReduceMC;
  private double cashBalanceMC;
  private double externalCashTransferMC;
  private double securitiesMC;
  private double marginCloseGainMC;
  private double securityRiskMC;
  private double gainMC;

  public PeriodHoldingAndDiff() {
  }

//  public PeriodHoldingAndDiff(LocalDate date, double dividendRealMC, double feeRealMC, double interestCashaccountRealMC,
//      double accumulateReduceMC, double cashBalanceMC, double externalCashTransferMC, double securitiesMC,
//      double securityRiskMC, double gainMC, double marginCloseGainMC) {
//    this.date = date;
//    this.dividendRealMC = dividendRealMC;
//    this.feeRealMC = feeRealMC;
//    this.interestCashaccountRealMC = interestCashaccountRealMC;
//    this.accumulateReduceMC = accumulateReduceMC;
//    this.cashBalanceMC = cashBalanceMC;
//    this.externalCashTransferMC = externalCashTransferMC;
//    this.securitiesMC = securitiesMC;
//    this.marginCloseGainMC = marginCloseGainMC;
//    this.securityRiskMC = securityRiskMC;
//    this.gainMC = gainMC;
//  }

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

  public double getTotalGainMC() {
    return DataBusinessHelper.roundStandard(gainMC + marginCloseGainMC);
  }

  public double getTotalBalanceMC() {
    return DataBusinessHelper.roundStandard(cashBalanceMC + this.securitiesMC + marginCloseGainMC);
  }

  public PeriodHoldingAndDiff calculateDiff(PeriodHoldingAndDiff subtrahendsHolding)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    PropertyDescriptor[] propertyDescriptors = PropertyUtils.getPropertyDescriptors(this);
    PeriodHoldingAndDiff diff = new PeriodHoldingAndDiff();
    for (PropertyDescriptor property : propertyDescriptors) {
      if (property.getWriteMethod() != null && property.getPropertyType() == double.class) {
        double minuedValue = (double) PropertyUtils.getProperty(this, property.getName());
        double subtrahends = (double) PropertyUtils.getProperty(subtrahendsHolding, property.getName());
        PropertyUtils.setProperty(diff, property.getName(), DataBusinessHelper.roundStandard(minuedValue - subtrahends));
      }
    }
    return diff;
  }

}