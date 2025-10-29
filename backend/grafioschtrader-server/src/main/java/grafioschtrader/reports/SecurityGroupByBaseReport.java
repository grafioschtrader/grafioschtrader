package grafioschtrader.reports;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.PropertyUtils;

import grafioschtrader.entities.Security;
import grafioschtrader.entities.Tenant;
import grafioschtrader.reportviews.DateTransactionCurrencypairMap;
import grafioschtrader.reportviews.securityaccount.SecurityPositionDynamicGrandSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionDynamicGroupSummary;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;

/**
 * Generic security position report generator that enables dynamic grouping by any single field within the Security
 * entity hierarchy. Provides flexible portfolio analysis by allowing users to organize position data according to
 * various classification criteria such as asset class, investment instrument type, or custom security attributes.
 * 
 * <p>
 * This class extends the base security position reporting functionality with sophisticated reflection-based field
 * access, enabling runtime specification of grouping criteria without requiring compile-time knowledge of the specific
 * field structure. It supports nested property access using Apache Commons BeanUtils for maximum flexibility.
 * </p>
 * 
 * <h3>Common Grouping Scenarios:</h3>
 * <p>
 * The class provides predefined field constants for frequently used grouping criteria:
 * </p>
 * <ul>
 * <li><strong>Asset Class Category:</strong> Groups by broad investment categories (stocks, bonds, etc.)</li>
 * <li><strong>Investment Instrument:</strong> Groups by specific instrument types within asset classes</li>
 * <li><strong>Asset Class ID:</strong> Groups by unique asset class identifiers for detailed analysis</li>
 * </ul>
 * 
 * @param <T> the type of the grouping field value, enabling type-safe group key management and ensuring consistent
 *            typing across the grouping hierarchy
 */
public class SecurityGroupByBaseReport<T> extends SecurityPositionSummaryReport {
  public String fieldName;
  public String value;
  public static String ASSETCLASS_CATEGORY_FIELD_NAME = "assetClass.categoryType";
  public static String ASSETCLASS_SPEC_INVEST_INST_FIELD_NAME = "assetClass.specialInvestmentInstrument";
  public static String ASSETCLASS_ID_ASSETCLASS_FIELD_NAME = "assetClass.idAssetClass";

  public SecurityGroupByBaseReport(String fieldName) {
    super();
    this.fieldName = fieldName;
  }

  @Override
  protected SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<T>> createGroupsAndCalcGrandTotal(
      final Tenant tenant, List<SecurityPositionSummary> securityPositionSummaryList,
      DateTransactionCurrencypairMap dateCurrencyMap) throws Exception {
    Map<T, SecurityPositionDynamicGroupSummary<T>> groupMap = new HashMap<>();

    ReportHelper.loadUntilDateHistoryquotes(historyquoteJpaRepository, dateCurrencyMap);

    for (final SecurityPositionSummary securityPositionSummary : securityPositionSummaryList) {
      Security security = securityPositionSummary.getSecurity();

      if (security.getIdSecuritycurrency() < 0 && securityPositionSummary.valueSecurity == 0) {
        continue;
      }
      double currencyExchangeRate = (security.getCurrency().equals(dateCurrencyMap.getMainCurrency())) ? 1.0
          : dateCurrencyMap.isUntilDateEqualNowOrAfter()
              ? dateCurrencyMap.getCurrencypairByFromCurrency(security.getCurrency()).getSLast()
              : dateCurrencyMap.getExactDateAndFromCurrency(dateCurrencyMap.getUntilDate(), security.getCurrency());

      T groupValue = getGroupValue(security);
      SecurityPositionDynamicGroupSummary<T> securityPositionDynamicGroupSummary = groupMap.computeIfAbsent(groupValue,
          gv -> new SecurityPositionDynamicGroupSummary<>(gv));
      securityPositionSummary.calcMainCurrency(currencyExchangeRate);
      securityPositionDynamicGroupSummary.addToGroupSummaryAndCalcGroupTotals(securityPositionSummary);
    }
    return createAndCalcGrandTotal(groupMap, dateCurrencyMap);
  }

  /**
   * Creates the final grand summary by aggregating all group summaries and calculating comprehensive totals across the
   * entire portfolio. This method produces the top-level summary structure that provides both detailed group-by-group
   * analysis and overall portfolio totals for complete investment analysis.
   * 
   * @param groupMap        the map of grouped position summaries organized by field value
   * @param dateCurrencyMap currency context for precision settings and final calculations
   * @return comprehensive grand summary with grouped positions and portfolio-wide totals
   */
  @SuppressWarnings("unchecked")
  protected SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<T>> createAndCalcGrandTotal(
      Map<T, SecurityPositionDynamicGroupSummary<T>> groupMap, DateTransactionCurrencypairMap dateCurrencyMap) {
    final SecurityPositionDynamicGrandSummary<SecurityPositionDynamicGroupSummary<T>> securityPositionGrandSummary = new SecurityPositionDynamicGrandSummary<>(
        dateCurrencyMap.getMainCurrency(),
        globalparametersService.getPrecisionForCurrency(dateCurrencyMap.getMainCurrency()));
    for (final Map.Entry<T, SecurityPositionDynamicGroupSummary<T>> ospcs : groupMap.entrySet()) {
      securityPositionGrandSummary.calcGrandTotal(
          (SecurityPositionDynamicGroupSummary<SecurityPositionDynamicGroupSummary<T>>) ospcs.getValue());
    }
    securityPositionGrandSummary.roundGrandTotals();

    return securityPositionGrandSummary;
  }

  @SuppressWarnings("unchecked")
  protected T getGroupValue(Security security)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    return (T) PropertyUtils.getNestedProperty(security, fieldName);
  }

}
