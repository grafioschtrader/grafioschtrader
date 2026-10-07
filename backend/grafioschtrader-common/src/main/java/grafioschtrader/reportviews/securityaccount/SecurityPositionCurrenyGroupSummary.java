package grafioschtrader.reportviews.securityaccount;

import com.fasterxml.jackson.annotation.JsonIgnore;

import grafiosch.common.DataHelper;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Summary of securities grouped by currency. A single security account may produce one or more instance of this class.
 */
public class SecurityPositionCurrenyGroupSummary extends SecurityPositionGroupSummary {

  /**
   * The newest exchange rate form this currency to the main currency. It is not used for calculations.
   */
  public double currencyExchangeRate;

  /**
   * In this case the currency represents the group.
   */
  public String currency;

  @Schema(description = "Total Gain/Loss for all Position of a currency")
  public double groupGainLossSecurity = 0.0;

  public double groupTransactionCost = 0.0;

  public double groupAccountValueSecurity;
  public double groupTaxCost = 0.0;

  /**
   * Net disposal proceeds in this currency of the positions that settle into a cash account of this currency. The
   * portfolio report adds them to the cash balance before it estimates the markup of converting that currency into the
   * main currency.
   */
  @JsonIgnore
  public double groupDisposalSameCurrencyNet;

  public SecurityPositionCurrenyGroupSummary(String currency, double currencyExchangeRate, int precision) {
    super(precision);
    this.currency = currency;
    this.currencyExchangeRate = currencyExchangeRate;
  }

  @Override
  public void addToGroupSummaryAndCalcGroupTotals(SecurityPositionSummary securityPositionSummary) {
    super.addToGroupSummaryAndCalcGroupTotals(securityPositionSummary);
    groupAccountValueSecurity += securityPositionSummary.accountValueSecurity;
    groupTaxCost += securityPositionSummary.taxCost;
    groupGainLossSecurity += securityPositionSummary.gainLossSecurity;
    groupTransactionCost += securityPositionSummary.transactionCost;
    groupDisposalSameCurrencyNet += securityPositionSummary.disposalSameCurrencyNet;
  }

  public double getGroupGainLossSecurity() {
    return DataHelper.round(groupGainLossSecurity, precision);
  }

  public double getGroupTransactionCost() {
    return DataHelper.round(groupTransactionCost, precision);
  }

  public double getGroupAccountValueSecurity() {
    return DataHelper.round(groupAccountValueSecurity, precision);
  }

  public double getGroupTaxCost() {
    return DataHelper.round(groupTaxCost, precision);
  }

}
