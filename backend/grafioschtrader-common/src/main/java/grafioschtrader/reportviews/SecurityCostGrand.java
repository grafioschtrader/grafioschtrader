package grafioschtrader.reportviews;

import java.util.Map;

public abstract class SecurityCostGrand<S, T> extends MapGroup<S, T> {
  public String mainCurrency;

  public double grandCountTransaction;
  public double grandCountPaidTransaction;
  public double grandTotalTaxCostMc;
  public double grandTotalTransactionCostMC;
  public double grandTotalAverageTransactionCostMC;

  public abstract SecurityCostGroup getSecurityCostGroup(T groupSummary);

  public SecurityCostGrand(String currency, Map<S, T> groupMap) {
    super(groupMap);
    this.mainCurrency = currency;
  }

  public void caclulateGrandSummary() {
    this.groupMap.forEach((idSecuritycurrency, groupSummary) -> {

      SecurityCostGroup securityCostGroup = getSecurityCostGroup(groupSummary);

      securityCostGroup.caclulateGroupSummary();
      grandTotalTaxCostMc += securityCostGroup.groupTotalTaxCostMc;
      grandTotalTransactionCostMC += securityCostGroup.groupTotalTransactionCostMC;
      grandCountPaidTransaction += securityCostGroup.groupCountPaidTransaction;
    });
    if (grandCountPaidTransaction > 0) {
      grandTotalAverageTransactionCostMC = grandTotalTransactionCostMC / grandCountPaidTransaction;
    }
  }

}
