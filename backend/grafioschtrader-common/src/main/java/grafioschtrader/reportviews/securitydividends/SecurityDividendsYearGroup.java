package grafioschtrader.reportviews.securitydividends;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import grafioschtrader.common.DataHelper;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securitysplit;
import grafioschtrader.entities.Securitysplit.SplitFactorAfterBefore;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.MapGroup;
import grafioschtrader.reportviews.SecurityCostGroup;

/**
 * Represent a year of the dividends report.
 *
 */
public class SecurityDividendsYearGroup extends MapGroup<Integer, SecurityDividendsPosition> {

  public Integer year;
  public double valueAtEndOfYearMC;
  public double yearInterestMC;
  public double yearFeeMC;
  public double yearAutoPaidTaxMC;
  public double yearTaxableAmountMC;
  public int yearCountPaidTransactions;
  public double yearRealReceivedDivInterestMC;
  public SecurityCostGroup securityCostGroup;

  protected int precisionMC;
  private Map<String, Integer> currencyPrecisionMap;

  public SecurityDividendsYearGroup(Integer year, int precisionMC, Map<String, Integer> currencyPrecisionMap) {
    super(new HashMap<>());
    this.year = year;
    this.precisionMC = precisionMC;
    this.currencyPrecisionMap = currencyPrecisionMap;
    securityCostGroup = new SecurityCostGroup(precisionMC);
  }

  public void calcDivInterest() {
    groupMap.values().forEach(securityDividendsPosition -> {
      if (securityDividendsPosition.valueAtEndOfYearMC != null) {
        valueAtEndOfYearMC += securityDividendsPosition.valueAtEndOfYearMC;
      }
    });

  }

  @Override
  protected SecurityDividendsPosition createInstance(Integer key) {
    return new SecurityDividendsPosition(key, precisionMC, currencyPrecisionMap);
  }

  public List<SecurityDividendsPosition> getSecurityDividendsPositions() {
    return groupMap.values().stream().sorted((x, y) -> x.security.getName().compareTo(y.security.getName()))
        .collect(Collectors.toList());
  }

  public SecurityDividendsPosition getOrCreateSecurityDividendsPosition(Security security) {
    SecurityDividendsPosition securityDividendsPosition = this.getOrCreateGroup(security.getIdSecuritycurrency());
    securityDividendsPosition.security = security;
    return securityDividendsPosition;
  }

  public void attachHistoryquoteAndCalcPositionTotal(Map<Integer, Map<Integer, Historyquote>> historyquoteYearIdMap,
      DateTransactionCurrencypairMap dateCurrencyMap) {

    groupMap.values().forEach(securityDividendsPosition -> {
      securityDividendsPosition.attachHistoryquoteAndCalcPositionTotal(historyquoteYearIdMap.get(year),
          dateCurrencyMap);
      yearCountPaidTransactions += securityDividendsPosition.countPaidTransactions;
      yearRealReceivedDivInterestMC += securityDividendsPosition.realReceivedDivInterestMC;
      yearAutoPaidTaxMC += securityDividendsPosition.autoPaidTaxMC;
      yearTaxableAmountMC += securityDividendsPosition.taxableAmountMC;
    });
    securityCostGroup.calcAverages(yearCountPaidTransactions);
  }

  public void fillYearWithOpenPositions(Map<Integer, UnitsCounter> unitsCounterBySecurityMap,
      final Map<Integer, List<Securitysplit>> securitysplitMap) {
    Date fromDate = new GregorianCalendar(year, 0, 1).getTime();
    Date untilDate = new GregorianCalendar(year, 11, 31).getTime();
    for (Map.Entry<Integer, UnitsCounter> entry : unitsCounterBySecurityMap.entrySet()) {
      SecurityDividendsPosition securityDividendsPosition = groupMap.get(entry.getKey());
      if (securityDividendsPosition == null && entry.getValue().units != 0) {
        SplitFactorAfterBefore splitFactorAfterBefore = Securitysplit.calcSplitFatorForFromDateAndToDate(entry.getKey(),
            fromDate, untilDate, securitysplitMap);
        entry.getValue().units = entry.getValue().units * splitFactorAfterBefore.fromToDateFactor;
        securityDividendsPosition = this.getOrCreateSecurityDividendsPosition(entry.getValue().security);
      }
      if (securityDividendsPosition != null) {
        securityDividendsPosition.unitsAtEndOfYear = entry.getValue().units;
      }
    }
  }

  public void addInterest(Double interestMC) {
    if (interestMC != null) {
      yearInterestMC += interestMC;
    }
  }

  public void addFee(Double feeMC) {
    if (feeMC != null) {
      yearFeeMC += feeMC;
    }
  }

  public double getValueAtEndOfYearMC() {
    return DataHelper.round(valueAtEndOfYearMC, precisionMC);
  }

  public double getYearInterestMC() {
    return DataHelper.round(yearInterestMC, precisionMC);
  }

  public double getYearFeeMC() {
    return DataHelper.round(yearFeeMC, precisionMC);
  }

  public double getYearAutoPaidTaxMC() {
    return DataHelper.round(yearAutoPaidTaxMC, precisionMC);

  }

  public double getYearTaxableAmountMC() {
    return DataHelper.round(yearTaxableAmountMC, precisionMC);
  }

  public double getYearRealReceivedDivInterestMC() {
    return DataHelper.round(yearRealReceivedDivInterestMC, precisionMC);
  }

}
