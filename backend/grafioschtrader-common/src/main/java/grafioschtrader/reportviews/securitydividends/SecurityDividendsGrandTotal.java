package grafioschtrader.reportviews.securitydividends;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataHelper;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.SecurityCostGrand;
import grafioschtrader.reportviews.SecurityCostGroup;

/**
 * One single object for the whole dividends report.
 *
 * @author Hugo Graf
 *
 */
public class SecurityDividendsGrandTotal extends SecurityCostGrand<Integer, SecurityDividendsYearGroup> {

  public double grandInterestMC;
  public double grandFeeMC;
  public double grandRealReceivedDivInterestMC;
  public double grandTaxableAmountMC;
  public List<Portfolio> portfolioList;

  public SecurityDividendsGrandTotal(String mainCurrency, Map<String, Integer> currencyPrecisionMap) {
    super(mainCurrency, new TreeMap<>(), currencyPrecisionMap);
  }

  @Override
  protected SecurityDividendsYearGroup createInstance(Integer key) {
    return new SecurityDividendsYearGroup(key,
        currencyPrecisionMap.getOrDefault(mainCurrency, GlobalConstants.FID_STANDARD_FRACTION_DIGITS),
        currencyPrecisionMap);
  }

  public void calcDivInterest() {
    groupMap.values().forEach(securityDividendsYearGroup -> {
      securityDividendsYearGroup.calcDivInterest();
      this.grandFeeMC += securityDividendsYearGroup.yearFeeMC;
      this.grandInterestMC += securityDividendsYearGroup.yearInterestMC;
      this.grandRealReceivedDivInterestMC += securityDividendsYearGroup.yearRealReceivedDivInterestMC;
      this.grandTaxableAmountMC += securityDividendsYearGroup.yearTaxableAmountMC;
    });

  }

  public void attachHistoryquoteAndCalcPositionTotal(Map<Integer, Map<Integer, Historyquote>> historyquoteYearIdMap,
      DateTransactionCurrencypairMap dateCurrencyMap) {
    groupMap.values().forEach(securityDividendsYearGroup -> securityDividendsYearGroup
        .attachHistoryquoteAndCalcPositionTotal(historyquoteYearIdMap, dateCurrencyMap));
    this.caclulateGrandSummary();
  }

  public Collection<SecurityDividendsYearGroup> getSecurityDividendsYearGroup() {
    return groupMap.values();
  }

  @Override
  public SecurityCostGroup getSecurityCostGroup(SecurityDividendsYearGroup groupSummary) {
    return groupSummary.securityCostGroup;
  }

  public Integer getNumberOfSecurityAccounts() {
    return portfolioList.stream().map(portfolio -> portfolio.getSecurityaccountList()).mapToInt(List::size).sum();
  }

  public Integer getNumberOfCashAccounts() {
    return portfolioList.stream().map(portfolio -> portfolio.getCashaccountList()).mapToInt(List::size).sum();
  }

  public double getGrandFeeMC() {
    return DataHelper.round(grandFeeMC, precisionMC);
  }

  public double getGrandInterestMC() {
    return DataHelper.round(grandInterestMC, precisionMC);
  }

  public double getGrandRealReceivedDivInterestMC() {
    return DataHelper.round(grandRealReceivedDivInterestMC, precisionMC);
  }

  public double getGrandTaxableAmountMC() {
    return DataHelper.round(grandTaxableAmountMC, precisionMC);
  }

}
