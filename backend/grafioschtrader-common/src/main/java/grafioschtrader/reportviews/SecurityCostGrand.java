package grafioschtrader.reportviews;

import java.util.Map;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataHelper;

public abstract class SecurityCostGrand<S, T> extends MapGroup<S, T> {
  public String mainCurrency;

  public double grandCountTransaction;
  public double grandCountPaidTransaction;
  public double grandTotalTaxCostMC;
  public double grandTotalTransactionCostMC;
  public double grandTotalAverageTransactionCostMC;

  public abstract SecurityCostGroup getSecurityCostGroup(T groupSummary);

  protected Map<String, Integer> currencyPrecisionMap;
  protected int precisionMC;

  public SecurityCostGrand(String currency, Map<S, T> groupMap, Map<String, Integer> currencyPrecisionMap) {
    super(groupMap);
    this.mainCurrency = currency;
    this.currencyPrecisionMap = currencyPrecisionMap;
    this.precisionMC = currencyPrecisionMap.getOrDefault(mainCurrency, GlobalConstants.FID_STANDARD_FRACTION_DIGITS);
  }

  public double getGrandTotalTaxCostMC() {
    return DataHelper.round(grandTotalTaxCostMC, precisionMC);
  }

  public double getGrandTotalTransactionCostMC() {
    return DataHelper.round(grandTotalTransactionCostMC, precisionMC);
  }

  public double getGrandTotalAverageTransactionCostMC() {
    return DataHelper.round(grandTotalAverageTransactionCostMC, precisionMC);
  }

  public void caclulateGrandSummary() {
    this.groupMap.forEach((idSecuritycurrency, groupSummary) -> {

      SecurityCostGroup securityCostGroup = getSecurityCostGroup(groupSummary);

      securityCostGroup.caclulateGroupSummary();
      grandTotalTaxCostMC += securityCostGroup.groupTotalTaxCostMC;
      grandTotalTransactionCostMC += securityCostGroup.groupTotalTransactionCostMC;
      grandCountPaidTransaction += securityCostGroup.groupCountPaidTransaction;
    });
    if (grandCountPaidTransaction > 0) {
      grandTotalAverageTransactionCostMC = grandTotalTransactionCostMC / grandCountPaidTransaction;
    }
  }

}
