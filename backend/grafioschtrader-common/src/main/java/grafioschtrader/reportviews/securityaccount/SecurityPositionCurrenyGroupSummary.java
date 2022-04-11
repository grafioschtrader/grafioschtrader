package grafioschtrader.reportviews.securityaccount;

import grafioschtrader.common.DataHelper;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Summary of securities grouped by currency. A single security account may
 * produce one or more instance of this class.
 *
 * @author Hugo Graf
 *
 */
public class SecurityPositionCurrenyGroupSummary extends SecurityPositionGroupSummary {

  /**
   * The newest exchange rate form this currency to the main currency. It is not
   * used for calculations.
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
