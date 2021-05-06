package grafioschtrader.reportviews.performance;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;

import org.apache.commons.beanutils.PropertyUtils;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataHelper;

public class PeriodHoldingAndDiff {
  @JsonFormat(pattern = GlobalConstants.STANDARD_DATE_FORMAT)
  public LocalDate date;
  public double dividendRealMC;
  public double feeRealMC;
  public double interestCashaccountRealMC;
  public double accumulateReduceMC;
  public double cashBalanceMC;
  public double externalCashTransferMC;
  public double securitiesMC;
  public double marginCloseGainMC;
  public double securityRiskMC;
  public double gainMC;

  public PeriodHoldingAndDiff() {
  }

  public PeriodHoldingAndDiff(LocalDate date, double dividendRealMC, double feeRealMC, double interestCashaccountRealMC,
      double accumulateReduceMC, double cashBalanceMC, double externalCashTransferMC, double securitiesMC,
      double securityRiskMC, double gainMC, double marginCloseGainMC) {
    this.date = date;
    this.dividendRealMC = dividendRealMC;
    this.feeRealMC = feeRealMC;
    this.interestCashaccountRealMC = interestCashaccountRealMC;
    this.accumulateReduceMC = accumulateReduceMC;
    this.cashBalanceMC = cashBalanceMC;
    this.externalCashTransferMC = externalCashTransferMC;
    this.securitiesMC = securitiesMC;
    this.marginCloseGainMC = marginCloseGainMC;
    this.securityRiskMC = securityRiskMC;
    this.gainMC = gainMC;
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

  public double getSecuritiesAndMarginGainMC() {
    return DataHelper.roundStandard(securitiesMC + marginCloseGainMC);
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
    return DataHelper.roundStandard(gainMC + marginCloseGainMC);
  }

  public double getTotalBalanceMC() {
    return DataHelper.roundStandard(cashBalanceMC + this.securitiesMC + marginCloseGainMC);
  }

  public PeriodHoldingAndDiff calculateDiff(PeriodHoldingAndDiff subtrahendsHolding)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    PropertyDescriptor[] propertyDescriptors = PropertyUtils.getPropertyDescriptors(this);
    PeriodHoldingAndDiff diff = new PeriodHoldingAndDiff();
    for (PropertyDescriptor property : propertyDescriptors) {
      if (property.getWriteMethod() != null && property.getPropertyType() == double.class) {
        double minuedValue = (double) PropertyUtils.getProperty(this, property.getName());
        double subtrahends = (double) PropertyUtils.getProperty(subtrahendsHolding, property.getName());
        PropertyUtils.setProperty(diff, property.getName(), DataHelper.roundStandard(minuedValue - subtrahends));
      }
    }
    return diff;
  }

}